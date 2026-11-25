/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mydecompilers.utils.UserPreferences;

/**
 * Small native-Android design system; no AndroidX dependencies.
 *
 * Every screen takes its colours from {@link Palette} and builds its controls
 * through the helpers below, so spacing, radii, type sizes and states stay the
 * same everywhere. Change a token here and the whole app follows.
 */
public final class UiStyle {

    // Kept for older call sites.
    public static final int BLUE = Color.rgb(49, 105, 231);
    public static final int INK = Color.rgb(31, 42, 64);
    public static final int MUTED = Color.rgb(102, 116, 137);
    public static final int LIGHT = Color.rgb(245, 247, 251);

    // Spacing / shape tokens (dp)
    public static final int GUTTER = 16;      // screen side padding
    public static final int CARD_RADIUS = 14;
    public static final int CONTROL_RADIUS = 12;
    public static final int CONTROL_HEIGHT = 48;

    // Button styles
    public static final int BTN_PRIMARY = 0;  // filled brand colour
    public static final int BTN_TONAL = 1;    // soft brand colour
    public static final int BTN_OUTLINE = 2;  // neutral, outlined
    public static final int BTN_DANGER = 3;   // filled red

    private UiStyle() { }

    // ------------------------------------------------------------ palette

    /** Colour tokens for one mode. */
    public static final class Palette {
        public final boolean dark;
        public final int bg, surface, field, outline;
        public final int text, textSecondary;
        public final int primary, onPrimary, accent, accentSoft;
        public final int success, successSoft, warning, warningSoft, danger, dangerSoft;
        public final int code, ripple;

        Palette(boolean dark) {
            this.dark = dark;
            if (dark) {
                bg = 0xff121820; surface = 0xff1c2531; field = 0xff232e3d; outline = 0xff33415a;
                text = 0xfff0f4fa; textSecondary = 0xffaab6c7;
                primary = 0xff3169e7; onPrimary = 0xffffffff; accent = 0xff7fa6ff; accentSoft = 0xff23314f;
                success = 0xff5fd08a; successSoft = 0xff1b3a2a;
                warning = 0xfff2b24c; warningSoft = 0xff3e3015;
                danger = 0xffff7b86; dangerSoft = 0xff4a2328;
                code = 0xff0e131a; ripple = 0x33ffffff;
            } else {
                bg = 0xfff5f7fb; surface = 0xffffffff; field = 0xffffffff; outline = 0xffdce3ed;
                text = 0xff1f2a40; textSecondary = 0xff667489;
                primary = 0xff3169e7; onPrimary = 0xffffffff; accent = 0xff2a5bd7; accentSoft = 0xffe8effd;
                success = 0xff1b8a4b; successSoft = 0xffe3f5ea;
                warning = 0xff8f5400; warningSoft = 0xfffff1d6;
                danger = 0xffc62f3b; dangerSoft = 0xfffde7e9;
                code = 0xfff0f3f8; ripple = 0x223169e7;
            }
        }
    }

    private static final Palette LIGHT_PALETTE = new Palette(false);
    private static final Palette DARK_PALETTE = new Palette(true);

    public static boolean isDark(Context c) {
        String mode = new UserPreferences(c).getThemeMode();
        if (UserPreferences.THEME_DARK.equals(mode)) return true;
        if (UserPreferences.THEME_LIGHT.equals(mode)) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static Palette palette(Context c) {
        return isDark(c) ? DARK_PALETTE : LIGHT_PALETTE;
    }

    // ------------------------------------------------------------ basics

    public static int dp(Context c, int n) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    public static GradientDrawable outlined(Context c, int color, int stroke, int radius) {
        GradientDrawable d = shape(c, color, radius);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    /** Rounded background with a touch ripple clipped to the same corners. */
    public static Drawable pressable(Context c, int rippleColor, int fill, int stroke, int radius) {
        GradientDrawable content = stroke == 0 ? shape(c, fill, radius) : outlined(c, fill, stroke, radius);
        GradientDrawable mask = shape(c, Color.WHITE, radius);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    /** Card background: surface fill, hairline outline, ripple. */
    public static Drawable cardBackground(Context c, Palette p) {
        return pressable(c, p.ripple, p.surface, p.outline, CARD_RADIUS);
    }

    public static void label(TextView v, int size, int color, boolean bold) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    public static void padded(View v, Context c, int h, int vertical) {
        v.setPadding(dp(c, h), dp(c, vertical), dp(c, h), dp(c, vertical));
    }

    public static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        label(t, sp, color, bold);
        return t;
    }

    public static LinearLayout.LayoutParams lp(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    // ------------------------------------------------------------ icons

    public static Drawable tinted(Context c, int res, int color) {
        Drawable d = c.getResources().getDrawable(res, c.getTheme()).mutate();
        d.setTint(color);
        return d;
    }

    public static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(tinted(c, res, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    // ------------------------------------------------------------ text blocks

    /** Large screen title with a muted subtitle, used at the top of every tab. */
    public static LinearLayout header(Context c, Palette p, String title, String subtitle) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, title, 24, p.text, true));
        if (subtitle != null) {
            TextView s = text(c, subtitle, 13, p.textSecondary, false);
            s.setPadding(0, dp(c, 2), 0, 0);
            col.addView(s);
        }
        col.setPadding(0, 0, 0, dp(c, 16));
        return col;
    }

    /** Small uppercase section label. */
    public static TextView sectionLabel(Context c, Palette p, String s) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.US), 12, p.accent, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    public static TextView chip(Context c, String s, int fg, int bg) {
        TextView t = text(c, s, 11, fg, true);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        t.setBackground(shape(c, bg, 8));
        return t;
    }

    public static LinearLayout card(Context c, Palette p) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(outlined(c, p.surface, p.outline, CARD_RADIUS + 2));
        return l;
    }

