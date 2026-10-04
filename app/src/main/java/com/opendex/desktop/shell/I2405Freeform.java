package com.opendex.desktop.shell;

import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;

/**
 * iQOO I2405 / Android 16 native-freeform bridge.
 *
 * This class runs inside OpenDex's existing Shizuku UserService (shell UID).
 * It never creates a second APK.  It first tries to put the virtual display's
 * default TaskDisplayArea into WINDOWING_MODE_FREEFORM using the same
 * WindowContainerTransaction mechanism used by Android's desktop shell.  If
 * the OEM already owns FEATURE_DEFAULT_TASK_CONTAINER and refuses a temporary
 * organizer, it falls back to applying WCT directly to the launched task.
 *
 * All hidden framework types are reached reflectively so the app still builds
 * against the normal Android SDK 36 stubs.
 */
final class I2405Freeform {
    static final int FREEFORM = 5;
    private static final int FEATURE_DEFAULT_TASK_CONTAINER = 1;
    private static final String ORGANIZER_DESCRIPTOR = "android.window.IDisplayAreaOrganizer";

    private static volatile boolean hiddenApiReady;

    private I2405Freeform() {}

    static boolean enabled() {
        return Build.VERSION.SDK_INT >= 36 && "I2405".equalsIgnoreCase(safe(Build.MODEL));
    }

    static String prepareDisplay(int displayId) {
        if (!enabled()) return "SKIP|not-I2405-sdk36";
        try {
            enableHiddenApis();
            Result result = setDefaultTaskDisplayAreaMode(displayId, FREEFORM);
            return result.toWire("TDA");
        } catch (Throwable t) {
            return "WARN|TDA|" + describe(t);
        }
    }

