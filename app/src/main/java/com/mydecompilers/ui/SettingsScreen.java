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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Typeface;

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
    private final ScrollView root;
    private final TextView storageInfo;
    private final EditText chunkEdit;

    private final boolean dark;
    private final int foreground, secondary, surface, background, outline;
    private LinearLayout content;

    public SettingsScreen(MainActivity a) {
        activity = a;
        prefs = new UserPreferences(a);
        dark = UserPreferences.THEME_DARK.equals(prefs.getThemeMode()) ||
                (UserPreferences.THEME_SYSTEM.equals(prefs.getThemeMode()) &&
                (a.getResources().getConfiguration().uiMode & 48) == 32);
        foreground = dark ? 0xfff0f4fa : 0xff202d43;
        secondary = dark ? 0xffaab8cb : 0xff68788e;
        surface = dark ? 0xff243144 : Color.WHITE;
        background = dark ? 0xff151e2b : 0xfff4f7fb;
        outline = dark ? 0xff39485e : 0xffe2e8f1;
        root = new ScrollView(a);
        root.setFillViewport(true);
        root.setBackgroundColor(background);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(22), dp(18), dp(32));
        root.addView(content, new ViewGroup.LayoutParams(-1, -2));

        TextView title = text("Settings", 27, foreground, true);
        content.addView(title);
        TextView subtitle = text("Personalize your workspace and decompilation options", 13, secondary, false);
        subtitle.setPadding(0, dp(5), 0, dp(12));
        content.addView(subtitle);

        section("APPEARANCE", "Display preferences");
        LinearLayout appearance = card();
        appearance.addView(text("App theme", 16, foreground, true));
        appearance.addView(description("Choose how the interface looks"));
        final Spinner theme = new Spinner(a);
        final String[] values = {UserPreferences.THEME_SYSTEM, UserPreferences.THEME_LIGHT, UserPreferences.THEME_DARK};
        theme.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Follow system", "Light", "Dark"}));
        int selected = 0;
        for (int i=0;i<values.length;i++) if (values[i].equals(prefs.getThemeMode())) selected=i;
        theme.setSelection(selected);
        theme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) { first=false; return; }
                if (!values[position].equals(prefs.getThemeMode())) {
                    prefs.setThemeMode(values[position]); activity.recreate();
                }
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        appearance.addView(theme);

        section("DECOMPILATION", "Control processing and output");
        LinearLayout options = card();
        options.addView(switchRow("Ignore library packages", "Skip common third-party libraries to speed up decompilation", prefs.getIgnoreLibraries(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setIgnoreLibraries(v); } }));
        LinearLayout ignoreButtons = actionRow();
        Button editIgnore = button("Edit ignore list");
        editIgnore.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editIgnoreList(); } });
        Button resetIgnore = button("Reset list");
        resetIgnore.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            prefs.setCustomIgnoredList(""); Toast.makeText(activity, "Ignore list reset to default", Toast.LENGTH_SHORT).show();
        } });
        addAction(ignoreButtons, editIgnore); addAction(ignoreButtons, resetIgnore);
        options.addView(ignoreButtons);
        options.addView(separator());
        options.addView(switchRow("Keep intermediate files", "Retain generated .dex and .jar files", prefs.getKeepIntermediateFiles(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setKeepIntermediateFiles(v); } }));
        options.addView(separator());
        options.addView(switchRow("Keep original resource paths", "Preserve APK paths to avoid resource name collisions", prefs.getKeepResourcePaths(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setKeepResourcePaths(v); } }));
        options.addView(separator());
        options.addView(switchRow("Show live memory usage", "Display heap usage during decompilation", prefs.getShowMemoryUsage(),
                new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { prefs.setShowMemoryUsage(v); } }));

        section("PERFORMANCE", "DEX processing");
        LinearLayout performance = card();
        performance.addView(text("Classes per DEX chunk", 16, foreground, true));
        performance.addView(description("Must be greater than 500 classes"));
        LinearLayout chunkRow = actionRow();
        chunkEdit = new EditText(a);
        chunkEdit.setSingleLine(true);
        chunkEdit.setTextColor(foreground);
        chunkEdit.setTextSize(15);
        chunkEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        chunkEdit.setText(String.valueOf(prefs.getChunkSize()));
        chunkEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
        chunkEdit.setBackground(UiStyle.outlined(a, background, outline, 10));
        chunkRow.addView(chunkEdit, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button apply = button("Apply");
        apply.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try {
                int n = Integer.parseInt(chunkEdit.getText().toString().trim());
                if (n <= 500) { Toast.makeText(activity, "Use a value above 500", Toast.LENGTH_SHORT).show(); return; }
                prefs.setChunkSize(n); Toast.makeText(activity, "Saved", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) { Toast.makeText(activity, "Not a number", Toast.LENGTH_SHORT).show(); }
        } });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(92), dp(44)); ap.leftMargin=dp(10);
        chunkRow.addView(apply, ap);
        performance.addView(chunkRow);

        section("STORAGE", "Project output location");
        LinearLayout storage = card();
        storage.addView(text("Working directory", 16, foreground, true));
        storageInfo = description("");
        storageInfo.setTextIsSelectable(true);
        storageInfo.setPadding(0, dp(9), 0, dp(10));
        storage.addView(storageInfo);
        LinearLayout dirs = actionRow();
        Button choose = button("Choose folder");
        choose.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { activity.pickFolder(); } });
        Button reset = button("Use Downloads");
        reset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            prefs.setWorkingDirectoryPath(""); refreshStorageInfo();
        } });
        addAction(dirs, choose); addAction(dirs, reset);
        storage.addView(dirs);

        section("ENGINES", "Available decompilers");
        LinearLayout engines = card();
        int count = 0;
        for (DecompilerEngine e : DecompilerRegistry.getAllEngines()) {
            if (count++ > 0) engines.addView(separator());
            engines.addView(text(e.getName() + "  ·  " + e.getVersion(), 15, foreground, true));
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

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private TextView description(String value) {
        TextView t = text(value, 13, secondary, false); t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, dp(4), 0, dp(5)); return t;
    }
    private void section(String title, String subtitle) {
        TextView h = text(title, 12, UiStyle.BLUE, true);
        h.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(20); content.addView(h,hp);
        TextView d=description(subtitle); d.setPadding(0,dp(2),0,dp(9)); content.addView(d);
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(activity); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16),dp(16),dp(16),dp(16));
        c.setBackground(UiStyle.outlined(activity,surface,outline,16));
        content.addView(c,new LinearLayout.LayoutParams(-1,-2)); return c;
    }
    private View separator() {
        View v = new View(activity); v.setBackgroundColor(outline);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(1));
        p.topMargin=dp(13); p.bottomMargin=dp(13); v.setLayoutParams(p); return v;
    }
    private LinearLayout actionRow() {
        LinearLayout r=new LinearLayout(activity); r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL); return r;
    }
    private void addAction(LinearLayout row, Button button) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);
        if(row.getChildCount()>0) p.leftMargin=dp(8); row.addView(button,p);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.getResources().getDisplayMetrics());
    }

    private Button button(String label) {
        Button b=new Button(activity); b.setText(label); b.setAllCaps(false); b.setTextSize(12);
        b.setTextColor(Color.WHITE); b.setPadding(dp(6),0,dp(6),0);
        b.setBackground(UiStyle.shape(activity,UiStyle.BLUE,10)); return b;
    }
    private View switchRow(String title, String desc, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row=new LinearLayout(activity); row.setOrientation(LinearLayout.VERTICAL);
        Switch sw=new Switch(activity); sw.setText(title); sw.setTextSize(15); sw.setTextColor(foreground);
        sw.setChecked(checked); sw.setOnCheckedChangeListener(listener);
        row.addView(sw); row.addView(description(desc)); return row;
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
