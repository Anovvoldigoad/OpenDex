package com.opendex.droiduphost;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String DROIDUP_PACKAGE = "com.levelup.droiduplauncher";

    private ShizukuHostBridge bridge;
    private TextView shizukuStatus;
    private TextView launcherStatus;
    private TextView detail;
    private ProgressBar progress;
    private Button permissionButton;
    private Button installButton;
    private Button startButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("DroidUP Dex Host");
        bridge = new ShizukuHostBridge(this);
        setContentView(buildUi());
        bridge.start(this::refresh);
        refresh();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(18, 18, 22));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text("DroidUP Dex · Android Host", 25, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWrap());

        TextView note = text("Launcher DroidUP asli dipakai tanpa perubahan UI. Host ini cuma menggantikan PC/scrcpy untuk membuat display dan memberi input.", 14, Color.rgb(190,190,198));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = fullWrap();
        noteLp.topMargin = dp(10);
        root.addView(note, noteLp);

        shizukuStatus = text("", 16, Color.WHITE);
        LinearLayout.LayoutParams statusLp = fullWrap();
        statusLp.topMargin = dp(28);
        root.addView(shizukuStatus, statusLp);

        launcherStatus = text("", 16, Color.WHITE);
        LinearLayout.LayoutParams lsLp = fullWrap();
        lsLp.topMargin = dp(8);
        root.addView(launcherStatus, lsLp);

        permissionButton = button("IZINKAN SHIZUKU");
        permissionButton.setOnClickListener(v -> bridge.requestPermission());
        root.addView(permissionButton, buttonLp());

        installButton = button("INSTALL / REPAIR DROIDUP LAUNCHER");
        installButton.setOnClickListener(v -> installLauncher(false));
        root.addView(installButton, buttonLp());

        startButton = button("START DROIDUP DEX");
        startButton.setOnClickListener(v -> startDex());
        root.addView(startButton, buttonLp());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(ProgressBar.GONE);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        pLp.topMargin = dp(14);
        root.addView(progress, pLp);

        detail = text("", 12, Color.rgb(160,160,170));
        LinearLayout.LayoutParams dLp = fullWrap();
        dLp.topMargin = dp(12);
        root.addView(detail, dLp);

        return root;
    }

    private void startDex() {
        if (!bridge.isReady()) {
            Toast.makeText(this, "Shizuku belum ready", Toast.LENGTH_SHORT).show();
            bridge.requestPermission();
            return;
        }
        if (!isLauncherInstalled()) {
            installLauncher(true);
            return;
        }
        bridge.enableDroidUpSettings(result -> {
            detail.setText(result);
            Intent i = new Intent(this, DesktopActivity.class);
            startActivity(i);
        });
    }

    private void installLauncher(boolean startAfter) {
        if (!bridge.isReady()) {
            Toast.makeText(this, "Shizuku belum ready", Toast.LENGTH_SHORT).show();
            bridge.requestPermission();
            return;
        }
        setBusy(true);
        progress.setVisibility(ProgressBar.VISIBLE);
        progress.setProgress(0);
        detail.setText("Mengirim APK launcher DroidUP asli ke shell…");
        bridge.installBundledLauncher(
                pct -> progress.setProgress(pct),
                result -> {
                    setBusy(false);
                    progress.setVisibility(ProgressBar.GONE);
                    detail.setText(result);
                    refresh();
                    if (result.contains("Success")) {
                        Toast.makeText(this, "DroidUP launcher terpasang", Toast.LENGTH_SHORT).show();
                        if (startAfter) startDex();
                    } else {
                        Toast.makeText(this, "Install launcher gagal. Lihat detail.", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void setBusy(boolean busy) {
        permissionButton.setEnabled(!busy);
        installButton.setEnabled(!busy);
        startButton.setEnabled(!busy);
    }

    private boolean isLauncherInstalled() {
        try {
            getPackageManager().getPackageInfo(DROIDUP_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void refresh() {
        if (shizukuStatus == null) return;
        shizukuStatus.setText("Shizuku: " + bridge.status());
        boolean installed = isLauncherInstalled();
        launcherStatus.setText("DroidUP Launcher: " + (installed ? "installed (original APK)" : "belum terpasang"));
        permissionButton.setVisibility(bridge.hasPermission() ? Button.GONE : Button.VISIBLE);
        installButton.setEnabled(bridge.isReady());
        startButton.setEnabled(bridge.isReady());
    }

    @Override protected void onDestroy() {
        if (bridge != null) bridge.stop();
        super.onDestroy();
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        return b;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(14);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
