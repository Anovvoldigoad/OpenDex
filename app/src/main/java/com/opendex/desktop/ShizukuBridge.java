package com.opendex.desktop;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import com.opendex.desktop.shell.ScrcpyShellService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public interface StateListener { void onStateChanged(); }
    public interface ResultCallback { void onResult(String result); }

    public static final String SCRCPY_SHA256 = "deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae";
    private static final int REQUEST_CODE = 7021;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final Shizuku.UserServiceArgs args;
    private volatile IScrcpyShellService remote;
    private volatile boolean binding;
    private volatile boolean serverInstalled;
    private StateListener stateListener;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            binding = false;
            remote = IScrcpyShellService.Stub.asInterface(service);
            notifyState();
            worker.execute(() -> {
                try {
                    if (ensureServerInstalledBlocking()) remote.prepareEnvironment();
                } catch (Throwable ignored) {}
                notifyState();
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            binding = false;
            remote = null;
            serverInstalled = false;
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
        serverInstalled = false;
        notifyState();
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) bind();
        notifyState();
    };

    public ShizukuBridge(Context context) {
        app = context.getApplicationContext();
        args = new Shizuku.UserServiceArgs(new ComponentName(app, ScrcpyShellService.class))
                .processNameSuffix("scrcpy_shell")
                .tag("opendex_scrcpy_engine_v1")
                .version(1)
                .daemon(false)
                .debuggable(BuildConfig.DEBUG);
    }

    public void start(StateListener listener) {
        stateListener = listener;
        try { Shizuku.addBinderReceivedListenerSticky(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.addBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.addRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        if (hasPermission()) main.postDelayed(this::bind, 120);
        notifyState();
    }

    public void stop() {
        try { Shizuku.unbindUserService(args, connection, false); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderReceivedListener(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        worker.shutdownNow();
        stateListener = null;
    }

    public boolean binderAlive() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public boolean hasPermission() {
        if (!binderAlive()) return false;
        try { return !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }

    public boolean isReady() { return remote != null && serverInstalled; }

    public String status() {
        if (!binderAlive()) return "Shizuku belum aktif";
        try { if (Shizuku.isPreV11()) return "Shizuku terlalu lama (butuh v11+)"; }
        catch (Throwable t) { return "Shizuku error"; }
        if (!hasPermission()) return "Izin Shizuku belum diberikan";
        IScrcpyShellService r = remote;
        if (r == null) return binding ? "Menghubungkan shell…" : "Shell belum terhubung";
        try {
            String base = "shell UID " + r.remoteUid();
            return serverInstalled ? "Ready · scrcpy 4.1 · " + base : "Menyiapkan scrcpy-server · " + base;
        } catch (Throwable t) { return "Shell ready"; }
    }

    public void requestPermission() {
        try {
            if (!binderAlive() || Shizuku.isPreV11()) { notifyState(); return; }
            if (hasPermission()) { bind(); return; }
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable ignored) { notifyState(); }
    }

    private void bind() {
        if (!hasPermission() || remote != null || binding) return;
        binding = true;
        notifyState();
        try { Shizuku.bindUserService(args, connection); }
        catch (Throwable t) { binding = false; notifyState(); }
    }

    private void notifyState() {
        StateListener listener = stateListener;
        if (listener != null) main.post(() -> {
            try { listener.onStateChanged(); } catch (Throwable ignored) {}
        });
    }

    public boolean ensureServerInstalledBlocking() {
        if (serverInstalled) return true;
        IScrcpyShellService r = remote;
        if (r == null) return false;
        try {
            byte[] server = readAsset("scrcpy-server-v4.1");
            serverInstalled = r.installScrcpyServer(server, SCRCPY_SHA256);
            return serverInstalled;
        } catch (Throwable t) {
            serverInstalled = false;
            return false;
        }
    }

    public String startScrcpyServerBlocking(int scid, int width, int height, int dpi, int bitRate, int maxFps) throws Exception {
        IScrcpyShellService r = remote;
        if (r == null) throw new IllegalStateException("Shizuku shell belum siap");
        if (!ensureServerInstalledBlocking()) throw new IllegalStateException("scrcpy-server asset gagal dipasang");
        return r.startScrcpyServer(scid, width, height, dpi, bitRate, maxFps);
    }

    public ParcelFileDescriptor connectScrcpyBlocking(int scid) throws Exception {
        IScrcpyShellService r = remote;
        if (r == null) throw new IllegalStateException("Shizuku shell belum siap");
        return r.connectScrcpy(scid);
    }

    public String sessionInfoBlocking(int scid) {
        IScrcpyShellService r = remote;
        if (r == null) return "shell unavailable";
        try { return r.sessionInfo(scid); } catch (Throwable t) { return t.getClass().getSimpleName() + ": " + t.getMessage(); }
    }

    public String prepareSessionDisplayBlocking(int scid) {
        IScrcpyShellService r = remote;
        if (r == null) return "shell unavailable";
        try { return r.prepareSessionDisplay(scid); }
        catch (Throwable t) { return t.getClass().getSimpleName() + ": " + t.getMessage(); }
    }

    public void stopScrcpyServer(int scid) {
        IScrcpyShellService r = remote;
        if (r == null) return;
        worker.execute(() -> { try { r.stopScrcpyServer(scid); } catch (Throwable ignored) {} });
    }

    public void exec(String command, ResultCallback callback) {
        IScrcpyShellService r = remote;
        if (r == null) { deliver(callback, "EXIT=126\nShizuku shell belum siap"); return; }
        worker.execute(() -> {
            String result;
            try { result = r.exec(command); }
            catch (Throwable t) { result = "EXIT=125\n" + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            deliver(callback, result);
        });
    }

    private byte[] readAsset(String name) throws Exception {
        try (InputStream in = app.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private void deliver(ResultCallback callback, String result) {
        if (callback != null) main.post(() -> callback.onResult(result));
    }
}
