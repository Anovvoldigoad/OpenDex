package com.opendex.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.opendex.launcher.ui.Ui;
import com.opendex.launcher.util.Prefs;

public final class SettingsActivity extends Activity {
    private static final int NOTIFICATION_REQUEST = 71;
    private ShizukuController shizuku;
    private TextView status;
    private TextView overlayAction;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shizuku = new ShizukuController(this);
        shizuku.start(state -> refresh());
        setContentView(buildUi());
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onDestroy() { if (shizuku != null) shizuku.release(); super.onDestroy(); }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xfff3f3f3);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 30));
        scroll.addView(root);

        TextView title = Ui.text(this, "Settings", 30, 0xff1b1b1b);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));

        TextView subtitle = Ui.text(this, "OpenDex desktop environment", 13, 0xff606060);
        root.addView(subtitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 30)));

        root.addView(section("System"));
        status = cardText("Checking Shizuku…");
        root.addView(status);
        root.addView(actionCard("Shizuku permission", "Grant shell access used for freeform windows", v -> handleShizuku()));
        root.addView(actionCard("Enable freeform mode", "Enable Android freeform and force-resizable activities", v ->
                shizuku.enableFreeform(result -> Toast.makeText(this, compact(result), Toast.LENGTH_LONG).show())));
        root.addView(actionCard("Default Home app", "Choose OpenDex as the Android launcher", v -> open(Settings.ACTION_HOME_SETTINGS)));

        root.addView(section("Taskbar"));
        overlayAction = actionCard("Desktop taskbar overlay", "Show OpenDex taskbar above Android apps", v -> toggleOverlay());
        root.addView(overlayAction);
        root.addView(actionCard("Overlay permission", "Allow Display over other apps", v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))));
        if (Build.VERSION.SDK_INT >= 33) {
            root.addView(actionCard("Taskbar notification", "Allow the foreground-service notification", v -> requestNotifications()));
        }

        root.addView(section("External display"));
        Switch auto = switchCard("Auto desktop on monitor", "Open a desktop automatically on HDMI / USB-C displays", Prefs.autoExternalDesktop(this));
        auto.setOnCheckedChangeListener((buttonView, checked) -> Prefs.setAutoExternalDesktop(this, checked));
        root.addView(auto);
        root.addView(actionCard("Open phone touchpad", "Use the phone as a basic pointer for the external display", v ->
                startActivity(new Intent(this, TouchpadActivity.class))));

        root.addView(section("About"));
        TextView note = cardText("OpenDex v0.3.1-alpha\nWindows-inspired Android desktop shell. Uses original visual assets.\n\nTap apps in OpenDex to launch them in freeform. Long-press an app for freeform, fullscreen, or close options. OEM support varies because Android vendors implement freeform differently.");
        root.addView(note);
        return scroll;
    }

    private TextView section(String name) {
        TextView t = Ui.text(this, name, 16, 0xff202020);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 6));
        return t;
    }

    private TextView cardText(String text) {
        TextView t = Ui.text(this, text, 13, 0xff303030);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        t.setBackground(Ui.stroke(Color.WHITE, 0xffe2e2e2, 1, Ui.dp(this, 10)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView actionCard(String title, String subtitle, android.view.View.OnClickListener click) {
        TextView t = Ui.text(this, title + "\n" + subtitle + "    ›", 14, 0xff202020);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        t.setBackground(Ui.stroke(Color.WHITE, 0xffe2e2e2, 1, Ui.dp(this, 10)));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 68));
        lp.bottomMargin = Ui.dp(this, 7);
        t.setLayoutParams(lp);
        return t;
    }

    @SuppressWarnings("deprecation")
    private Switch switchCard(String title, String subtitle, boolean checked) {
        Switch s = new Switch(this);
        s.setText(title + "\n" + subtitle);
        s.setTextColor(0xff202020);
        s.setTextSize(14);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setChecked(checked);
        s.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        s.setBackground(Ui.stroke(Color.WHITE, 0xffe2e2e2, 1, Ui.dp(this, 10)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 72));
        lp.bottomMargin = Ui.dp(this, 7);
        s.setLayoutParams(lp);
        return s;
    }

    private void handleShizuku() {
        switch (shizuku.getState()) {
            case PERMISSION_REQUIRED: shizuku.requestPermission(); break;
            case PERMISSION_BLOCKED:
                Toast.makeText(this, "Open Shizuku > Authorized applications > OpenDex.", Toast.LENGTH_LONG).show(); break;
            case OFFLINE:
                Toast.makeText(this, "Start Shizuku first.", Toast.LENGTH_LONG).show(); break;
            case READY:
                Toast.makeText(this, shizuku.getStateLabel(), Toast.LENGTH_SHORT).show(); break;
            default: Toast.makeText(this, shizuku.getStateLabel(), Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void toggleOverlay() {
        boolean enabled = Prefs.overlayEnabled(this);
        if (!enabled && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow overlay permission first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        Prefs.setOverlayEnabled(this, !enabled);
        Intent service = new Intent(this, TaskbarOverlayService.class);
        try {
            if (enabled) stopService(service);
            else if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (Throwable t) {
            Toast.makeText(this, "Taskbar service: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notification permission already granted.", Toast.LENGTH_SHORT).show();
        } else requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
    }

    private void refresh() {
        if (status != null) status.setText(shizuku.getStateLabel() + "\nOverlay permission: " + (Settings.canDrawOverlays(this) ? "ready" : "not granted") + "\nTaskbar overlay: " + (Prefs.overlayEnabled(this) ? "on" : "off"));
        if (overlayAction != null) overlayAction.setText((Prefs.overlayEnabled(this) ? "Disable" : "Enable") + " desktop taskbar overlay\nShow OpenDex taskbar above Android apps    ›");
    }

    private void open(String action) {
        try { startActivity(new Intent(action)); } catch (Throwable t) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private static String compact(String s) {
        if (s == null) return "unknown";
        s = s.replace('\n', ' ').trim();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }
}
