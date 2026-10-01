package com.opendex.launcher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.opendex.launcher.ui.Ui;
import com.opendex.launcher.ui.WindowsDesktopView;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Crash-safe launcher entry point.
 *
 * Startup is deliberately staged so an OEM-specific failure in Shizuku,
 * package scanning, display management, or immersive mode does not turn into
 * a silent force-close. Fatal UI construction failures are rendered on-screen
 * with a copyable stack trace.
 */
public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private ShizukuController shizuku;
    private WindowsDesktopView desktop;
    private ExternalDisplayController externalDisplays;
    private TextView bootStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Throwable ignored) {}
        showBootScreen();
        main.post(this::startOpenDexSafely);
    }

    private void startOpenDexSafely() {
        try {
            setBootStatus("Preparing desktop…");
            shizuku = new ShizukuController(this);
        } catch (Throwable t) {
            showFatal("Shizuku controller construction", t);
            return;
        }

        try {
            setBootStatus("Building Windows desktop…");
            desktop = new WindowsDesktopView(this, shizuku, -1);
            setContentView(desktop);
        } catch (Throwable t) {
            showFatal("Desktop UI construction", t);
            return;
        }

        // The desktop must remain usable even when Shizuku is absent/broken.
        try {
            shizuku.start(state -> {
                try { if (desktop != null) desktop.refreshStatus(); } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            toast("Shizuku unavailable: " + shortMessage(t));
        }

        // External displays are optional; never let vendor display bugs kill HOME.
        try {
            externalDisplays = new ExternalDisplayController(this, shizuku);
            externalDisplays.start();
        } catch (Throwable t) {
            externalDisplays = null;
            toast("External display disabled: " + shortMessage(t));
        }

        try { enterImmersive(); } catch (Throwable ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        try { enterImmersive(); } catch (Throwable ignored) {}
        try { if (desktop != null) { desktop.refreshStatus(); desktop.refreshApps(); } } catch (Throwable ignored) {}
        try { if (externalDisplays != null) externalDisplays.refresh(); } catch (Throwable ignored) {}
    }

    @Override protected void onDestroy() {
        try { if (externalDisplays != null) externalDisplays.stop(); } catch (Throwable ignored) {}
        try { if (shizuku != null) shizuku.release(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void showBootScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 28), Ui.dp(this, 28), Ui.dp(this, 28));
        root.setBackgroundColor(0xff0b1020);

        TextView title = Ui.text(this, "OpenDex", 34, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));

        bootStatus = Ui.text(this, "Starting…", 14, 0xffb9c7db);
        bootStatus.setGravity(Gravity.CENTER);
        root.addView(bootStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        setContentView(root);
    }

    private void setBootStatus(String value) {
        if (bootStatus != null) bootStatus.setText(value);
    }

    private void showFatal(String stage, Throwable error) {
        String trace = stackTrace(error);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff111827);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 22), Ui.dp(this, 26), Ui.dp(this, 22), Ui.dp(this, 32));
        scroll.addView(root);

        TextView title = Ui.text(this, "OpenDex startup error", 25, 0xffffd7d7);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        TextView stageView = Ui.text(this, "Stage: " + stage + "\n\n" + error.getClass().getName() + ": " + String.valueOf(error.getMessage()), 14, Color.WHITE);
        stageView.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 14));
        root.addView(stageView);

        TextView copy = Ui.button(this, "Copy diagnostic");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("OpenDex crash", "Stage: " + stage + "\n" + trace));
            toast("Diagnostic copied");
        });
        root.addView(copy, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));

        TextView details = Ui.text(this, trace, 11, 0xffd4d9e2);
        details.setTextIsSelectable(true);
        details.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(details);
        setContentView(scroll);
    }

    private void enterImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void toast(String text) {
        try { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
    }

    private static String shortMessage(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return t == null ? "unknown" : t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (t != null) t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
