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
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Field;
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

    private volatile boolean taskWatcherRunning;
    private Thread taskWatcherThread;
    private int desktopDisplayId = -1;
    private int desktopWidth;
    private int desktopHeight;
    private final Set<Integer> processedFreeformTasks = new HashSet<>();

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

                // Keep the display policy inherited/undefined (0). On the tested OEM this resolves
                // to fullscreen for the launcher. A shell-side watcher below converts only child
                // app tasks to bounded/resizable tasks, so the desktop itself never gets a caption.
                String displayMode = configureDesktopWindowing(id);
                startTaskFreeformWatcher(id, width, height);

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
     * Keep the display itself in WINDOWING_MODE_UNDEFINED/fullscreen so DroidUP is a true
     * desktop background. Some OEM builds ignore ActivityOptions.setLaunchBounds() while the
     * display policy resolves to fullscreen. Instead of forcing the whole display to mode 5,
     * watch for newly-created non-launcher tasks and resize only those tasks through the public
     * `am task` shell surface. AOSP's `am task resize` forces the task resizable and places it
     * in a bounded stack, which is exactly what we need here without touching DroidUP's UI.
     */
    private synchronized void startTaskFreeformWatcher(int displayId, int width, int height) {
        stopTaskFreeformWatcher();
        desktopDisplayId = displayId;
        desktopWidth = width;
        desktopHeight = height;
        processedFreeformTasks.clear();
        taskWatcherRunning = true;
        taskWatcherThread = new Thread(() -> runTaskWatcher(displayId, width, height), "DroidUP-TaskWatcher");
        taskWatcherThread.setDaemon(true);
        taskWatcherThread.start();
    }

    private void runTaskWatcher(int displayId, int width, int height) {
        // Let the launcher become stable before evaluating child tasks.
        SystemClock.sleep(450);
        while (taskWatcherRunning && desktopDisplayId == displayId) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(200);
                    for (ActivityManager.RunningTaskInfo task : tasks) {
                        if (!taskWatcherRunning || desktopDisplayId != displayId) break;
                        if (task == null || getTaskDisplayId(task) != displayId) continue;
                        String pkg = taskPackage(task);
                        if (pkg == null || pkg.isEmpty()) continue;
                        if ("com.levelup.droiduplauncher".equals(pkg)
                                || "com.opendex.droiduphost".equals(pkg)
                                || "com.android.systemui".equals(pkg)
                                || "com.android.shell".equals(pkg)) {
                            continue;
                        }
                        int taskId = task.taskId;
                        synchronized (processedFreeformTasks) {
                            if (processedFreeformTasks.contains(taskId)) continue;
                            processedFreeformTasks.add(taskId);
                        }
                        forceTaskBounded(task, width, height);
                    }
                }
            } catch (Throwable ignored) {
                // Keep the watcher alive; an individual OEM task object must not kill the session.
            }
            SystemClock.sleep(180);
        }
    }

    private void forceTaskBounded(ActivityManager.RunningTaskInfo task, int width, int height) {
        if (task == null) return;
        int taskId = task.taskId;
        Rect bounds = readTaskBounds(task);
        if (!isUsefulWindowBounds(bounds, width, height)) {
            bounds = defaultWindowBounds(taskId, width, height);
        } else {
            bounds = clampBounds(bounds, width, height);
        }

        String rect = bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom;
        // `am task resizeable 2` marks the task resizable; `am task resize` then applies
        // bounded geometry. Use a short-lived shell only once per newly-seen task, not per frame.
        runShellQuick("am task resizeable " + taskId + " 2; am task resize " + taskId + " " + rect);
    }

    private Rect defaultWindowBounds(int taskId, int width, int height) {
        int w = Math.max(640, Math.round(width * 0.72f));
        int h = Math.max(420, Math.round(height * 0.76f));
        w = Math.min(w, Math.max(1, width - 80));
        h = Math.min(h, Math.max(1, height - 100));
        int slot = Math.abs(taskId) % 5;
        int stepX = Math.max(18, width / 80);
        int stepY = Math.max(18, height / 45);
        int left = Math.max(20, (width - w) / 2 + (slot - 2) * stepX);
        int top = Math.max(20, (height - h) / 2 + (slot - 2) * stepY);
        if (left + w > width - 20) left = Math.max(20, width - w - 20);
        if (top + h > height - 70) top = Math.max(20, height - h - 70);
        return new Rect(left, top, left + w, top + h);
    }

    private static Rect clampBounds(Rect in, int width, int height) {
        Rect out = new Rect(in);
        int minW = Math.min(480, Math.max(240, width / 4));
        int minH = Math.min(320, Math.max(180, height / 4));
        if (out.width() < minW || out.height() < minH) return new Rect(in);
        int dx = 0, dy = 0;
        if (out.left < 0) dx = -out.left;
        if (out.right + dx > width) dx += width - (out.right + dx);
        if (out.top < 0) dy = -out.top;
        if (out.bottom + dy > height - 40) dy += (height - 40) - (out.bottom + dy);
        out.offset(dx, dy);
        return out;
    }

    private static boolean isUsefulWindowBounds(Rect r, int width, int height) {
        if (r == null || r.isEmpty()) return false;
        if (r.width() < 240 || r.height() < 180) return false;
        // Full-display bounds mean the OEM discarded DroidUP's requested launch bounds.
        return r.width() < width * 0.94f || r.height() < height * 0.92f;
    }

    private static String taskPackage(ActivityManager.RunningTaskInfo task) {
        if (task == null) return null;
        ComponentName c = task.topActivity != null ? task.topActivity : task.baseActivity;
        return c == null ? null : c.getPackageName();
    }

    private static Rect readTaskBounds(ActivityManager.RunningTaskInfo task) {
        if (task == null) return null;
        try {
            Field configurationField = findField(task.getClass(), "configuration");
            if (configurationField == null) return null;
            configurationField.setAccessible(true);
            Object configuration = configurationField.get(task);
            if (configuration == null) return null;
            Field wcField = findField(configuration.getClass(), "windowConfiguration");
            if (wcField == null) return null;
            wcField.setAccessible(true);
            Object wc = wcField.get(configuration);
            if (wc == null) return null;
            Method getBounds = findMethod(wc.getClass(), "getBounds");
            if (getBounds == null) return null;
            getBounds.setAccessible(true);
            Object value = getBounds.invoke(wc);
            return value instanceof Rect ? new Rect((Rect) value) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            try { return c.getDeclaredField(name); }
            catch (Throwable ignored) {}
            try { return c.getField(name); }
            catch (Throwable ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    private static String runShellQuick(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "timeout";
            }
            return readAll(process.getInputStream());
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }

    private synchronized void stopTaskFreeformWatcher() {
        taskWatcherRunning = false;
        Thread t = taskWatcherThread;
        taskWatcherThread = null;
        if (t != null) t.interrupt();
        desktopDisplayId = -1;
        synchronized (processedFreeformTasks) {
            processedFreeformTasks.clear();
        }
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
                if (task == null || getTaskDisplayId(task) != displayId) continue;
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
                if (task == null || getTaskDisplayId(task) != displayId) continue;
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

    /**
     * TaskInfo#getDisplayId() and TaskInfo#displayId are hidden framework API members,
     * so they are not present in the public Android SDK stubs used by GitHub Actions.
     * Resolve them reflectively inside the Shizuku shell process instead of referencing
     * the hidden API at compile time.
     */
    private static int getTaskDisplayId(ActivityManager.RunningTaskInfo task) {
        if (task == null) return -1;
        try {
            Method getter = findMethod(task.getClass(), "getDisplayId");
            if (getter != null) {
                getter.setAccessible(true);
                Object value = getter.invoke(task);
                if (value instanceof Integer) return (Integer) value;
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> c = task.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("displayId");
                    f.setAccessible(true);
                    return f.getInt(task);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
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
        stopTaskFreeformWatcher();
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
