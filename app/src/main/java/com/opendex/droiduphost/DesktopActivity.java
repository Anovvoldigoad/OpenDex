package com.opendex.droiduphost;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

/**
 * Displays the ORIGINAL, byte-identical DroidUP launcher.
 * The trusted/freeform virtual display is owned by the Shizuku shell UserService.
 */
public final class DesktopActivity extends ComponentActivity implements SurfaceHolder.Callback {
    public static final String EXTRA_WIDTH = "desktop_width";
    public static final String EXTRA_HEIGHT = "desktop_height";
    public static final String EXTRA_DPI = "desktop_dpi";

    private SurfaceView surfaceView;
    private FrameLayout root;
    private TextView statusView;
    private ShizukuHostBridge bridge;
    private boolean surfaceReady;
    private boolean sessionStarting;
    private int displayId = -1;

    private int vdWidth;
    private int vdHeight;
    private int vdDpi;

    private float downX, downY;
    private long downTime;
    private boolean fastInput = true;
    private long lastMoveSent;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        vdWidth = Math.max(800, getIntent().getIntExtra(EXTRA_WIDTH, 1920));
        vdHeight = Math.max(480, getIntent().getIntExtra(EXTRA_HEIGHT, 1080));
        if (vdHeight > vdWidth) {
            int t = vdWidth; vdWidth = vdHeight; vdHeight = t;
        }
        vdDpi = Math.max(120, Math.min(640, getIntent().getIntExtra(EXTRA_DPI, 240)));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (displayId >= 0) {
                    if (!bridge.injectKeyFast(displayId, KeyEvent.KEYCODE_BACK)) {
                        bridge.keyFallback(displayId, KeyEvent.KEYCODE_BACK);
                    }
                } else {
                    finish();
                }
            }
        });

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        surfaceView.getHolder().setFixedSize(vdWidth, vdHeight);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.setOnTouchListener(this::onDesktopTouch);
        surfaceView.setOnKeyListener((v, keyCode, event) -> {
            if (displayId < 0) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    || keyCode == KeyEvent.KEYCODE_POWER) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) return true;
            if (event.getAction() != KeyEvent.ACTION_UP) return false;
            if (!bridge.injectKeyFast(displayId, keyCode)) bridge.keyFallback(displayId, keyCode);
            return true;
        });

        FrameLayout.LayoutParams surfaceLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        root.addView(surfaceView, surfaceLp);

        // Preserve the desktop aspect ratio for manual resolutions instead of stretching it.
        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> fitSurfaceToRoot(r - l, b - t));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(13);
        statusView.setBackgroundColor(0xB0000000);
        statusView.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusView.setText("Menyiapkan DroidUP Dex " + vdWidth + "×" + vdHeight + " @ " + vdDpi + "dpi…");
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        statusLp.leftMargin = dp(10);
        statusLp.topMargin = dp(10);
        root.addView(statusView, statusLp);

        setContentView(root);

        bridge = new ShizukuHostBridge(this);
        bridge.start(this::maybeStartSession);
    }

    private void fitSurfaceToRoot(int rootW, int rootH) {
        if (rootW <= 0 || rootH <= 0 || surfaceView == null) return;
        float target = vdWidth / (float) vdHeight;
        float actual = rootW / (float) rootH;
        int outW, outH;
        if (actual > target) {
            outH = rootH;
            outW = Math.round(outH * target);
        } else {
            outW = rootW;
            outH = Math.round(outW / target);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
        if (lp.width != outW || lp.height != outH || lp.gravity != Gravity.CENTER) {
            lp.width = outW;
            lp.height = outH;
            lp.gravity = Gravity.CENTER;
            surfaceView.setLayoutParams(lp);
        }
    }

    private void maybeStartSession() {
        if (sessionStarting || !surfaceReady || !bridge.isReady()) {
            if (!bridge.isReady()) status("Menunggu Shizuku… " + bridge.status());
            return;
        }
        sessionStarting = true;
        status("Mengaktifkan freeform…");
        bridge.enableDroidUpSettings(result -> {
            status("Membuat TRUSTED virtual display " + vdWidth + "×" + vdHeight + "…");
            createDisplayAndLaunch();
        });
    }

    private void createDisplayAndLaunch() {
        if (!surfaceReady) {
            sessionStarting = false;
            return;
        }
        bridge.createTrustedDesktopDisplay(
                surfaceView.getHolder().getSurface(),
                vdWidth, vdHeight, vdDpi,
                result -> {
                    if (!result.startsWith("OK|")) {
                        sessionStarting = false;
                        status("Trusted virtual display gagal:\n" + result);
                        return;
                    }
                    try {
                        String[] parts = result.split("\\|");
                        displayId = Integer.parseInt(parts[1]);
                    } catch (Throwable t) {
                        sessionStarting = false;
                        status("Display response rusak:\n" + result);
                        return;
                    }

                    if (result.contains("|displayMode=FAILED")) {
                        sessionStarting = false;
                        status("Display berhasil dibuat, tapi mode FULLSCREEN display gagal.\n\n" + result
                                + "\n\nROM belum menerima per-display windowing mode 1.");
                        return;
                    }

                    status("Display " + displayId + " · FREEFORM " + vdWidth + "×" + vdHeight
                            + " @ " + vdDpi + "dpi\n"
                            + "Display tetap FULLSCREEN; aplikasi dengan launch bounds masuk FREEFORM per-task.\n" + result);
                    bridge.exec("am force-stop --user current com.levelup.droiduplauncher", ignored ->
                            bridge.startOriginalLauncherFullscreen(displayId, launchResult -> {
                                if (launchResult.startsWith("EXIT=0")) {
                                    new Handler(Looper.getMainLooper()).postDelayed(
                                            () -> statusView.setVisibility(View.GONE), 900);
                                    surfaceView.requestFocus();
                                } else {
                                    status("Launcher gagal dibuka fullscreen:\n" + launchResult);
                                }
                            })
                    );
                }
        );
    }

    private boolean onDesktopTouch(View v, MotionEvent e) {
        if (displayId < 0) return true;
        float sx = vdWidth / (float) Math.max(1, v.getWidth());
        float sy = vdHeight / (float) Math.max(1, v.getHeight());
        float x = clamp(e.getX() * sx, 0, vdWidth - 1);
        float y = clamp(e.getY() * sy, 0, vdHeight - 1);
        long now = SystemClock.uptimeMillis();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                downTime = now;
                lastMoveSent = now;
                fastInput = bridge.injectPointer(displayId, MotionEvent.ACTION_DOWN, x, y, downTime, now);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (fastInput && now - lastMoveSent >= 12) {
                    fastInput = bridge.injectPointer(displayId, MotionEvent.ACTION_MOVE, x, y, downTime, now);
                    lastMoveSent = now;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (fastInput && bridge.injectPointer(displayId, MotionEvent.ACTION_UP, x, y, downTime, now)) return true;
                slowFallback(x, y, now);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (fastInput) bridge.injectPointer(displayId, MotionEvent.ACTION_CANCEL, x, y, downTime, now);
                return true;
            default:
                return true;
        }
    }

    private void slowFallback(float x, float y, long now) {
        long duration = Math.max(1, now - downTime);
        float dx = x - downX;
        float dy = y - downY;
        if ((dx * dx + dy * dy) < 900) {
            bridge.tapFallback(displayId, Math.round(x), Math.round(y));
        } else {
            bridge.swipeFallback(displayId, Math.round(downX), Math.round(downY),
                    Math.round(x), Math.round(y), (int) duration);
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        maybeStartSession();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseDisplay();
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override protected void onDestroy() {
        releaseDisplay();
        if (bridge != null) bridge.stop();
        super.onDestroy();
    }

    private void releaseDisplay() {
        displayId = -1;
        sessionStarting = false;
        if (bridge != null) bridge.releaseDesktopDisplay();
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void status(String text) {
        runOnUiThread(() -> {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(text);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
