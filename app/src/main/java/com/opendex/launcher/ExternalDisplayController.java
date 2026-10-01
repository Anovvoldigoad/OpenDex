package com.opendex.launcher;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import com.opendex.launcher.util.Prefs;

import java.util.HashMap;
import java.util.Map;

public final class ExternalDisplayController implements DisplayManager.DisplayListener {
    private final Activity activity;
    private final ShizukuController shizuku;
    private final DisplayManager displayManager;
    private final Map<Integer, DesktopPresentation> presentations = new HashMap<>();

    public ExternalDisplayController(Activity activity, ShizukuController shizuku) {
        this.activity = activity;
        this.shizuku = shizuku;
        this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
    }

    public void start() {
        displayManager.registerDisplayListener(this, null);
        refresh();
    }

    public void stop() {
        displayManager.unregisterDisplayListener(this);
        for (DesktopPresentation p : presentations.values()) try { p.dismiss(); } catch (Throwable ignored) {}
        presentations.clear();
    }

    public void refresh() {
        if (!Prefs.autoExternalDesktop(activity)) return;
        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display display : displays) show(display);
    }

    private void show(Display display) {
        if (display == null || display.getDisplayId() == Display.DEFAULT_DISPLAY || presentations.containsKey(display.getDisplayId())) return;
        try {
            DesktopPresentation p = new DesktopPresentation(activity, display, shizuku);
            p.setOnDismissListener(dialog -> presentations.remove(display.getDisplayId()));
            p.show();
            presentations.put(display.getDisplayId(), p);
        } catch (Throwable ignored) {}
    }

    @Override public void onDisplayAdded(int displayId) { refresh(); }
    @Override public void onDisplayChanged(int displayId) {
        DesktopPresentation p = presentations.get(displayId);
        if (p != null) p.refresh();
    }
    @Override public void onDisplayRemoved(int displayId) {
        DesktopPresentation p = presentations.remove(displayId);
        if (p != null) try { p.dismiss(); } catch (Throwable ignored) {}
    }
}
