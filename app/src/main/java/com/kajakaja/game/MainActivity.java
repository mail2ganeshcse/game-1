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

        private static final int COLS = 7;
        private static final int ROWS = 9;
        private static final int EMPTY = -1;
        private static final int OBSTACLE = 5;

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
            if (mode == MODE_PLAY) {
                danger += dt * (0.006f + level * 0.0016f);
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
            mode = MODE_PLAY;
            movesLeft = Math.max(12, 20 - level / 2);
            heroStep = 0;
            danger = Math.min(0.34f, 0.12f + level * 0.018f);
            targetColor = stageIndex();
            message = "Tap matching blocks. Clear the escape route.";
            for (int i = 0; i < 4; i++) {
                collected[i] = 0;
                need[i][0] = 0;
            }
            need[targetColor][0] = 10 + level * 2;
            need[(targetColor + 1) % 4][0] = 6 + level;
            buildBoard();
            computeHint();
        }

        private void nextLevel() {
            level++;
            setupStage();
        }

        private void buildBoard() {
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    board[r][col] = random.nextInt(4);
                }
            }
            int obstacles = Math.min(8, 2 + level);
            for (int i = 0; i < obstacles; i++) {
                int r = 2 + random.nextInt(ROWS - 2);
                int col = random.nextInt(COLS);
                board[r][col] = OBSTACLE;
            }
            for (int col = 0; col < COLS; col++) {
                board[ROWS - 1][col] = random.nextInt(4);
            }
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
            for (Cell c : cluster) {
                board[c.r][c.c] = EMPTY;
                collected[color]++;
                score += 20 + bonus * 12;
            }
            heroStep += Math.max(1, cluster.size() / 3 + bonus);
            movesLeft--;
            danger = Math.max(0.04f, danger - 0.025f * cluster.size() - bonus * 0.02f);
            collapseBoard();
            message = cluster.size() >= 6 ? "Power clear! Route opens faster." : "Good move. Keep opening the route.";
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
            heroStep += 5;
            danger = Math.max(0.03f, danger - 0.23f);
            message = "Magic broke traps and revealed the route";
            computeHint();
            checkWinLoss();
        }

        private void checkWinLoss() {
            if (heroStep >= 18 && requirementsMet()) {
                mode = MODE_CLEAR;
                clearPause = 1.2f;
                score += 900 + level * 120;
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
                    board[write][col] = random.nextInt(4);
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
            int escapeCol = Math.min(COLS - 1, Math.max(0, heroStep / 3));
            for (Cell c : cluster) {
                if (c.r <= 2 || Math.abs(c.c - escapeCol) <= 1) return true;
            }
            return false;
        }

        private void drawScene(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            p.setShader(new LinearGradient(0, 0, 0, h,
                    Color.rgb(37, 28, 87), Color.rgb(161, 63, 154), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            if (openingArt != null) {
                p.setAlpha(52);
                float scale = Math.max(w / (float) openingArt.getWidth(), h / (float) openingArt.getHeight());
                Rect src = new Rect(0, 0, openingArt.getWidth(), openingArt.getHeight());
                RectF dst = new RectF((w - openingArt.getWidth() * scale) / 2f, 0,
                        (w + openingArt.getWidth() * scale) / 2f, openingArt.getHeight() * scale);
                c.drawBitmap(openingArt, src, dst, p);
                p.setAlpha(255);
            }

            drawRoom(c, w, h);
            drawHazard(c, w, h);
        }

        private void drawRoom(Canvas c, int w, int h) {
            p.setColor(Color.argb(190, 210, 82, 176));
            c.drawRoundRect(new RectF(w * 0.06f, h * 0.15f, w * 0.94f, h * 0.88f), 26, 26, p);
            p.setColor(Color.argb(70, 255, 255, 255));
            for (int y = (int) (h * 0.17f); y < h * 0.88f; y += 34) {
                c.drawLine(w * 0.08f, y, w * 0.92f, y, p);
            }
            for (int x = (int) (w * 0.08f); x < w * 0.92f; x += 74) {
                c.drawLine(x, h * 0.16f, x + 28, h * 0.88f, p);
            }
            p.setColor(Color.rgb(53, 48, 86));
            c.drawRoundRect(new RectF(w * 0.71f, h * 0.21f, w * 0.90f, h * 0.34f), 14, 14, p);
            p.setColor(Color.rgb(255, 214, 77));
            c.drawRoundRect(new RectF(w * 0.75f, h * 0.245f, w * 0.88f, h * 0.34f), 10, 10, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(22f);
            text.setColor(Color.rgb(59, 38, 75));
            c.drawText("EXIT", w * 0.815f, h * 0.305f, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawHazard(Canvas c, int w, int h) {
            float pulse = (float) Math.sin(anim * 6f) * 8f;
            if (stageIndex() == 0) {
                p.setColor(Color.rgb(160, 162, 168));
                c.drawRoundRect(new RectF(w * 0.08f, h * 0.32f, w * 0.22f + pulse, h * 0.52f), 8, 8, p);
                p.setColor(Color.rgb(230, 231, 232));
                for (int i = 0; i < 5; i++) {
                    Path spike = new Path();
                    float y = h * 0.34f + i * h * 0.033f;
                    spike.moveTo(w * 0.22f + pulse, y);
                    spike.lineTo(w * 0.32f + pulse, y + 18);
                    spike.lineTo(w * 0.22f + pulse, y + 36);
                    spike.close();
                    c.drawPath(spike, p);
                }
            } else if (stageIndex() == 1) {
                p.setColor(Color.rgb(106, 109, 116));
                c.drawRoundRect(new RectF(w * 0.18f, h * 0.14f, w * 0.82f, h * 0.22f + pulse), 12, 12, p);
                p.setColor(Color.rgb(222, 222, 210));
                for (int i = 0; i < 7; i++) {
                    Path spike = new Path();
                    float x = w * 0.22f + i * w * 0.08f;
                    spike.moveTo(x, h * 0.22f + pulse);
                    spike.lineTo(x + 18, h * 0.29f + pulse);
                    spike.lineTo(x + 36, h * 0.22f + pulse);
                    spike.close();
                    c.drawPath(spike, p);
                }
            } else if (stageIndex() == 2) {
                p.setStrokeWidth(28f);
                p.setColor(Color.rgb(126, 224, 255));
                Path slide = new Path();
                slide.moveTo(w * 0.12f, h * 0.22f);
                slide.cubicTo(w * 0.86f, h * 0.27f, w * 0.18f, h * 0.40f, w * 0.70f, h * 0.54f);
                c.drawPath(slide, p);
                p.setStrokeWidth(1f);
            } else {
                p.setColor(Color.rgb(110, 85, 144));
                c.drawRoundRect(new RectF(w * 0.10f, h * 0.19f, w * 0.35f, h * 0.36f), 16, 16, p);
                p.setColor(Color.rgb(18, 18, 28));
                c.drawRoundRect(new RectF(w * 0.17f, h * 0.24f, w * 0.28f, h * 0.36f), 8, 8, p);
            }
        }

        private void drawBoard(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            cell = Math.min(w * 0.118f, h * 0.062f);
            boardX = (w - cell * COLS) / 2f;
            boardY = h * 0.48f + (shake > 0f ? (float) Math.sin(anim * 80f) * 7f * shake : 0f);
            p.setColor(Color.argb(95, 22, 18, 52));
            c.drawRoundRect(new RectF(boardX - 12, boardY - 12, boardX + cell * COLS + 12, boardY + cell * ROWS + 12), 20, 20, p);
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    drawTile(c, r, col, board[r][col]);
                }
            }
        }

        private void drawTile(Canvas c, int r, int col, int color) {
            float x = boardX + col * cell;
            float y = boardY + r * cell;
            RectF rect = new RectF(x + 4, y + 4, x + cell - 4, y + cell - 4);
            boolean hinted = isHinted(r, col);
            p.setColor(Color.argb(95, 0, 0, 0));
            c.drawRoundRect(new RectF(rect.left + 3, rect.top + 5, rect.right + 3, rect.bottom + 6), 10, 10, p);
            if (color == OBSTACLE) {
                p.setColor(Color.rgb(92, 80, 105));
                c.drawRoundRect(rect, 10, 10, p);
                p.setColor(Color.rgb(232, 226, 198));
                c.drawCircle(rect.centerX(), rect.centerY(), cell * 0.20f, p);
                p.setColor(Color.rgb(45, 38, 56));
                p.setStrokeWidth(5f);
                c.drawLine(rect.left + 12, rect.top + 14, rect.right - 12, rect.bottom - 14, p);
                p.setStrokeWidth(1f);
                return;
            }
            p.setColor(tileColor(color));
            c.drawRoundRect(rect, 11, 11, p);
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
                p.setStrokeWidth(5f);
                p.setColor(Color.argb(220, 255, 255, 255));
                c.drawRoundRect(rect, 12, 12, p);
                p.setStyle(Paint.Style.FILL);
                p.setStrokeWidth(1f);
            }
        }

        private void drawHero(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            float route = Math.min(1f, heroStep / 18f);
            float x = w * (0.18f + route * 0.58f);
            float y = h * (0.405f - (float) Math.sin(route * Math.PI) * 0.06f);
            p.setColor(Color.argb(95, 0, 0, 0));
            c.drawOval(new RectF(x - 34, y + 48, x + 36, y + 64), p);
            p.setColor(Color.rgb(244, 146, 49));
            c.drawCircle(x, y - 22, 29, p);
            p.setColor(Color.rgb(255, 205, 151));
            c.drawCircle(x + 3, y - 17, 21, p);
            p.setColor(Color.WHITE);
            c.drawCircle(x - 5, y - 21, 5, p);
            c.drawCircle(x + 12, y - 21, 5, p);
            p.setColor(Color.rgb(48, 67, 102));
            c.drawCircle(x - 4, y - 21, 2.8f, p);
            c.drawCircle(x + 13, y - 21, 2.8f, p);
            p.setColor(Color.rgb(255, 247, 235));
            c.drawRoundRect(new RectF(x - 22, y + 8, x + 25, y + 62), 12, 12, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(27f);
            text.setColor(Color.rgb(225, 45, 43));
            c.drawText("K", x + 1, y + 43, text);
            text.setTextAlign(Paint.Align.LEFT);
            p.setStrokeWidth(8f);
            p.setColor(Color.rgb(43, 78, 174));
            c.drawLine(x - 8, y + 58, x - 30 + (float) Math.sin(anim * 8f) * 8f, y + 88, p);
            c.drawLine(x + 13, y + 58, x + 36 - (float) Math.sin(anim * 8f) * 8f, y + 84, p);
            p.setColor(Color.rgb(255, 205, 151));
            c.drawLine(x - 21, y + 25, x - 48, y + 38 + (float) Math.sin(anim * 7f) * 7f, p);
            c.drawLine(x + 23, y + 25, x + 50, y + 13 + (float) Math.cos(anim * 7f) * 7f, p);
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
                c.drawText("Rescue Complete", w / 2f, h * 0.42f, text);
                text.setTextSize(25f);
                c.drawText("Reward +1 magic. Next trap is loading.", w / 2f, h * 0.51f, text);
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
            c.drawText("Rescue puzzle adventure", w / 2f, h * 0.31f, text);
            c.drawText("Tap color blocks, beat traps, use AI moves and magic.", w / 2f, h * 0.39f, text);
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

        private int stageIndex() {
            return Math.max(0, (level - 1) % 4);
        }

        private String stageName() {
            switch (stageIndex()) {
                case 0:
                    return "Spike Wall Escape";
                case 1:
                    return "Crusher Ceiling";
                case 2:
                    return "Tunnel Slide";
                default:
                    return "Locked Candy Vault";
            }
        }

        private String stageThreat() {
            switch (stageIndex()) {
                case 0:
                    return "The spike wall";
                case 1:
                    return "The crusher";
                case 2:
                    return "The tunnel rush";
                default:
                    return "The vault trap";
            }
        }

        private String objectiveText() {
            return String.format(Locale.US, "Goal: collect route colors and move kajakaja to EXIT before danger fills.");
        }

        static final class Cell {
            final int r;
            final int c;

            Cell(int r, int c) {
                this.r = r;
                this.c = c;
            }
        }

        interface GameStateListener {
            void onRunningStateChanged(boolean isRunning);
        }
    }
}
