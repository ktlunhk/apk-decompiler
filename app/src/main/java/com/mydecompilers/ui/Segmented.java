/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pill-style segmented control (replaces drop-down spinners for 2-3 choices). */
public final class Segmented extends LinearLayout {

    public interface Listener {
        void onSelected(int index);
    }

    private final UiStyle.Palette p;
    private final TextView[] segments;
    private int selected;
    private Listener listener;

    public Segmented(Context c, String[] labels, int selected) {
        super(c);
        p = UiStyle.palette(c);
        setOrientation(HORIZONTAL);
        setPadding(UiStyle.dp(c, 3), UiStyle.dp(c, 3), UiStyle.dp(c, 3), UiStyle.dp(c, 3));
        setBackground(UiStyle.outlined(c, p.field, p.outline, UiStyle.CONTROL_RADIUS));
        segments = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setTextSize(14);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setClickable(true);
            t.setFocusable(true);
            t.setMinHeight(UiStyle.dp(c, 40));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (index != Segmented.this.selected) {
                        select(index);
                        if (listener != null) listener.onSelected(index);
                    }
                }
            });
            segments[i] = t;
            addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        select(selected);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int getSelectedIndex() {
        return selected;
    }

    /** Changes the highlighted segment without notifying the listener. */
    public void select(int index) {
        selected = index;
        Context c = getContext();
        for (int i = 0; i < segments.length; i++) {
            boolean on = i == index;
            segments[i].setTextColor(on ? p.onPrimary : p.textSecondary);
            segments[i].setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            segments[i].setBackground(on
                    ? UiStyle.pressable(c, 0x33ffffff, p.primary, 0, UiStyle.CONTROL_RADIUS - 3)
                    : UiStyle.pressable(c, p.ripple, 0x00000000, 0, UiStyle.CONTROL_RADIUS - 3));
        }
    }
}
