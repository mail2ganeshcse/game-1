package com.kajakaja.game;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741";

    private AdView bannerAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        FrameLayout root = new FrameLayout(this);
        KajakajaView gameView = new KajakajaView(this);
        gameView.setGameStateListener(isRunning -> {
            if (bannerAd != null) {
                bannerAd.setVisibility(isRunning ? View.VISIBLE : View.GONE);
            }
        });

        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        bannerAd = new AdView(this);
        bannerAd.setAdUnitId(TEST_BANNER_AD_UNIT_ID);
        bannerAd.setAdSize(AdSize.BANNER);
        bannerAd.setVisibility(View.GONE);
        FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        root.addView(bannerAd, adParams);

        setContentView(root);
        new Thread(() -> MobileAds.initialize(this, initializationStatus -> {})).start();
        bannerAd.loadAd(new AdRequest.Builder().build());
        hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        if (bannerAd != null) {
            bannerAd.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    static final class KajakajaView extends View {
        private static final int MODE_TITLE = 0;
        private static final int MODE_RUN = 1;
        private static final int MODE_LEVEL_CLEAR = 2;
        private static final int MODE_GAME_OVER = 3;

        private static final int TYPE_TRAP = 0;
        private static final int TYPE_SAMOSA = 1;
        private static final int TYPE_CLUE = 2;
        private static final int TYPE_GATE = 3;
        private static final int TYPE_BLOCK = 4;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random(7);
        private final List<Entity> entities = new ArrayList<>();
        private final Bitmap openingArt;
        private GameStateListener gameStateListener;
        private boolean lastRunningState = false;

        private int mode = MODE_TITLE;
        private int level = 1;
        private int lane = 1;
        private int targetLane = 1;
        private int score = 0;
        private int samosas = 0;
        private int clues = 0;
        private int blockers = 0;
        private int routeAnswer = 1;
        private int chosenRoute = -1;
        private String failureReason = "Caught by the danger";
        private float playerY;
        private float world = 0f;
        private float speed = 480f;
        private float danger = 0.18f;
        private float dash = 0f;
        private float spawnAt = 650f;
        private float gateAt = 3600f;
        private float flash = 0f;
        private float levelPause = 0f;
        private long lastFrame = 0L;
        private float downX;
        private float downY;
        private long downTime;
        private float bgShift;

        KajakajaView(Activity activity) {
            super(activity);
            openingArt = BitmapFactory.decodeResource(getResources(), R.drawable.kajakaja_opening);
            setFocusable(true);
            text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        }

        void setGameStateListener(GameStateListener listener) {
            gameStateListener = listener;
            notifyGameState();
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 0f : Math.min(0.033f, (now - lastFrame) / 1_000_000_000f);
            lastFrame = now;

            if (mode == MODE_RUN) update(dt);
            else if (mode == MODE_LEVEL_CLEAR) {
                levelPause -= dt;
                if (levelPause <= 0f) nextLevel();
            }

            drawWorld(c);
            drawEntities(c);
            drawHero(c);
            drawHud(c);
            drawOverlay(c);
            notifyGameState();
            postInvalidateOnAnimation();
        }

        private void notifyGameState() {
            boolean isRunning = mode == MODE_RUN;
            if (gameStateListener != null && isRunning != lastRunningState) {
                lastRunningState = isRunning;
                gameStateListener.onRunningStateChanged(isRunning);
            }
        }

        private void update(float dt) {
            float runSpeed = speed + level * 38f + (dash > 0f ? 320f : 0f);
            if (dash > 0f) dash = Math.max(0f, dash - dt * 0.55f);
            world += runSpeed * dt;
            bgShift += runSpeed * dt;
            danger += dt * (0.035f + level * 0.005f + stageIndex() * 0.003f);
            flash = Math.max(0f, flash - dt);

            float[] lanes = lanes();
            playerY += (lanes[targetLane] - playerY) * Math.min(1f, dt * 11f);
            if (Math.abs(playerY - lanes[targetLane]) < 3f) lane = targetLane;

            if (world > spawnAt) spawnChunk();
            if (world > gateAt && !hasGate()) spawnGate();

            Iterator<Entity> it = entities.iterator();
            while (it.hasNext()) {
                Entity e = it.next();
                e.x -= runSpeed * dt;
                if (e.x < -160f) {
                    it.remove();
                    continue;
                }
                if (e.type != TYPE_GATE && e.lane == lane && Math.abs(e.x - heroX()) < 54f && Math.abs(e.y - playerY) < 58f) {
                    if (e.type == TYPE_TRAP) {
                        danger += 0.19f;
                        dash = 0f;
                        flash = 0.22f;
                    } else if (e.type == TYPE_SAMOSA) {
                        samosas++;
                        score += 80;
                        dash = Math.min(1f, dash + 0.18f);
                        danger = Math.max(0.06f, danger - 0.045f);
                    } else if (e.type == TYPE_CLUE) {
                        clues++;
                        score += 160;
                    } else if (e.type == TYPE_BLOCK) {
                        blockers++;
                        score += 140;
                        danger = Math.max(0.05f, danger - 0.035f);
                    }
                    it.remove();
                } else if (e.type == TYPE_GATE && e.x < heroX() + 36f && e.x > heroX() - 30f) {
                    int route = Math.max(0, Math.min(2, lane));
                    chosenRoute = route;
                    if (route == routeAnswer && clues >= requiredClues() && blockers >= requiredBlockers()) {
                        score += 700 + level * 150;
                        danger = Math.max(0.04f, danger - 0.22f);
                        mode = MODE_LEVEL_CLEAR;
                        levelPause = 1.15f;
                    } else {
                        failureReason = route != routeAnswer
                                ? "Wrong path. " + threatName() + " found you."
                                : "Task failed. " + threatName() + " broke through.";
                        danger = 1f;
                        flash = 0.28f;
                    }
                }
            }

            if (danger >= 1f) {
                failureReason = failureReason == null ? "Caught by the danger" : failureReason;
                mode = MODE_GAME_OVER;
            }
        }

        private void spawnChunk() {
            float w = getWidth();
            int safeLane = random.nextInt(3);
            for (int i = 0; i < 3; i++) {
                if (i != safeLane && random.nextFloat() < 0.68f) {
                    entities.add(new Entity(TYPE_TRAP, w + 120f + random.nextInt(90), laneY(i), i));
                }
            }
            if (random.nextFloat() < 0.72f) {
                int l = random.nextInt(3);
                entities.add(new Entity(TYPE_SAMOSA, w + 260f + random.nextInt(180), laneY(l), l));
            }
            if (clues < requiredClues() && random.nextFloat() < 0.48f) {
                int l = random.nextInt(3);
                entities.add(new Entity(TYPE_CLUE, w + 420f + random.nextInt(160), laneY(l), l));
            }
            if (blockers < requiredBlockers() && random.nextFloat() < 0.58f) {
                int l = random.nextInt(3);
                entities.add(new Entity(TYPE_BLOCK, w + 500f + random.nextInt(220), laneY(l), l));
            }
            spawnAt = world + 500f - Math.min(180f, level * 20f);
        }

        private void spawnGate() {
            routeAnswer = random.nextInt(3);
            entities.add(new Entity(TYPE_GATE, getWidth() + 170f, laneY(1), 1));
            gateAt = Float.MAX_VALUE;
        }

        private boolean hasGate() {
            for (Entity e : entities) if (e.type == TYPE_GATE) return true;
            return false;
        }

        private void startGame() {
            mode = MODE_RUN;
            level = 1;
            score = 0;
            samosas = 0;
            failureReason = "Caught by the danger";
            setupLevel();
        }

        private void setupLevel() {
            entities.clear();
            lane = 1;
            targetLane = 1;
            world = 0f;
            speed = 460f + level * 24f;
            danger = Math.min(0.25f, 0.12f + level * 0.012f);
            dash = 0f;
            clues = 0;
            blockers = 0;
            chosenRoute = -1;
            spawnAt = 380f;
            gateAt = 3700f + level * 480f;
            playerY = laneY(1);
            routeAnswer = random.nextInt(3);
            failureReason = "Caught by " + threatName();
            mode = MODE_RUN;
        }

        private void nextLevel() {
            level++;
            setupLevel();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                downTime = System.currentTimeMillis();
                if (mode == MODE_TITLE || mode == MODE_GAME_OVER) {
                    startGame();
                    return true;
                }
                if (mode == MODE_LEVEL_CLEAR) {
                    nextLevel();
                    return true;
                }
            } else if (e.getAction() == MotionEvent.ACTION_UP && mode == MODE_RUN) {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (Math.abs(dy) > 32f && Math.abs(dy) > Math.abs(dx)) {
                    targetLane = clamp(targetLane + (dy > 0 ? 1 : -1), 0, 2);
                } else if (System.currentTimeMillis() - downTime < 220) {
                    if (e.getY() < getHeight() * 0.44f) targetLane = clamp(targetLane - 1, 0, 2);
                    else if (e.getY() > getHeight() * 0.58f) targetLane = clamp(targetLane + 1, 0, 2);
                    else if (samosas > 0) {
                        samosas--;
                        dash = 1f;
                    }
                }
            }
            return true;
        }

        private void drawWorld(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            drawCinematicForest(c, w, h);

            p.setColor(Color.rgb(103, 73, 42));
            Path road = new Path();
            road.moveTo(0, h * 0.38f);
            road.lineTo(w, h * 0.28f);
            road.lineTo(w, h);
            road.lineTo(0, h);
            road.close();
            c.drawPath(road, p);

            p.setShader(new LinearGradient(0, h * 0.32f, 0, h, Color.argb(70, 180, 130, 68), Color.argb(150, 45, 28, 14), Shader.TileMode.CLAMP));
            c.drawPath(road, p);
            p.setShader(null);

            p.setStrokeWidth(4f);
            p.setColor(Color.argb(120, 242, 193, 92));
            for (int i = 0; i < 3; i++) {
                float y = laneY(i);
                c.drawLine(0, y + 42, w, y + 18, p);
            }
            p.setStrokeWidth(1f);

            p.setColor(Color.argb(175, 92, 22, 22));
            c.drawRect(0, 0, danger * w, h, p);
            p.setColor(Color.rgb(255, 167, 53));
            c.drawRect(danger * w - 8f, 0, danger * w, h, p);
            drawThreat(c, danger * w - 92f, h);
        }

        private void drawEntities(Canvas c) {
            for (Entity e : entities) {
                if (e.type == TYPE_TRAP) drawTrap(c, e.x, e.y);
                else if (e.type == TYPE_SAMOSA) drawSamosa(c, e.x, e.y, 28f);
                else if (e.type == TYPE_CLUE) drawClue(c, e.x, e.y);
                else if (e.type == TYPE_BLOCK) drawBlocker(c, e.x, e.y);
                else drawGate(c, e.x);
            }
        }

        private void drawHero(Canvas c) {
            float x = heroX();
            p.setColor(Color.argb(90, 0, 0, 0));
            c.drawOval(new RectF(x - 38, playerY + 34, x + 42, playerY + 52), p);
            p.setColor(Color.rgb(34, 32, 30));
            c.drawCircle(x, playerY - 28, 22, p);
            p.setColor(Color.rgb(241, 181, 89));
            c.drawCircle(x + 2, playerY - 31, 16, p);
            p.setColor(Color.rgb(217, 80, 39));
            c.drawRoundRect(new RectF(x - 22, playerY - 9, x + 27, playerY + 39), 10, 10, p);
            p.setStrokeWidth(8f);
            p.setColor(Color.rgb(245, 207, 113));
            c.drawLine(x - 8, playerY + 35, x - 31, playerY + 58, p);
            c.drawLine(x + 18, playerY + 34, x + 42, playerY + 50, p);
            c.drawLine(x - 18, playerY + 6, x - 42, playerY + 22, p);
            c.drawLine(x + 22, playerY + 7, x + 48, playerY - 1, p);
            p.setStrokeWidth(1f);
            if (dash > 0f) {
                p.setColor(Color.argb(145, 255, 218, 82));
                c.drawCircle(x - 28, playerY + 12, 42 + dash * 22, p);
            }
        }

        private void drawTrap(Canvas c, float x, float y) {
            p.setColor(Color.rgb(58, 43, 34));
            c.drawRect(x - 34, y + 10, x + 36, y + 28, p);
            p.setColor(Color.rgb(194, 184, 151));
            Path spike = new Path();
            for (int i = -2; i <= 2; i++) {
                spike.reset();
                spike.moveTo(x + i * 16f, y - 30);
                spike.lineTo(x + i * 16f - 8, y + 10);
                spike.lineTo(x + i * 16f + 8, y + 10);
                spike.close();
                c.drawPath(spike, p);
            }
        }

        private void drawSamosa(Canvas c, float x, float y, float r) {
            p.setColor(Color.rgb(245, 183, 69));
            Path tri = new Path();
            tri.moveTo(x, y - r);
            tri.lineTo(x + r * 1.15f, y + r);
            tri.lineTo(x - r * 1.15f, y + r);
            tri.close();
            c.drawPath(tri, p);
            p.setColor(Color.rgb(179, 85, 32));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5f);
            c.drawPath(tri, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(1f);
        }

        private void drawClue(Canvas c, float x, float y) {
            p.setColor(Color.rgb(233, 219, 158));
            c.drawRoundRect(new RectF(x - 28, y - 30, x + 28, y + 30), 6, 6, p);
            p.setColor(Color.rgb(83, 56, 37));
            p.setStrokeWidth(5f);
            c.drawLine(x - 14, y - 5, x + 12, y - 18, p);
            c.drawLine(x + 12, y - 18, x + 17, y + 10, p);
            p.setStrokeWidth(1f);
        }

        private void drawGate(Canvas c, float x) {
            float[] ys = lanes();
            for (int i = 0; i < 3; i++) {
                p.setColor(i == routeAnswer && clues >= requiredClues() && blockers >= requiredBlockers() ? Color.rgb(231, 174, 52) : Color.rgb(74, 55, 37));
                c.drawRoundRect(new RectF(x - 46, ys[i] - 58, x + 46, ys[i] + 58), 18, 18, p);
                p.setColor(Color.rgb(24, 20, 18));
                c.drawRoundRect(new RectF(x - 26, ys[i] - 24, x + 26, ys[i] + 58), 10, 10, p);
            }
        }

        private void drawHud(Canvas c) {
            float w = getWidth();
            p.setColor(Color.argb(150, 10, 13, 12));
            c.drawRoundRect(new RectF(22, 18, w - 22, 104), 8, 8, p);
            text.setTextSize(28f);
            text.setColor(Color.WHITE);
            c.drawText("kajakaja", 42, 54, text);
            text.setTextSize(22f);
            c.drawText(stageName(), 42, 86, text);
            c.drawText("Level " + level, 260, 54, text);
            c.drawText("Score " + score, 380, 54, text);
            c.drawText("Samosas " + samosas, 540, 54, text);
            c.drawText("Clues " + clues + "/" + requiredClues(), 710, 54, text);
            c.drawText("Blocks " + blockers + "/" + requiredBlockers(), 850, 54, text);
            c.drawText("Task: " + objectiveText(), 260, 88, text);
            p.setColor(Color.rgb(54, 55, 51));
            c.drawRoundRect(new RectF(w - 190, 39, w - 42, 59), 6, 6, p);
            p.setColor(Color.rgb(244, 190, 64));
            c.drawRoundRect(new RectF(w - 190, 39, w - 190 + 148 * dash, 59), 6, 6, p);
        }

        private void drawOverlay(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (flash > 0f) {
                p.setColor(Color.argb((int) (flash * 520), 255, 40, 22));
                c.drawRect(0, 0, w, h, p);
            }
            if (mode == MODE_RUN) return;

            p.setColor(Color.argb(190, 6, 9, 8));
            c.drawRect(0, 0, w, h, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.WHITE);
            if (mode == MODE_TITLE) {
                drawOpeningArt(c);
                p.setColor(Color.argb(115, 0, 0, 0));
                c.drawRect(0, 0, w, h, p);
                text.setTextSize(76f);
                c.drawText("kajakaja", w * 0.30f, h * 0.28f, text);
                text.setTextSize(28f);
                c.drawText("Survive living forest stages: snakes, ruins, cliffs, and fire.", w / 2f, h * 0.46f, text);
                c.drawText("Collect clues, carry blockers, seal the threat, then choose the right route.", w / 2f, h * 0.55f, text);
                text.setTextSize(34f);
                c.drawText("Tap to run", w / 2f, h * 0.72f, text);
            } else if (mode == MODE_LEVEL_CLEAR) {
                text.setTextSize(52f);
                c.drawText("Threat blocked", w / 2f, h * 0.44f, text);
                text.setTextSize(28f);
                c.drawText(String.format(Locale.US, "%s opens. Level %d waits.", stageName(), level + 1), w / 2f, h * 0.56f, text);
            } else {
                text.setTextSize(48f);
                c.drawText(failureReason, w / 2f, h * 0.42f, text);
                text.setTextSize(30f);
                c.drawText("Final score " + score + "   Level " + level, w / 2f, h * 0.55f, text);
                c.drawText("Tap to try again", w / 2f, h * 0.68f, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawCinematicForest(Canvas c, int w, int h) {
            if (openingArt != null) {
                float scale = Math.max(w / (float) openingArt.getWidth(), h / (float) openingArt.getHeight());
                int srcW = Math.min(openingArt.getWidth(), (int) (w / scale));
                int srcH = Math.min(openingArt.getHeight(), (int) (h / scale));
                int drift = (int) ((bgShift * 0.04f) % Math.max(1, openingArt.getWidth() - srcW));
                Rect src = new Rect(drift, 0, drift + srcW, srcH);
                c.drawBitmap(openingArt, src, new RectF(0, 0, w, h), p);
                p.setColor(Color.argb(105, 8, 19, 14));
                c.drawRect(0, 0, w, h, p);
            } else {
                p.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(17, 31, 25), Color.rgb(77, 56, 31), Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }

            p.setColor(Color.argb(95, 186, 205, 165));
            for (int i = 0; i < 5; i++) {
                float y = h * (0.16f + i * 0.055f);
                float x = -80f + ((i * 310f - bgShift * 0.06f) % (w + 220f));
                c.drawOval(new RectF(x, y, x + w * 0.42f, y + 42f), p);
            }

            for (int i = -1; i < 14; i++) {
                float x = ((i * 155f) - (bgShift * 0.22f % 155f));
                p.setColor(Color.rgb(19, 60, 38));
                c.drawRect(x, 0, x + 28, h, p);
                p.setColor(Color.rgb(24, 86, 48));
                c.drawCircle(x + 16, 54 + (i % 4) * 26, 70, p);
                p.setColor(Color.rgb(12, 41, 28));
                c.drawRect(x + 28, 0, x + 42, h, p);
            }

            p.setColor(Color.argb(150, 91, 81, 62));
            for (int i = 0; i < 4; i++) {
                float x = (w - ((bgShift * 0.12f + i * 360f) % (w + 260f)));
                c.drawRoundRect(new RectF(x, h * 0.18f, x + 70, h * 0.43f), 8, 8, p);
                p.setColor(Color.argb(130, 55, 50, 41));
                c.drawRect(x - 32, h * 0.39f, x + 102, h * 0.43f, p);
                p.setColor(Color.argb(150, 91, 81, 62));
            }

            p.setStrokeWidth(7f);
            p.setColor(Color.argb(150, 41, 97, 48));
            for (int i = 0; i < 8; i++) {
                float x = ((i * 240f) - (bgShift * 0.35f % 240f));
                Path vine = new Path();
                vine.moveTo(x, 0);
                vine.cubicTo(x + 35, h * 0.16f, x - 28, h * 0.25f, x + 18, h * 0.38f);
                c.drawPath(vine, p);
            }
            p.setStrokeWidth(1f);
        }

        private void drawThreat(Canvas c, float x, float h) {
            float y = laneY(1) + 8f;
            if (stageIndex() == 0) {
                drawSnake(c, x, y);
            } else if (stageIndex() == 1) {
                drawRollingBoulder(c, x, y);
            } else if (stageIndex() == 2) {
                drawFireWall(c, x, h);
            } else {
                drawShadowHunters(c, x, y);
            }
        }

        private void drawSnake(Canvas c, float x, float y) {
            p.setStrokeWidth(34f);
            p.setColor(Color.rgb(21, 88, 43));
            Path body = new Path();
            body.moveTo(x - 170, y + 60);
            body.cubicTo(x - 100, y - 50, x - 30, y + 82, x + 52, y - 10);
            c.drawPath(body, p);
            p.setStrokeWidth(10f);
            p.setColor(Color.rgb(218, 184, 79));
            c.drawPath(body, p);
            p.setStrokeWidth(1f);
            p.setColor(Color.rgb(24, 103, 48));
            c.drawOval(new RectF(x + 22, y - 54, x + 120, y + 38), p);
            p.setColor(Color.rgb(255, 60, 40));
            c.drawCircle(x + 88, y - 20, 8, p);
            p.setColor(Color.rgb(255, 225, 120));
            c.drawCircle(x + 62, y - 24, 6, p);
            c.drawCircle(x + 90, y - 22, 6, p);
        }

        private void drawRollingBoulder(Canvas c, float x, float y) {
            p.setColor(Color.rgb(76, 72, 64));
            c.drawCircle(x, y, 72, p);
            p.setColor(Color.rgb(131, 124, 101));
            p.setStrokeWidth(8f);
            c.drawArc(new RectF(x - 45, y - 45, x + 45, y + 45), bgShift % 360, 250, false, p);
            p.setStrokeWidth(1f);
        }

        private void drawFireWall(Canvas c, float x, float h) {
            for (int i = 0; i < 7; i++) {
                p.setColor(i % 2 == 0 ? Color.rgb(252, 105, 28) : Color.rgb(255, 196, 57));
                Path flame = new Path();
                float fx = x + i * 28f;
                flame.moveTo(fx, h);
                flame.lineTo(fx + 35, h * 0.52f + (i % 3) * 28f);
                flame.lineTo(fx + 70, h);
                flame.close();
                c.drawPath(flame, p);
            }
        }

        private void drawShadowHunters(Canvas c, float x, float y) {
            p.setColor(Color.rgb(16, 16, 18));
            for (int i = 0; i < 3; i++) {
                float hx = x - i * 58f;
                c.drawCircle(hx, y - 34, 20, p);
                c.drawRoundRect(new RectF(hx - 18, y - 16, hx + 20, y + 50), 8, 8, p);
                p.setColor(Color.rgb(255, 91, 43));
                c.drawCircle(hx + 7, y - 38, 5, p);
                p.setColor(Color.rgb(16, 16, 18));
            }
        }

        private void drawBlocker(Canvas c, float x, float y) {
            p.setColor(Color.rgb(102, 70, 38));
            c.drawRoundRect(new RectF(x - 42, y - 20, x + 42, y + 20), 12, 12, p);
            p.setColor(Color.rgb(165, 112, 54));
            c.drawCircle(x - 30, y, 17, p);
            c.drawCircle(x + 30, y, 17, p);
            p.setColor(Color.rgb(70, 45, 24));
            p.setStrokeWidth(4f);
            c.drawLine(x - 16, y - 17, x - 4, y + 17, p);
            c.drawLine(x + 12, y - 17, x + 25, y + 17, p);
            p.setStrokeWidth(1f);
        }

        private int stageIndex() {
            return Math.max(0, (level - 1) % 4);
        }

        private int requiredClues() {
            return Math.min(3, 1 + level / 2);
        }

        private int requiredBlockers() {
            return Math.min(4, 2 + level / 3);
        }

        private String stageName() {
            switch (stageIndex()) {
                case 0:
                    return "Snake Gorge";
                case 1:
                    return "Falling Ruins";
                case 2:
                    return "Fire Grove";
                default:
                    return "Hunter Night";
            }
        }

        private String threatName() {
            switch (stageIndex()) {
                case 0:
                    return "the serpent";
                case 1:
                    return "the stone rush";
                case 2:
                    return "the forest fire";
                default:
                    return "the hunters";
            }
        }

        private String objectiveText() {
            switch (stageIndex()) {
                case 0:
                    return "collect logs, block the serpent, find the lit path";
                case 1:
                    return "carry beams, seal the ruins, choose the safe arch";
                case 2:
                    return "grab wet wood, break the fire line, escape";
                default:
                    return "build a barricade, read clues, vanish";
            }
        }

        private void drawOpeningArt(Canvas c) {
            if (openingArt == null) return;
            float w = getWidth();
            float h = getHeight();
            float scale = Math.max(w / openingArt.getWidth(), h / openingArt.getHeight());
            float sw = openingArt.getWidth() * scale;
            float sh = openingArt.getHeight() * scale;
            RectF dst = new RectF((w - sw) * 0.5f, (h - sh) * 0.5f, (w + sw) * 0.5f, (h + sh) * 0.5f);
            c.drawBitmap(openingArt, null, dst, p);
        }

        private float heroX() {
            return getWidth() * 0.25f;
        }

        private float laneY(int l) {
            int h = Math.max(1, getHeight());
            return h * (0.48f + l * 0.17f);
        }

        private float[] lanes() {
            return new float[]{laneY(0), laneY(1), laneY(2)};
        }

        private int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        static final class Entity {
            final int type;
            final int lane;
            float x;
            final float y;

            Entity(int type, float x, float y, int lane) {
                this.type = type;
                this.x = x;
                this.y = y;
                this.lane = lane;
            }
        }

        interface GameStateListener {
            void onRunningStateChanged(boolean isRunning);
        }
    }
}