    public static View divider(Context c, Palette p) {
        View v = new View(c);
        v.setBackgroundColor(p.outline);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    /** Centered icon + title + hint, shown in place of an empty list. */
    public static LinearLayout emptyState(Context c, Palette p, int iconRes, String title, String message) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(c, 32), dp(c, 48), dp(c, 32), 0);
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(shape(c, p.accentSoft, 28));
        ImageView ic = icon(c, iconRes, p.accent, 28);
        tile.addView(ic, new FrameLayout.LayoutParams(dp(c, 28), dp(c, 28), Gravity.CENTER));
        col.addView(tile, new LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)));
        TextView t = text(c, title, 16, p.text, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t, lp(-1, -2, 0, dp(c, 14), 0, 0));
        if (message != null) {
            TextView m = text(c, message, 13, p.textSecondary, false);
            m.setGravity(Gravity.CENTER);
            col.addView(m, lp(-1, -2, 0, dp(c, 4), 0, 0));
        }
        return col;
    }

    // ------------------------------------------------------------ inputs

    /** Rounded text field with an optional leading icon. Use {@link #inputOf} to reach the EditText. */
    public static LinearLayout inputBox(Context c, Palette p, int iconRes, String hint) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        box.setMinimumHeight(dp(c, CONTROL_HEIGHT));
        box.setBackground(outlined(c, p.field, p.outline, CONTROL_RADIUS));
        if (iconRes != 0) {
            box.addView(icon(c, iconRes, p.textSecondary, 20), lp(dp(c, 20), dp(c, 20), 0, 0, dp(c, 10), 0));
        }
        EditText e = new EditText(c);
        e.setBackground(null);
        e.setHint(hint);
        e.setHintTextColor(p.textSecondary);
        e.setTextColor(p.text);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setSingleLine(true);
        e.setPadding(0, dp(c, 10), 0, dp(c, 10));
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.setTag(e);
        return box;
    }

    public static EditText inputOf(LinearLayout box) {
        return (EditText) box.getTag();
    }

    // ------------------------------------------------------------ progress

    /** Thin rounded determinate bar in brand colours. */
    public static void styleProgress(Context c, Palette p, ProgressBar bar) {
        GradientDrawable track = shape(c, p.outline, 4);
        GradientDrawable fill = shape(c, p.primary, 4);
        ClipDrawable clip = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[] { track, clip });
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        bar.setProgressDrawable(layers);
    }

    // ------------------------------------------------------------ buttons

    /** Button with optional leading icon, centred as a group. */
    public static final class Btn extends LinearLayout {
        private final TextView label;
        private final ImageView icon;

        Btn(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            setMinimumHeight(dp(c, CONTROL_HEIGHT));
            setPadding(dp(c, 14), 0, dp(c, 14), 0);
            icon = new ImageView(c);
            addView(icon, new LayoutParams(dp(c, 18), dp(c, 18)));
            label = new TextView(c);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setGravity(Gravity.CENTER);
            addView(label, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        public void setText(CharSequence t) {
            label.setText(t);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setAlpha(enabled ? 1f : 0.45f);
        }
    }

    public static Btn button(Context c, String text, int style) {
        return button(c, text, style, 0);
    }

    public static Btn button(Context c, String text, int style, int iconRes) {
        Palette p = palette(c);
        Btn b = new Btn(c);
        int fg, fill, stroke, ripple;
        switch (style) {
            case BTN_TONAL:
                fg = p.accent; fill = p.accentSoft; stroke = 0; ripple = p.ripple;
                break;
            case BTN_OUTLINE:
                fg = p.text; fill = p.surface; stroke = p.outline; ripple = p.ripple;
                break;
            case BTN_DANGER:
                fg = Color.WHITE; fill = p.dark ? 0xffd04450 : 0xffc62f3b; stroke = 0; ripple = 0x33ffffff;
                break;
            default:
                fg = p.onPrimary; fill = p.primary; stroke = 0; ripple = 0x33ffffff;
                break;
        }
        b.label.setText(text);
        b.label.setTextColor(fg);
        b.setBackground(pressable(c, ripple, fill, stroke, CONTROL_RADIUS));
        if (iconRes != 0) {
            b.icon.setImageDrawable(tinted(c, iconRes, fg));
            ((LinearLayout.LayoutParams) b.label.getLayoutParams()).leftMargin = dp(c, 8);
        } else {
            b.icon.setVisibility(View.GONE);
        }
        return b;
    }

    /** Square icon-only button (refresh, prev/next, ...). */
    public static FrameLayout iconButton(Context c, Palette p, int iconRes, String description) {
        FrameLayout f = new FrameLayout(c);
        f.setClickable(true);
        f.setFocusable(true);
        f.setContentDescription(description);
        f.setBackground(pressable(c, p.ripple, p.surface, p.outline, CONTROL_RADIUS));
        ImageView ic = icon(c, iconRes, p.accent, 22);
        f.addView(ic, new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER));
        return f;
    }
}
