package com.opendex.launcher;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;

import com.opendex.launcher.ui.WindowsDesktopView;

public final class DesktopPresentation extends Presentation {
    private final ShizukuController shizuku;
    private WindowsDesktopView desktop;

    public DesktopPresentation(Context outerContext, Display display, ShizukuController shizuku) {
        super(outerContext, display);
        this.shizuku = shizuku;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        desktop = new WindowsDesktopView(getContext(), shizuku, getDisplay().getDisplayId());
        setContentView(desktop);
    }

    public void refresh() {
        if (desktop != null) { desktop.refreshStatus(); desktop.refreshApps(); }
    }
}
