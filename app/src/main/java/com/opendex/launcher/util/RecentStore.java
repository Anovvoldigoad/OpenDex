package com.opendex.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecentStore {
    private static final String FILE = "opendex_recent";
    private static final String KEY = "packages";
    private static final int MAX = 10;

    private RecentStore() {}

    public static synchronized void push(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        List<String> current = new ArrayList<>(get(context));
        current.remove(packageName);
        current.add(0, packageName);
        if (current.size() > MAX) current = new ArrayList<>(current.subList(0, MAX));
        context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY, String.join("\n", current)).apply();
    }

    public static List<String> get(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }
}
