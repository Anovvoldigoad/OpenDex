package com.opendex.droiduphost;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/** Resolution/DPI policy for the virtual desktop. */
public final class DesktopConfig {
    public static final String PREFS = "desktop_config";
    public static final String KEY_MODE = "resolution_mode";
    public static final String KEY_CUSTOM_W = "custom_width";
    public static final String KEY_CUSTOM_H = "custom_height";
    public static final String KEY_CUSTOM_DPI = "custom_dpi";

    public static final String MODE_AUTO = "Auto (layar HP)";
    public static final String MODE_1080 = "1920 × 1080";
    public static final String MODE_900 = "1600 × 900";
    public static final String MODE_768 = "1366 × 768";
    public static final String MODE_720 = "1280 × 720";
    public static final String MODE_CUSTOM = "Custom";

    public final int width;
    public final int height;
    public final int dpi;
    public final String mode;

    public DesktopConfig(int width, int height, int dpi, String mode) {
        this.width = Math.max(800, width);
        this.height = Math.max(480, height);
        this.dpi = clamp(dpi, 120, 640);
        this.mode = mode == null ? MODE_AUTO : mode;
    }

    public static String[] modes() {
        return new String[] { MODE_AUTO, MODE_1080, MODE_900, MODE_768, MODE_720, MODE_CUSTOM };
    }

    public static int[] detectCurrentPixels(Activity activity) {
        int w = 1920, h = 1080;
        try {
            WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                w = Math.max(metrics.widthPixels, metrics.heightPixels);
                h = Math.min(metrics.widthPixels, metrics.heightPixels);
            }
        } catch (Throwable ignored) {}
        return new int[] { w, h };
    }

    public static int[] detectPhysicalMode(Activity activity) {
        int[] current = detectCurrentPixels(activity);
        int w = current[0], h = current[1];
        try {
            WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            Display.Mode mode = wm.getDefaultDisplay().getMode();
            int mw = mode.getPhysicalWidth();
            int mh = mode.getPhysicalHeight();
            if (mw > 0 && mh > 0) {
                w = Math.max(mw, mh);
                h = Math.min(mw, mh);
            }
        } catch (Throwable ignored) {}
        return new int[] { w, h };
    }

    /** Keep desktop scale close to 1080p @ 240 dpi while following selected resolution. */
    public static int recommendedDpi(int heightPx) {
        int raw = Math.round(heightPx * (240f / 1080f));
        // Round to an 8-dpi step so UI scaling is stable and predictable.
        return clamp(Math.round(raw / 8f) * 8, 160, 360);
    }

    public static DesktopConfig fromSelection(Activity activity, String mode, int customW, int customH, int customDpi) {
        if (MODE_1080.equals(mode)) return new DesktopConfig(1920, 1080, recommendedDpi(1080), mode);
        if (MODE_900.equals(mode)) return new DesktopConfig(1600, 900, recommendedDpi(900), mode);
        if (MODE_768.equals(mode)) return new DesktopConfig(1366, 768, recommendedDpi(768), mode);
        if (MODE_720.equals(mode)) return new DesktopConfig(1280, 720, recommendedDpi(720), mode);
        if (MODE_CUSTOM.equals(mode)) {
            int w = Math.max(customW, customH);
            int h = Math.min(customW, customH);
            if (w < 800 || h < 480) { w = 1280; h = 720; }
            int dpi = customDpi > 0 ? customDpi : recommendedDpi(h);
            return new DesktopConfig(w, h, dpi, mode);
        }
        int[] detected = detectCurrentPixels(activity);
        return new DesktopConfig(detected[0], detected[1], recommendedDpi(detected[1]), MODE_AUTO);
    }

    public static void save(Activity activity, DesktopConfig cfg) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODE, cfg.mode)
                .putInt(KEY_CUSTOM_W, cfg.width)
                .putInt(KEY_CUSTOM_H, cfg.height)
                .putInt(KEY_CUSTOM_DPI, cfg.dpi)
                .apply();
    }

    public static String savedMode(Activity activity) {
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString(KEY_MODE, MODE_AUTO);
    }

    public static int savedW(Activity activity) {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CUSTOM_W, 1920);
    }

    public static int savedH(Activity activity) {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CUSTOM_H, 1080);
    }

    public static int savedDpi(Activity activity) {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CUSTOM_DPI, 240);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
