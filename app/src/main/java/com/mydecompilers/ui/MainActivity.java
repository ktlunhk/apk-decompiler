/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mydecompilers.Constants;
import com.mydecompilers.R;
import com.mydecompilers.data.PackageInfo;

/** Hosts the three top-level screens: Apps, History and Settings. */
public class MainActivity extends BaseActivity {

    private static final int REQ_PICK_FILE = 4001;
    private static final int REQ_PICK_FOLDER = 4002;
    private static final int REQ_STORAGE = 4003;

    private HomeScreen home;
    private HistoryScreen history;
    private SettingsScreen settings;
    private FrameLayout content;
    private int currentTab = 0;

    // bottom navigation
    private final int[] navIcons = { R.drawable.ic_apps, R.drawable.ic_history, R.drawable.ic_settings };
    private final String[] navLabels = { "Apps", "History", "Settings" };
    private final FrameLayout[] navPills = new FrameLayout[3];
    private final ImageView[] navImages = new ImageView[3];
    private final TextView[] navTexts = new TextView[3];

    @Override
    protected boolean showUp() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("myAPK Decompiler");
        String crash = com.mydecompilers.utils.CrashLog.read(this);
        if (crash != null) {
            showCrashReport(crash);
            return;
        }
        try {
            buildMain(savedInstanceState);
        } catch (Throwable t) {
            com.mydecompilers.utils.CrashLog.save(this, t);
            showCrashReport(com.mydecompilers.utils.CrashLog.read(this));
        }
    }

    private void showCrashReport(final String report) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(p.bg);
        col.setPadding(dp(UiStyle.GUTTER), dp(16), dp(UiStyle.GUTTER), dp(UiStyle.GUTTER));
        col.addView(UiStyle.header(this, p, "The app crashed last time",
                "Copy this report and send it so the problem can be fixed."), matchWrap());

        ScrollView sv = new ScrollView(this);
        sv.setBackground(UiStyle.outlined(this, p.code, p.outline, UiStyle.CARD_RADIUS));
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(11);
        tv.setTextColor(p.text);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        sv.addView(tv);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        View copy = UiStyle.button(this, "Copy report", UiStyle.BTN_OUTLINE, R.drawable.ic_copy);
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", report));
                toast("Copied");
            }
        });
        View cont = UiStyle.button(this, "Continue", UiStyle.BTN_PRIMARY);
        cont.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                com.mydecompilers.utils.CrashLog.clear(MainActivity.this);
                recreate();
            }
        });
        row.addView(copy, UiStyle.lp(0, -2, 0, 0, dp(6), 0));
        ((LinearLayout.LayoutParams) copy.getLayoutParams()).weight = 1f;
        row.addView(cont, UiStyle.lp(0, -2, dp(6), 0, 0, 0));
        ((LinearLayout.LayoutParams) cont.getLayoutParams()).weight = 1f;
        col.addView(row, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));
        setContentView(col);
    }

    private void buildMain(Bundle savedInstanceState) {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(UiStyle.divider(this, p));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(p.surface);
        for (int i = 0; i < 3; i++) bar.addView(navItem(i), weight(1f));
        root.addView(bar, matchWrap());

        setContentView(root);

        home = new HomeScreen(this);
        history = new HistoryScreen(this);
        settings = new SettingsScreen(this);

        requestStoragePermission();
        selectTab(savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : 0);
        handleIncoming(getIntent());
    }

    /** One bottom-navigation item: icon inside a pill that lights up when selected, plus a label. */
    private View navItem(final int index) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(0, dp(8), 0, dp(8));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(navLabels[index]);
        item.setBackground(UiStyle.pressable(this, p.ripple, 0x00000000, 0, 0));
        item.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(index);
            }
        });

        FrameLayout pill = new FrameLayout(this);
        ImageView img = new ImageView(this);
        pill.addView(img, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        item.addView(pill, new LinearLayout.LayoutParams(dp(60), dp(32)));

        TextView label = UiStyle.text(this, navLabels[index], 12, p.textSecondary, false);
        item.addView(label, UiStyle.lp(-2, -2, 0, dp(2), 0, 0));

        navPills[index] = pill;
        navImages[index] = img;
        navTexts[index] = label;
        return item;
    }

    private void selectTab(int index) {
        currentTab = index;
        for (int i = 0; i < 3; i++) {
            boolean on = i == index;
            int color = on ? p.accent : p.textSecondary;
            navPills[i].setBackground(on ? UiStyle.shape(this, p.accentSoft, 16) : null);
            navImages[i].setImageDrawable(UiStyle.tinted(this, navIcons[i], color));
            navTexts[i].setTextColor(on ? p.text : p.textSecondary);
            navTexts[i].setTypeface(android.graphics.Typeface.DEFAULT, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        content.removeAllViews();
        View v;
        if (index == 0) {
            v = home.getView();
            home.onShow();
        } else if (index == 1) {
            v = history.getView();
            history.onShow();
        } else {
            v = settings.getView();
            settings.onShow();
        }
        content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE }, REQ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (settings != null) settings.refresh();
            if (currentTab == 1 && history != null) history.onShow();
        }
    }

    /** Opens the system folder picker for the working directory. */
    public void pickFolder() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_PICK_FOLDER);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", currentTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTab == 1 && history != null) history.onShow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (home != null) handleIncoming(intent);
    }

    // ------------------------------------------------------------ file import

    /** Opens the system file picker. */
    public void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            java.io.File dir = com.mydecompilers.utils.Storage.treeUriToFile(data.getData());
            if (dir == null || !(dir.exists() || dir.mkdirs()) || !dir.canWrite()) {
                new AlertDialog.Builder(this)
                        .setTitle("Folder not usable")
                        .setMessage("This folder cannot be written by path. Pick a folder on internal storage or an SD card"
                                + " (and allow storage access when asked).")
                        .setPositiveButton(android.R.string.ok, null).show();
            } else {
                new com.mydecompilers.utils.UserPreferences(this).setWorkingDirectoryPath(dir.getAbsolutePath());
                if (settings != null) settings.refresh();
            }
            return;
        }
        if (requestCode == REQ_PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importUri(data.getData());
        }
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri != null) {
            intent.setAction(Intent.ACTION_MAIN); // handle once
            importUri(uri);
        }
    }

    private void importUri(final Uri uri) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Importing file...");
        dialog.setCancelable(false);
        dialog.show();
        new Thread(new Runnable() {
            public void run() {
                final PackageInfo info = PackageInfo.fromUri(MainActivity.this, uri);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {
                        }
                        if (info == null) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Unsupported file")
                                    .setMessage("Pick an .apk, .apks, .xapk, .dex or .jar file.")
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        } else {
                            openDecompiler(info);
                        }
                    }
                });
            }
        }).start();
    }

    public void openDecompiler(PackageInfo info) {
        Intent i = new Intent(this, DecompilerActivity.class);
        i.putExtra(Constants.EXTRA_PACKAGE_INFO, info);
        startActivity(i);
    }
}
