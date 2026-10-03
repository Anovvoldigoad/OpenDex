package com.opendex.i2405probe;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Process;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ProbeUserService extends IProbeService.Stub {
    private static final String LOG_PATH = "/sdcard/Download/OpenDex_I2405_WCT_Probe.txt";
    private static final int FREEFORM = 5;
    private static final long MAX_RUN_MS = 120_000L;

    private final Object lock = new Object();
    private final StringBuilder log = new StringBuilder();
    private final Set<Integer> completedTasks = new HashSet<>();
    private final Map<Integer, Integer> slotByDisplay = new HashMap<>();
    private volatile boolean running;
    private Thread worker;

    public ProbeUserService() {
        init();
    }

    // Some Shizuku versions prefer a Context constructor.
    public ProbeUserService(Context context) {
        init();
    }

    private void init() {
        try {
            HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/app/",
                    "Landroid/window/",
                    "Landroid/content/res/",
                    "Landroid/view/");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String startProbe() {
        synchronized (lock) {
            if (running) return "Probe sudah running. Buka Dextop dan lanjut tes.";
            running = true;
            completedTasks.clear();
            slotByDisplay.clear();
            log.setLength(0);
            append("============================================================");
            append("OpenDex I2405 WCT Probe v0.1");
            append("time=" + nowFull());
            append("uid=" + Process.myUid() + " pid=" + Process.myPid());
            append("device=" + Build.MANUFACTURER + "/" + Build.BRAND + "/" + Build.MODEL
                    + " sdk=" + Build.VERSION.SDK_INT + " android=" + Build.VERSION.RELEASE);
            append("goal=force non-zero-display app tasks through native WindowContainerTransaction");
            append("============================================================");
            append("Waiting for Dextop task(s) on displayId > 0 …");
            flush();

            worker = new Thread(this::runLoop, "i2405-wct-probe");
            worker.start();
            return "Probe worker STARTED sebagai uid=" + Process.myUid()
                    + ". Sekarang buka Dextop → Start → Chrome → YouTube. Jangan pindah ke app ini sampai tes selesai.";
        }
    }

    @Override
    public String getLog() {
        synchronized (lock) {
            return log.toString();
        }
    }

    @Override
    public void stopProbe() {
        running = false;
        append("Probe stop requested by UI");
        flush();
    }

    @Override
    public void destroy() {
        running = false;
        System.exit(0);
    }

    private void runLoop() {
        long deadline = System.currentTimeMillis() + MAX_RUN_MS;
        boolean sawTarget = false;
        try {
            logReflectionSurface();
            while (running && System.currentTimeMillis() < deadline) {
                List<?> tasks = getTasks(-1);
                if (tasks != null) {
                    for (Object task : tasks) {
                        int taskId = intField(task, "taskId", -1);
                        int displayId = intField(task, "displayId", -1);
                        if (taskId < 0 || displayId <= 0 || completedTasks.contains(taskId)) continue;

                        String pkg = topPackage(task);
                        int activityType = taskActivityType(task);
                        if (shouldIgnore(pkg, activityType)) continue;

                        sawTarget = true;
                        probeTask(task, taskId, displayId, pkg);
                        completedTasks.add(taskId);
                    }
                }
                Thread.sleep(550);
            }
        } catch (Throwable t) {
            append("FATAL worker: " + describe(t));
        } finally {
            running = false;
            if (!sawTarget) {
                append("VERDICT=NO_TARGET_TASKS_SEEN");
                append("No app task on displayId>0 was visible to ActivityTaskManager during the probe window.");
            }
            append("Probe finished time=" + nowFull());
            flush();
        }
    }

    private void logReflectionSurface() {
        append("--- API SURFACE ---");
        try {
            Class<?> atm = Class.forName("android.app.ActivityTaskManager");
            append("ActivityTaskManager=" + atm);
            append("ATM.getTasks(4 args)=" + methodExists(atm, "getTasks", 4));
            Object svc = invokeStatic(atm, "getService");
            append("IActivityTaskManager proxy=" + (svc == null ? "null" : svc.getClass().getName()));
            if (svc != null) {
                List<String> names = new ArrayList<>();
                for (Method m : svc.getClass().getMethods()) {
                    String n = m.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("windowing") || n.contains("resizetask") || n.contains("gettasks")) {
                        names.add(m.toString());
                    }
                }
                for (String n : names) append("ATM_METHOD " + n);
            }
            append("WCT=" + Class.forName("android.window.WindowContainerTransaction"));
            append("WindowOrganizer=" + Class.forName("android.window.WindowOrganizer"));
        } catch (Throwable t) {
            append("API surface error=" + describe(t));
        }
    }

    private void probeTask(Object task, int taskId, int displayId, String pkg) {
        append("");
        append("--- TARGET taskId=" + taskId + " displayId=" + displayId + " package=" + pkg + " ---");
        try {
            State before = stateOf(task);
            append("BEFORE " + before);
            Rect desired = desiredBounds(before.bounds, displayId);
            append("DESIRED bounds=" + desired + " mode=FREEFORM(5)");

            AttemptResult a = applyWct(task, desired, false);
            append("WCT_A taskMode+setBounds apply=" + a);
            Thread.sleep(700);
            Object verifyA = findTask(taskId, displayId);
            State afterA = verifyA == null ? null : stateOf(verifyA);
            append("VERIFY_A " + afterA);

            if (afterA != null && afterA.mode == FREEFORM && !sameRect(afterA.bounds, before.bounds)) {
                append("VERDICT=SUCCESS_NATIVE_FREEFORM taskId=" + taskId);
                return;
            }

            Object latest = verifyA != null ? verifyA : task;
            AttemptResult b = applyWct(latest, desired, true);
            append("WCT_B taskMode+activityMode+setBounds apply=" + b);
            Thread.sleep(850);
            Object verifyB = findTask(taskId, displayId);
            State afterB = verifyB == null ? null : stateOf(verifyB);
            append("VERIFY_B " + afterB);

            if (afterB != null && afterB.mode == FREEFORM && !sameRect(afterB.bounds, before.bounds)) {
                append("VERDICT=SUCCESS_NATIVE_FREEFORM taskId=" + taskId);
            } else if ((a.applied || b.applied) && afterB != null && afterB.mode != FREEFORM) {
                append("VERDICT=ORIGINOS_COERCED_FULLSCREEN taskId=" + taskId);
            } else if (a.securityBlocked || b.securityBlocked) {
                append("VERDICT=WCT_BLOCKED_BY_PERMISSION taskId=" + taskId);
            } else {
                append("VERDICT=WCT_FAILED_OR_TASK_VANISHED taskId=" + taskId);
            }
        } catch (Throwable t) {
            append("TASK probe error=" + describe(t));
            append("VERDICT=WCT_PROBE_EXCEPTION taskId=" + taskId);
        }
        flush();
    }

    private AttemptResult applyWct(Object task, Rect bounds, boolean includeActivityMode) {
        AttemptResult result = new AttemptResult();
        try {
            Object token = objectField(task, "token");
            if (token == null) throw new IllegalStateException("TaskInfo.token is null");

            Class<?> tokenClass = Class.forName("android.window.WindowContainerToken");
            Class<?> wctClass = Class.forName("android.window.WindowContainerTransaction");
            Constructor<?> wctCtor = wctClass.getDeclaredConstructor();
            wctCtor.setAccessible(true);
            Object wct = wctCtor.newInstance();

            Method setMode = wctClass.getDeclaredMethod("setWindowingMode", tokenClass, int.class);
            setMode.setAccessible(true);
            setMode.invoke(wct, token, FREEFORM);

            if (includeActivityMode) {
                try {
                    Method setActivityMode = wctClass.getDeclaredMethod(
                            "setActivityWindowingMode", tokenClass, int.class);
                    setActivityMode.setAccessible(true);
                    setActivityMode.invoke(wct, token, FREEFORM);
                    result.activityModeIncluded = true;
                } catch (Throwable t) {
                    result.activityModeError = describe(t);
                }
            }

            Method setBounds = wctClass.getDeclaredMethod("setBounds", tokenClass, Rect.class);
            setBounds.setAccessible(true);
            setBounds.invoke(wct, token, bounds);

            Class<?> organizerClass = Class.forName("android.window.WindowOrganizer");
            Constructor<?> orgCtor = organizerClass.getDeclaredConstructor();
            orgCtor.setAccessible(true);
            Object organizer = orgCtor.newInstance();
            Method apply = organizerClass.getDeclaredMethod("applyTransaction", wctClass);
            apply.setAccessible(true);
            apply.invoke(organizer, wct);
            result.applied = true;
        } catch (Throwable t) {
            Throwable root = rootCause(t);
            result.error = root.getClass().getName() + ": " + root.getMessage();
            result.securityBlocked = root instanceof SecurityException
                    || result.error.toLowerCase(Locale.ROOT).contains("permission")
                    || result.error.toLowerCase(Locale.ROOT).contains("manage_activity_tasks");
        }
        return result;
    }

    private List<?> getTasks(int displayId) throws Exception {
        Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
        Method getInstance = atmClass.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        Object atm = getInstance.invoke(null);
        Method getTasks = atmClass.getDeclaredMethod(
                "getTasks", int.class, boolean.class, boolean.class, int.class);
        getTasks.setAccessible(true);
        Object out = getTasks.invoke(atm, 100, false, true, displayId);
        return out instanceof List ? (List<?>) out : null;
    }

    private Object findTask(int taskId, int displayId) throws Exception {
        List<?> tasks = getTasks(displayId);
        if (tasks == null) return null;
        for (Object task : tasks) {
            if (intField(task, "taskId", -1) == taskId) return task;
        }
        return null;
    }

    private State stateOf(Object task) throws Exception {
        Object config = objectField(task, "configuration");
        if (config == null) throw new IllegalStateException("configuration=null");
        Field wcField = Configuration.class.getDeclaredField("windowConfiguration");
        wcField.setAccessible(true);
        Object wc = wcField.get(config);
        Method getMode = wc.getClass().getDeclaredMethod("getWindowingMode");
        Method getBounds = wc.getClass().getDeclaredMethod("getBounds");
        getMode.setAccessible(true);
        getBounds.setAccessible(true);
        int mode = (Integer) getMode.invoke(wc);
        Rect bounds = new Rect((Rect) getBounds.invoke(wc));
        return new State(mode, bounds);
    }

    private int taskActivityType(Object task) {
        try {
            return intField(task, "topActivityType", -1);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private String topPackage(Object task) {
        try {
            Object top = objectField(task, "topActivity");
            if (top instanceof ComponentName) return ((ComponentName) top).getPackageName();
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private boolean shouldIgnore(String pkg, int activityType) {
        if (activityType > 0 && activityType != 1) return true; // standard activity only
        if (pkg == null) return false;
        return pkg.equals("moe.n4tsu.dextop")
                || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.launcher3")
                || pkg.equals("com.opendex.i2405probe");
    }

    private Rect desiredBounds(Rect full, int displayId) {
        int w = full.width();
        int h = full.height();
        if (w < 800 || h < 500) {
            w = 1920;
            h = 1080;
        }
        int slot = slotByDisplay.getOrDefault(displayId, 0);
        slotByDisplay.put(displayId, slot + 1);
        int mx = Math.max(32, w / 40);
        int my = Math.max(32, h / 30);
        int gap = Math.max(24, w / 80);
        if ((slot & 1) == 0) {
            return new Rect(mx, my, (w / 2) - gap, h - my);
        }
        return new Rect((w / 2) + gap, my, w - mx, h - my);
    }

    private static boolean sameRect(Rect a, Rect b) {
        return a != null && b != null && a.equals(b);
    }

    private static Object objectField(Object obj, String name) throws Exception {
        Field f = findField(obj.getClass(), name);
        f.setAccessible(true);
        return f.get(obj);
    }

    private static int intField(Object obj, String name, int fallback) {
        try {
            Field f = findField(obj.getClass(), name);
            f.setAccessible(true);
            return f.getInt(obj);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object invokeStatic(Class<?> clazz, String name) throws Exception {
        Method m = clazz.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(null);
    }

    private static boolean methodExists(Class<?> c, String name, int paramCount) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == paramCount) return true;
        }
        return false;
    }

    private void append(String line) {
        synchronized (lock) {
            log.append('[').append(now()).append("] ").append(line).append('\n');
        }
        flush();
    }

    private void flush() {
        try {
            File file = new File(LOG_PATH);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            String text;
            synchronized (lock) {
                text = log.toString();
            }
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String nowFull() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date());
    }

    private static String describe(Throwable t) {
        Throwable root = rootCause(t);
        return root.getClass().getName() + ": " + root.getMessage();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur;
    }

    private static final class State {
        final int mode;
        final Rect bounds;
        State(int mode, Rect bounds) {
            this.mode = mode;
            this.bounds = bounds;
        }
        @Override public String toString() {
            return "windowingMode=" + mode + " bounds=" + bounds;
        }
    }

    private static final class AttemptResult {
        boolean applied;
        boolean securityBlocked;
        boolean activityModeIncluded;
        String activityModeError;
        String error;
        @Override public String toString() {
            return "applied=" + applied
                    + " securityBlocked=" + securityBlocked
                    + " activityModeIncluded=" + activityModeIncluded
                    + (activityModeError == null ? "" : " activityModeError={" + activityModeError + "}")
                    + (error == null ? "" : " error={" + error + "}");
        }
    }
}
