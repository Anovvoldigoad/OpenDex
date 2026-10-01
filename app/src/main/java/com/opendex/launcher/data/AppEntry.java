package com.opendex.launcher.data;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public final class AppEntry {
    public final String label;
    public final String packageName;
    public final ComponentName component;
    public final Drawable icon;

    public AppEntry(String label, String packageName, ComponentName component, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.component = component;
        this.icon = icon;
    }
}
