package com.mydecompilers.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Small native-Android design system; no AndroidX dependencies. */
public final class UiStyle {
    public static final int BLUE = Color.rgb(49, 105, 231);
    public static final int INK = Color.rgb(31, 42, 64);
    public static final int MUTED = Color.rgb(113, 126, 148);
    public static final int LIGHT = Color.rgb(245, 247, 251);
    private UiStyle() { }
    public static int dp(Context c, int n) { return (int) TypedValue.applyDimension(1, n, c.getResources().getDisplayMetrics()); }
    public static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    public static GradientDrawable outlined(Context c, int color, int stroke, int radius) {
        GradientDrawable d = shape(c,color,radius); d.setStroke(dp(c,1),stroke); return d;
    }
    public static void label(TextView v, int size, int color, boolean bold) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP,size); v.setTextColor(color);
        if(bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    }
    public static void padded(View v, Context c, int h, int vertical) { v.setPadding(dp(c,h),dp(c,vertical),dp(c,h),dp(c,vertical)); }
}
