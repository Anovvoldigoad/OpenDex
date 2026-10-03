package com.opendex.desktop;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppRepository {
    private AppRepository() {}

    @SuppressWarnings("deprecation")
    public static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        List<AppEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolved) {
            if (ri == null || ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            if (context.getPackageName().equals(pkg)) continue;
            ComponentName component = new ComponentName(pkg, cls);
            String flat = component.flattenToShortString();
            if (!seen.add(flat)) continue;
            CharSequence labelCs;
            Drawable icon;
            try { labelCs = ri.loadLabel(pm); } catch (Throwable t) { labelCs = pkg; }
            try { icon = ri.loadIcon(pm); } catch (Throwable t) { icon = context.getApplicationInfo().loadIcon(pm); }
            out.add(new AppEntry(labelCs == null ? pkg : labelCs.toString(), pkg, flat, icon));
        }
        Collections.sort(out, Comparator.comparing(a -> a.label.toLowerCase()));
        return out;
    }
}
