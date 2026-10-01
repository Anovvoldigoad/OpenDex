package com.opendex.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Original blue folded-ribbon wallpaper inspired by modern desktop design. */
public final class WallpaperView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public WallpaperView(Context context) { super(context); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        paint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xff07111f, 0xff0b2a54, 0xff10233b}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new LinearGradient(w * .15f, h * .15f, w * .85f, h * .85f,
                new int[]{0xff44c7ff, 0xff0d77e8, 0xff173e8f}, null, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(w * .18f, h * .08f);
        path.cubicTo(w * .58f, h * .18f, w * .45f, h * .50f, w * .86f, h * .44f);
        path.cubicTo(w * .70f, h * .62f, w * .54f, h * .83f, w * .24f, h * .92f);
        path.cubicTo(w * .37f, h * .65f, w * .31f, h * .35f, w * .18f, h * .08f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setShader(new LinearGradient(w * .2f, h * .15f, w * .75f, h * .7f,
                new int[]{0x9955d7ff, 0x552c85ff, 0x0011396e}, null, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(w * .38f, h * .20f);
        path.cubicTo(w * .67f, h * .30f, w * .60f, h * .50f, w * .82f, h * .56f);
        path.cubicTo(w * .62f, h * .68f, w * .45f, h * .72f, w * .29f, h * .79f);
        path.cubicTo(w * .43f, h * .55f, w * .49f, h * .35f, w * .38f, h * .20f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }
}
