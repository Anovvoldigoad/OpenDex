package com.opendex.desktop.shell;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.SystemClock;
import android.system.Os;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Keep;

import com.opendex.desktop.IWindowShellService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Privileged Shizuku UserService. Each desktop window owns one trusted virtual display.
 * The Android app is fullscreen *inside that display*; the normal app process draws the
 * desktop window frame around the SurfaceView. This avoids OEM/AOSP freeform decorations.
 */
public final class WindowShellService extends IWindowShellService.Stub {
    private static final int VD_PUBLIC = 1 << 0;
    private static final int VD_PRESENTATION = 1 << 1;
    private static final int VD_OWN_CONTENT_ONLY = 1 << 3;
    private static final int VD_SUPPORTS_TOUCH = 1 << 6;
    private static final int VD_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VD_TRUSTED = 1 << 10;
    private static final int VD_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VD_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VD_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VD_OWN_FOCUS = 1 << 14;
    private static final int VD_DEVICE_DISPLAY_GROUP = 1 << 15;

    private final Context context;
    private final Map<Integer, VirtualDisplay> displays = new HashMap<>();
    private InputManager inputManager;
    private Method injectInputEventMethod;
    private Method setDisplayIdMethod;

    public WindowShellService() { this.context = null; }

    @Keep
    public WindowShellService(Context context) { this.context = context; }

    @Override public int remoteUid() { return Os.getuid(); }

