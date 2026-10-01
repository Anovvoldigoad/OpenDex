package com.opendex.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable solid(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable stroke(int fill, int stroke, int strokePx, float radiusPx) {
        GradientDrawable d = solid(fill, radiusPx);
        d.setStroke(strokePx, stroke);
        return d;
    }

    public static TextView text(Context c, String value, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    public static TextView button(Context c, String value) {
        TextView t = text(c, value, 14, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        t.setBackground(solid(0x553a4456, dp(c, 8)));
        t.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }
}
