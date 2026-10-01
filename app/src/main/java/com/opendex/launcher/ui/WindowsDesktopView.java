package com.opendex.launcher.ui;

import android.os.BatteryManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.opendex.launcher.SettingsActivity;
import com.opendex.launcher.ShizukuController;
import com.opendex.launcher.TouchpadActivity;
import com.opendex.launcher.data.AppEntry;
import com.opendex.launcher.data.AppRepository;
import com.opendex.launcher.util.RecentStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A responsive Windows-11-inspired desktop shell. All visual assets are original.
 * Native Android apps are launched through Shizuku into freeform/fullscreen modes.
 */
public final class WindowsDesktopView extends FrameLayout {
    private final ShizukuController shizuku;
    private final int targetDisplayId;
    private final AppRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<AppEntry> allApps = new ArrayList<>();

    private FrameLayout startPanel;
    private FrameLayout quickPanel;
    private LinearLayout pinnedGridHost;
    private LinearLayout recommendedHost;
    private LinearLayout taskbarAppsHost;
    private EditText startSearch;
    private TextView startSectionTitle;
    private TextView clockText;
    private TextView batteryText;
    private TextView shizukuText;
    private boolean showingAllApps;

    public WindowsDesktopView(Context context, ShizukuController shizuku, int targetDisplayId) {
        super(context);
        this.shizuku = shizuku;
        this.targetDisplayId = targetDisplayId;
        this.repository = new AppRepository(context);
        setFocusable(true);
        buildDesktop();
        refreshApps();
        refreshStatus();
        scheduleClock();
    }

