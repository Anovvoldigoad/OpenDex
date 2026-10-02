package com.opendex.droiduphost.shell;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.graphics.Rect;
import android.system.Os;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Keep;

import com.opendex.droiduphost.IHostShellService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shizuku UserService running as shell/root.
 *
 * IMPORTANT: the virtual display is created here, not by the normal app process.
 * On modern Android an untrusted app-owned virtual display cannot host arbitrary
 * third-party activities. Creating the display as shell and requesting TRUSTED
 * mirrors scrcpy's new-display architecture closely enough for DroidUP to launch
 * other apps without changing DroidUP's UI or DEX.
 */
public final class HostShellService extends IHostShellService.Stub {
    private static final File PAYLOAD = new File("/data/local/tmp/DroidUP-Dex-Launcher.apk");

    // DisplayManager virtual display flag values copied from AOSP/scrcpy.
    private static final int VD_PUBLIC = 1 << 0;
    private static final int VD_PRESENTATION = 1 << 1;
    private static final int VD_OWN_CONTENT_ONLY = 1 << 3;
    private static final int VD_SUPPORTS_TOUCH = 1 << 6;
    private static final int VD_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int VD_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VD_TRUSTED = 1 << 10;
    private static final int VD_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VD_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VD_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VD_OWN_FOCUS = 1 << 14;
    private static final int VD_DEVICE_DISPLAY_GROUP = 1 << 15;

    private final Context context;
    private FileOutputStream payloadOut;
    private VirtualDisplay desktopDisplay;

    private InputManager inputManager;
    private Method injectInputEventMethod;
    private Method setDisplayIdMethod;

    public HostShellService() {
        this.context = null;
    }

    @Keep
    public HostShellService(Context context) {
        this.context = context;
    }

