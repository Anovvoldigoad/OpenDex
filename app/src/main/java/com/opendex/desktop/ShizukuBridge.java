package com.opendex.desktop;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;

import com.opendex.desktop.shell.WindowShellService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public interface StateListener { void onStateChanged(); }
    public interface ResultCallback { void onResult(String result); }

    private static final int REQUEST_CODE = 6021;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final Shizuku.UserServiceArgs args;
    private volatile IWindowShellService remote;
    private volatile boolean binding;
    private StateListener stateListener;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            binding = false;
            remote = IWindowShellService.Stub.asInterface(service);
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

    public ShizukuBridge(Context context) {
        Context app = context.getApplicationContext();
        args = new Shizuku.UserServiceArgs(new ComponentName(app, WindowShellService.class))
                .processNameSuffix("desktop_shell")
                .tag("opendex_mi_engine_v1")
                .version(1)
                .daemon(false)
                .debuggable(BuildConfig.DEBUG);
    }

    public void start(StateListener listener) {
        stateListener = listener;
        try { Shizuku.addBinderReceivedListenerSticky(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.addBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.addRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        if (hasPermission()) main.postDelayed(this::bind, 150);
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
        try { return "Ready · shell UID " + remote.remoteUid(); }
        catch (Throwable t) { return "Ready"; }
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

    public void createDisplay(Surface surface, int width, int height, int dpi, String name, ResultCallback callback) {
        IWindowShellService service = remote;
        if (service == null) { deliver(callback, "ERROR|Shizuku shell belum siap"); return; }
        worker.execute(() -> {
            String result;
            try { result = service.createWindowDisplay(surface, width, height, dpi, name, Build.VERSION.SDK_INT); }
            catch (Throwable t) { result = "ERROR|" + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            deliver(callback, result);
        });
    }

    public void resizeDisplay(int displayId, int width, int height, int dpi, ResultCallback callback) {
        IWindowShellService service = remote;
        if (service == null) { deliver(callback, "ERROR|Shizuku shell belum siap"); return; }
        worker.execute(() -> {
            String result;
            try { result = service.resizeWindowDisplay(displayId, width, height, dpi); }
            catch (Throwable t) { result = "ERROR|" + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            deliver(callback, result);
        });
    }

    public void launchComponent(int displayId, String packageName, String componentName, ResultCallback callback) {
        IWindowShellService service = remote;
        if (service == null) { deliver(callback, "ERROR|Shizuku shell belum siap"); return; }
        worker.execute(() -> {
            String result;
            try { result = service.launchComponent(displayId, packageName, componentName); }
            catch (Throwable t) { result = "ERROR|" + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            deliver(callback, result);
        });
    }

    public void releaseDisplay(int displayId) {
        IWindowShellService service = remote;
        if (service == null || displayId < 0) return;
        worker.execute(() -> { try { service.releaseWindowDisplay(displayId); } catch (Throwable ignored) {} });
    }

    public boolean injectPointer(int displayId, int action, float x, float y, long downTime, long eventTime) {
        IWindowShellService service = remote;
        if (service == null) return false;
        try { return service.injectPointer(displayId, action, x, y, downTime, eventTime); }
        catch (Throwable t) { return false; }
    }

    public boolean injectKey(int displayId, int keyCode) {
        IWindowShellService service = remote;
        if (service == null) return false;
        try { return service.injectKey(displayId, keyCode); }
        catch (Throwable t) { return false; }
    }


    public void exec(String command, ResultCallback callback) {
        IWindowShellService service = remote;
        if (service == null) { deliver(callback, "EXIT=126\nShizuku shell belum siap"); return; }
        worker.execute(() -> {
            String result;
            try { result = service.exec(command); }
            catch (Throwable t) { result = "EXIT=125\n" + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            deliver(callback, result);
        });
    }

    public void tapFallback(int displayId, int x, int y) {
        exec("input touchscreen -d " + displayId + " tap " + x + " " + y, null);
    }

    public void swipeFallback(int displayId, int x1, int y1, int x2, int y2, int durationMs) {
        int duration = Math.max(80, Math.min(durationMs, 2000));
        exec("input touchscreen -d " + displayId + " swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + duration, null);
    }

    private void deliver(ResultCallback callback, String result) {
        if (callback != null) main.post(() -> callback.onResult(result));
    }
}
