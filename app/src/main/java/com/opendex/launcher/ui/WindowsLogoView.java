package com.opendex.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class WindowsLogoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public WindowsLogoView(Context context) { super(context); setClickable(true); setFocusable(true); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        p.setColor(0xffeef6ff);
        float w=getWidth(), h=getHeight(), gap=Math.max(2f,w*.06f);
        float s=Math.min(w,h)*.26f, cx=w/2f, cy=h/2f;
        c.drawRect(cx-s-gap/2, cy-s-gap/2, cx-gap/2, cy-gap/2, p);
        c.drawRect(cx+gap/2, cy-s-gap/2, cx+s+gap/2, cy-gap/2, p);
        c.drawRect(cx-s-gap/2, cy+gap/2, cx-gap/2, cy+s+gap/2, p);
        c.drawRect(cx+gap/2, cy+gap/2, cx+s+gap/2, cy+s+gap/2, p);
    }
}