    static String prepareLaunchedTask(int displayId, String packageName) {
        if (!enabled()) return "SKIP|not-I2405-sdk36";
        try {
            enableHiddenApis();
            // Give ActivityTaskManager a moment to publish the new task.
            try { Thread.sleep(180L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            Object task = findTask(displayId, packageName);
            if (task == null) return "WARN|TASK|not-found|display=" + displayId + "|pkg=" + packageName;

            int before = taskWindowingMode(task);
            Rect bounds = taskBounds(task);
            Rect target = freeformBounds(bounds);
            Result result = setTaskMode(task, FREEFORM, target);

            try { Thread.sleep(220L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Object verify = findTask(displayId, packageName);
            int after = verify == null ? -1 : taskWindowingMode(verify);
            Rect afterBounds = verify == null ? null : taskBounds(verify);

            return result.toWire("TASK")
                    + "|before=" + before
                    + "|after=" + after
                    + "|bounds=" + rectText(afterBounds);
        } catch (Throwable t) {
            return "WARN|TASK|" + describe(t);
        }
    }

    /**
     * Try the display-level route first. This is the important path: a default
     * TaskDisplayArea in freeform mode makes subsequent launches inherit a
     * desktop-capable windowing environment rather than relying only on launch
     * hints such as --windowingMode 5.
     */
    private static Result setDefaultTaskDisplayAreaMode(int displayId, int mode) throws Exception {
        Object controller = windowOrganizerController();
        if (controller == null) return Result.fail("window organizer controller=null");

        Object daController = invoke(controller, "getDisplayAreaOrganizerController");
        if (daController == null) return Result.fail("display-area organizer controller=null");

        Class<?> organizerInterface = Class.forName("android.window.IDisplayAreaOrganizer");
        CallbackBinder callbackBinder = new CallbackBinder();
        Object organizer = Proxy.newProxyInstance(
                organizerInterface.getClassLoader(),
                new Class<?>[]{organizerInterface},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("asBinder".equals(name)) return callbackBinder;
                    if ("toString".equals(name)) return "OpenDexI2405DisplayAreaOrganizer";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return args != null && args.length == 1 && proxy == args[0];
                    return defaultValue(method.getReturnType());
                });
        callbackBinder.attachInterface((IInterface) organizer, ORGANIZER_DESCRIPTOR);

        boolean registered = false;
        try {
            Object slice = invoke(daController, "registerOrganizer", organizer, FEATURE_DEFAULT_TASK_CONTAINER);
            registered = true;
            Object listObject = slice == null ? null : invoke(slice, "getList");
            if (!(listObject instanceof List<?>)) return Result.fail("display-area list unavailable");

            Object targetInfo = null;
            for (Object appeared : (List<?>) listObject) {
                if (appeared == null) continue;
                Object info = invoke(appeared, "getDisplayAreaInfo");
                if (info == null) continue;
                int id = intField(info, "displayId", -1);
                int feature = intField(info, "featureId", -1);
                if (id == displayId && feature == FEATURE_DEFAULT_TASK_CONTAINER) {
                    targetInfo = info;
                    break;
                }
            }
            if (targetInfo == null) return Result.fail("default TDA not found for display=" + displayId);

            Object token = objectField(targetInfo, "token");
            if (token == null) return Result.fail("default TDA token=null");

            int before = configurationWindowingMode(objectField(targetInfo, "configuration"));
            Object wct = newWct();
            invoke(wct, "setWindowingMode", token, mode);
            invoke(controller, "applyTransaction", wct);
            return Result.ok("display=" + displayId + "|before=" + before + "|target=" + mode);
        } catch (Throwable t) {
            return Result.fail(describe(t));
        } finally {
            if (registered) {
                try { invoke(daController, "unregisterOrganizer", organizer); } catch (Throwable ignored) {}
            }
        }
    }

    private static Result setTaskMode(Object task, int mode, Rect bounds) {
        try {
            Object token = objectField(task, "token");
            if (token == null) return Result.fail("task token=null");

            Object wct = newWct();
            invoke(wct, "setWindowingMode", token, mode);
            try { invoke(wct, "setActivityWindowingMode", token, mode); } catch (Throwable ignored) {}
            if (bounds != null && !bounds.isEmpty()) invoke(wct, "setBounds", token, bounds);

            Object controller = windowOrganizerController();
            if (controller == null) return Result.fail("window organizer controller=null");
            invoke(controller, "applyTransaction", wct);
            return Result.ok("mode=" + mode + "|requestedBounds=" + rectText(bounds));
        } catch (Throwable t) {
            return Result.fail(describe(t));
        }
    }

    private static Object findTask(int displayId, String packageName) throws Exception {
        Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
        Method getInstance = atmClass.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        Object atm = getInstance.invoke(null);

        Method getTasks = atmClass.getDeclaredMethod("getTasks", int.class, boolean.class, boolean.class, int.class);
        getTasks.setAccessible(true);
        Object result = getTasks.invoke(atm, 100, false, true, displayId);
        if (!(result instanceof List<?>)) return null;

        Object firstStandard = null;
        for (Object task : (List<?>) result) {
            if (task == null) continue;
            if (intField(task, "displayId", -1) != displayId) continue;
            int type = intField(task, "topActivityType", -1);
            if (type > 0 && type != 1) continue;
            if (firstStandard == null) firstStandard = task;

            Object top = objectFieldQuiet(task, "topActivity");
            if (top instanceof ComponentName && packageName != null
                    && packageName.equals(((ComponentName) top).getPackageName())) {
                return task;
            }
        }
        return firstStandard;
    }

    private static int taskWindowingMode(Object task) {
        try {
            Object config = objectField(task, "configuration");
            return configurationWindowingMode(config);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int configurationWindowingMode(Object configuration) {
        try {
            if (!(configuration instanceof Configuration)) return -1;
            Field wcField = Configuration.class.getDeclaredField("windowConfiguration");
            wcField.setAccessible(true);
            Object wc = wcField.get(configuration);
            Object value = invoke(wc, "getWindowingMode");
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static Rect taskBounds(Object task) {
        try {
            Object config = objectField(task, "configuration");
            if (!(config instanceof Configuration)) return null;
            Field wcField = Configuration.class.getDeclaredField("windowConfiguration");
            wcField.setAccessible(true);
            Object wc = wcField.get(config);
            Object value = invoke(wc, "getBounds");
            return value instanceof Rect ? new Rect((Rect) value) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rect freeformBounds(Rect source) {
        int width = source != null && source.width() > 0 ? source.width() : 1920;
        int height = source != null && source.height() > 0 ? source.height() : 1080;
        int leftBase = source == null ? 0 : source.left;
        int topBase = source == null ? 0 : source.top;
        int mx = Math.max(28, Math.round(width * 0.06f));
        int my = Math.max(28, Math.round(height * 0.07f));
        return new Rect(leftBase + mx, topBase + my,
                leftBase + width - mx, topBase + height - my);
    }

    private static Object windowOrganizerController() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        getService.setAccessible(true);
        Object binder = getService.invoke(null, "window_organizer");
        if (!(binder instanceof IBinder)) return null;

        Class<?> stub = Class.forName("android.window.IWindowOrganizerController$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        asInterface.setAccessible(true);
        return asInterface.invoke(null, binder);
    }

    private static Object newWct() throws Exception {
        Class<?> wct = Class.forName("android.window.WindowContainerTransaction");
        Constructor<?> ctor = wct.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static synchronized void enableHiddenApis() {
        if (hiddenApiReady) return;
        try {
            HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/app/",
                    "Landroid/window/",
                    "Landroid/os/",
                    "Landroid/content/res/");
        } catch (Throwable ignored) {
        }
        hiddenApiReady = true;
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        if (target == null) throw new NullPointerException(name + " target=null");
        Method m = findCompatibleMethod(target.getClass(), name, args);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (compatible(m, name, args)) return m;
            }
        }
        for (Class<?> i : type.getInterfaces()) {
            for (Method m : i.getMethods()) {
                if (compatible(m, name, args)) return m;
            }
        }
        for (Method m : type.getMethods()) {
            if (compatible(m, name, args)) return m;
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + args.length);
    }

    private static boolean compatible(Method method, String name, Object[] args) {
        if (!method.getName().equals(name)) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != args.length) return false;
        for (int i = 0; i < params.length; i++) {
            Object arg = args[i];
            if (arg == null) continue;
            Class<?> p = wrap(params[i]);
            if (!p.isAssignableFrom(arg.getClass())) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        return c;
    }

    private static Object objectField(Object obj, String name) throws Exception {
        if (obj == null) throw new NullPointerException(name + " owner=null");
        Field f = findField(obj.getClass(), name);
        f.setAccessible(true);
        return f.get(obj);
    }

    private static Object objectFieldQuiet(Object obj, String name) {
        try { return objectField(obj, name); } catch (Throwable ignored) { return null; }
    }

    private static int intField(Object obj, String name, int fallback) {
        try {
            Object value = objectField(obj, name);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == null || !type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg == null || msg.isEmpty() ? "" : ":" + msg.replace('\n', ' '));
    }

    private static String rectText(Rect r) {
        if (r == null) return "null";
        return r.left + "," + r.top + "," + r.right + "," + r.bottom;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Result {
        final boolean applied;
        final String detail;

        private Result(boolean applied, String detail) {
            this.applied = applied;
            this.detail = detail == null ? "" : detail;
        }

        static Result ok(String detail) { return new Result(true, detail); }
        static Result fail(String detail) { return new Result(false, detail); }

        String toWire(String stage) {
            return (applied ? "OK" : "WARN") + "|" + stage + "|" + detail;
        }
    }

    private static final class CallbackBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(ORGANIZER_DESCRIPTOR);
                return true;
            }
            // We only need the initial DisplayAreaAppearedInfo list returned by
            // registerOrganizer(). Later callbacks are intentionally ignored.
            if (reply != null) reply.writeNoException();
            return true;
        }
    }
}
