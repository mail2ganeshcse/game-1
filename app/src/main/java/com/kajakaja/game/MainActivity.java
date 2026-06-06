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
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new KajakajaView(this));
        hideSystemBars();
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

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random(7);
        private final List<Entity> entities = new ArrayList<>();
        private final Bitmap openingArt;

        private int mode = MODE_TITLE;
        private int level = 1;
        private int lane = 1;
        private int targetLane = 1;
        private int score = 0;
        private int samosas = 0;
        private int clues = 0;
        private int routeAnswer = 1;
        private int chosenRoute = -1;
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
            postInvalidateOnAnimation();
        }

        private void update(float dt) {
            float runSpeed = speed + level * 38f + (dash > 0f ? 320f : 0f);
            if (dash > 0f) dash = Math.max(0f, dash - dt * 0.55f);
            world += runSpeed * dt;
            bgShift += runSpeed * dt;
            danger += dt * (0.032f + level * 0.004f);
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
                    }
                    it.remove();
                } else if (e.type == TYPE_GATE && e.x < heroX() + 36f && e.x > heroX() - 30f) {
                    int route = Math.max(0, Math.min(2, lane));
                    chosenRoute = route;
                    if (route == routeAnswer && clues >= Math.max(1, level)) {
                        score += 500 + level * 120;
                        mode = MODE_LEVEL_CLEAR;
                        levelPause = 1.15f;
                    } else {
                        danger += 0.28f;
                        flash = 0.28f;
                        gateAt = world + 1500f;
                        it.remove();
                    }
                }
            }

            if (danger >= 1f) mode = MODE_GAME_OVER;
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
            if (clues < Math.max(1, level) && random.nextFloat() < 0.42f) {
                int l = random.nextInt(3);
                entities.add(new Entity(TYPE_CLUE, w + 420f + random.nextInt(160), laneY(l), l));
            }
            spawnAt = world + 540f - Math.min(170f, level * 18f);
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
            chosenRoute = -1;
            spawnAt = 380f;
            gateAt = 3500f + level * 420f;
            playerY = laneY(1);
            routeAnswer = random.nextInt(3);
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
            p.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(17, 31, 25), Color.rgb(77, 56, 31), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            p.setColor(Color.rgb(22, 66, 43));
            for (int i = -1; i < 12; i++) {
                float x = ((i * 170f) - (bgShift * 0.2f % 170f));
                c.drawRect(x, 0, x + 34, h, p);
                p.setColor(Color.rgb(31, 97, 58));
                c.drawCircle(x + 17, 44 + (i % 3) * 18, 54, p);
                p.setColor(Color.rgb(22, 66, 43));
            }

            p.setColor(Color.rgb(103, 73, 42));
            Path road = new Path();
            road.moveTo(0, h * 0.34f);
            road.lineTo(w, h * 0.25f);
            road.lineTo(w, h);
            road.lineTo(0, h);
            road.close();
            c.drawPath(road, p);

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
        }

        private void drawEntities(Canvas c) {
            for (Entity e : entities) {
                if (e.type == TYPE_TRAP) drawTrap(c, e.x, e.y);
                else if (e.type == TYPE_SAMOSA) drawSamosa(c, e.x, e.y, 28f);
                else if (e.type == TYPE_CLUE) drawClue(c, e.x, e.y);
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
                p.setColor(i == routeAnswer && clues >= Math.max(1, level) ? Color.rgb(231, 174, 52) : Color.rgb(74, 55, 37));
                c.drawRoundRect(new RectF(x - 46, ys[i] - 58, x + 46, ys[i] + 58), 18, 18, p);
                p.setColor(Color.rgb(24, 20, 18));
                c.drawRoundRect(new RectF(x - 26, ys[i] - 24, x + 26, ys[i] + 58), 10, 10, p);
            }
        }

        private void drawHud(Canvas c) {
            float w = getWidth();
            p.setColor(Color.argb(150, 10, 13, 12));
            c.drawRoundRect(new RectF(22, 18, w - 22, 82), 8, 8, p);
            text.setTextSize(30f);
            text.setColor(Color.WHITE);
            c.drawText("kajakaja", 42, 58, text);
            text.setTextSize(24f);
            c.drawText("Level " + level, 270, 58, text);
            c.drawText("Score " + score, 390, 58, text);
            c.drawText("Samosas " + samosas, 560, 58, text);
            c.drawText("Clues " + clues + "/" + Math.max(1, level), 750, 58, text);
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
                c.drawText("Run the old forest. Read the clues. Choose before danger arrives.", w / 2f, h * 0.46f, text);
                c.drawText("Swipe lanes. Collect samosas for sprint power. Escape each route gate.", w / 2f, h * 0.55f, text);
                text.setTextSize(34f);
                c.drawText("Tap to run", w / 2f, h * 0.72f, text);
            } else if (mode == MODE_LEVEL_CLEAR) {
                text.setTextSize(52f);
                c.drawText("Route found", w / 2f, h * 0.44f, text);
                text.setTextSize(28f);
                c.drawText(String.format(Locale.US, "Level %d opens. Keep moving.", level + 1), w / 2f, h * 0.56f, text);
            } else {
                text.setTextSize(58f);
                c.drawText("Caught by the danger", w / 2f, h * 0.42f, text);
                text.setTextSize(30f);
                c.drawText("Final score " + score + "   Level " + level, w / 2f, h * 0.55f, text);
                c.drawText("Tap to try again", w / 2f, h * 0.68f, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
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
    }
}
