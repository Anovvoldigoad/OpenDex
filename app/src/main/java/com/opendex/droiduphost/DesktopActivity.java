package com.opendex.droiduphost;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

/**
 * Displays the ORIGINAL DroidUP launcher on a public, own-content-only virtual display.
 * The launcher APK itself is not modified.
 */
public final class DesktopActivity extends Activity implements SurfaceHolder.Callback {
    private static final int VD_WIDTH = 1920;
    private static final int VD_HEIGHT = 1080;
    private static final int VD_DPI = 240;

    private SurfaceView surfaceView;
    private TextView statusView;
    private ShizukuHostBridge bridge;
    private VirtualDisplay virtualDisplay;
    private boolean surfaceReady;
    private boolean sessionStarting;
    private int displayId = -1;

    private float downX, downY;
    private long downTime;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        surfaceView.getHolder().setFixedSize(VD_WIDTH, VD_HEIGHT);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.setOnTouchListener(this::onDesktopTouch);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(13);
        statusView.setBackgroundColor(0xB0000000);
        statusView.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusView.setText("Menyiapkan DroidUP Dex…");
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

    private void maybeStartSession() {
        if (sessionStarting || !surfaceReady || !bridge.isReady()) {
            if (!bridge.isReady()) status("Menunggu Shizuku… " + bridge.status());
            return;
        }
        sessionStarting = true;
        status("Mengaktifkan freeform…");
        bridge.enableDroidUpSettings(result -> {
            status("Membuat virtual display 1920×1080/240…");
            createDisplayAndLaunch();
        });
    }

    private void createDisplayAndLaunch() {
        try {
            if (virtualDisplay != null) virtualDisplay.release();
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
            virtualDisplay = dm.createVirtualDisplay(
                    "DroidUP Dex",
                    VD_WIDTH,
                    VD_HEIGHT,
                    VD_DPI,
                    surfaceView.getHolder().getSurface(),
                    flags
            );
            if (virtualDisplay == null || virtualDisplay.getDisplay() == null) {
                throw new IllegalStateException("DisplayManager mengembalikan null");
            }
            displayId = virtualDisplay.getDisplay().getDisplayId();
            status("Virtual display id=" + displayId + " · membuka DroidUP Launcher…");
            bridge.exec("am force-stop --user current com.levelup.droiduplauncher", ignored ->
                    bridge.startOriginalLauncher(displayId, result -> {
                        if (result.startsWith("EXIT=0")) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> statusView.setVisibility(View.GONE), 700);
                            surfaceView.requestFocus();
                        } else {
                            status("Launcher gagal dibuka:\n" + result);
                        }
                    })
            );
        } catch (Throwable t) {
            sessionStarting = false;
            status("Virtual display gagal: " + t.getClass().getSimpleName() + "\n" + String.valueOf(t.getMessage()));
        }
    }

    private boolean onDesktopTouch(View v, MotionEvent e) {
        if (displayId < 0) return true;
        float sx = VD_WIDTH / (float) Math.max(1, v.getWidth());
        float sy = VD_HEIGHT / (float) Math.max(1, v.getHeight());
        int x = clamp(Math.round(e.getX() * sx), 0, VD_WIDTH - 1);
        int y = clamp(Math.round(e.getY() * sy), 0, VD_HEIGHT - 1);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                downTime = android.os.SystemClock.uptimeMillis();
                return true;
            case MotionEvent.ACTION_UP:
                long duration = Math.max(1, android.os.SystemClock.uptimeMillis() - downTime);
                float dx = x - downX;
                float dy = y - downY;
                if ((dx * dx + dy * dy) < 900) {
                    bridge.tap(displayId, x, y);
                } else {
                    bridge.swipe(displayId, Math.round(downX), Math.round(downY), x, y, (int) duration);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (displayId >= 0 && event.getAction() == KeyEvent.ACTION_UP) {
            int code = event.getKeyCode();
            if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_POWER) {
                bridge.key(displayId, code);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (displayId >= 0) bridge.key(displayId, KeyEvent.KEYCODE_BACK);
        else super.onBackPressed();
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
        VirtualDisplay vd = virtualDisplay;
        virtualDisplay = null;
        if (vd != null) {
            try { vd.release(); } catch (Throwable ignored) {}
        }
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

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
