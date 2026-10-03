package com.opendex.desktop;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.graphics.SurfaceTexture;
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
    private final TextureView surfaceView;
    private Surface displaySurface;
    private final TextView statusView;
    private final int titleHeight;
    private final int border;
    private final int dpi;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int displayId = -1;
    private boolean displayStarting;
    private boolean closed;
    private boolean maximized;
    private boolean minimized;

    private int restoreX;
    private int restoreY;
    private int restoreW;
    private int restoreH;

    private float dragDownRawX;
    private float dragDownRawY;
    private int dragStartX;
    private int dragStartY;

    private float resizeDownRawX;
    private float resizeDownRawY;
    private int resizeStartW;
    private int resizeStartH;

    private float pointerDownX;
    private float pointerDownY;
    private long pointerDownTime;
    private boolean directInput;

    public AppWindowView(Context context, AppEntry app, ShizukuBridge bridge, Host host, int dpi) {
        super(context);
        this.app = app;
        this.bridge = bridge;
        this.host = host;
        this.dpi = Math.max(160, Math.min(360, dpi));
        this.titleHeight = dp(36);
        this.border = dp(1);

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackground(makeBackground(0xFF161A22, 0xFF515A6B, dp(10), border));
        setElevation(dp(12));

        LinearLayout vertical = new LinearLayout(context);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setPadding(border, border, border, border);
        addView(vertical, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout title = new LinearLayout(context);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, dp(2), 0);
        title.setBackgroundColor(0xFF20252F);
        vertical.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, titleHeight));

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
        vertical.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        surfaceView = new TextureView(context);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.setSurfaceTextureListener(this);
        content.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusView = new TextView(context);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(12);
        statusView.setGravity(Gravity.CENTER);
        statusView.setText("Menunggu display…");
        statusView.setBackgroundColor(0x88000000);
        content.addView(statusView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View resizeHandle = new View(context);
        resizeHandle.setBackground(makeBackground(0xFF798292, 0x00000000, dp(3), 0));
        FrameLayout.LayoutParams rh = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.BOTTOM | Gravity.END);
        rh.rightMargin = dp(3);
        rh.bottomMargin = dp(3);
        addView(resizeHandle, rh);

        title.setOnTouchListener(this::onTitleTouch);
        resizeHandle.setOnTouchListener(this::onResizeTouch);
        surfaceView.setOnTouchListener(this::onSurfaceTouch);
        surfaceView.setOnKeyListener((v, keyCode, event) -> {
            if (displayId < 0 || event.getAction() != KeyEvent.ACTION_UP) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK) return bridge.injectKey(displayId, KeyEvent.KEYCODE_BACK);
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_POWER) return false;
            return bridge.injectKey(displayId, keyCode);
        });

        min.setOnClickListener(v -> host.minimizeWindow(this));
        max.setOnClickListener(v -> toggleMaximize());
        close.setOnClickListener(v -> host.closeWindow(this));
        setOnClickListener(v -> host.focusWindow(this));
    }

    public AppEntry app() { return app; }
    public int displayId() { return displayId; }
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
        surfaceView.requestFocus();
    }

    public void minimize() {
        if (minimized) return;
        minimized = true;
        int offscreen = Math.max(host.workspaceHeight(), getHeight()) + dp(120);
        setTranslationY(offscreen);
    }

    public void restoreFromMinimize() {
        minimized = false;
        setTranslationY(0f);
        post(this::focus);
    }

    public void close() {
        closed = true;
        int id = displayId;
        displayId = -1;
        if (id >= 0) bridge.releaseDisplay(id);
    }

    private boolean onTitleTouch(View view, MotionEvent event) {
        host.focusWindow(this);
        if (maximized) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragDownRawX = event.getRawX();
                dragDownRawY = event.getRawY();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
                dragStartX = lp.leftMargin;
                dragStartY = lp.topMargin;
                return true;
            case MotionEvent.ACTION_MOVE:
                int nx = dragStartX + Math.round(event.getRawX() - dragDownRawX);
                int ny = dragStartY + Math.round(event.getRawY() - dragDownRawY);
                int maxX = Math.max(0, host.workspaceWidth() - getWidth());
                int maxY = Math.max(0, host.workspaceHeight() - titleHeight);
                updatePosition(clamp(nx, 0, maxX), clamp(ny, 0, maxY));
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
                resizeDownRawX = event.getRawX();
                resizeDownRawY = event.getRawY();
                resizeStartW = getWidth();
                resizeStartH = getHeight();
                return true;
            case MotionEvent.ACTION_MOVE:
                int w = Math.max(dp(360), resizeStartW + Math.round(event.getRawX() - resizeDownRawX));
                int h = Math.max(dp(260), resizeStartH + Math.round(event.getRawY() - resizeDownRawY));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
                w = Math.min(w, Math.max(dp(360), host.workspaceWidth() - lp.leftMargin));
                h = Math.min(h, Math.max(dp(260), host.workspaceHeight() - lp.topMargin));
                updateSize(w, h, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                syncDisplaySize();
                return true;
            default:
                return false;
        }
    }

    private boolean onSurfaceTouch(View view, MotionEvent event) {
        host.focusWindow(this);
        if (displayId < 0) return true;
        float sx = currentDisplayWidth() / (float) Math.max(1, surfaceView.getWidth());
        float sy = currentDisplayHeight() / (float) Math.max(1, surfaceView.getHeight());
        float x = clamp(event.getX() * sx, 0f, currentDisplayWidth() - 1f);
        float y = clamp(event.getY() * sy, 0f, currentDisplayHeight() - 1f);
        long now = SystemClock.uptimeMillis();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerDownX = x;
                pointerDownY = y;
                pointerDownTime = now;
                directInput = bridge.injectPointer(displayId, MotionEvent.ACTION_DOWN, x, y, pointerDownTime, now);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (directInput) directInput = bridge.injectPointer(displayId, MotionEvent.ACTION_MOVE, x, y, pointerDownTime, now);
                return true;
            case MotionEvent.ACTION_UP:
                if (directInput && bridge.injectPointer(displayId, MotionEvent.ACTION_UP, x, y, pointerDownTime, now)) return true;
                long duration = Math.max(1, now - pointerDownTime);
                float dx = x - pointerDownX;
                float dy = y - pointerDownY;
                if (dx * dx + dy * dy < 625f) {
                    bridge.tapFallback(displayId, Math.round(x), Math.round(y));
                } else {
                    bridge.swipeFallback(displayId, Math.round(pointerDownX), Math.round(pointerDownY), Math.round(x), Math.round(y), (int) duration);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (directInput) bridge.injectPointer(displayId, MotionEvent.ACTION_CANCEL, x, y, pointerDownTime, now);
                return true;
            default:
                return true;
        }
    }

    private void toggleMaximize() {
        host.focusWindow(this);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (!maximized) {
            restoreX = lp.leftMargin;
            restoreY = lp.topMargin;
            restoreW = getWidth();
            restoreH = getHeight();
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
        postDelayed(this::syncDisplaySize, 80);
    }

    private void updatePosition(int x, int y) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.leftMargin = x;
        lp.topMargin = y;
        setLayoutParams(lp);
    }

    private void updateSize(int width, int height, boolean sync) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.width = width;
        lp.height = height;
        setLayoutParams(lp);
        if (sync) postDelayed(this::syncDisplaySize, 40);
    }

    private void syncDisplaySize() {
        if (displayId < 0 || closed) return;
        int width = currentDisplayWidth();
        int height = currentDisplayHeight();
        SurfaceTexture st = surfaceView.getSurfaceTexture();
        if (st != null) st.setDefaultBufferSize(width, height);
        bridge.resizeDisplay(displayId, width, height, dpi, result -> {
            if (!result.startsWith("OK|")) showStatus("Resize gagal\n" + result);
        });
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (closed || displayStarting || displayId >= 0) return;
        displayStarting = true;
        surfaceTexture.setDefaultBufferSize(currentDisplayWidth(), currentDisplayHeight());
        if (displaySurface != null) {
            try { displaySurface.release(); } catch (Throwable ignored) {}
        }
        displaySurface = new Surface(surfaceTexture);
        showStatus("Membuat window display…");
        postDelayed(() -> {
            if (closed || displaySurface == null || !displaySurface.isValid()) { displayStarting = false; return; }
            int displayWidth = currentDisplayWidth();
            int displayHeight = currentDisplayHeight();
            bridge.createDisplay(displaySurface, displayWidth, displayHeight, dpi, app.label, result -> {
                displayStarting = false;
                if (closed) return;
                if (!result.startsWith("OK|")) {
                    showStatus("Display gagal\n" + result);
                    return;
                }
                try {
                    displayId = Integer.parseInt(result.split("\\|")[1]);
                } catch (Throwable t) {
                    showStatus("Response display rusak\n" + result);
                    return;
                }
                showStatus("Membuka " + app.label + "…");
                bridge.launchComponent(displayId, app.packageName, app.componentName, launch -> {
                    if (launch.startsWith("EXIT=0")) {
                        statusView.setVisibility(View.GONE);
                        surfaceView.requestFocus();
                    } else {
                        showStatus("Launch gagal\n" + launch);
                    }
                });
            });
        }, 80);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        surfaceTexture.setDefaultBufferSize(currentDisplayWidth(), currentDisplayHeight());
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        int id = displayId;
        displayId = -1;
        displayStarting = false;
        if (id >= 0) bridge.releaseDisplay(id);
        if (displaySurface != null) {
            try { displaySurface.release(); } catch (Throwable ignored) {}
            displaySurface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

    private int currentDisplayWidth() { return Math.max(320, surfaceView.getWidth()); }
    private int currentDisplayHeight() { return Math.max(240, surfaceView.getHeight()); }

    private TextView titleButton(String text) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(17);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setClickable(true);
        view.setFocusable(true);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    private void showStatus(String text) {
        main.post(() -> {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(text);
        });
    }

    private GradientDrawable makeBackground(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(radius);
        if (strokeWidth > 0) gd.setStroke(strokeWidth, stroke);
        return gd;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
