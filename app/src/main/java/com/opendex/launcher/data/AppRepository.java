package com.opendex.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

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
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        List<AppEntry> result = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (pkg == null || cls == null || pkg.equals(context.getPackageName())) continue;
            CharSequence labelCs = info.loadLabel(pm);
            String label = labelCs == null ? pkg : labelCs.toString();
            result.add(new AppEntry(label, pkg, new ComponentName(pkg, cls), info.loadIcon(pm)));
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort(Comparator.comparing(a -> a.label, collator));
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
