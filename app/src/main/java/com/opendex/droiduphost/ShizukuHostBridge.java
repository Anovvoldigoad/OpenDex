package com.opendex.droiduphost;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.opendex.droiduphost.shell.HostShellService;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class ShizukuHostBridge {
    public interface StateListener { void onStateChanged(); }
    public interface ResultCallback { void onResult(String result); }
    public interface ProgressCallback { void onProgress(int percent); }

    private static final int REQUEST_CODE = 4401;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private final Context app;
    private volatile IHostShellService remote;
    private volatile boolean binding;
    private StateListener stateListener;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            binding = false;
            remote = IHostShellService.Stub.asInterface(service);
            notifyState();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            binding = false;
            remote = null;
            notifyState();
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = () -> {
        if (hasPermission()) bind();
        notifyState();
    };
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        binding = false;
        remote = null;
        notifyState();
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) bind();
        notifyState();
    };

    public ShizukuHostBridge(Context context) {
        app = context.getApplicationContext();
        args = new Shizuku.UserServiceArgs(new ComponentName(app, HostShellService.class))
                .processNameSuffix("droidup_shell")
                .tag("opendex_droidup_shell_v1")
                .version(1)
                .daemon(false)
                .debuggable(BuildConfig.DEBUG);
    }

    public void start(StateListener listener) {
        stateListener = listener;
        try { Shizuku.addBinderReceivedListenerSticky(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.addBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.addRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        if (hasPermission()) main.postDelayed(this::bind, 250);
        notifyState();
    }

    public void stop() {
        try { Shizuku.unbindUserService(args, connection, false); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderReceivedListener(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        stateListener = null;
    }

    public boolean binderAlive() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public boolean hasPermission() {
        if (!binderAlive()) return false;
        try {
            return !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isReady() { return remote != null; }

    public String status() {
        if (!binderAlive()) return "Shizuku belum aktif";
        try { if (Shizuku.isPreV11()) return "Shizuku terlalu lama (butuh v11+)"; }
        catch (Throwable t) { return "Shizuku error"; }
        if (!hasPermission()) return "Izin Shizuku belum diberikan";
        if (remote == null) return binding ? "Menghubungkan shell…" : "Shell belum terhubung";
        try { return "Shizuku ready · UID " + remote.remoteUid(); }
        catch (Throwable t) { return "Shizuku ready"; }
    }

    public void requestPermission() {
        try {
            if (!binderAlive() || Shizuku.isPreV11()) { notifyState(); return; }
            if (hasPermission()) { bind(); return; }
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable ignored) {
            notifyState();
        }
    }

    private void bind() {
        if (!hasPermission() || remote != null || binding) return;
        binding = true;
        notifyState();
        try { Shizuku.bindUserService(args, connection); }
        catch (Throwable t) { binding = false; notifyState(); }
    }

    private void notifyState() {
        StateListener l = stateListener;
        if (l != null) main.post(() -> {
            try { l.onStateChanged(); } catch (Throwable ignored) {}
        });
    }

    public void exec(String command, ResultCallback cb) {
        IHostShellService service = remote;
        if (service == null) {
            if (cb != null) main.post(() -> cb.onResult("EXIT=126\nShizuku shell belum siap"));
            return;
        }
        worker.execute(() -> {
            String result;
            try { result = service.exec(command); }
            catch (Throwable t) { result = "EXIT=125\n" + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            final String out = result;
            if (cb != null) main.post(() -> cb.onResult(out));
        });
    }

    public void enableDroidUpSettings(ResultCallback cb) {
        exec("settings put global enable_freeform_support 1; "
                + "settings put global force_resizable_activities 1; "
                + "settings put global force_desktop_mode_on_external_displays 0; "
                + "printf 'freeform='; settings get global enable_freeform_support; "
                + "printf '\\nresizable='; settings get global force_resizable_activities", cb);
    }

    public void installBundledLauncher(ProgressCallback progress, ResultCallback cb) {
        IHostShellService service = remote;
        if (service == null) {
            if (cb != null) main.post(() -> cb.onResult("EXIT=126\nShizuku shell belum siap"));
            return;
        }
        worker.execute(() -> {
            try (InputStream in = app.getAssets().open("DroidUP-Dex-Launcher.apk")) {
                int total = in.available();
                if (!service.beginPayload()) throw new IllegalStateException("Tidak bisa membuat payload di /data/local/tmp");
                byte[] buf = new byte[128 * 1024];
                int n;
                int sent = 0;
                int lastPercent = -1;
                while ((n = in.read(buf)) != -1) {
                    byte[] chunk;
                    if (n == buf.length) {
                        chunk = buf.clone();
                    } else {
                        chunk = new byte[n];
                        System.arraycopy(buf, 0, chunk, 0, n);
                    }
                    if (!service.appendPayload(chunk)) throw new IllegalStateException("Transfer payload gagal");
                    sent += n;
                    final int pct = total > 0 ? Math.min(99, (int) ((sent * 100L) / total)) : 50;
                    if (pct != lastPercent) {
                        lastPercent = pct;
                        if (progress != null) main.post(() -> progress.onProgress(pct));
                    }
                }
                String result = service.installPayload();
                if (progress != null) main.post(() -> progress.onProgress(100));
                if (cb != null) main.post(() -> cb.onResult(result));
            } catch (Throwable t) {
                if (cb != null) main.post(() -> cb.onResult("EXIT=125\n" + t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        });
    }

    public void tap(int displayId, int x, int y) {
        exec("input touchscreen -d " + displayId + " tap " + x + " " + y, null);
    }

    public void swipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) {
        int dur = Math.max(80, Math.min(durationMs, 2000));
        exec("input touchscreen -d " + displayId + " swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + dur, null);
    }

    public void key(int displayId, int keyCode) {
        exec("input -d " + displayId + " keyevent " + keyCode, null);
    }

    public void startOriginalLauncher(int displayId, ResultCallback cb) {
        exec("am start --user current --display " + displayId
                + " -n com.levelup.droiduplauncher/.MainActivity", cb);
    }
}
