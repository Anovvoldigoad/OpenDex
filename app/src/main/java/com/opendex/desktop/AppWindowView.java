package com.opendex.desktop;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AppWindowView extends FrameLayout implements TextureView.SurfaceTextureListener {
    public interface Host {
        void focusWindow(AppWindowView window);
        void minimizeWindow(AppWindowView window);
        void closeWindow(AppWindowView window);
        int workspaceWidth();
        int workspaceHeight();
    }

    private final AppEntry app;
    private final ShizukuBridge bridge;
    private final Host host;
    private final TextureView videoView;
    private final TextView statusView;
    private final int titleHeight;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Surface videoSurface;
    private ScrcpySession session;
    private boolean closed;
    private boolean minimized;
    private boolean maximized;

    private int restoreX;
    private int restoreY;
    private int restoreW;
    private int restoreH;

    private float dragRawX;
    private float dragRawY;
    private int dragX;
    private int dragY;

    private float resizeRawX;
    private float resizeRawY;
    private int resizeW;
    private int resizeH;

    public AppWindowView(Context context, AppEntry app, ShizukuBridge bridge, Host host) {
        super(context);
        this.app = app;
        this.bridge = bridge;
        this.host = host;
        this.titleHeight = dp(36);

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setElevation(dp(16));
        setBackground(roundRect(0xFF10141C, 0xFF4B5565, dp(10), dp(1)));

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(1), dp(1), dp(1), dp(1));
        addView(shell, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout title = new LinearLayout(context);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, dp(2), 0);
        title.setBackgroundColor(0xFF202632);
        shell.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, titleHeight));

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.rightMargin = dp(7);
        title.addView(icon, iconLp);

        TextView label = new TextView(context);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setSingleLine(true);
        title.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView min = titleButton("—");
        TextView max = titleButton("□");
        TextView close = titleButton("×");
        title.addView(min);
        title.addView(max);
        title.addView(close);

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Color.BLACK);
        shell.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        videoView = new TextureView(context);
        videoView.setOpaque(true);
        videoView.setFocusable(true);
        videoView.setFocusableInTouchMode(true);
        videoView.setSurfaceTextureListener(this);
        content.addView(videoView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusView = new TextView(context);
        statusView.setText("Menunggu surface…");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(12);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackgroundColor(0xB0000000);
        content.addView(statusView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View resize = new View(context);
        resize.setBackground(roundRect(0xFF8C96A8, 0x00000000, dp(3), 0));
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.BOTTOM | Gravity.END);
        rlp.rightMargin = dp(3);
        rlp.bottomMargin = dp(3);
        addView(resize, rlp);

        title.setOnTouchListener(this::onTitleTouch);
        resize.setOnTouchListener(this::onResizeTouch);
        videoView.setOnTouchListener(this::onVideoTouch);
        videoView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP || session == null) return false;
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_POWER) return false;
            return session.sendKey(keyCode, event.getMetaState());
        });

        min.setOnClickListener(v -> host.minimizeWindow(this));
        max.setOnClickListener(v -> toggleMaximize());
        close.setOnClickListener(v -> host.closeWindow(this));
        setOnClickListener(v -> host.focusWindow(this));
    }

    public AppEntry app() { return app; }
    public boolean isMinimized() { return minimized; }

    public void setInitialBounds(int x, int y, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.leftMargin = x;
        lp.topMargin = y;
        setLayoutParams(lp);
    }

    public void focus() {
        if (minimized) restoreFromMinimize();
        bringToFront();
        requestFocus();
        videoView.requestFocus();
    }

    public void minimize() {
        if (minimized) return;
        minimized = true;
        setVisibility(INVISIBLE);
    }

    public void restoreFromMinimize() {
        minimized = false;
        setVisibility(VISIBLE);
        post(this::focus);
    }

    public void close() {
        if (closed) return;
        closed = true;
        ScrcpySession s = session;
        session = null;
        if (s != null) s.close();
        if (videoSurface != null) {
            try { videoSurface.release(); } catch (Throwable ignored) {}
            videoSurface = null;
        }
        main.removeCallbacksAndMessages(null);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (closed) return;
        videoSurface = new Surface(surfaceTexture);
        startSession(width, height);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        ScrcpySession s = session;
        session = null;
        if (s != null) s.close();
        if (videoSurface != null) {
            try { videoSurface.release(); } catch (Throwable ignored) {}
            videoSurface = null;
        }
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    private void startSession(int width, int height) {
        if (session != null || videoSurface == null || !videoSurface.isValid()) return;
        int w = even(clamp(width, 480, 2800));
        int h = even(clamp(height, 320, 2800));
        int density = getResources().getDisplayMetrics().densityDpi;
        int dpi = clamp(Math.round(density * 0.78f), 180, 360);
        ScrcpySession s = new ScrcpySession(bridge, app.packageName, videoSurface,
                w, h, dpi, 7_000_000, 60,
                new ScrcpySession.Listener() {
                    @Override public void onState(String state) { post(() -> showStatus(state, false)); }
                    @Override public void onReady(int videoWidth, int videoHeight) { post(() -> showStatus("", true)); }
                    @Override public void onVideoSizeChanged(int width1, int height1) { }
                    @Override public void onFatal(String error) { post(() -> showStatus(error, false)); }
                });
        session = s;
        s.start();
    }

    private void showStatus(String text, boolean hide) {
        statusView.setText(text == null ? "" : text);
        statusView.setVisibility(hide ? GONE : VISIBLE);
    }

    private boolean onTitleTouch(View view, MotionEvent event) {
        host.focusWindow(this);
        if (maximized) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragRawX = event.getRawX();
                dragRawY = event.getRawY();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
                dragX = lp.leftMargin;
                dragY = lp.topMargin;
                return true;
            case MotionEvent.ACTION_MOVE:
                int nx = dragX + Math.round(event.getRawX() - dragRawX);
                int ny = dragY + Math.round(event.getRawY() - dragRawY);
                int maxX = Math.max(0, host.workspaceWidth() - getWidth());
                int maxY = Math.max(0, host.workspaceHeight() - titleHeight);
                moveTo(clamp(nx, 0, maxX), clamp(ny, 0, maxY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return false;
        }
    }

    private boolean onResizeTouch(View view, MotionEvent event) {
        host.focusWindow(this);
        if (maximized) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                resizeRawX = event.getRawX();
                resizeRawY = event.getRawY();
                resizeW = getWidth();
                resizeH = getHeight();
                return true;
            case MotionEvent.ACTION_MOVE:
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
                int w = Math.max(dp(420), resizeW + Math.round(event.getRawX() - resizeRawX));
                int h = Math.max(dp(300), resizeH + Math.round(event.getRawY() - resizeRawY));
                w = Math.min(w, Math.max(dp(420), host.workspaceWidth() - lp.leftMargin));
                h = Math.min(h, Math.max(dp(300), host.workspaceHeight() - lp.topMargin));
                resizeTo(w, h);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestRemoteResize();
                return true;
            default:
                return false;
        }
    }

    private boolean onVideoTouch(View view, MotionEvent event) {
        host.focusWindow(this);
        ScrcpySession s = session;
        if (s == null) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            s.sendTouch(action, event.getX(), event.getY(), videoView.getWidth(), videoView.getHeight());
            return true;
        }
        return false;
    }

    private void toggleMaximize() {
        host.focusWindow(this);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (!maximized) {
            restoreX = lp.leftMargin;
            restoreY = lp.topMargin;
            restoreW = lp.width;
            restoreH = lp.height;
            lp.leftMargin = 0;
            lp.topMargin = 0;
            lp.width = host.workspaceWidth();
            lp.height = host.workspaceHeight();
            setLayoutParams(lp);
            maximized = true;
        } else {
            lp.leftMargin = restoreX;
            lp.topMargin = restoreY;
            lp.width = restoreW;
            lp.height = restoreH;
            setLayoutParams(lp);
            maximized = false;
        }
        postDelayed(this::requestRemoteResize, 100);
    }

    private void requestRemoteResize() {
        ScrcpySession s = session;
        if (s == null || videoView.getWidth() < 320 || videoView.getHeight() < 240) return;
        s.resizeDisplay(videoView.getWidth(), videoView.getHeight());
    }

    private void moveTo(int x, int y) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.leftMargin = x;
        lp.topMargin = y;
        setLayoutParams(lp);
    }

    private void resizeTo(int w, int h) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.width = w;
        lp.height = h;
        setLayoutParams(lp);
    }

    private TextView titleButton(String text) {
        TextView b = new TextView(getContext());
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(42), titleHeight));
        return b;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(radius);
        if (strokeWidth > 0) gd.setStroke(strokeWidth, stroke);
        return gd;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static int even(int v) { return (v & 1) == 0 ? v : v - 1; }
}
