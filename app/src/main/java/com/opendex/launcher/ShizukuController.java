package com.opendex.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.opendex.launcher.shell.UserShellService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class ShizukuController {
    public interface StateListener { void onStateChanged(State state); }
    public interface CommandCallback { void onComplete(String output); }

    public enum State {
        OFFLINE, UNSUPPORTED, PERMISSION_REQUIRED, PERMISSION_BLOCKED, BINDING, READY, ERROR
    }

    public enum LaunchMode { FREEFORM, FULLSCREEN }

    private static final int REQUEST_CODE = 4107;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs serviceArgs;
    private volatile IUserShellService remote;
    private volatile boolean binding;
    private StateListener listener;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            binding = false;
            remote = IUserShellService.Stub.asInterface(service);
            dispatchState();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            binding = false;
            remote = null;
            dispatchState();
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = () -> {
        if (hasPermission()) bindUserService();
        dispatchState();
    };
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        binding = false;
        remote = null;
        dispatchState();
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) bindUserService();
        dispatchState();
    };

    public ShizukuController(Context context) {
        Context appContext = context.getApplicationContext();
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(appContext, UserShellService.class))
                .processNameSuffix("opendex_shell")
                .tag("opendex_shell_v3")
                .version(3)
                .daemon(false)
                .debuggable(BuildConfig.DEBUG);
    }

    public void start(StateListener stateListener) {
        listener = stateListener;
        try { Shizuku.addBinderReceivedListenerSticky(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.addBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.addRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        // Do not bind synchronously during Activity construction. Some OEMs have
        // fragile Shizuku UserService startup paths; let HOME become visible first.
        if (hasPermission()) main.postDelayed(this::bindUserService, 450);
        dispatchState();
    }

    public void release() {
        try { Shizuku.unbindUserService(serviceArgs, connection, false); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderReceivedListener(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        listener = null;
    }

    public boolean binderAlive() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public boolean hasPermission() {
        if (!binderAlive()) return false;
        try {
            return !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    public boolean permissionBlocked() {
        if (!binderAlive()) return false;
        try {
            return !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                    && Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable t) { return false; }
    }

    public void requestPermission() {
        try {
            if (!binderAlive() || Shizuku.isPreV11()) { dispatchState(); return; }
            if (hasPermission()) { bindUserService(); return; }
            if (Shizuku.shouldShowRequestPermissionRationale()) { dispatchState(); return; }
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable t) { dispatchState(); }
    }

    public State getState() {
        if (!binderAlive()) return State.OFFLINE;
        try { if (Shizuku.isPreV11()) return State.UNSUPPORTED; } catch (Throwable t) { return State.ERROR; }
        if (permissionBlocked()) return State.PERMISSION_BLOCKED;
        if (!hasPermission()) return State.PERMISSION_REQUIRED;
        if (remote != null) return State.READY;
        return State.BINDING;
    }

    public String getStateLabel() {
        switch (getState()) {
            case OFFLINE: return "Shizuku offline";
            case UNSUPPORTED: return "Shizuku terlalu lama";
            case PERMISSION_REQUIRED: return "Shizuku belum diizinkan";
            case PERMISSION_BLOCKED: return "Izin Shizuku diblokir";
            case BINDING: return "Menghubungkan Shizuku…";
            case READY:
                try { return "Shizuku ready · UID " + (remote == null ? "?" : remote.remoteUid()); }
                catch (RemoteException e) { return "Shizuku ready"; }
            default: return "Shizuku error";
        }
    }

    private void bindUserService() {
        if (!hasPermission() || remote != null || binding) return;
        binding = true;
        dispatchState();
        try { Shizuku.bindUserService(serviceArgs, connection); }
        catch (Throwable t) { binding = false; dispatchState(); }
    }

    private void dispatchState() {
        StateListener l = listener;
        if (l != null) main.post(() -> {
            try { l.onStateChanged(getState()); } catch (Throwable ignored) {}
        });
    }

    public void execute(String command, CommandCallback callback) {
        IUserShellService service = remote;
        if (service == null) {
            if (callback != null) main.post(() -> callback.onComplete("EXIT=126\nShizuku shell belum siap"));
            return;
        }
        executor.execute(() -> {
            String result;
            try { result = service.exec(command); }
            catch (Throwable t) { result = "EXIT=125\n" + t.getClass().getSimpleName() + ": " + t.getMessage(); }
            String finalResult = result;
            if (callback != null) main.post(() -> callback.onComplete(finalResult));
        });
    }

    public void enableFreeform(CommandCallback callback) {
        execute("settings put global enable_freeform_support 1; " +
                "settings put global force_resizable_activities 1; " +
                "printf 'enable_freeform_support='; settings get global enable_freeform_support; " +
                "printf 'force_resizable_activities='; settings get global force_resizable_activities", callback);
    }

    public void launch(ComponentName component, int displayId, LaunchMode mode, CommandCallback callback) {
        StringBuilder command = new StringBuilder("am start --user current");
        if (displayId >= 0) command.append(" --display ").append(displayId);
        command.append(" --windowingMode ").append(mode == LaunchMode.FREEFORM ? 5 : 1);
        command.append(" -n ").append(shellQuote(component.flattenToShortString()));
        execute(command.toString(), callback);
    }

    public void forceStop(String packageName, CommandCallback callback) {
        execute("am force-stop --user current " + shellQuote(packageName), callback);
    }

    public void key(int displayId, String keyCode, CommandCallback callback) {
        String display = displayId >= 0 ? " -d " + displayId : "";
        execute("input" + display + " keyevent " + shellQuote(keyCode), callback);
    }

    public void pointerMove(int displayId, int x, int y) {
        if (displayId < 0) return;
        execute("input mouse -d " + displayId + " motionevent MOVE " + x + " " + y, null);
    }

    public void pointerClick(int displayId, int x, int y) {
        if (displayId < 0) return;
        execute("input mouse -d " + displayId + " tap " + x + " " + y, null);
    }

    public void pointerDrag(int displayId, int x1, int y1, int x2, int y2, int durationMs) {
        if (displayId < 0) return;
        execute("input mouse -d " + displayId + " swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + durationMs, null);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
