/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mydecompilers.R;

/** Applies the chosen light/dark theme and offers a few view-building helpers. */
public abstract class BaseActivity extends Activity {

    /** Colour tokens for the active mode; available from onCreate() on. */
    protected UiStyle.Palette p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(isDarkTheme() ? R.style.AppThemeDark : R.style.AppThemeLight);
        super.onCreate(savedInstanceState);
        p = UiStyle.palette(this);
        if (showUp() && getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    /** Detail screens show a back arrow in the action bar; the main screen does not. */
    protected boolean showUp() {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected boolean isDarkTheme() {
        return UiStyle.isDark(this);
    }

    protected int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    /** Primary-coloured text. */
    protected TextView text(String s, int sp, boolean bold) {
        return UiStyle.text(this, s, sp, p.text, bold);
    }

    /** Muted secondary text. */
    protected TextView caption(String s, int sp) {
        return UiStyle.text(this, s, sp, p.textSecondary, false);
    }

    protected LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    protected static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    protected void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    protected void setVisible(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
