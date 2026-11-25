/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Rounded "pick one" field that opens a themed single-choice dialog. */
public final class SelectField extends LinearLayout {

    public interface Listener {
        void onChanged(int index);
    }

    private final String[] options;
    private final String dialogTitle;
    private final TextView value;
    private int index;
    private Listener listener;

    public SelectField(Context c, String dialogTitle, String[] options, int selected) {
        super(c);
        UiStyle.Palette p = UiStyle.palette(c);
        this.options = options;
        this.dialogTitle = dialogTitle;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(true);
        setMinimumHeight(UiStyle.dp(c, UiStyle.CONTROL_HEIGHT));
        setPadding(UiStyle.dp(c, 14), 0, UiStyle.dp(c, 10), 0);
        setBackground(UiStyle.pressable(c, p.ripple, p.field, p.outline, UiStyle.CONTROL_RADIUS));

        value = new TextView(c);
        value.setTextSize(15);
        value.setTextColor(p.text);
        value.setSingleLine(true);
        addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addView(UiStyle.icon(c, com.mydecompilers.R.drawable.ic_expand_more, p.textSecondary, 22));

        setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(getContext())
                        .setTitle(SelectField.this.dialogTitle)
                        .setSingleChoiceItems(SelectField.this.options, index, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                if (which != index) {
                                    select(which);
                                    if (listener != null) listener.onChanged(which);
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        select(selected);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int getSelectedIndex() {
        return index;
    }

    /** Changes the shown option without notifying the listener. */
    public void select(int i) {
        if (i < 0 || i >= options.length) i = 0;
        index = i;
        value.setText(options[i]);
    }
}
