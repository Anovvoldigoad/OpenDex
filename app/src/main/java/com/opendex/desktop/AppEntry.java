package com.opendex.desktop;

import android.graphics.drawable.Drawable;

public final class AppEntry {
    public final String label;
    public final String packageName;
    public final String componentName;
    public final Drawable icon;

    public AppEntry(String label, String packageName, String componentName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.componentName = componentName;
        this.icon = icon;
    }
}