    @Override
    public synchronized String exec(String command) {
        if (command == null || command.trim().isEmpty()) return "EXIT=2\nempty command";
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "EXIT=124\ntimeout";
            }
            return "EXIT=" + process.exitValue() + "\n" + readAll(process.getInputStream());
        } catch (Throwable t) {
            return "EXIT=127\n" + shortError(t);
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override
    public synchronized String createWindowDisplay(Surface surface, int width, int height, int dpi, String name, int sdkInt) {
        if (surface == null || !surface.isValid()) return "ERROR|invalid surface";
        if (context == null) return "ERROR|UserService context unavailable";
        width = clamp(width, 320, 2800);
        height = clamp(height, 240, 2800);
        dpi = clamp(dpi, 120, 640);

        final int base = VD_PUBLIC | VD_PRESENTATION | VD_OWN_CONTENT_ONLY
                | VD_SUPPORTS_TOUCH | VD_DESTROY_CONTENT_ON_REMOVAL;
        int flags = base;
        if (sdkInt >= 33) {
            flags |= VD_TRUSTED | VD_OWN_DISPLAY_GROUP | VD_ALWAYS_UNLOCKED | VD_TOUCH_FEEDBACK_DISABLED;
            if (sdkInt >= 34) flags |= VD_OWN_FOCUS | VD_DEVICE_DISPLAY_GROUP;
        }

        int[] attempts;
        if (sdkInt >= 34) {
            attempts = new int[]{
                    flags,
                    flags & ~VD_DEVICE_DISPLAY_GROUP,
                    flags & ~(VD_DEVICE_DISPLAY_GROUP | VD_OWN_FOCUS),
                    base | VD_TRUSTED | VD_ALWAYS_UNLOCKED,
                    base | VD_TRUSTED
            };
        } else if (sdkInt >= 33) {
            attempts = new int[]{flags, base | VD_TRUSTED | VD_ALWAYS_UNLOCKED, base | VD_TRUSTED};
        } else {
            attempts = new int[]{base};
        }

        Throwable last = null;
        for (int candidate : attempts) {
            try {
                DisplayManager dm = obtainDisplayManager();
                VirtualDisplay vd = dm.createVirtualDisplay(
                        "OpenDex Window - " + safeName(name), width, height, dpi, surface, candidate);
                if (vd == null || vd.getDisplay() == null) throw new IllegalStateException("null VirtualDisplay");
                int displayId = vd.getDisplay().getDisplayId();
                displays.put(displayId, vd);

                // Keep each app display stable. The app itself is fullscreen inside this display;
                // the host window controls geometry, so native freeform is intentionally unused.
                runShellQuick("wm set-ignore-orientation-request -d " + displayId + " true");
                return "OK|" + displayId + "|" + width + "x" + height + "@" + dpi
                        + "|flags=0x" + Integer.toHexString(candidate) + "|uid=" + Os.getuid();
            } catch (Throwable t) {
                last = t;
            }
        }
        return "ERROR|create display failed|" + shortError(last);
    }

    @Override
    public synchronized String resizeWindowDisplay(int displayId, int width, int height, int dpi) {
        VirtualDisplay vd = displays.get(displayId);
        if (vd == null) return "ERROR|display not found|" + displayId;
        width = clamp(width, 320, 2800);
        height = clamp(height, 240, 2800);
        dpi = clamp(dpi, 120, 640);
        try {
            vd.resize(width, height, dpi);
            return "OK|" + displayId + "|" + width + "x" + height + "@" + dpi;
        } catch (Throwable t) {
            return "ERROR|resize failed|" + shortError(t);
        }
    }

    @Override
    public synchronized String launchComponent(int displayId, String packageName, String componentName) {
        if (!displays.containsKey(displayId)) return "EXIT=2\ndisplay not found: " + displayId;
        if (!safeToken(packageName) || !safeComponent(componentName)) return "EXIT=2\ninvalid component";

        // Avoid native freeform completely: app is fullscreen *inside its own virtual display*.
        // NEW_TASK + CLEAR_TASK prevents an old phone task from swallowing the launch on many OEMs.
        String cmd = "settings put global force_resizable_activities 1; "
                + "am start --user current --display " + displayId
                + " --windowingMode 1 -f 0x10008000 -n " + componentName;
        return exec(cmd);
    }

    @Override
    public synchronized void releaseWindowDisplay(int displayId) {
        VirtualDisplay vd = displays.remove(displayId);
        if (vd != null) {
            try { vd.release(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public synchronized boolean injectPointer(int displayId, int action, float x, float y, long downTime, long eventTime) {
        if (!displays.containsKey(displayId)) return false;
        MotionEvent event = null;
        try {
            event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
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
        if (!displays.containsKey(displayId)) return false;
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

    private DisplayManager obtainDisplayManager() throws Exception {
        Context shellContext = new ContextWrapper(context) {
            @Override public String getPackageName() { return "com.android.shell"; }
            @Override public String getOpPackageName() { return "com.android.shell"; }
            @Override public Context getApplicationContext() { return this; }
        };
        Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        return ctor.newInstance(shellContext);
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
        Object result = injectInputEventMethod.invoke(inputManager, event, 0);
        return result instanceof Boolean && (Boolean) result;
    }

    private boolean setDisplayId(InputEvent event, int displayId) {
        try {
            if (setDisplayIdMethod == null) setDisplayIdMethod = InputEvent.class.getMethod("setDisplayId", int.class);
            setDisplayIdMethod.invoke(event, displayId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String runShellQuick(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start();
            if (!p.waitFor(4, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "timeout";
            }
            return readAll(p.getInputStream());
        } catch (Throwable t) {
            return shortError(t);
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = input.read(b)) != -1) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String safeName(String value) {
        if (value == null) return "App";
        return value.replaceAll("[^A-Za-z0-9._ -]", "_");
    }
    private static boolean safeToken(String value) { return value != null && value.matches("[A-Za-z0-9._$]+" ); }
    private static boolean safeComponent(String value) { return value != null && value.matches("[A-Za-z0-9._$/]+/[A-Za-z0-9._$/]+" ); }
    private static String shortError(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage() == null ? "" : t.getMessage().replace('|', '/').replace('\n', ' ');
        if (m.length() > 160) m = m.substring(0, 160);
        return t.getClass().getSimpleName() + (m.isEmpty() ? "" : ":" + m);
    }

    @Override
    public synchronized void destroy() {
        for (VirtualDisplay vd : displays.values()) {
            try { vd.release(); } catch (Throwable ignored) {}
        }
        displays.clear();
        System.exit(0);
    }
}
