package com.opendex.launcher;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.opendex.launcher.ui.WindowsDesktopView;

public final class MainActivity extends Activity {
    private ShizukuController shizuku;
    private WindowsDesktopView desktop;
    private ExternalDisplayController externalDisplays;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();
        shizuku = new ShizukuController(this);
        desktop = new WindowsDesktopView(this, shizuku, -1);
        setContentView(desktop);
        shizuku.start(state -> { if (desktop != null) desktop.refreshStatus(); });
        externalDisplays = new ExternalDisplayController(this, shizuku);
        externalDisplays.start();
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
        if (desktop != null) { desktop.refreshStatus(); desktop.refreshApps(); }
        if (externalDisplays != null) externalDisplays.refresh();
    }

    @Override protected void onDestroy() {
        if (externalDisplays != null) externalDisplays.stop();
        if (shizuku != null) shizuku.release();
        super.onDestroy();
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
}