    @Override
    public synchronized String exec(String command) {
        if (command == null || command.trim().isEmpty()) return "EXIT=2\nempty command";
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "EXIT=124\ntimeout";
            }
            String output = readAll(process.getInputStream());
            return "EXIT=" + process.exitValue() + "\n" + output;
        } catch (Throwable t) {
            return "EXIT=127\n" + t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override public int remoteUid() { return Os.getuid(); }

    @Override
    public synchronized boolean beginPayload() {
        closePayload();
        try {
            if (PAYLOAD.exists() && !PAYLOAD.delete()) return false;
            payloadOut = new FileOutputStream(PAYLOAD, false);
            return true;
        } catch (Throwable t) {
            closePayload();
            return false;
        }
    }

    @Override
    public synchronized boolean appendPayload(byte[] data) {
        if (payloadOut == null || data == null) return false;
        try {
            payloadOut.write(data);
            return true;
        } catch (Throwable t) {
            closePayload();
            return false;
        }
    }

    @Override
    public synchronized String installPayload() {
        closePayload();
        if (!PAYLOAD.isFile() || PAYLOAD.length() < 1024) return "EXIT=2\npayload missing";
        return exec("chmod 0644 " + PAYLOAD.getAbsolutePath()
                + "; pm install -r -d " + PAYLOAD.getAbsolutePath()
                + "; rc=$?; rm -f " + PAYLOAD.getAbsolutePath() + "; exit $rc");
    }

    /** Create a trusted scrcpy-style virtual display while running as shell. */
    @Override
    public synchronized String createDesktopDisplay(Surface surface, int width, int height, int dpi, int sdkInt) {
        releaseDesktopDisplay();
        if (surface == null || !surface.isValid()) return "ERROR|invalid Surface";
        if (context == null) return "ERROR|Shizuku UserService Context unavailable; Shizuku v13+ required";

        final int baseFlags = VD_PUBLIC | VD_PRESENTATION | VD_OWN_CONTENT_ONLY
                | VD_SUPPORTS_TOUCH | VD_ROTATES_WITH_CONTENT | VD_DESTROY_CONTENT_ON_REMOVAL;
        int flags = baseFlags;
        if (sdkInt >= 33) {
            flags |= VD_TRUSTED | VD_OWN_DISPLAY_GROUP | VD_ALWAYS_UNLOCKED | VD_TOUCH_FEEDBACK_DISABLED;
            if (sdkInt >= 34) flags |= VD_OWN_FOCUS | VD_DEVICE_DISPLAY_GROUP;
        }

        // Keep TRUSTED in every fallback. An untrusted display would reproduce v0.4's launch failure.
        int[] attempts;
        if (sdkInt >= 34) {
            attempts = new int[] {
                    flags,
                    flags & ~VD_DEVICE_DISPLAY_GROUP,
                    flags & ~(VD_DEVICE_DISPLAY_GROUP | VD_OWN_FOCUS),
                    baseFlags | VD_TRUSTED | VD_ALWAYS_UNLOCKED,
                    baseFlags | VD_TRUSTED
            };
        } else if (sdkInt >= 33) {
            attempts = new int[] { flags, baseFlags | VD_TRUSTED | VD_ALWAYS_UNLOCKED, baseFlags | VD_TRUSTED };
        } else {
            // TRUSTED flag only exists on newer Android; older policy is less restrictive.
            attempts = new int[] { baseFlags };
        }

        Throwable last = null;
        for (int candidate : attempts) {
            try {
                DisplayManager dm = obtainDisplayManager();
                desktopDisplay = dm.createVirtualDisplay(
                        "DroidUP Dex Trusted",
                        width,
                        height,
                        dpi,
                        surface,
                        candidate
                );
                if (desktopDisplay == null || desktopDisplay.getDisplay() == null) {
                    throw new IllegalStateException("DisplayManager returned null");
                }
                int id = desktopDisplay.getDisplay().getDisplayId();

                // Keep the display policy inherited/undefined (0). DroidUP remains the desktop background;
                // child app tasks still use the original DroidUP launch bounds.
                String displayMode = configureDesktopWindowing(id);

                return "OK|" + id + "|flags=0x" + Integer.toHexString(candidate)
                        + "|uid=" + Os.getuid() + "|" + displayMode.replace('\n', ' ');
            } catch (Throwable t) {
                last = t;
                if (desktopDisplay != null) {
                    try { desktopDisplay.release(); } catch (Throwable ignored) {}
                    desktopDisplay = null;
                }
            }
        }
        return "ERROR|trusted display creation failed|"
                + (last == null ? "unknown" : last.getClass().getSimpleName() + ": " + last.getMessage());
    }


    /**
     * Set the TaskDisplayArea to WINDOWING_MODE_UNDEFINED (0). This intentionally stops forcing
     * fullscreen/freeform at the display level and lets the parent display policy resolve the final
     * mode. DroidUP child apps still provide ActivityOptions.setLaunchBounds().
     */
    private String configureDesktopWindowing(int displayId) {
        String id = Integer.toString(displayId);

        // 0 == WINDOWING_MODE_UNDEFINED. Do not force mode 1 or 5 at the display level.
        String setMode = exec("wm set-display-windowing-mode -d " + id + " 0");
        String getMode = exec("wm get-display-windowing-mode -d " + id);

        // Desktop canvas stays landscape even if a phone-oriented app requests portrait.
        exec("wm set-ignore-orientation-request -d " + id + " true");
        exec("wm user-rotation -d " + id + " lock 0");

        boolean setOk = setMode.startsWith("EXIT=0");
        boolean getOk = getMode.startsWith("EXIT=0");
        String probe = (getOk ? getMode : setMode)
                .replace("EXIT=0", "")
                .replace("EXIT=255", "")
                .replace("EXIT=1", "")
                .trim();
        if (probe.length() > 160) probe = probe.substring(0, 160);
        return "displayMode=" + (setOk ? "UNDEFINED0" : "FAILED")
                + (probe.isEmpty() ? "" : ":" + probe);
    }

    /**
     * Set the concrete task that belongs to {@code packageName} on {@code displayId}
     * to WINDOWING_MODE_UNDEFINED (0) and clear remembered freeform bounds.
     *
     * Launch-time ActivityOptions are only a hint on some OEM/desktop shells. The shell may
     * still create the task as freeform and attach a caption bar. This method acts after the
     * task exists, which is the same level Android's desktop controller changes when moving a
     * desktop task windowing mode.
     */
    @Override
    public synchronized String forcePackageTaskWindowingUndefined(String packageName, int displayId) {
        if (context == null) return "ERROR|context unavailable";
        if (packageName == null || packageName.trim().isEmpty()) return "ERROR|empty package";

        int taskId = findTaskId(packageName.trim(), displayId);
        if (taskId < 0) return "ERROR|task not found|pkg=" + packageName + "|display=" + displayId;

        StringBuilder diag = new StringBuilder();
        diag.append("task=").append(taskId).append("|display=").append(displayId);

        Throwable firstError = null;
        boolean modeOk = false;
        boolean boundsOk = false;

        // Strategy A: ActivityTaskManager manager instance from SystemServiceRegistry.
        try {
            Object atm = context.getSystemService("activity_task");
            if (atm != null) {
                Method setMode = findMethod(atm.getClass(), "setTaskWindowingMode",
                        int.class, int.class, boolean.class);
                if (setMode != null) {
                    setMode.setAccessible(true);
                    Object result = setMode.invoke(atm, taskId, 0 /* UNDEFINED */, true);
                    modeOk = !(result instanceof Boolean) || ((Boolean) result);
                    diag.append("|managerMode=").append(modeOk);
                }
                Method resize = findMethod(atm.getClass(), "resizeTask",
                        int.class, Rect.class, int.class);
                if (resize != null) {
                    resize.setAccessible(true);
                    Object result = resize.invoke(atm, taskId, null, 0 /* RESIZE_MODE_SYSTEM */);
                    boundsOk = !(result instanceof Boolean) || ((Boolean) result);
                    diag.append("|managerBounds=").append(boundsOk);
                }
            }
        } catch (Throwable t) {
            firstError = unwrap(t);
            diag.append("|managerErr=").append(shortError(firstError));
        }

        // Strategy B: hidden ActivityTaskManager binder service. This is tried independently
        // because OEMs differ in which hidden surface remains callable from the shell UID.
        if (!modeOk || !boundsOk) {
            try {
                Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
                Method getService = findMethod(atmClass, "getService");
                if (getService == null) throw new NoSuchMethodException("ActivityTaskManager.getService");
                getService.setAccessible(true);
                Object service = getService.invoke(null);
                if (service == null) throw new IllegalStateException("activity_task binder unavailable");

                if (!modeOk) {
                    Method setMode = findMethod(service.getClass(), "setTaskWindowingMode",
                            int.class, int.class, boolean.class);
                    if (setMode == null) throw new NoSuchMethodException("setTaskWindowingMode");
                    setMode.setAccessible(true);
                    Object result = setMode.invoke(service, taskId, 0 /* UNDEFINED */, true);
                    modeOk = !(result instanceof Boolean) || ((Boolean) result);
                    diag.append("|binderMode=").append(modeOk);
                }

                if (!boundsOk) {
                    Method resize = findMethod(service.getClass(), "resizeTask",
                            int.class, Rect.class, int.class);
                    if (resize != null) {
                        resize.setAccessible(true);
                        Object result = resize.invoke(service, taskId, null, 0);
                        boundsOk = !(result instanceof Boolean) || ((Boolean) result);
                        diag.append("|binderBounds=").append(boundsOk);
                    }
                }
            } catch (Throwable t) {
                Throwable e = unwrap(t);
                if (firstError == null) firstError = e;
                diag.append("|binderErr=").append(shortError(e));
            }
        }

        // Re-probe the task after the transaction. windowingMode is public on RunningTaskInfo.
        int mode = findTaskWindowingMode(packageName.trim(), displayId);
        diag.append("|finalMode=").append(mode);
        // finalMode is the resolved runtime mode; setting 0 may resolve to 1 or another parent mode.
        if (mode >= 0) diag.append("|requestedMode=0");

        if (modeOk) return "OK|" + diag;
        return "ERROR|windowing-0 transaction failed|" + diag
                + (firstError == null ? "" : "|first=" + shortError(firstError));
    }

    private int findTaskId(String packageName, int displayId) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return -1;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(200);
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (task == null || task.getDisplayId() != displayId) continue;
                if (matchesPackage(task.topActivity, packageName)
                        || matchesPackage(task.baseActivity, packageName)) {
                    return task.taskId;
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private int findTaskWindowingMode(String packageName, int displayId) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return -1;
            for (ActivityManager.RunningTaskInfo task : am.getRunningTasks(200)) {
                if (task == null || task.getDisplayId() != displayId) continue;
                if (matchesPackage(task.topActivity, packageName)
                        || matchesPackage(task.baseActivity, packageName)) {
                    try {
                        Method wm = findMethod(task.getClass(), "getWindowingMode");
                        if (wm != null) {
                            wm.setAccessible(true);
                            Object value = wm.invoke(task);
                            if (value instanceof Integer) return (Integer) value;
                        }
                    } catch (Throwable ignored) {}
                    return -1;
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static boolean matchesPackage(ComponentName component, String packageName) {
        return component != null && packageName.equals(component.getPackageName());
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> c = type;
        while (c != null) {
            try { return c.getDeclaredMethod(name, params); }
            catch (Throwable ignored) {}
            try { return c.getMethod(name, params); }
            catch (Throwable ignored) {}
            for (Class<?> iface : c.getInterfaces()) {
                try { return iface.getMethod(name, params); }
                catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Throwable unwrap(Throwable t) {
        Throwable out = t;
        while (out.getCause() != null && out.getCause() != out) out = out.getCause();
        return out;
    }

    private static String shortError(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        if (msg == null) msg = "";
        msg = msg.replace('|', '/').replace('\n', ' ');
        if (msg.length() > 120) msg = msg.substring(0, 120);
        return t.getClass().getSimpleName() + (msg.isEmpty() ? "" : ":" + msg);
    }

    private DisplayManager obtainDisplayManager() throws Exception {
        // DisplayManagerService validates that packageName belongs to Binder.getCallingUid().
        // This process runs as UID 2000, so report the shell package exactly like scrcpy FakeContext.
        Context shellContext = new ContextWrapper(context) {
            @Override public String getPackageName() { return "com.android.shell"; }
            @Override public String getOpPackageName() { return "com.android.shell"; }
            @Override public Context getApplicationContext() { return this; }
        };

        // Same general approach as scrcpy: construct DisplayManager with a shell identity context.
        Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        return ctor.newInstance(shellContext);
    }

    @Override
    public synchronized void releaseDesktopDisplay() {
        VirtualDisplay vd = desktopDisplay;
        desktopDisplay = null;
        if (vd != null) {
            try { vd.release(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public synchronized boolean injectPointer(int displayId, int action, float x, float y, long downTime, long eventTime) {
        MotionEvent event = null;
        try {
            event = MotionEvent.obtain(
                    downTime,
                    eventTime,
                    action,
                    x,
                    y,
                    0
            );
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            if (!setDisplayId(event, displayId)) return false;
            return injectInputEvent(event);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (event != null) event.recycle();
        }
    }

    @Override
    public synchronized boolean injectKey(int displayId, int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now + 1, KeyEvent.ACTION_UP, keyCode, 0);
        try {
            if (!setDisplayId(down, displayId) || !setDisplayId(up, displayId)) return false;
            return injectInputEvent(down) && injectInputEvent(up);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean injectInputEvent(InputEvent event) throws Exception {
        if (inputManager == null) {
            Object im = context == null ? null : context.getSystemService(Context.INPUT_SERVICE);
            if (!(im instanceof InputManager)) return false;
            inputManager = (InputManager) im;
        }
        if (injectInputEventMethod == null) {
            injectInputEventMethod = InputManager.class.getMethod("injectInputEvent", InputEvent.class, int.class);
        }
        Object result = injectInputEventMethod.invoke(inputManager, event, 0); // ASYNC
        return result instanceof Boolean && (Boolean) result;
    }

    private boolean setDisplayId(InputEvent event, int displayId) {
        try {
            if (setDisplayIdMethod == null) {
                setDisplayIdMethod = InputEvent.class.getMethod("setDisplayId", int.class);
            }
            setDisplayIdMethod.invoke(event, displayId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private synchronized void closePayload() {
        if (payloadOut != null) {
            try { payloadOut.flush(); } catch (Throwable ignored) {}
            try { payloadOut.close(); } catch (Throwable ignored) {}
            payloadOut = null;
        }
    }

    @Override public void destroy() {
        releaseDesktopDisplay();
        closePayload();
        System.exit(0);
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
