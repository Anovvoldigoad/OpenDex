package com.opendex.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.opendex.launcher.data.AppEntry;
import com.opendex.launcher.data.AppRepository;
import com.opendex.launcher.ui.Ui;
import com.opendex.launcher.ui.WindowsLogoView;
import com.opendex.launcher.util.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class TaskbarOverlayService extends Service {
    private static final String CHANNEL_ID = "opendex_taskbar";
    private static final int NOTIFICATION_ID = 3001;
    private WindowManager windowManager;
    private FrameLayout overlay;
    private ShizukuController shizuku;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView clock;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        shizuku = new ShizukuController(this);
        shizuku.start(state -> {});
        if (Settings.canDrawOverlays(this)) showOverlay();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.overlayEnabled(this)) { stopSelf(); return START_NOT_STICKY; }
        if (overlay == null && Settings.canDrawOverlays(this)) showOverlay();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Throwable ignored) {}
        }
        overlay = null;
        if (shizuku != null) shizuku.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new FrameLayout(this);
        overlay.setPadding(Ui.dp(this, 5), Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 4));
        overlay.setBackground(Ui.stroke(0xe6171c27, 0x45ffffff, 1, Ui.dp(this, 11)));

        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        overlay.addView(center, centerLp);

        FrameLayout startWrap = iconWrap();
        WindowsLogoView start = new WindowsLogoView(this);
        start.setOnClickListener(v -> openDesktop());
        startWrap.addView(start, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        center.addView(startWrap);

        TextView back = textIcon("←");
        back.setOnClickListener(v -> shizuku.key(-1, "KEYCODE_BACK", null));
        center.addView(back, iconLp());
        TextView home = textIcon("⌂");
        home.setOnClickListener(v -> shizuku.key(-1, "KEYCODE_HOME", null));
        center.addView(home, iconLp());
        TextView recent = textIcon("▣");
        recent.setOnClickListener(v -> shizuku.key(-1, "KEYCODE_APP_SWITCH", null));
        center.addView(recent, iconLp());

        AppRepository repo = new AppRepository(this);
        List<AppEntry> pins = repo.choosePinned(repo.loadLaunchableApps(), 3);
        for (AppEntry app : pins) {
            FrameLayout wrap = iconWrap();
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            wrap.addView(icon, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            wrap.setOnClickListener(v -> shizuku.launch(app.component, -1, ShizukuController.LaunchMode.FREEFORM, null));
            center.addView(wrap);
        }

        clock = Ui.text(this, "", 10, Color.WHITE);
        clock.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(Ui.dp(this, 74), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
        overlay.addView(clock, clockLp);
        updateClock();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, Ui.dp(this, 56),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        lp.x = 0; lp.y = Ui.dp(this, 4);
        windowManager.addView(overlay, lp);
    }

    private void updateClock() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm\ndd/MM", Locale.getDefault()).format(new Date()));
        handler.postDelayed(this::updateClock, 30_000);
    }

    private FrameLayout iconWrap() {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Ui.solid(0x002d3645, Ui.dp(this, 7)));
        f.setLayoutParams(iconLp());
        return f;
    }

    private TextView textIcon(String value) {
        TextView t = Ui.text(this, value, 20, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.solid(0x002d3645, Ui.dp(this, 7)));
        return t;
    }

    private LinearLayout.LayoutParams iconLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 44), LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        return lp;
    }

    private void openDesktop() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, getString(com.opendex.launcher.R.string.taskbar_channel_name), NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps the optional OpenDex overlay taskbar running.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private Notification buildNotification() {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(com.opendex.launcher.R.drawable.opendex_logo)
                .setContentTitle("OpenDex taskbar")
                .setContentText("Desktop taskbar overlay is active")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
