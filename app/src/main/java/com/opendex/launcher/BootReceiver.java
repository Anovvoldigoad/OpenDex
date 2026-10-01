package com.opendex.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.opendex.launcher.util.Prefs;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Prefs.overlayEnabled(context) || !Settings.canDrawOverlays(context)) return;
        try {
            Intent service = new Intent(context, TaskbarOverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
        } catch (Throwable ignored) {}
    }
}
