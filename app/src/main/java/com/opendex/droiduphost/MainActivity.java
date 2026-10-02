package com.opendex.droiduphost;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String DROIDUP_PACKAGE = "com.levelup.droiduplauncher";

    private ShizukuHostBridge bridge;
    private TextView shizukuStatus;
    private TextView launcherStatus;
    private TextView resolutionInfo;
    private TextView detail;
    private ProgressBar progress;
    private Button permissionButton;
    private Button installButton;
    private Button startButton;
    private Spinner resolutionSpinner;
    private EditText widthInput;
    private EditText heightInput;
    private EditText dpiInput;

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
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(18, 18, 22));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text("DroidUP Dex · Android Host", 25, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWrap());

        TextView note = text("UI DroidUP asli tetap dipakai. Host menangani trusted display, freeform, input, dan resolusi desktop.", 14, Color.rgb(190,190,198));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = fullWrap();
        noteLp.topMargin = dp(8);
        root.addView(note, noteLp);

        shizukuStatus = text("", 16, Color.WHITE);
        LinearLayout.LayoutParams statusLp = fullWrap();
        statusLp.topMargin = dp(20);
        root.addView(shizukuStatus, statusLp);

        launcherStatus = text("", 16, Color.WHITE);
        LinearLayout.LayoutParams lsLp = fullWrap();
        lsLp.topMargin = dp(6);
        root.addView(launcherStatus, lsLp);

        TextView resTitle = text("Desktop resolution", 15, Color.WHITE);
        LinearLayout.LayoutParams rtLp = fullWrap();
        rtLp.topMargin = dp(18);
        root.addView(resTitle, rtLp);

        resolutionSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, DesktopConfig.modes());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(adapter);
        resolutionSpinner.setSelection(indexOfMode(DesktopConfig.savedMode(this)));
        root.addView(resolutionSpinner, fullWrap());

        resolutionInfo = text("", 12, Color.rgb(170,170,180));
        LinearLayout.LayoutParams riLp = fullWrap();
        riLp.topMargin = dp(6);
        root.addView(resolutionInfo, riLp);

        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        widthInput = numericInput(Integer.toString(DesktopConfig.savedW(this)), "Width");
        heightInput = numericInput(Integer.toString(DesktopConfig.savedH(this)), "Height");
        dpiInput = numericInput(Integer.toString(DesktopConfig.savedDpi(this)), "DPI");
        customRow.addView(widthInput, weighted());
        customRow.addView(heightInput, weightedWithMargin());
        customRow.addView(dpiInput, weightedWithMargin());
        LinearLayout.LayoutParams crLp = fullWrap();
        crLp.topMargin = dp(8);
        root.addView(customRow, crLp);

        TextView customHelp = text("Custom dipakai kalau mode = Custom. Auto membaca resolusi layar HP saat ini; DPI otomatis menjaga skala desktop tetap nyaman.", 11, Color.rgb(145,145,155));
        LinearLayout.LayoutParams chLp = fullWrap();
        chLp.topMargin = dp(4);
        root.addView(customHelp, chLp);

        resolutionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateResolutionPreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

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

        updateResolutionPreview();
        return root;
    }

    private void updateResolutionPreview() {
        if (resolutionSpinner == null || resolutionInfo == null) return;
        String mode = String.valueOf(resolutionSpinner.getSelectedItem());
        DesktopConfig cfg = DesktopConfig.fromSelection(this, mode,
                parseInt(widthInput, DesktopConfig.savedW(this)),
                parseInt(heightInput, DesktopConfig.savedH(this)),
                parseInt(dpiInput, DesktopConfig.savedDpi(this)));
        int[] current = DesktopConfig.detectCurrentPixels(this);
        int[] physical = DesktopConfig.detectPhysicalMode(this);
        resolutionInfo.setText("Auto detect: " + current[0] + "×" + current[1]
                + " · panel mode: " + physical[0] + "×" + physical[1]
                + "\nSelected: " + cfg.width + "×" + cfg.height + " @ " + cfg.dpi + " dpi");
    }

    private DesktopConfig selectedConfig() {
        String mode = String.valueOf(resolutionSpinner.getSelectedItem());
        DesktopConfig cfg = DesktopConfig.fromSelection(this, mode,
                parseInt(widthInput, 1920), parseInt(heightInput, 1080), parseInt(dpiInput, 240));
        DesktopConfig.save(this, cfg);
        // Keep fields synchronized with what will actually be used.
        widthInput.setText(Integer.toString(cfg.width));
        heightInput.setText(Integer.toString(cfg.height));
        dpiInput.setText(Integer.toString(cfg.dpi));
        return cfg;
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
        DesktopConfig cfg = selectedConfig();
        bridge.enableDroidUpSettings(result -> {
            detail.setText(result + "\nDesktop=" + cfg.width + "×" + cfg.height + " @ " + cfg.dpi + "dpi");
            Intent i = new Intent(this, DesktopActivity.class);
            i.putExtra(DesktopActivity.EXTRA_WIDTH, cfg.width);
            i.putExtra(DesktopActivity.EXTRA_HEIGHT, cfg.height);
            i.putExtra(DesktopActivity.EXTRA_DPI, cfg.dpi);
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
        updateResolutionPreview();
    }

    @Override protected void onDestroy() {
        if (bridge != null) bridge.stop();
        super.onDestroy();
    }

    private int indexOfMode(String mode) {
        String[] modes = DesktopConfig.modes();
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(mode)) return i;
        return 0;
    }

    private int parseInt(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Throwable ignored) { return fallback; }
    }

    private EditText numericInput(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(120,120,130));
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextSize(13);
        return e;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(48), 1f);
    }

    private LinearLayout.LayoutParams weightedWithMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.leftMargin = dp(8);
        return lp;
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
        lp.topMargin = dp(12);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
