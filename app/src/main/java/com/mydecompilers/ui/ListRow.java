/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mydecompilers.R;

/**
 * The one list-row design used by Apps, History and the source explorer:
 * leading icon tile, title, optional subtitle, optional chip + meta text, chevron.
 */
public final class ListRow extends LinearLayout {

    public final ImageView icon;
    private final UiStyle.Palette p;
    private final TextView title, subtitle, chip, meta;
    private final LinearLayout metaRow;
    private final ImageView chevron;

    public ListRow(Context c) {
        super(c);
        p = UiStyle.palette(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(UiStyle.dp(c, 12), UiStyle.dp(c, 12), UiStyle.dp(c, 12), UiStyle.dp(c, 12));
        setBackground(UiStyle.cardBackground(c, p));
        setMinimumHeight(UiStyle.dp(c, 68));

        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, new LinearLayout.LayoutParams(UiStyle.dp(c, 44), UiStyle.dp(c, 44)));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setPadding(UiStyle.dp(c, 12), 0, UiStyle.dp(c, 4), 0);

        title = UiStyle.text(c, "", 15, p.text, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        col.addView(title);

        subtitle = UiStyle.text(c, "", 12, p.textSecondary, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(subtitle);

        metaRow = new LinearLayout(c);
        metaRow.setOrientation(HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        chip = UiStyle.chip(c, "", p.accent, p.accentSoft);
        metaRow.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        meta = UiStyle.text(c, "", 12, p.textSecondary, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        metaRow.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(metaRow, UiStyle.lp(-2, -2, 0, UiStyle.dp(c, 6), 0, 0));

        addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        chevron = UiStyle.icon(c, R.drawable.ic_chevron_right, p.textSecondary, 20);
        addView(chevron);
    }

    /** Coloured rounded tile with a glyph (folders, files, ...). */
    public void setTile(int iconRes, int fg, int bg) {
        int pad = UiStyle.dp(getContext(), 10);
        icon.setPadding(pad, pad, pad, pad);
        icon.setBackground(UiStyle.shape(getContext(), bg, 12));
        icon.setImageDrawable(UiStyle.tinted(getContext(), iconRes, fg));
    }

    /** Plain image (a real app icon is loaded into {@link #icon} afterwards). */
    public void useAppIcon() {
        icon.setPadding(0, 0, 0, 0);
        icon.setBackground(null);
    }

    public void setChevronVisible(boolean visible) {
        chevron.setVisibility(visible ? VISIBLE : GONE);
    }

    /** Null or empty values hide the matching part of the row. */
    public void bind(String titleText, String subtitleText, String chipText, String metaText) {
        title.setText(titleText);
        show(subtitle, subtitleText);
        boolean hasChip = chipText != null && chipText.length() > 0;
        boolean hasMeta = metaText != null && metaText.length() > 0;
        if (hasChip) chip.setText(chipText);
        chip.setVisibility(hasChip ? VISIBLE : GONE);
        meta.setText(hasMeta ? metaText : "");
        meta.setVisibility(hasMeta ? VISIBLE : GONE);
        ((LinearLayout.LayoutParams) meta.getLayoutParams()).leftMargin = hasChip ? UiStyle.dp(getContext(), 8) : 0;
        metaRow.setVisibility(hasChip || hasMeta ? VISIBLE : GONE);
    }

    private static void show(TextView v, String s) {
        boolean has = s != null && s.length() > 0;
        v.setText(has ? s : "");
        v.setVisibility(has ? View.VISIBLE : View.GONE);
    }
}
