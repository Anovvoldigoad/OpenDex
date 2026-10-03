package com.opendex.i2405probe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_SHIZUKU = 4201;
    private TextView status;
    private TextView logView;
    private Button grantButton;
    private Button startButton;
    private Button refreshButton;
    private IProbeService service;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> runOnUiThread(this::refreshShizukuState);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IProbeService.Stub.asInterface(binder);
            runOnUiThread(() -> {
                status.setText("Shizuku shell service: CONNECTED (UID 2000 expected)");
                startButton.setEnabled(true);
                refreshButton.setEnabled(true);
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            runOnUiThread(() -> {
                status.setText("Shizuku shell service: DISCONNECTED");
                startButton.setEnabled(false);
                refreshButton.setEnabled(false);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        Shizuku.addRequestPermissionResultListener(permissionListener);
        refreshShizukuState();
    }

    private void buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(16, 17, 20));

        TextView title = new TextView(this);
        title.setText("OpenDex I2405 · WCT Probe v0.1");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Tes khusus iQOO I2405 / Android 16. Probe ini tidak memakai scrcpy dan tidak butuh root. Ia menjalankan worker sebagai Shizuku shell, menunggu task Dextop di display non-zero, lalu mencoba WindowContainerTransaction (WCT) native FREEFORM + bounds dan memverifikasi hasilnya.");
        desc.setTextColor(Color.LTGRAY);
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, dp(14));
        root.addView(desc);

        status = new TextView(this);
        status.setTextColor(Color.rgb(128, 203, 196));
        status.setTextSize(14);
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status);

        grantButton = button("1 · Grant Shizuku");
        grantButton.setOnClickListener(v -> requestShizuku());
        root.addView(grantButton);

        startButton = button("2 · START PROBE + OPEN DEXTOP");
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> startProbeAndDextop());
        root.addView(startButton);

        refreshButton = button("3 · REFRESH RESULT");
        refreshButton.setEnabled(false);
        refreshButton.setOnClickListener(v -> refreshLog());
        root.addView(refreshButton);

        TextView hint = new TextView(this);
        hint.setText("Sesudah tombol 2: di Dextop pilih 1920×1080 / 240 dpi → Start → buka Chrome → buka YouTube → tunggu ±10 detik → Stop Dextop → balik ke app ini → Refresh Result. Log juga disimpan ke /sdcard/Download/OpenDex_I2405_WCT_Probe.txt");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(13);
        hint.setPadding(0, dp(10), 0, dp(10));
        root.addView(hint);

        logView = new TextView(this);
        logView.setTextColor(Color.WHITE);
        logView.setTextSize(11);
        logView.setTextIsSelectable(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setText("Result belum ada.");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, sp);

        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku belum running", Toast.LENGTH_LONG).show();
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindProbeService();
        } else {
            Shizuku.requestPermission(REQ_SHIZUKU);
        }
    }

    private void refreshShizukuState() {
        if (!Shizuku.pingBinder()) {
            status.setText("Shizuku: NOT RUNNING");
            grantButton.setEnabled(true);
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            status.setText("Shizuku: permission OK · binding shell service…");
            grantButton.setEnabled(false);
            bindProbeService();
        } else {
            status.setText("Shizuku: running · permission belum diberikan");
            grantButton.setEnabled(true);
        }
    }

    private void bindProbeService() {
        if (service != null) return;
        try {
            ComponentName component = new ComponentName(getPackageName(), ProbeUserService.class.getName());
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(component)
                    .daemon(false)
                    .processNameSuffix("wct_probe")
                    .debuggable(true)
                    .version(1);
            Shizuku.bindUserService(args, connection);
        } catch (Throwable t) {
            status.setText("Bind gagal: " + t);
        }
    }

    private void startProbeAndDextop() {
        if (service == null) return;
        try {
            String reply = service.startProbe();
            logView.setText(reply);

            Intent launch = getPackageManager().getLaunchIntentForPackage("moe.n4tsu.dextop");
            if (launch == null) {
                Toast.makeText(this, "Dextop (moe.n4tsu.dextop) tidak ditemukan", Toast.LENGTH_LONG).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Throwable t) {
            logView.setText("startProbe error: " + t);
        }
    }

    private void refreshLog() {
        if (service == null) return;
        try {
            logView.setText(service.getLog());
        } catch (Throwable t) {
            logView.setText("getLog error: " + t);
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }
}
