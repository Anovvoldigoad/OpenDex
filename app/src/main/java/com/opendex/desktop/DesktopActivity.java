package com.opendex.desktop;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DesktopActivity extends Activity implements AppWindowView.Host {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<AppWindowView> windows = new ArrayList<>();
    private final Map<AppWindowView, View> taskButtons = new HashMap<>();

    private FrameLayout root;
    private FrameLayout workspace;
    private LinearLayout taskbar;
    private LinearLayout runningArea;
    private FrameLayout startMenu;
    private TextView clock;
    private TextView shizukuStatus;
    private Button shizukuButton;
    private ShizukuBridge bridge;
    private List<AppEntry> apps = new ArrayList<>();
    private int cascade;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersive();
        buildDesktop();

        bridge = new ShizukuBridge(this);
        bridge.start(this::refreshShizukuState);
        loadApps();
        tickClock();
    }

    private void buildDesktop() {
        root = new FrameLayout(this);
        root.setBackground(makeBackground(0xFF0F1520, 0x00000000, 0, 0));
        setContentView(root);

        workspace = new FrameLayout(this);
        FrameLayout.LayoutParams workspaceLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        workspaceLp.bottomMargin = dp(58);
        root.addView(workspace, workspaceLp);

        // Minimal desktop watermark/status. UI is intentionally secondary in v0.6.
        TextView brand = new TextView(this);
        brand.setText("OpenDex · Mi engine prototype");
        brand.setTextColor(0x66FFFFFF);
        brand.setTextSize(12);
        FrameLayout.LayoutParams brandLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        brandLp.topMargin = dp(12);
        brandLp.rightMargin = dp(16);
        workspace.addView(brand, brandLp);

        taskbar = new LinearLayout(this);
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding(dp(8), dp(5), dp(8), dp(5));
        taskbar.setBackgroundColor(0xEE151A23);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.BOTTOM);
        root.addView(taskbar, barLp);

        TextView start = taskbarButton("⊞", 22);
        taskbar.addView(start, new LinearLayout.LayoutParams(dp(48), dp(48)));
        start.setOnClickListener(v -> toggleStartMenu());

        runningArea = new LinearLayout(this);
        runningArea.setOrientation(LinearLayout.HORIZONTAL);
        runningArea.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams runningLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        runningLp.leftMargin = dp(6);
        taskbar.addView(runningArea, runningLp);

        shizukuStatus = new TextView(this);
        shizukuStatus.setTextColor(0xFFD8DCE6);
        shizukuStatus.setTextSize(11);
        shizukuStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        shizukuStatus.setPadding(dp(8), 0, dp(8), 0);
        taskbar.addView(shizukuStatus, new LinearLayout.LayoutParams(dp(180), dp(48)));

        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(12);
        clock.setGravity(Gravity.CENTER);
        taskbar.addView(clock, new LinearLayout.LayoutParams(dp(70), dp(48)));

        buildStartMenu();
        buildShizukuOverlay();
    }

    private void buildStartMenu() {
        startMenu = new FrameLayout(this);
        startMenu.setVisibility(View.GONE);
        startMenu.setBackground(makeBackground(0xFA1B202A, 0xFF424B5B, dp(14), dp(1)));
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        menuLp.leftMargin = dp(8);
        menuLp.rightMargin = dp(8);
        menuLp.topMargin = dp(18);
        menuLp.bottomMargin = dp(66);
        root.addView(startMenu, menuLp);

        TextView loading = new TextView(this);
        loading.setTag("menu_loading");
        loading.setText("Memuat aplikasi…");
        loading.setTextColor(Color.WHITE);
        loading.setGravity(Gravity.CENTER);
        startMenu.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void populateStartMenu() {
        startMenu.removeAllViews();
        LinearLayout vertical = new LinearLayout(this);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setPadding(dp(16), dp(12), dp(16), dp(12));
        startMenu.addView(vertical, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView heading = new TextView(this);
        heading.setText("Apps");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        heading.setPadding(dp(4), 0, 0, dp(10));
        vertical.addView(heading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        ScrollView scroll = new ScrollView(this);
        vertical.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        scroll.addView(grid, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (AppEntry app : apps) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), dp(8), dp(4), dp(7));
            item.setBackground(makeBackground(0x00000000, 0x00000000, dp(8), 0));

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

            TextView label = new TextView(this);
            label.setText(app.label);
            label.setTextColor(0xFFE8EBF2);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
            labelLp.topMargin = dp(4);
            item.addView(label, labelLp);

            GridLayout.LayoutParams cell = new GridLayout.LayoutParams();
            cell.width = Math.max(dp(72), (getResources().getDisplayMetrics().widthPixels - dp(44)) / 5);
            cell.height = dp(82);
            cell.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(item, cell);
            item.setOnClickListener(v -> {
                startMenu.setVisibility(View.GONE);
                openApp(app);
            });
        }
    }

    private void buildShizukuOverlay() {
        LinearLayout card = new LinearLayout(this);
        card.setTag("shizuku_card");
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(24), dp(20), dp(24), dp(20));
        card.setBackground(makeBackground(0xFA171B23, 0xFF586174, dp(14), dp(1)));

        TextView title = new TextView(this);
        title.setText("Shizuku diperlukan");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        TextView desc = new TextView(this);
        desc.setTag("shizuku_desc");
        desc.setTextColor(0xFFD4D9E3);
        desc.setTextSize(13);
        desc.setGravity(Gravity.CENTER);
        card.addView(desc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));

        shizukuButton = new Button(this);
        shizukuButton.setText("GRANT SHIZUKU");
        shizukuButton.setOnClickListener(v -> bridge.requestPermission());
        card.addView(shizukuButton, new LinearLayout.LayoutParams(dp(210), dp(52)));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210), Gravity.CENTER);
        cardLp.leftMargin = dp(20);
        cardLp.rightMargin = dp(20);
        root.addView(card, cardLp);
    }

    private void refreshShizukuState() {
        if (bridge == null) return;
        shizukuStatus.setText(bridge.status());
        View card = root.findViewWithTag("shizuku_card");
        TextView desc = root.findViewWithTag("shizuku_desc");
        if (desc != null) desc.setText(bridge.status());
        if (bridge.isReady()) {
            if (card != null) card.setVisibility(View.GONE);
        } else {
            if (card != null) card.setVisibility(View.VISIBLE);
            if (shizukuButton != null) shizukuButton.setEnabled(bridge.binderAlive());
        }
    }

    private void loadApps() {
        loader.execute(() -> {
            List<AppEntry> loaded = AppRepository.load(this);
            main.post(() -> {
                apps = loaded;
                populateStartMenu();
            });
        });
    }

    private void openApp(AppEntry app) {
        if (bridge == null || !bridge.isReady()) {
            Toast.makeText(this, "Shizuku belum ready", Toast.LENGTH_SHORT).show();
            refreshShizukuState();
            return;
        }

        AppWindowView window = new AppWindowView(this, app, bridge, this, 240);
        windows.add(window);
        workspace.addView(window);

        workspace.post(() -> {
            int sw = Math.max(dp(800), workspace.getWidth());
            int sh = Math.max(dp(480), workspace.getHeight());
            int w = Math.min(Math.round(sw * 0.68f), sw - dp(80));
            int h = Math.min(Math.round(sh * 0.74f), sh - dp(70));
            int step = dp(26);
            int x = Math.max(dp(16), (sw - w) / 2 + ((cascade % 5) - 2) * step);
            int y = Math.max(dp(16), (sh - h) / 2 + ((cascade % 5) - 2) * step);
            cascade++;
            window.setInitialBounds(x, y, w, h);
            focusWindow(window);
        });

        View task = makeTaskButton(window);
        taskButtons.put(window, task);
        runningArea.addView(task);
    }

    private View makeTaskButton(AppWindowView window) {
        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(7), dp(4), dp(7), dp(4));
        button.setBackground(makeBackground(0x33FFFFFF, 0x00000000, dp(8), 0));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(window.app().icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(44));
        lp.rightMargin = dp(4);
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> {
            if (window.isMinimized()) window.restoreFromMinimize();
            focusWindow(window);
        });
        return button;
    }

    private void toggleStartMenu() {
        startMenu.setVisibility(startMenu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        startMenu.bringToFront();
        taskbar.bringToFront();
    }

    @Override
    public void focusWindow(AppWindowView window) {
        if (window == null) return;
        window.focus();
        startMenu.bringToFront();
        if (startMenu.getVisibility() != View.VISIBLE) workspace.bringChildToFront(window);
        taskbar.bringToFront();
    }

    @Override
    public void minimizeWindow(AppWindowView window) {
        if (window != null) window.minimize();
        taskbar.bringToFront();
    }

    @Override
    public void closeWindow(AppWindowView window) {
        if (window == null) return;
        window.close();
        workspace.removeView(window);
        windows.remove(window);
        View task = taskButtons.remove(window);
        if (task != null) runningArea.removeView(task);
    }

    @Override public int workspaceWidth() { return Math.max(1, workspace.getWidth()); }
    @Override public int workspaceHeight() { return Math.max(1, workspace.getHeight()); }

    private TextView taskbarButton(String text, int sp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER);
        view.setBackground(makeBackground(0x22FFFFFF, 0x00000000, dp(9), 0));
        return view;
    }

    private void tickClock() {
        String value = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        clock.setText(value);
        main.postDelayed(this::tickClock, 30_000);
    }

    private GradientDrawable makeBackground(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(radius);
        if (strokeWidth > 0) gd.setStroke(strokeWidth, stroke);
        return gd;
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override protected void onDestroy() {
        for (AppWindowView window : new ArrayList<>(windows)) window.close();
        windows.clear();
        if (bridge != null) bridge.stop();
        loader.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
