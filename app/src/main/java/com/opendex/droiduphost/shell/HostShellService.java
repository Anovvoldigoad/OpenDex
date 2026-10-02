package com.opendex.droiduphost.shell;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.os.SystemClock;
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
                return "OK|" + id + "|flags=0x" + Integer.toHexString(candidate)
                        + "|uid=" + Os.getuid();
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
