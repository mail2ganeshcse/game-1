package com.kajakaja.game;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
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
            if (bannerAd != null) bannerAd.setVisibility(isRunning ? View.VISIBLE : View.GONE);
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
        if (bannerAd != null) bannerAd.destroy();
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
        private static final int MODE_PLAY = 1;
        private static final int MODE_CLEAR = 2;
        private static final int MODE_OVER = 3;
        private static final int MODE_WIN = 4;

        private static final int COLS = 7;
        private static final int ROWS = 9;
        private static final int EMPTY = -1;
        private static final int OBSTACLE = 5;
        private static final int TOTAL_LEVELS = 10;

        private static final LevelSpec[] LEVELS = new LevelSpec[]{
                new LevelSpec("Spike Wall Rescue", "The spike wall", 0, 1, 12, 7, 19, 3,
                        Color.rgb(86, 42, 132), Color.rgb(229, 80, 174), Color.rgb(255, 215, 74), 0),
                new LevelSpec("Crusher Ceiling", "The crusher", 1, 2, 13, 8, 18, 4,
                        Color.rgb(54, 61, 120), Color.rgb(180, 80, 205), Color.rgb(105, 229, 255), 1),
                new LevelSpec("Tunnel Slide Drop", "The tunnel rush", 2, 3, 14, 8, 18, 4,
                        Color.rgb(39, 100, 151), Color.rgb(84, 201, 233), Color.rgb(255, 204, 72), 2),
                new LevelSpec("Candy Vault Lock", "The vault trap", 3, 0, 15, 9, 17, 5,
                        Color.rgb(96, 48, 142), Color.rgb(247, 95, 179), Color.rgb(153, 237, 61), 3),
                new LevelSpec("Glass Bridge Panic", "The cracked bridge", 1, 0, 16, 10, 17, 5,
                        Color.rgb(43, 75, 122), Color.rgb(72, 198, 255), Color.rgb(255, 82, 74), 4),
                new LevelSpec("Laser Gate Maze", "The laser gate", 0, 2, 18, 10, 16, 6,
                        Color.rgb(77, 40, 118), Color.rgb(255, 72, 101), Color.rgb(72, 221, 255), 5),
                new LevelSpec("Rolling Stone Hall", "The rolling stone", 2, 1, 19, 11, 16, 6,
                        Color.rgb(74, 63, 91), Color.rgb(168, 126, 88), Color.rgb(255, 217, 79), 6),
                new LevelSpec("Water Pipe Escape", "The flood pipe", 1, 3, 20, 12, 15, 7,
                        Color.rgb(33, 97, 132), Color.rgb(56, 218, 218), Color.rgb(160, 232, 83), 7),
                new LevelSpec("Magnet Trap Lab", "The magnet trap", 3, 2, 21, 13, 15, 7,
                        Color.rgb(82, 46, 123), Color.rgb(119, 96, 237), Color.rgb(255, 211, 65), 8),
                new LevelSpec("Final Sky Exit", "The final trap", 0, 3, 24, 14, 14, 8,
                        Color.rgb(32, 47, 107), Color.rgb(255, 110, 88), Color.rgb(255, 230, 98), 9)
        };

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random(31);
        private final int[][] board = new int[ROWS][COLS];
        private final int[][] need = new int[4][1];
        private final int[] collected = new int[4];
        private final List<Cell> hintCells = new ArrayList<>();
        private final Bitmap openingArt;

        private GameStateListener gameStateListener;
        private boolean lastRunningState;
        private int mode = MODE_TITLE;
        private int level = 1;
        private int score = 0;
        private int magic = 0;
        private int movesLeft = 18;
        private int heroStep = 0;
        private int targetColor = 0;
        private float danger = 0.15f;
        private float anim = 0f;
        private float shake = 0f;
        private float heroProgress = 0f;
        private float targetHeroProgress = 0f;
        private float routeSpark = 0f;
        private float clearPause = 0f;
        private float boardX;
        private float boardY;
        private float cell;
        private float downX;
        private float downY;
        private long lastFrame;
        private String message = "";
        private String failure = "The trap caught kajakaja";

        KajakajaView(Activity activity) {
            super(activity);
            openingArt = BitmapFactory.decodeResource(getResources(), R.drawable.kajakaja_opening);
            text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
            setFocusable(true);
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
            anim += dt;
            shake = Math.max(0f, shake - dt * 4f);
            routeSpark = Math.max(0f, routeSpark - dt * 2.2f);
            heroProgress += (targetHeroProgress - heroProgress) * Math.min(1f, dt * 5.6f);
            if (mode == MODE_PLAY) {
                danger += dt * (0.0048f + level * 0.0013f);
                if (danger >= 1f) fail("Too slow. " + stageThreat() + " reached the hero.");
            } else if (mode == MODE_CLEAR) {
                clearPause -= dt;
                if (clearPause <= 0f) nextLevel();
            }

            drawScene(c);
            drawBoard(c);
            drawHero(c);
            drawUi(c);
            drawOverlay(c);
            notifyGameState();
            postInvalidateOnAnimation();
        }

        private void notifyGameState() {
            boolean running = mode == MODE_PLAY;
            if (gameStateListener != null && running != lastRunningState) {
                lastRunningState = running;
                gameStateListener.onRunningStateChanged(running);
            }
        }

        private void startGame() {
            level = 1;
            score = 0;
            magic = 0;
            setupStage();
        }

        private void setupStage() {
            LevelSpec spec = stage();
            random.setSeed(5100L + level * 97L);
            mode = MODE_PLAY;
            movesLeft = spec.moves;
            heroStep = 0;
            heroProgress = 0f;
            targetHeroProgress = 0f;
            routeSpark = 0f;
            danger = Math.min(0.36f, 0.11f + level * 0.018f);
            targetColor = spec.primaryColor;
            message = "Tap matching blocks. Clear the escape route.";
            for (int i = 0; i < 4; i++) {
                collected[i] = 0;
                need[i][0] = 0;
            }
            need[spec.primaryColor][0] = spec.primaryGoal;
            need[spec.secondaryColor][0] = spec.secondaryGoal;
            buildBoard();
            computeHint();
        }

        private void nextLevel() {
            if (level >= TOTAL_LEVELS) {
                mode = MODE_WIN;
                return;
            }
            level++;
            setupStage();
        }

        private void buildBoard() {
            LevelSpec spec = stage();
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    board[r][col] = weightedColor(spec, r, col);
                }
            }
            for (int i = 0; i < spec.obstacles; i++) {
                int r = 2 + random.nextInt(ROWS - 3);
                int col = random.nextInt(COLS);
                if (Math.abs(col - pathColumnForRow(r)) > 1 || random.nextBoolean()) {
                    board[r][col] = OBSTACLE;
                }
            }
            for (int col = 0; col < COLS; col++) {
                board[ROWS - 1][col] = col % 2 == 0 ? spec.primaryColor : random.nextInt(4);
            }
        }

        private int weightedColor(LevelSpec spec, int r, int col) {
            int roll = random.nextInt(100);
            if (Math.abs(col - pathColumnForRow(r)) <= 1 && roll < 46) return spec.primaryColor;
            if (roll < 31) return spec.primaryColor;
            if (roll < 52) return spec.secondaryColor;
            return random.nextInt(4);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                if (mode == MODE_TITLE) {
                    startGame();
                    return true;
                }
                if (mode == MODE_OVER) {
                    if (magic > 0) {
                        magic--;
                        setupStage();
                    } else {
                        startGame();
                    }
                    return true;
                }
                if (mode == MODE_CLEAR) {
                    nextLevel();
                    return true;
                }
                if (mode == MODE_WIN) {
                    startGame();
                    return true;
                }
            } else if (e.getAction() == MotionEvent.ACTION_UP && mode == MODE_PLAY) {
                float x = e.getX();
                float y = e.getY();
                if (inAiButton(x, y)) {
                    useAiMove();
                    return true;
                }
                if (inMagicButton(x, y)) {
                    useMagic();
                    return true;
                }
                int col = (int) ((x - boardX) / cell);
                int row = (int) ((y - boardY) / cell);
                if (row >= 0 && row < ROWS && col >= 0 && col < COLS) tapCell(row, col);
            }
            return true;
        }

        private void tapCell(int row, int col) {
            List<Cell> cluster = collectCluster(row, col);
            if (cluster.size() < 2) {
                message = "Match at least 2 blocks";
                danger = Math.min(1f, danger + 0.05f);
                shake = 0.35f;
                movesLeft--;
                checkLoss();
                return;
            }
            int color = board[row][col];
            int bonus = clusterTouchesEscape(cluster) ? 2 : 0;
            routeSpark = 1f;
            for (Cell c : cluster) {
                board[c.r][c.c] = EMPTY;
                collected[color]++;
                score += 28 + bonus * 16;
            }
            heroStep = Math.min(24, heroStep + Math.max(1, cluster.size() / 3 + bonus));
            targetHeroProgress = Math.min(1f, heroStep / 24f);
            movesLeft--;
            danger = Math.max(0.035f, danger - 0.022f * cluster.size() - bonus * 0.025f);
            collapseBoard();
            message = cluster.size() >= 6 ? "HD combo! Path bridge opens faster." : "Good move. The escape path is forming.";
            computeHint();
            checkWinLoss();
        }

        private void useAiMove() {
            computeHint();
            if (hintCells.isEmpty()) {
                message = "AI sees no useful cluster yet";
                return;
            }
            Cell first = hintCells.get(0);
            message = "AI action: clearing best rescue cluster";
            tapCell(first.r, first.c);
        }

        private void useMagic() {
            if (magic <= 0) {
                message = "No magic reward available";
                shake = 0.28f;
                return;
            }
            magic--;
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    if (board[r][col] == OBSTACLE && random.nextBoolean()) board[r][col] = targetColor;
                }
            }
            collected[targetColor] = Math.max(collected[targetColor], need[targetColor][0] - 3);
            heroStep = Math.min(24, heroStep + 6);
            targetHeroProgress = Math.min(1f, heroStep / 24f);
            routeSpark = 1f;
            danger = Math.max(0.03f, danger - 0.23f);
            message = "Magic broke traps and revealed the route";
            computeHint();
            checkWinLoss();
        }

        private void checkWinLoss() {
            if (heroStep >= 24 && requirementsMet()) {
                if (level >= TOTAL_LEVELS) {
                    mode = MODE_WIN;
                    score += 2500;
                    magic += 2;
                    message = "All 10 rescue rooms cleared.";
                    return;
                }
                mode = MODE_CLEAR;
                clearPause = 1.2f;
                score += 1100 + level * 160;
                magic++;
                message = "Stage rescued. Magic reward earned.";
                return;
            }
            checkLoss();
        }

        private void checkLoss() {
            if (movesLeft <= 0) fail(stageThreat() + " struck before the path opened.");
        }

        private void fail(String reason) {
            failure = reason;
            danger = 1f;
            shake = 0.7f;
            mode = MODE_OVER;
        }

        private boolean requirementsMet() {
            for (int i = 0; i < 4; i++) {
                if (need[i][0] > 0 && collected[i] < need[i][0]) return false;
            }
            return true;
        }

        private List<Cell> collectCluster(int row, int col) {
            List<Cell> result = new ArrayList<>();
            if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return result;
            int color = board[row][col];
            if (color < 0 || color >= 4) return result;
            boolean[][] seen = new boolean[ROWS][COLS];
            ArrayDeque<Cell> q = new ArrayDeque<>();
            q.add(new Cell(row, col));
            seen[row][col] = true;
            while (!q.isEmpty()) {
                Cell cur = q.removeFirst();
                result.add(cur);
                addIfSame(q, seen, cur.r - 1, cur.c, color);
                addIfSame(q, seen, cur.r + 1, cur.c, color);
                addIfSame(q, seen, cur.r, cur.c - 1, color);
                addIfSame(q, seen, cur.r, cur.c + 1, color);
            }
            return result;
        }

        private void addIfSame(ArrayDeque<Cell> q, boolean[][] seen, int r, int col, int color) {
            if (r < 0 || r >= ROWS || col < 0 || col >= COLS || seen[r][col] || board[r][col] != color) return;
            seen[r][col] = true;
            q.add(new Cell(r, col));
        }

        private void collapseBoard() {
            for (int col = 0; col < COLS; col++) {
                int write = ROWS - 1;
                for (int r = ROWS - 1; r >= 0; r--) {
                    if (board[r][col] != EMPTY) {
                        board[write][col] = board[r][col];
                        if (write != r) board[r][col] = EMPTY;
                        write--;
                    }
                }
                while (write >= 0) {
                    board[write][col] = weightedColor(stage(), write, col);
                    write--;
                }
            }
        }

        private void computeHint() {
            hintCells.clear();
            int best = Integer.MIN_VALUE;
            boolean[][] seen = new boolean[ROWS][COLS];
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    if (seen[r][col] || board[r][col] < 0 || board[r][col] >= 4) continue;
                    List<Cell> cluster = collectCluster(r, col);
                    for (Cell c : cluster) seen[c.r][c.c] = true;
                    if (cluster.size() < 2) continue;
                    int color = board[r][col];
                    int value = cluster.size() * 10;
                    if (need[color][0] > collected[color]) value += 55;
                    if (clusterTouchesEscape(cluster)) value += 45;
                    if (cluster.size() >= 5) value += 30;
                    if (value > best) {
                        best = value;
                        hintCells.clear();
                        hintCells.addAll(cluster);
                    }
                }
            }
        }

        private boolean clusterTouchesEscape(List<Cell> cluster) {
            for (Cell c : cluster) {
                if (c.r <= 2 || Math.abs(c.c - pathColumnForRow(c.r)) <= 1) return true;
            }
            return false;
        }

        private int pathColumnForRow(int row) {
            float t = row / (float) Math.max(1, ROWS - 1);
            int style = stage().hazardStyle;
            float wave = (float) Math.sin((t * Math.PI * 1.8f) + style * 0.55f);
            return Math.max(0, Math.min(COLS - 1, Math.round(COLS * 0.5f + wave * 2.1f)));
        }

        private void drawScene(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            LevelSpec spec = stage();
            p.setShader(new LinearGradient(0, 0, 0, h,
                    darken(spec.wallTop, 0.42f), darken(spec.wallBottom, 0.20f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            if (openingArt != null) {
                p.setAlpha(38);
                float scale = Math.max(w / (float) openingArt.getWidth(), h / (float) openingArt.getHeight());
                Rect src = new Rect(0, 0, openingArt.getWidth(), openingArt.getHeight());
                RectF dst = new RectF((w - openingArt.getWidth() * scale) / 2f, 0,
                        (w + openingArt.getWidth() * scale) / 2f, openingArt.getHeight() * scale);
                c.drawBitmap(openingArt, src, dst, p);
                p.setAlpha(255);
            }

            drawRoom(c, w, h);
            drawEscapePath(c, w, h);
            drawHazard(c, w, h);
        }

        private void drawRoom(Canvas c, int w, int h) {
            LevelSpec spec = stage();
            RectF room = new RectF(w * 0.055f, h * 0.145f, w * 0.945f, h * 0.895f);
            p.setColor(Color.argb(155, 0, 0, 0));
            c.drawRoundRect(new RectF(room.left + 8, room.top + 12, room.right + 8, room.bottom + 16), 34, 34, p);
            p.setShader(new LinearGradient(0, room.top, 0, room.bottom,
                    spec.wallTop, spec.wallBottom, Shader.TileMode.CLAMP));
            c.drawRoundRect(room, 34, 34, p);
            p.setShader(null);
            p.setColor(Color.argb(85, 255, 255, 255));
            c.drawRoundRect(new RectF(room.left + 9, room.top + 9, room.right - 9, room.bottom - 9), 27, 27, p);
            p.setColor(Color.argb(95, 70, 28, 86));
            for (int y = (int) room.top + 24; y < room.bottom; y += 32) {
                c.drawLine(room.left + 10, y, room.right - 10, y, p);
            }
            for (int y = (int) room.top + 18; y < room.bottom; y += 64) {
                for (int x = (int) room.left + 22; x < room.right - 16; x += 88) {
                    c.drawLine(x + ((y / 64) % 2) * 34, y, x + 24 + ((y / 64) % 2) * 34, y + 32, p);
                }
            }
            p.setColor(Color.argb(115, 255, 255, 255));
            c.drawOval(new RectF(room.left + 22, room.top + 18, room.right - 22, room.top + 96), p);

            RectF exitFrame = new RectF(w * 0.695f, h * 0.205f, w * 0.91f, h * 0.35f);
            p.setColor(Color.rgb(39, 37, 75));
            c.drawRoundRect(exitFrame, 18, 18, p);
            p.setShader(new LinearGradient(exitFrame.left, exitFrame.top, exitFrame.right, exitFrame.bottom,
                    spec.accent, Color.WHITE, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(exitFrame.left + 11, exitFrame.top + 14, exitFrame.right - 8, exitFrame.bottom - 6), 13, 13, p);
            p.setShader(null);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(24f);
            text.setColor(Color.rgb(42, 32, 72));
            c.drawText("EXIT", exitFrame.centerX() + 8, exitFrame.centerY() + 11, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawHazard(Canvas c, int w, int h) {
            LevelSpec spec = stage();
            float pulse = (float) Math.sin(anim * 5.4f) * 10f;
            float pressure = danger * w * 0.13f;
            switch (spec.hazardStyle) {
                case 0:
                    drawSideSpikes(c, w, h, pulse + pressure);
                    break;
                case 1:
                    drawCrusher(c, w, h, pulse + danger * h * 0.08f);
                    break;
                case 2:
                    drawTubeSlide(c, w, h);
                    break;
                case 3:
                    drawVault(c, w, h, pulse);
                    break;
                case 4:
                    drawGlassBridge(c, w, h);
                    break;
                case 5:
                    drawLaserGate(c, w, h, pulse);
                    break;
                case 6:
                    drawRollingStone(c, w, h);
                    break;
                case 7:
                    drawWaterPipe(c, w, h);
                    break;
                case 8:
                    drawMagnetTrap(c, w, h, pulse);
                    break;
                default:
                    drawFinalTrap(c, w, h, pulse);
                    break;
            }
        }

        private void drawEscapePath(Canvas c, int w, int h) {
            Path path = new Path();
            for (int i = 0; i <= 24; i++) {
                float[] pt = routePoint(i / 24f, w, h);
                if (i == 0) path.moveTo(pt[0], pt[1]);
                else path.lineTo(pt[0], pt[1]);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(34f);
            p.setColor(Color.argb(120, 32, 26, 74));
            c.drawPath(path, p);
            p.setStrokeWidth(20f);
            p.setColor(Color.argb(190, 255, 220, 79));
            c.drawPath(path, p);
            p.setStrokeWidth(10f);
            p.setColor(Color.argb(210, 255, 255, 255));
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);

            int lit = Math.min(24, Math.max(0, heroStep));
            for (int i = 0; i <= lit; i++) {
                float[] pt = routePoint(i / 24f, w, h);
                p.setColor(i == lit ? Color.argb(230, 255, 255, 255) : stage().accent);
                c.drawCircle(pt[0], pt[1], i == lit ? 9f + routeSpark * 8f : 5.5f, p);
            }
        }

        private float[] routePoint(float t, int w, int h) {
            LevelSpec spec = stage();
            float x = w * (0.18f + t * 0.62f);
            float y;
            if (spec.hazardStyle == 2) {
                y = h * (0.31f + 0.055f * (float) Math.sin(t * Math.PI * 2.4f));
            } else if (spec.hazardStyle == 4) {
                y = h * (0.39f + 0.04f * (float) Math.sin(t * Math.PI * 3f));
            } else if (spec.hazardStyle == 7) {
                y = h * (0.34f + 0.065f * (float) Math.sin(t * Math.PI * 1.7f));
            } else {
                y = h * (0.405f - 0.07f * (float) Math.sin(t * Math.PI));
            }
            return new float[]{x, y};
        }

        private void drawSideSpikes(Canvas c, int w, int h, float push) {
            p.setShader(new LinearGradient(w * 0.07f, h * 0.31f, w * 0.28f, h * 0.53f,
                    Color.rgb(210, 213, 220), Color.rgb(91, 92, 104), Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(w * 0.07f, h * 0.31f, w * 0.20f + push, h * 0.54f), 10, 10, p);
            p.setShader(null);
            p.setColor(Color.rgb(238, 239, 244));
            for (int i = 0; i < 6; i++) {
                float y = h * 0.325f + i * h * 0.033f;
                Path spike = new Path();
                spike.moveTo(w * 0.20f + push, y);
                spike.lineTo(w * 0.33f + push, y + 17);
                spike.lineTo(w * 0.20f + push, y + 34);
                spike.close();
                c.drawPath(spike, p);
            }
        }

        private void drawCrusher(Canvas c, int w, int h, float drop) {
            RectF plate = new RectF(w * 0.15f, h * 0.145f, w * 0.84f, h * 0.225f + drop);
            p.setShader(new LinearGradient(0, plate.top, 0, plate.bottom,
                    Color.rgb(235, 236, 238), Color.rgb(93, 95, 105), Shader.TileMode.CLAMP));
            c.drawRoundRect(plate, 14, 14, p);
            p.setShader(null);
            p.setColor(Color.rgb(238, 236, 220));
            for (int i = 0; i < 8; i++) {
                float x = w * 0.18f + i * w * 0.08f;
                Path spike = new Path();
                spike.moveTo(x, plate.bottom - 2);
                spike.lineTo(x + 18, plate.bottom + h * 0.065f);
                spike.lineTo(x + 36, plate.bottom - 2);
                spike.close();
                c.drawPath(spike, p);
            }
        }

        private void drawTubeSlide(Canvas c, int w, int h) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(44f);
            p.setColor(Color.argb(170, 70, 25, 150));
            Path tube = new Path();
            tube.moveTo(w * 0.11f, h * 0.24f);
            tube.cubicTo(w * 0.94f, h * 0.22f, w * 0.13f, h * 0.42f, w * 0.76f, h * 0.54f);
            c.drawPath(tube, p);
            p.setStrokeWidth(26f);
            p.setColor(Color.rgb(139, 235, 255));
            c.drawPath(tube, p);
            p.setStrokeWidth(8f);
            p.setColor(Color.argb(220, 255, 255, 255));
            c.drawPath(tube, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawVault(Canvas c, int w, int h, float pulse) {
            RectF vault = new RectF(w * 0.11f, h * 0.20f, w * 0.39f, h * 0.38f);
            p.setColor(Color.rgb(112, 84, 150));
            c.drawRoundRect(vault, 20, 20, p);
            p.setColor(Color.rgb(23, 22, 35));
            c.drawRoundRect(new RectF(vault.left + 42, vault.top + 34, vault.right - 42, vault.bottom), 10, 10, p);
            p.setColor(stage().accent);
            c.drawCircle(vault.centerX() + pulse * 0.25f, vault.centerY(), 13, p);
        }

        private void drawGlassBridge(Canvas c, int w, int h) {
            p.setColor(Color.argb(110, 166, 235, 255));
            for (int i = 0; i < 5; i++) {
                float x = w * (0.18f + i * 0.12f);
                c.drawRoundRect(new RectF(x, h * 0.37f, x + w * 0.09f, h * 0.43f), 8, 8, p);
                p.setColor(Color.argb(125, 255, 255, 255));
                c.drawLine(x + 12, h * 0.38f, x + w * 0.06f, h * 0.42f, p);
                p.setColor(Color.argb(110, 166, 235, 255));
            }
        }

        private void drawLaserGate(Canvas c, int w, int h, float pulse) {
            p.setStrokeWidth(7f);
            p.setColor(Color.argb(210, 255, 43, 78));
            for (int i = 0; i < 4; i++) {
                float y = h * (0.25f + i * 0.055f) + pulse * 0.4f;
                c.drawLine(w * 0.12f, y, w * 0.58f, y + 20, p);
            }
            p.setStrokeWidth(1f);
            p.setColor(Color.rgb(55, 38, 72));
            c.drawRoundRect(new RectF(w * 0.10f, h * 0.21f, w * 0.16f, h * 0.45f), 10, 10, p);
            c.drawRoundRect(new RectF(w * 0.56f, h * 0.21f, w * 0.62f, h * 0.45f), 10, 10, p);
        }

        private void drawRollingStone(Canvas c, int w, int h) {
            float x = w * (0.15f + danger * 0.18f);
            float y = h * 0.34f;
            p.setShader(new LinearGradient(x - 58, y - 58, x + 58, y + 58,
                    Color.rgb(183, 171, 141), Color.rgb(74, 70, 67), Shader.TileMode.CLAMP));
            c.drawCircle(x, y, 58, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8f);
            p.setColor(Color.argb(130, 35, 31, 28));
            c.drawArc(new RectF(x - 38, y - 38, x + 38, y + 38), anim * 180f, 260f, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(1f);
        }

        private void drawWaterPipe(Canvas c, int w, int h) {
            p.setStrokeWidth(30f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(92, 202, 235));
            for (int i = 0; i < 3; i++) {
                float y = h * (0.25f + i * 0.055f);
                c.drawLine(w * 0.08f, y, w * (0.42f + danger * 0.18f), y + (float) Math.sin(anim * 5f + i) * 10f, p);
            }
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setStrokeWidth(1f);
        }

        private void drawMagnetTrap(Canvas c, int w, int h, float pulse) {
            p.setColor(Color.rgb(82, 62, 118));
            c.drawRoundRect(new RectF(w * 0.10f, h * 0.23f, w * 0.30f, h * 0.41f), 18, 18, p);
            p.setColor(Color.rgb(245, 52, 72));
            c.drawRect(w * 0.12f, h * 0.25f, w * 0.18f, h * 0.39f, p);
            p.setColor(Color.rgb(80, 206, 255));
            c.drawRect(w * 0.22f, h * 0.25f, w * 0.28f, h * 0.39f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5f);
            p.setColor(Color.argb(160, 255, 255, 255));
            c.drawCircle(w * 0.36f, h * 0.32f, 36 + pulse, p);
            c.drawCircle(w * 0.36f, h * 0.32f, 62 + pulse, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(1f);
        }

        private void drawFinalTrap(Canvas c, int w, int h, float pulse) {
            p.setColor(Color.argb(190, 255, 98, 62));
            for (int i = 0; i < 6; i++) {
                Path flame = new Path();
                float x = w * 0.10f + i * w * 0.075f;
                flame.moveTo(x, h * 0.46f);
                flame.lineTo(x + 24, h * (0.27f + (i % 2) * 0.025f) + pulse);
                flame.lineTo(x + 50, h * 0.46f);
                flame.close();
                c.drawPath(flame, p);
            }
        }

        private void drawBoard(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            cell = Math.min(w * 0.121f, h * 0.064f);
            boardX = (w - cell * COLS) / 2f;
            boardY = h * 0.455f + (shake > 0f ? (float) Math.sin(anim * 80f) * 7f * shake : 0f);
            RectF tray = new RectF(boardX - 16, boardY - 16, boardX + cell * COLS + 16, boardY + cell * ROWS + 16);
            p.setColor(Color.argb(150, 0, 0, 0));
            c.drawRoundRect(new RectF(tray.left + 5, tray.top + 8, tray.right + 5, tray.bottom + 10), 24, 24, p);
            p.setShader(new LinearGradient(0, tray.top, 0, tray.bottom,
                    Color.argb(210, 70, 42, 116), Color.argb(235, 24, 18, 57), Shader.TileMode.CLAMP));
            c.drawRoundRect(tray, 24, 24, p);
            p.setShader(null);
            p.setColor(Color.argb(85, 255, 255, 255));
            c.drawRoundRect(new RectF(tray.left + 8, tray.top + 8, tray.right - 8, tray.bottom - 8), 18, 18, p);
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    drawTile(c, r, col, board[r][col]);
                }
            }
        }

        private void drawTile(Canvas c, int r, int col, int color) {
            float x = boardX + col * cell;
            float y = boardY + r * cell;
            RectF rect = new RectF(x + 3, y + 3, x + cell - 3, y + cell - 3);
            boolean hinted = isHinted(r, col);
            p.setColor(Color.argb(115, 0, 0, 0));
            c.drawRoundRect(new RectF(rect.left + 3, rect.top + 5, rect.right + 3, rect.bottom + 7), 12, 12, p);
            if (color == OBSTACLE) {
                p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                        Color.rgb(145, 133, 157), Color.rgb(58, 49, 73), Shader.TileMode.CLAMP));
                c.drawRoundRect(rect, 10, 10, p);
                p.setShader(null);
                p.setColor(Color.rgb(232, 226, 198));
                c.drawCircle(rect.centerX(), rect.centerY(), cell * 0.20f, p);
                p.setColor(Color.rgb(45, 38, 56));
                p.setStrokeWidth(5f);
                c.drawLine(rect.left + 12, rect.top + 14, rect.right - 12, rect.bottom - 14, p);
                p.setStrokeWidth(1f);
                return;
            }
            p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    lighten(tileColor(color), 0.30f), tileColor(color), Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, 11, 11, p);
            p.setShader(null);
            p.setColor(Color.argb(72, 255, 255, 255));
            c.drawRoundRect(new RectF(rect.left + 5, rect.top + 5, rect.right - 5, rect.top + cell * 0.27f), 8, 8, p);
            p.setColor(Color.argb(75, 54, 35, 65));
            if (color == 0) c.drawCircle(rect.centerX(), rect.centerY(), cell * 0.16f, p);
            else if (color == 1) c.drawRoundRect(new RectF(rect.centerX() - 11, rect.centerY() - 11, rect.centerX() + 11, rect.centerY() + 11), 4, 4, p);
            else if (color == 2) drawStar(c, rect.centerX(), rect.centerY(), cell * 0.19f, p);
            else {
                p.setStrokeWidth(5f);
                c.drawLine(rect.left + 15, rect.centerY(), rect.right - 15, rect.centerY(), p);
                p.setStrokeWidth(1f);
            }
            if (hinted && mode == MODE_PLAY) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(6f);
                p.setColor(Color.argb(210, 255, 255, 255));
                c.drawRoundRect(rect, 12, 12, p);
                p.setStrokeWidth(2f);
                p.setColor(stage().accent);
                c.drawRoundRect(new RectF(rect.left - 3, rect.top - 3, rect.right + 3, rect.bottom + 3), 14, 14, p);
                p.setStyle(Paint.Style.FILL);
                p.setStrokeWidth(1f);
            }
        }

        private void drawHero(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            float[] pt = routePoint(Math.min(1f, heroProgress), w, h);
            float x = pt[0];
            float y = pt[1];
            float bob = (float) Math.sin(anim * 9f) * 4f;
            p.setColor(Color.argb(95, 0, 0, 0));
            c.drawOval(new RectF(x - 38, y + 52, x + 42, y + 70), p);
            p.setColor(Color.rgb(224, 44, 45));
            c.drawRoundRect(new RectF(x - 18, y - 62 + bob, x + 25, y - 31 + bob), 13, 13, p);
            p.setColor(Color.WHITE);
            c.drawCircle(x + 7, y - 49 + bob, 5, p);
            p.setColor(Color.rgb(244, 146, 49));
            c.drawCircle(x, y - 22 + bob, 31, p);
            p.setColor(Color.rgb(255, 205, 151));
            c.drawCircle(x + 3, y - 17 + bob, 22, p);
            p.setColor(Color.WHITE);
            c.drawCircle(x - 5, y - 21 + bob, 5, p);
            c.drawCircle(x + 12, y - 21 + bob, 5, p);
            p.setColor(Color.rgb(48, 67, 102));
            c.drawCircle(x - 4, y - 21 + bob, 2.8f, p);
            c.drawCircle(x + 13, y - 21 + bob, 2.8f, p);
            p.setColor(Color.rgb(255, 247, 235));
            c.drawRoundRect(new RectF(x - 22, y + 8 + bob, x + 25, y + 62 + bob), 12, 12, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(27f);
            text.setColor(Color.rgb(225, 45, 43));
            c.drawText("K", x + 1, y + 43 + bob, text);
            text.setTextAlign(Paint.Align.LEFT);
            p.setStrokeWidth(8f);
            p.setColor(Color.rgb(43, 78, 174));
            c.drawLine(x - 8, y + 58 + bob, x - 30 + (float) Math.sin(anim * 8f) * 8f, y + 88, p);
            c.drawLine(x + 13, y + 58 + bob, x + 36 - (float) Math.sin(anim * 8f) * 8f, y + 84, p);
            p.setColor(Color.rgb(255, 205, 151));
            c.drawLine(x - 21, y + 25 + bob, x - 48, y + 38 + (float) Math.sin(anim * 7f) * 7f, p);
            c.drawLine(x + 23, y + 25 + bob, x + 50, y + 13 + (float) Math.cos(anim * 7f) * 7f, p);
            p.setStrokeWidth(1f);
        }

        private void drawUi(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            p.setColor(Color.argb(178, 12, 10, 34));
            c.drawRoundRect(new RectF(18, 20, w - 18, h * 0.135f), 18, 18, p);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(Color.WHITE);
            text.setTextSize(30f);
            c.drawText("kajakaja", 38, 58, text);
            text.setTextSize(22f);
            c.drawText(stageName(), 38, 91, text);
            c.drawText("Level " + level, w * 0.39f, 58, text);
            c.drawText("Moves " + movesLeft, w * 0.39f, 91, text);
            c.drawText("Magic " + magic, w * 0.64f, 58, text);
            c.drawText("Score " + score, w * 0.64f, 91, text);

            float dangerX = 36;
            float dangerY = h * 0.145f;
            p.setColor(Color.argb(130, 30, 22, 38));
            c.drawRoundRect(new RectF(dangerX, dangerY, w - 36, dangerY + 20), 9, 9, p);
            p.setColor(Color.rgb(255, 70, 61));
            c.drawRoundRect(new RectF(dangerX, dangerY, dangerX + (w - 72) * danger, dangerY + 20), 9, 9, p);

            text.setTextSize(20f);
            text.setColor(Color.WHITE);
            c.drawText(objectiveText(), 38, h * 0.19f, text);
            drawNeed(c, w, h);
            drawButton(c, aiRect(w, h), "AI MOVE", Color.rgb(76, 207, 255));
            drawButton(c, magicRect(w, h), "MAGIC", Color.rgb(255, 194, 64));
            text.setTextSize(19f);
            text.setColor(Color.rgb(255, 241, 210));
            c.drawText(message, 38, h - 92, text);
        }

        private void drawNeed(Canvas c, int w, int h) {
            float x = 38;
            float y = h * 0.213f;
            for (int i = 0; i < 4; i++) {
                if (need[i][0] <= 0) continue;
                p.setColor(tileColor(i));
                c.drawRoundRect(new RectF(x, y, x + 34, y + 34), 8, 8, p);
                text.setTextSize(19f);
                text.setColor(Color.WHITE);
                c.drawText(Math.min(collected[i], need[i][0]) + "/" + need[i][0], x + 42, y + 25, text);
                x += 118;
            }
        }

        private void drawOverlay(Canvas c) {
            if (mode == MODE_PLAY) return;
            int w = getWidth();
            int h = getHeight();
            if (mode == MODE_TITLE) {
                drawTitle(c, w, h);
                return;
            }
            p.setColor(Color.argb(190, 5, 6, 18));
            c.drawRect(0, 0, w, h, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.WHITE);
            if (mode == MODE_CLEAR) {
                text.setTextSize(46f);
                c.drawText("Level " + level + " Complete", w / 2f, h * 0.42f, text);
                text.setTextSize(25f);
                c.drawText("Reward +1 magic. Level " + (level + 1) + " is loading.", w / 2f, h * 0.51f, text);
            } else if (mode == MODE_WIN) {
                text.setTextSize(44f);
                c.drawText("All 10 Levels Cleared", w / 2f, h * 0.40f, text);
                text.setTextSize(25f);
                c.drawText("kajakaja escaped every trap.", w / 2f, h * 0.50f, text);
                c.drawText("Final score " + score + "   Tap to play again", w / 2f, h * 0.60f, text);
            } else {
                text.setTextSize(38f);
                c.drawText(failure, w / 2f, h * 0.40f, text);
                text.setTextSize(24f);
                c.drawText("Score " + score + "   Level " + level, w / 2f, h * 0.50f, text);
                c.drawText(magic > 0 ? "Tap to spend magic and retry" : "Tap to restart", w / 2f, h * 0.60f, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawTitle(Canvas c, int w, int h) {
            if (openingArt != null) {
                float scale = Math.max(w / (float) openingArt.getWidth(), h / (float) openingArt.getHeight());
                RectF dst = new RectF((w - openingArt.getWidth() * scale) / 2f, 0,
                        (w + openingArt.getWidth() * scale) / 2f, openingArt.getHeight() * scale);
                c.drawBitmap(openingArt, null, dst, p);
            }
            p.setShader(new LinearGradient(0, 0, 0, h,
                    Color.argb(165, 18, 10, 39), Color.argb(235, 10, 6, 24), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.WHITE);
            text.setTextSize(58f);
            c.drawText("kajakaja", w / 2f, h * 0.24f, text);
            text.setTextSize(25f);
            c.drawText("HD rescue puzzle adventure", w / 2f, h * 0.31f, text);
            c.drawText("10 trap rooms, animated paths, AI moves and magic.", w / 2f, h * 0.39f, text);
            text.setTextSize(32f);
            c.drawText("Tap to play", w / 2f, h * 0.70f, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawButton(Canvas c, RectF rect, String label, int color) {
            p.setColor(Color.argb(145, 0, 0, 0));
            c.drawRoundRect(new RectF(rect.left + 3, rect.top + 5, rect.right + 3, rect.bottom + 6), 16, 16, p);
            p.setColor(color);
            c.drawRoundRect(rect, 16, 16, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(20f);
            text.setColor(Color.rgb(30, 22, 48));
            c.drawText(label, rect.centerX(), rect.centerY() + 7, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private RectF aiRect(int w, int h) {
            return new RectF(w - 182, h * 0.204f, w - 30, h * 0.254f);
        }

        private RectF magicRect(int w, int h) {
            return new RectF(w - 182, h * 0.265f, w - 30, h * 0.315f);
        }

        private boolean inAiButton(float x, float y) {
            return aiRect(getWidth(), getHeight()).contains(x, y);
        }

        private boolean inMagicButton(float x, float y) {
            return magicRect(getWidth(), getHeight()).contains(x, y);
        }

        private int tileColor(int color) {
            switch (color) {
                case 0:
                    return Color.rgb(246, 64, 58);
                case 1:
                    return Color.rgb(40, 169, 255);
                case 2:
                    return Color.rgb(157, 226, 55);
                default:
                    return Color.rgb(255, 211, 54);
            }
        }

        private void drawStar(Canvas c, float x, float y, float r, Paint paint) {
            Path star = new Path();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float rr = i % 2 == 0 ? r : r * 0.45f;
                float px = x + (float) Math.cos(a) * rr;
                float py = y + (float) Math.sin(a) * rr;
                if (i == 0) star.moveTo(px, py);
                else star.lineTo(px, py);
            }
            star.close();
            c.drawPath(star, paint);
        }

        private boolean isHinted(int r, int col) {
            for (Cell c : hintCells) if (c.r == r && c.c == col) return true;
            return false;
        }

        private LevelSpec stage() {
            int index = Math.max(0, Math.min(TOTAL_LEVELS - 1, level - 1));
            return LEVELS[index];
        }

        private String stageName() {
            return stage().name;
        }

        private String stageThreat() {
            return stage().threat;
        }

        private String objectiveText() {
            return String.format(Locale.US, "Level %d/%d: clear route colors and move kajakaja to EXIT.", level, TOTAL_LEVELS);
        }

        private int lighten(int color, float amount) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            return Color.rgb(
                    Math.min(255, (int) (r + (255 - r) * amount)),
                    Math.min(255, (int) (g + (255 - g) * amount)),
                    Math.min(255, (int) (b + (255 - b) * amount)));
        }

        private int darken(int color, float amount) {
            return Color.rgb(
                    Math.max(0, (int) (Color.red(color) * (1f - amount))),
                    Math.max(0, (int) (Color.green(color) * (1f - amount))),
                    Math.max(0, (int) (Color.blue(color) * (1f - amount))));
        }

        static final class Cell {
            final int r;
            final int c;

            Cell(int r, int c) {
                this.r = r;
                this.c = c;
            }
        }

        static final class LevelSpec {
            final String name;
            final String threat;
            final int primaryColor;
            final int secondaryColor;
            final int primaryGoal;
            final int secondaryGoal;
            final int moves;
            final int obstacles;
            final int wallTop;
            final int wallBottom;
            final int accent;
            final int hazardStyle;

            LevelSpec(String name, String threat, int primaryColor, int secondaryColor,
                      int primaryGoal, int secondaryGoal, int moves, int obstacles,
                      int wallTop, int wallBottom, int accent, int hazardStyle) {
                this.name = name;
                this.threat = threat;
                this.primaryColor = primaryColor;
                this.secondaryColor = secondaryColor;
                this.primaryGoal = primaryGoal;
                this.secondaryGoal = secondaryGoal;
                this.moves = moves;
                this.obstacles = obstacles;
                this.wallTop = wallTop;
                this.wallBottom = wallBottom;
                this.accent = accent;
                this.hazardStyle = hazardStyle;
            }
        }

        interface GameStateListener {
            void onRunningStateChanged(boolean isRunning);
        }
    }
}