    private void buildDesktop() {
        WallpaperView wallpaper = new WallpaperView(getContext());
        addView(wallpaper, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        addView(buildDesktopIcons(), desktopIconLayoutParams());
        addView(buildTaskbar(), taskbarLayoutParams());

        startPanel = buildStartPanel();
        addView(startPanel, floatingPanelLayoutParams(true));
        startPanel.setVisibility(GONE);

        quickPanel = buildQuickPanel();
        addView(quickPanel, floatingPanelLayoutParams(false));
        quickPanel.setVisibility(GONE);

        setOnClickListener(v -> closePanels());
        setOnLongClickListener(v -> {
            Toast.makeText(getContext(), "OpenDex desktop · tahan app untuk opsi window", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private View buildDesktopIcons() {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(getContext(), 10), Ui.dp(getContext(), 12), 0, 0);
        column.setOnClickListener(v -> closePanels());
        column.addView(desktopShortcut("▣", "This PC", v -> openFiles()));
        column.addView(desktopShortcut("⌘", "All apps", v -> showStart(true)));
        column.addView(desktopShortcut("⚙", "Settings", v -> openAppActivity(SettingsActivity.class)));
        column.addView(desktopShortcut("⌁", "Touchpad", v -> openTouchpad()));
        return column;
    }

    private View desktopShortcut(String symbol, String label, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(getContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(Ui.dp(getContext(), 5), Ui.dp(getContext(), 6), Ui.dp(getContext(), 5), Ui.dp(getContext(), 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(getContext(), 82), Ui.dp(getContext(), 92));
        lp.bottomMargin = Ui.dp(getContext(), 4);
        tile.setLayoutParams(lp);

        TextView icon = Ui.text(getContext(), symbol, 31, Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        tile.addView(icon, new LinearLayout.LayoutParams(Ui.dp(getContext(), 54), Ui.dp(getContext(), 54)));

        TextView name = Ui.text(getContext(), label, 12, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setShadowLayer(3, 0, 1, 0xaa000000);
        name.setMaxLines(2);
        tile.addView(name, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 30)));
        tile.setOnClickListener(listener);
        tile.setBackground(Ui.solid(0x00111111, Ui.dp(getContext(), 7)));
        return tile;
    }

    private View buildTaskbar() {
        FrameLayout shell = new FrameLayout(getContext());
        shell.setPadding(Ui.dp(getContext(), 8), Ui.dp(getContext(), 5), Ui.dp(getContext(), 8), Ui.dp(getContext(), 5));
        shell.setBackground(Ui.stroke(0xdd171c27, 0x35ffffff, 1, Ui.dp(getContext(), 12)));
        shell.setOnClickListener(v -> { /* consume */ });

        LinearLayout center = new LinearLayout(getContext());
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER);
        shell.addView(center, centerLp);

        FrameLayout startWrap = taskIconWrap();
        WindowsLogoView start = new WindowsLogoView(getContext());
        start.setContentDescription("Start");
        start.setOnClickListener(v -> toggleStart());
        startWrap.addView(start, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        center.addView(startWrap);

        TextView search = taskTextIcon("⌕");
        search.setContentDescription("Search");
        search.setOnClickListener(v -> showStartWithKeyboard());
        center.addView(search, taskItemLp());

        taskbarAppsHost = new LinearLayout(getContext());
        taskbarAppsHost.setOrientation(LinearLayout.HORIZONTAL);
        center.addView(taskbarAppsHost, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        LinearLayout tray = new LinearLayout(getContext());
        tray.setOrientation(LinearLayout.HORIZONTAL);
        tray.setGravity(Gravity.CENTER_VERTICAL);
        tray.setPadding(Ui.dp(getContext(), 7), 0, Ui.dp(getContext(), 7), 0);
        tray.setBackground(Ui.solid(0x18ffffff, Ui.dp(getContext(), 8)));
        tray.setOnClickListener(v -> toggleQuickPanel());
        FrameLayout.LayoutParams trayLp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL);
        shell.addView(tray, trayLp);

        TextView net = Ui.text(getContext(), "⌁", 16, Color.WHITE);
        net.setGravity(Gravity.CENTER);
        tray.addView(net, new LinearLayout.LayoutParams(Ui.dp(getContext(), 30), LayoutParams.MATCH_PARENT));

        batteryText = Ui.text(getContext(), "▰", 11, Color.WHITE);
        batteryText.setGravity(Gravity.CENTER);
        tray.addView(batteryText, new LinearLayout.LayoutParams(Ui.dp(getContext(), 44), LayoutParams.MATCH_PARENT));

        clockText = Ui.text(getContext(), "", 11, Color.WHITE);
        clockText.setGravity(Gravity.CENTER);
        clockText.setPadding(Ui.dp(getContext(), 5), 0, Ui.dp(getContext(), 5), 0);
        tray.addView(clockText, new LinearLayout.LayoutParams(Ui.dp(getContext(), 94), LayoutParams.MATCH_PARENT));
        return shell;
    }

    private FrameLayout buildStartPanel() {
        FrameLayout shell = new FrameLayout(getContext());
        shell.setPadding(Ui.dp(getContext(), 24), Ui.dp(getContext(), 20), Ui.dp(getContext(), 24), Ui.dp(getContext(), 14));
        shell.setBackground(Ui.stroke(0xf21f2633, 0x45ffffff, 1, Ui.dp(getContext(), 14)));
        shell.setElevation(Ui.dp(getContext(), 20));
        shell.setOnClickListener(v -> { /* consume */ });

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        shell.addView(body, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        startSearch = new EditText(getContext());
        startSearch.setSingleLine(true);
        startSearch.setHint("Type here to search");
        startSearch.setHintTextColor(0xff9ca7b8);
        startSearch.setTextColor(Color.WHITE);
        startSearch.setTextSize(14);
        startSearch.setPadding(Ui.dp(getContext(), 16), 0, Ui.dp(getContext(), 16), 0);
        startSearch.setBackground(Ui.stroke(0xff2a3242, 0xff4cc2ff, Ui.dp(getContext(), 1), Ui.dp(getContext(), 9)));
        body.addView(startSearch, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 44)));
        startSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { renderStartContent(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 52));
        headerLp.topMargin = Ui.dp(getContext(), 4);
        body.addView(header, headerLp);

        startSectionTitle = Ui.text(getContext(), "Pinned", 14, Color.WHITE);
        startSectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(startSectionTitle, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));

        TextView allAppsButton = Ui.button(getContext(), "All apps  ›");
        allAppsButton.setTextSize(12);
        allAppsButton.setOnClickListener(v -> { showingAllApps = !showingAllApps; renderStartContent(); });
        header.addView(allAppsButton, new LinearLayout.LayoutParams(Ui.dp(getContext(), 92), Ui.dp(getContext(), 32)));

        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        body.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        pinnedGridHost = new LinearLayout(getContext());
        pinnedGridHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(pinnedGridHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView recommendedTitle = Ui.text(getContext(), "Recommended", 14, Color.WHITE);
        recommendedTitle.setTypeface(Typeface.DEFAULT_BOLD);
        recommendedTitle.setPadding(0, Ui.dp(getContext(), 16), 0, Ui.dp(getContext(), 8));
        content.addView(recommendedTitle);

        recommendedHost = new LinearLayout(getContext());
        recommendedHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(recommendedHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout footer = new LinearLayout(getContext());
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(Ui.dp(getContext(), 4), Ui.dp(getContext(), 6), Ui.dp(getContext(), 4), 0);
        body.addView(footer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 48)));

        TextView avatar = Ui.text(getContext(), "●", 20, 0xff8fc9ff);
        avatar.setGravity(Gravity.CENTER);
        footer.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(getContext(), 38), LayoutParams.MATCH_PARENT));

        shizukuText = Ui.text(getContext(), "Shizuku…", 12, 0xffe8edf5);
        shizukuText.setOnClickListener(v -> handleShizukuClick());
        footer.addView(shizukuText, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));

        TextView settings = taskTextIcon("⚙");
        settings.setOnClickListener(v -> openAppActivity(SettingsActivity.class));
        footer.addView(settings, new LinearLayout.LayoutParams(Ui.dp(getContext(), 42), Ui.dp(getContext(), 38)));

        TextView power = taskTextIcon("⏻");
        power.setOnClickListener(v -> launchSystemSettings(Settings.ACTION_SETTINGS));
        footer.addView(power, new LinearLayout.LayoutParams(Ui.dp(getContext(), 42), Ui.dp(getContext(), 38)));
        return shell;
    }

    private FrameLayout buildQuickPanel() {
        FrameLayout shell = new FrameLayout(getContext());
        shell.setPadding(Ui.dp(getContext(), 16), Ui.dp(getContext(), 16), Ui.dp(getContext(), 16), Ui.dp(getContext(), 14));
        shell.setBackground(Ui.stroke(0xf21f2633, 0x45ffffff, 1, Ui.dp(getContext(), 14)));
        shell.setElevation(Ui.dp(getContext(), 18));
        shell.setOnClickListener(v -> { /* consume */ });

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        shell.addView(body, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView title = Ui.text(getContext(), "Quick settings", 15, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 38)));

        LinearLayout row1 = quickRow();
        row1.addView(quickTile("⌁", "Wi‑Fi", Settings.ACTION_WIFI_SETTINGS), quickTileLp());
        row1.addView(quickTile("ᛒ", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS), quickTileLp());
        body.addView(row1);

        LinearLayout row2 = quickRow();
        row2.addView(quickTile("▣", "Display", Settings.ACTION_DISPLAY_SETTINGS), quickTileLp());
        TextView freeform = quickTile("□", "Freeform", null);
        freeform.setOnClickListener(v -> shizuku.enableFreeform(this::toastCommand));
        row2.addView(freeform, quickTileLp());
        body.addView(row2);

        TextView state = Ui.text(getContext(), "Tap Freeform once after granting Shizuku.", 11, 0xffbdc6d4);
        state.setPadding(Ui.dp(getContext(), 4), Ui.dp(getContext(), 12), Ui.dp(getContext(), 4), 0);
        body.addView(state, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        TextView settings = Ui.button(getContext(), "Open OpenDex settings");
        settings.setOnClickListener(v -> openAppActivity(SettingsActivity.class));
        body.addView(settings, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 38)));
        return shell;
    }

    private LinearLayout quickRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 66));
        lp.topMargin = Ui.dp(getContext(), 6);
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout.LayoutParams quickTileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(Ui.dp(getContext(), 3), 0, Ui.dp(getContext(), 3), 0);
        return lp;
    }

    private TextView quickTile(String icon, String label, String action) {
        TextView tile = Ui.text(getContext(), icon + "\n" + label, 12, Color.WHITE);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(Ui.solid(0xff2b3445, Ui.dp(getContext(), 8)));
        tile.setClickable(true);
        if (action != null) tile.setOnClickListener(v -> launchSystemSettings(action));
        return tile;
    }

    public void refreshApps() {
        try {
            io.execute(() -> {
                try {
                    List<AppEntry> loaded = repository.loadLaunchableApps();
                    main.post(() -> {
                        try {
                            allApps.clear();
                            allApps.addAll(loaded);
                            renderStartContent();
                            renderTaskbarApps();
                        } catch (Throwable t) {
                            Toast.makeText(getContext(), "App list render failed: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Throwable t) {
                    main.post(() -> Toast.makeText(getContext(), "App scan failed: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show());
                }
            });
        } catch (Throwable t) {
            Toast.makeText(getContext(), "App worker unavailable: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    public void refreshStatus() {
        try { if (shizukuText != null) shizukuText.setText(shizuku.getStateLabel()); }
        catch (Throwable t) { if (shizukuText != null) shizukuText.setText("Shizuku unavailable"); }
    }

    private void renderStartContent() {
        if (pinnedGridHost == null) return;
        String query = startSearch == null ? "" : startSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<AppEntry> source;
        if (!query.isEmpty()) {
            source = new ArrayList<>();
            for (AppEntry a : allApps) {
                if (a.label.toLowerCase(Locale.ROOT).contains(query) || a.packageName.toLowerCase(Locale.ROOT).contains(query)) source.add(a);
                if (source.size() >= 30) break;
            }
            startSectionTitle.setText("Search results");
        } else if (showingAllApps) {
            source = new ArrayList<>(allApps);
            startSectionTitle.setText("All apps");
        } else {
            source = repository.choosePinned(allApps, isWide() ? 18 : 12);
            startSectionTitle.setText("Pinned");
        }
        renderAppRows(pinnedGridHost, source, showingAllApps || !query.isEmpty() ? 5 : (isWide() ? 6 : 4));

        recommendedHost.removeAllViews();
        List<AppEntry> recents = repository.resolveRecents(allApps);
        if (recents.isEmpty()) {
            TextView empty = Ui.text(getContext(), "Apps you open will appear here.", 12, 0xffaeb8c6);
            empty.setPadding(Ui.dp(getContext(), 6), Ui.dp(getContext(), 4), 0, Ui.dp(getContext(), 8));
            recommendedHost.addView(empty);
        } else {
            int count = Math.min(recents.size(), 4);
            for (int i = 0; i < count; i++) recommendedHost.addView(recommendedRow(recents.get(i)));
        }
    }

    private void renderAppRows(LinearLayout host, List<AppEntry> apps, int columns) {
        host.removeAllViews();
        if (apps.isEmpty()) {
            TextView empty = Ui.text(getContext(), "No apps found", 13, 0xffb8c0cc);
            empty.setGravity(Gravity.CENTER);
            host.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 80)));
            return;
        }
        for (int base = 0; base < apps.size(); base += columns) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            host.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 88)));
            for (int c = 0; c < columns; c++) {
                int idx = base + c;
                if (idx < apps.size()) row.addView(startAppTile(apps.get(idx)), new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
                else row.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
            }
            if (!showingAllApps && (startSearch == null || startSearch.getText().length() == 0) && base + columns >= (isWide() ? 18 : 12)) break;
        }
    }

    private View startAppTile(AppEntry app) {
        LinearLayout tile = new LinearLayout(getContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(Ui.dp(getContext(), 3), Ui.dp(getContext(), 4), Ui.dp(getContext(), 3), Ui.dp(getContext(), 4));
        ImageView icon = new ImageView(getContext());
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tile.addView(icon, new LinearLayout.LayoutParams(Ui.dp(getContext(), 38), Ui.dp(getContext(), 38)));
        TextView label = Ui.text(getContext(), app.label, 10, Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 35));
        lp.topMargin = Ui.dp(getContext(), 3);
        tile.addView(label, lp);
        tile.setBackground(Ui.solid(0x002c3545, Ui.dp(getContext(), 7)));
        tile.setOnClickListener(v -> launchApp(app, ShizukuController.LaunchMode.FREEFORM));
        tile.setOnLongClickListener(v -> { showAppMenu(v, app); return true; });
        return tile;
    }

    private View recommendedRow(AppEntry app) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(getContext(), 7), Ui.dp(getContext(), 3), Ui.dp(getContext(), 7), Ui.dp(getContext(), 3));
        ImageView icon = new ImageView(getContext());
        icon.setImageDrawable(app.icon);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(getContext(), 32), Ui.dp(getContext(), 32)));
        TextView label = Ui.text(getContext(), app.label, 12, Color.WHITE);
        label.setPadding(Ui.dp(getContext(), 10), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 42), 1));
        row.setOnClickListener(v -> launchApp(app, ShizukuController.LaunchMode.FREEFORM));
        row.setOnLongClickListener(v -> { showAppMenu(v, app); return true; });
        return row;
    }

    private void renderTaskbarApps() {
        if (taskbarAppsHost == null) return;
        taskbarAppsHost.removeAllViews();
        List<AppEntry> pins = repository.choosePinned(allApps, isWide() ? 5 : 3);
        for (AppEntry app : pins) {
            FrameLayout wrap = taskIconWrap();
            ImageView icon = new ImageView(getContext());
            icon.setImageDrawable(app.icon);
            icon.setPadding(Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
            wrap.addView(icon, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            wrap.setOnClickListener(v -> launchApp(app, ShizukuController.LaunchMode.FREEFORM));
            wrap.setOnLongClickListener(v -> { showAppMenu(v, app); return true; });
            taskbarAppsHost.addView(wrap);
        }
    }

    private FrameLayout taskIconWrap() {
        FrameLayout wrap = new FrameLayout(getContext());
        wrap.setBackground(Ui.solid(0x002d3645, Ui.dp(getContext(), 7)));
        LinearLayout.LayoutParams lp = taskItemLp();
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private TextView taskTextIcon(String text) {
        TextView t = Ui.text(getContext(), text, 21, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.solid(0x002d3645, Ui.dp(getContext(), 7)));
        return t;
    }

    private LinearLayout.LayoutParams taskItemLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(getContext(), 46), LayoutParams.MATCH_PARENT);
        lp.leftMargin = Ui.dp(getContext(), 2);
        lp.rightMargin = Ui.dp(getContext(), 2);
        return lp;
    }

    private void showAppMenu(View anchor, AppEntry app) {
        LinearLayout menu = new LinearLayout(getContext());
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
        menu.setBackground(Ui.stroke(0xf5222936, 0x35ffffff, 1, Ui.dp(getContext(), 10)));
        PopupWindow popup = new PopupWindow(menu, Ui.dp(getContext(), 190), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(Ui.dp(getContext(), 14));
        menu.addView(menuItem("Open in freeform", v -> { popup.dismiss(); launchApp(app, ShizukuController.LaunchMode.FREEFORM); }));
        menu.addView(menuItem("Open fullscreen", v -> { popup.dismiss(); launchApp(app, ShizukuController.LaunchMode.FULLSCREEN); }));
        menu.addView(menuItem("Close app", v -> { popup.dismiss(); shizuku.forceStop(app.packageName, this::toastCommand); }));
        popup.showAsDropDown(anchor, -Ui.dp(getContext(), 70), -Ui.dp(getContext(), 8));
    }

    private TextView menuItem(String text, OnClickListener click) {
        TextView item = Ui.text(getContext(), text, 13, Color.WHITE);
        item.setPadding(Ui.dp(getContext(), 12), 0, Ui.dp(getContext(), 12), 0);
        item.setOnClickListener(click);
        item.setBackground(Ui.solid(0x002f3948, Ui.dp(getContext(), 6)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 42));
        lp.bottomMargin = Ui.dp(getContext(), 2);
        item.setLayoutParams(lp);
        return item;
    }

    private void launchApp(AppEntry app, ShizukuController.LaunchMode mode) {
        if (shizuku.getState() != ShizukuController.State.READY) {
            handleShizukuClick();
            return;
        }
        RecentStore.push(getContext(), app.packageName);
        shizuku.launch(app.component, targetDisplayId, mode, output -> {
            if (output == null || !output.startsWith("EXIT=0")) toastCommand(output);
        });
        closePanels();
        renderStartContent();
    }

    private void handleShizukuClick() {
        ShizukuController.State state = shizuku.getState();
        if (state == ShizukuController.State.PERMISSION_REQUIRED) {
            shizuku.requestPermission();
        } else if (state == ShizukuController.State.PERMISSION_BLOCKED) {
            Toast.makeText(getContext(), "Buka Shizuku > Authorized applications > izinkan OpenDex.", Toast.LENGTH_LONG).show();
        } else if (state == ShizukuController.State.OFFLINE) {
            Toast.makeText(getContext(), "Jalankan Shizuku dulu lalu kembali ke OpenDex.", Toast.LENGTH_LONG).show();
        } else if (state == ShizukuController.State.READY) {
            Toast.makeText(getContext(), shizuku.getStateLabel(), Toast.LENGTH_SHORT).show();
        }
        refreshStatus();
    }

    private void toggleStart() {
        if (startPanel.getVisibility() == VISIBLE) closePanels(); else showStart(false);
    }

    private void showStart(boolean allAppsMode) {
        quickPanel.setVisibility(GONE);
        showingAllApps = allAppsMode;
        if (startSearch != null) startSearch.setText("");
        renderStartContent();
        startPanel.setVisibility(VISIBLE);
    }

    private void showStartWithKeyboard() {
        showStart(false);
        startSearch.requestFocus();
        main.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(startSearch, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    private void toggleQuickPanel() {
        if (quickPanel.getVisibility() == VISIBLE) closePanels();
        else { startPanel.setVisibility(GONE); quickPanel.setVisibility(VISIBLE); }
    }

    private void closePanels() {
        if (startPanel != null) startPanel.setVisibility(GONE);
        if (quickPanel != null) quickPanel.setVisibility(GONE);
        clearFocus();
    }

    private void openFiles() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Throwable t) { launchSystemSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); }
    }

    private void openTouchpad() {
        DisplayManager dm = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        boolean found = false;
        for (Display d : displays) if (d.getDisplayId() != Display.DEFAULT_DISPLAY) { found = true; break; }
        if (!found) {
            Toast.makeText(getContext(), "Sambungkan monitor dulu untuk memakai touchpad.", Toast.LENGTH_SHORT).show();
            return;
        }
        openAppActivity(TouchpadActivity.class);
    }

    private void openAppActivity(Class<?> cls) {
        try { getContext().startActivity(new Intent(getContext(), cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable t) { Toast.makeText(getContext(), t.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void launchSystemSettings(String action) {
        try { getContext().startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable t) { getContext().startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    }

    private void toastCommand(String output) {
        String value = output == null ? "unknown result" : output.replace('\n', ' ').trim();
        if (value.length() > 190) value = value.substring(0, 190) + "…";
        Toast.makeText(getContext(), value, Toast.LENGTH_LONG).show();
    }

    private void scheduleClock() {
        main.post(new Runnable() {
            @Override public void run() {
                try {
                    if (clockText != null) {
                        String fmt = isWide() ? "HH:mm\ndd/MM/yyyy" : "HH:mm\ndd/MM";
                        clockText.setText(new SimpleDateFormat(fmt, Locale.getDefault()).format(new Date()));
                    }
                    if (batteryText != null) {
                        BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
                        int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                        batteryText.setText(pct >= 0 ? pct + "%" : "▰");
                    }
                } catch (Throwable ignored) {
                    if (batteryText != null) batteryText.setText("▰");
                } finally {
                    main.postDelayed(this, 30_000);
                }
            }
        });
    }

    private LayoutParams desktopIconLayoutParams() {
        LayoutParams lp = new LayoutParams(Ui.dp(getContext(), 92), LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = Ui.dp(getContext(), 8);
        lp.topMargin = Ui.dp(getContext(), 8);
        lp.bottomMargin = Ui.dp(getContext(), 70);
        return lp;
    }

    private LayoutParams taskbarLayoutParams() {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 58), Gravity.BOTTOM);
        lp.leftMargin = Ui.dp(getContext(), isWide() ? 12 : 4);
        lp.rightMargin = Ui.dp(getContext(), isWide() ? 12 : 4);
        lp.bottomMargin = Ui.dp(getContext(), 4);
        return lp;
    }

    private LayoutParams floatingPanelLayoutParams(boolean start) {
        int screenDp = getResources().getConfiguration().screenWidthDp;
        int widthDp = Math.min(start ? 660 : 330, Math.max(300, screenDp - 20));
        int heightDp = start ? Math.min(650, Math.max(470, getResources().getConfiguration().screenHeightDp - 90)) : 300;
        int gravity = start ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.BOTTOM | Gravity.END;
        LayoutParams lp = new LayoutParams(Ui.dp(getContext(), widthDp), Ui.dp(getContext(), heightDp), gravity);
        lp.bottomMargin = Ui.dp(getContext(), 70);
        if (!start) lp.rightMargin = Ui.dp(getContext(), 12);
        return lp;
    }

    private boolean isWide() { return getResources().getConfiguration().screenWidthDp >= 720; }
}
