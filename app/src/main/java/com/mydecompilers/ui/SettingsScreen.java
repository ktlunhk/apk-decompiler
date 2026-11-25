/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mydecompilers.R;
import com.mydecompilers.engines.DecompilerEngine;
import com.mydecompilers.engines.DecompilerRegistry;
import com.mydecompilers.utils.Storage;
import com.mydecompilers.utils.UserPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/** App preferences. */
public class SettingsScreen {

    private final MainActivity activity;
    private final UserPreferences prefs;
    private final UiStyle.Palette p;
    private final ScrollView root;
    private final TextView storageInfo;
    private final EditText chunkEdit;
    private final LinearLayout content;

    public SettingsScreen(MainActivity a) {
        activity = a;
        prefs = new UserPreferences(a);
        p = UiStyle.palette(a);
        root = new ScrollView(a);
        root.setFillViewport(true);
        root.setBackgroundColor(p.bg);
        root.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(UiStyle.GUTTER), dp(16), dp(UiStyle.GUTTER), dp(24));
        root.addView(content, new ViewGroup.LayoutParams(-1, -2));

        content.addView(UiStyle.header(a, p, "Settings", "Personalize your workspace and decompilation options"));

        // ---------------------------------------------------------- appearance
        section("Appearance", "Display preferences", false);
        LinearLayout appearance = card();
        appearance.addView(title("App theme"));
        appearance.addView(description("Choose how the interface looks"));
        final String[] values = {UserPreferences.THEME_SYSTEM, UserPreferences.THEME_LIGHT, UserPreferences.THEME_DARK};
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(prefs.getThemeMode())) selected = i;
        Segmented theme = new Segmented(a, new String[] {"System", "Light", "Dark"}, selected);
        theme.setListener(new Segmented.Listener() {
            public void onSelected(int index) {
                if (!values[index].equals(prefs.getThemeMode())) {
                    prefs.setThemeMode(values[index]);
                    activity.recreate();
                }
            }
        });
        appearance.addView(theme, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));

        // ---------------------------------------------------------- decompilation
        section("Decompilation", "Control processing and output", true);
        LinearLayout options = card();
        options.addView(switchRow("Ignore library packages", "Skip common third-party libraries to speed up decompilation", prefs.getIgnoreLibraries(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setIgnoreLibraries(v); } }));
        LinearLayout ignoreButtons = actionRow();
        View editIgnore = UiStyle.button(a, "Edit ignore list", UiStyle.BTN_TONAL);
        editIgnore.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editIgnoreList(); } });
        View resetIgnore = UiStyle.button(a, "Reset list", UiStyle.BTN_OUTLINE);
        resetIgnore.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            prefs.setCustomIgnoredList(""); Toast.makeText(activity, "Ignore list reset to default", Toast.LENGTH_SHORT).show();
        } });
        addAction(ignoreButtons, editIgnore);
        addAction(ignoreButtons, resetIgnore);
        options.addView(ignoreButtons, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));
        options.addView(separator());
        options.addView(switchRow("Keep intermediate files", "Retain generated .dex and .jar files", prefs.getKeepIntermediateFiles(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setKeepIntermediateFiles(v); } }));
        options.addView(separator());
        options.addView(switchRow("Keep original resource paths", "Preserve APK paths to avoid resource name collisions", prefs.getKeepResourcePaths(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setKeepResourcePaths(v); } }));
        options.addView(separator());
        options.addView(switchRow("Show live memory usage", "Display heap usage during decompilation", prefs.getShowMemoryUsage(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setShowMemoryUsage(v); } }));

        // ---------------------------------------------------------- performance
        section("Performance", "DEX processing", true);
        LinearLayout performance = card();
        performance.addView(title("Classes per DEX chunk"));
        performance.addView(description("Must be greater than 500 classes"));
        LinearLayout chunkRow = actionRow();
        LinearLayout chunkBox = UiStyle.inputBox(a, p, 0, "Classes per chunk");
        chunkEdit = UiStyle.inputOf(chunkBox);
        chunkEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        chunkEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        chunkEdit.setText(String.valueOf(prefs.getChunkSize()));
        chunkRow.addView(chunkBox, new LinearLayout.LayoutParams(0, dp(UiStyle.CONTROL_HEIGHT), 1));
        View apply = UiStyle.button(a, "Apply", UiStyle.BTN_PRIMARY);
        apply.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try {
                int n = Integer.parseInt(chunkEdit.getText().toString().trim());
                if (n <= 500) { Toast.makeText(activity, "Use a value above 500", Toast.LENGTH_SHORT).show(); return; }
                prefs.setChunkSize(n); Toast.makeText(activity, "Saved", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) { Toast.makeText(activity, "Not a number", Toast.LENGTH_SHORT).show(); }
        } });
        chunkRow.addView(apply, UiStyle.lp(dp(96), dp(UiStyle.CONTROL_HEIGHT), dp(10), 0, 0, 0));
        performance.addView(chunkRow, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));

        // ---------------------------------------------------------- storage
        section("Storage", "Project output location", true);
        LinearLayout storage = card();
        storage.addView(title("Working directory"));
        storageInfo = description("");
        storageInfo.setTextIsSelectable(true);
        storageInfo.setPadding(dp(12), dp(10), dp(12), dp(10));
        storageInfo.setBackground(UiStyle.shape(a, p.code, UiStyle.CONTROL_RADIUS));
        storage.addView(storageInfo, UiStyle.lp(-1, -2, 0, dp(8), 0, 0));
        LinearLayout dirs = actionRow();
        View choose = UiStyle.button(a, "Choose folder", UiStyle.BTN_TONAL, R.drawable.ic_folder_open);
        choose.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { activity.pickFolder(); } });
        View reset = UiStyle.button(a, "Use Downloads", UiStyle.BTN_OUTLINE);
        reset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            prefs.setWorkingDirectoryPath(""); refreshStorageInfo();
        } });
        addAction(dirs, choose);
        addAction(dirs, reset);
        storage.addView(dirs, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));

        // ---------------------------------------------------------- engines
        section("Engines", "Available decompilers", true);
        LinearLayout engines = card();
        int count = 0;
        for (DecompilerEngine e : DecompilerRegistry.getAllEngines()) {
            if (count++ > 0) engines.addView(separator());
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(title(e.getName()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(UiStyle.chip(a, e.getVersion(), p.accent, p.accentSoft));
            engines.addView(line);
            engines.addView(description(e.getDescription()));
        }
        /*
        section("ABOUT", "Application information");
        LinearLayout about = card();
        about.addView(text("myAPK Decompiler", 17, foreground, true));
        about.addView(description("Java / AIDE port of the open-source project by Ameer Muawiya."));
        about.addView(description("Licensed under GNU General Public License v3.0."));
        */
        refreshStorageInfo();
    }

    // ------------------------------------------------------------ building blocks

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.getResources().getDisplayMetrics());
    }

    private TextView title(String value) {
        return UiStyle.text(activity, value, 15, p.text, true);
    }

    private TextView description(String value) {
        TextView t = UiStyle.text(activity, value, 13, p.textSecondary, false);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, dp(3), 0, 0);
        return t;
    }

    private void section(String title, String subtitle, boolean spaceAbove) {
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = spaceAbove ? dp(24) : 0;
        content.addView(UiStyle.sectionLabel(activity, p, title), hp);
        TextView d = description(subtitle);
        d.setPadding(0, dp(2), 0, dp(10));
        content.addView(d);
    }

    private LinearLayout card() {
        LinearLayout c = UiStyle.card(activity, p);
        content.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private View separator() {
        View v = UiStyle.divider(activity, p);
        ((LinearLayout.LayoutParams) v.getLayoutParams()).setMargins(0, dp(14), 0, dp(14));
        return v;
    }

    private LinearLayout actionRow() {
        LinearLayout r = new LinearLayout(activity);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private void addAction(LinearLayout row, View button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (row.getChildCount() > 0) lp.leftMargin = dp(8);
        row.addView(button, lp);
    }

    /** Title + description on the left, switch on the right; the whole row toggles. */
    private View switchRow(String title, String desc, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(title(title));
        text.addView(description(desc));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(activity);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw, UiStyle.lp(-2, -2, dp(12), 0, 0, 0));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sw.toggle();
            }
        });
        return row;
    }

    private void refreshStorageInfo() {
        File eff = Storage.appStorage(activity);
        boolean custom = prefs.getWorkingDirectoryPath().trim().length() > 0;
        String note = "";
        if (!custom && !eff.equals(Storage.defaultDownloadDir())) {
            note = "\n\nStorage permission is not granted, so the app-private folder is used. Grant it to use the Download folder.";
        }
        storageInfo.setText("Projects are stored in:\n" + eff.getAbsolutePath() + note);
    }

    private void editIgnoreList() {
        String current = prefs.getCustomIgnoredList();
        if (current.trim().length() == 0) current = readBundledIgnoreList();
        final EditText et = new EditText(activity);
        et.setText(current);
        et.setSingleLine(false);
        et.setMinLines(10);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setTypeface(android.graphics.Typeface.MONOSPACE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        et.setTextColor(p.text);
        new AlertDialog.Builder(activity)
                .setTitle("Ignored library packages")
                .setMessage("One package prefix per line. Lines starting with # are comments.")
                .setView(et)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        prefs.setCustomIgnoredList(et.getText().toString());
                    }
                }).show();
    }

    private String readBundledIgnoreList() {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(activity.getAssets().open("ignored.list"), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public View getView() {
        return root;
    }

    public void refresh() {
        refreshStorageInfo();
    }

    public void onShow() {
        refreshStorageInfo();
    }
}
