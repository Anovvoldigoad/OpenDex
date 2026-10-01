package com.opendex.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import com.opendex.launcher.util.RecentStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AppRepository {
    private final Context context;

    public AppRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<AppEntry> loadLaunchableApps() {
        List<AppEntry> result = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
            for (ResolveInfo info : resolved) {
                try {
                    if (info == null || info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    String cls = info.activityInfo.name;
                    if (pkg == null || cls == null || pkg.equals(context.getPackageName())) continue;
                    CharSequence labelCs;
                    try { labelCs = info.loadLabel(pm); } catch (Throwable ignored) { labelCs = pkg; }
                    String label = labelCs == null ? pkg : labelCs.toString();
                    Drawable icon;
                    try { icon = info.loadIcon(pm); } catch (Throwable ignored) { icon = pm.getDefaultActivityIcon(); }
                    result.add(new AppEntry(label, pkg, new ComponentName(pkg, cls), icon));
                } catch (Throwable ignored) {
                    // One broken package must never crash the launcher process.
                }
            }
            try {
                Collator collator = Collator.getInstance(Locale.getDefault());
                result.sort(Comparator.comparing(a -> a.label, collator));
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            // Return an empty desktop instead of killing HOME.
        }
        return result;
    }

    public List<AppEntry> choosePinned(List<AppEntry> all, int max) {
        List<AppEntry> out = new ArrayList<>();
        String[] preferred = {"chrome", "youtube", "files", "drive", "spotify", "whatsapp", "telegram"};
        for (String needle : preferred) {
            for (AppEntry app : all) {
                if (containsIgnoreCase(app.packageName, needle) || containsIgnoreCase(app.label, needle)) {
                    if (!containsPackage(out, app.packageName)) out.add(app);
                    break;
                }
            }
            if (out.size() >= max) return out;
        }
        for (AppEntry app : all) {
            if (!containsPackage(out, app.packageName)) out.add(app);
            if (out.size() >= max) break;
        }
        return out;
    }

    public List<AppEntry> resolveRecents(List<AppEntry> all) {
        Map<String, AppEntry> byPkg = new HashMap<>();
        for (AppEntry app : all) byPkg.put(app.packageName, app);
        List<AppEntry> out = new ArrayList<>();
        for (String pkg : RecentStore.get(context)) {
            AppEntry app = byPkg.get(pkg);
            if (app != null) out.add(app);
        }
        return out;
    }

    public AppEntry findByPackage(List<AppEntry> all, String pkg) {
        for (AppEntry app : all) if (app.packageName.equals(pkg)) return app;
        return null;
    }

    private static boolean containsPackage(List<AppEntry> list, String pkg) {
        for (AppEntry app : list) if (app.packageName.equals(pkg)) return true;
        return false;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
