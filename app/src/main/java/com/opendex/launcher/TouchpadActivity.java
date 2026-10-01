package com.opendex.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.opendex.launcher.ui.Ui;

public final class TouchpadActivity extends Activity {
    private ShizukuController shizuku;
    private int displayId = -1;
    private int displayW = 1920;
    private int displayH = 1080;
    private float cursorX;
    private float cursorY;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private long downAt;
    private long lastMoveSent;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shizuku = new ShizukuController(this);
        shizuku.start(state -> {});
        resolveDisplay();
        setContentView(buildUi());
    }

    @Override protected void onResume() { super.onResume(); resolveDisplay(); }
    @Override protected void onDestroy() { if (shizuku != null) shizuku.release(); super.onDestroy(); }

    private void resolveDisplay() {
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (d.getDisplayId() == Display.DEFAULT_DISPLAY) continue;
            displayId = d.getDisplayId();
            DisplayMetrics m = new DisplayMetrics();
            d.getRealMetrics(m);
            displayW = Math.max(1, m.widthPixels);
            displayH = Math.max(1, m.heightPixels);
            if (cursorX == 0 && cursorY == 0) { cursorX = displayW / 2f; cursorY = displayH / 2f; }
            return;
        }
        displayId = -1;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        root.setBackgroundColor(0xff10141d);

        TextView title = Ui.text(this, "OpenDex Touchpad", 24, Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        TextView hint = Ui.text(this, "Gerakkan 1 jari untuk pointer · tap untuk klik · 2 jari atas/bawah untuk page scroll", 13, 0xffaeb8c6);
        hint.setPadding(0, 0, 0, Ui.dp(this, 12));
        root.addView(hint);

        TextView pad = Ui.text(this, "", 16, 0xffd8e6f7);
        pad.setGravity(Gravity.CENTER);
        pad.setBackground(Ui.stroke(0xff1c2432, 0x55ffffff, 1, Ui.dp(this, 18)));
        pad.setOnTouchListener(this::onPadTouch);
        root.addView(pad, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(0, Ui.dp(this, 12), 0, 0);
        root.addView(nav, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));
        nav.addView(navButton("← Back", "KEYCODE_BACK"), navLp());
        nav.addView(navButton("⌂ Home", "KEYCODE_HOME"), navLp());
        nav.addView(navButton("▣ Recent", "KEYCODE_APP_SWITCH"), navLp());
        return root;
    }

    private TextView navButton(String text, String key) {
        TextView b = Ui.button(this, text);
        b.setOnClickListener(v -> {
            if (!ready()) return;
            shizuku.key(displayId, key, null);
        });
        return b;
    }

    private LinearLayout.LayoutParams navLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        return lp;
    }

    private boolean onPadTouch(View v, MotionEvent e) {
        if (!ready()) return true;
        if (e.getPointerCount() >= 2) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                downY = e.getY();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                float dy = e.getY() - downY;
                if (Math.abs(dy) > Ui.dp(this, 40)) shizuku.key(displayId, dy > 0 ? "KEYCODE_PAGE_UP" : "KEYCODE_PAGE_DOWN", null);
            }
            return true;
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downAt = System.currentTimeMillis();
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - lastX;
                float dy = e.getY() - lastY;
                lastX = e.getX(); lastY = e.getY();
                cursorX = clamp(cursorX + dx * 1.6f, 0, displayW - 1);
                cursorY = clamp(cursorY + dy * 1.6f, 0, displayH - 1);
                long now = System.currentTimeMillis();
                if (now - lastMoveSent >= 55) {
                    lastMoveSent = now;
                    shizuku.pointerMove(displayId, Math.round(cursorX), Math.round(cursorY));
                }
                return true;
            case MotionEvent.ACTION_UP:
                float moved = Math.abs(e.getX() - downX) + Math.abs(e.getY() - downY);
                if (moved < Ui.dp(this, 12) && System.currentTimeMillis() - downAt < 350) {
                    shizuku.pointerClick(displayId, Math.round(cursorX), Math.round(cursorY));
                }
                return true;
            default: return true;
        }
    }

    private boolean ready() {
        if (displayId < 0) {
            Toast.makeText(this, "Monitor eksternal belum terdeteksi.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (shizuku.getState() != ShizukuController.State.READY) {
            Toast.makeText(this, "Grant Shizuku dulu dari OpenDex Settings.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
