package com.opendex.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "opendex_prefs";
    private static final String OVERLAY = "overlay_enabled";
    private static final String AUTO_EXTERNAL = "auto_external_desktop";
    private static final String PHONE_TOUCHPAD = "phone_touchpad_on_external";

    private Prefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean overlayEnabled(Context c) { return prefs(c).getBoolean(OVERLAY, false); }
    public static void setOverlayEnabled(Context c, boolean v) { prefs(c).edit().putBoolean(OVERLAY, v).apply(); }

    public static boolean autoExternalDesktop(Context c) { return prefs(c).getBoolean(AUTO_EXTERNAL, true); }
    public static void setAutoExternalDesktop(Context c, boolean v) { prefs(c).edit().putBoolean(AUTO_EXTERNAL, v).apply(); }

    public static boolean phoneTouchpadOnExternal(Context c) { return prefs(c).getBoolean(PHONE_TOUCHPAD, false); }
    public static void setPhoneTouchpadOnExternal(Context c, boolean v) { prefs(c).edit().putBoolean(PHONE_TOUCHPAD, v).apply(); }
}
