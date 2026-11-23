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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mydecompilers.Constants;
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
    private Button tabApps, tabHistory, tabSettings;
    private int currentTab = 0;

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
        col.setPadding(dp(12), dp(12), dp(12), dp(12));
        col.addView(text("The app crashed last time", 18, true), matchWrap());
        col.addView(text("Copy this report and send it so the problem can be fixed.", 13, false), matchWrap());
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(11);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        Button copy = new Button(this);
        copy.setText("Copy report");
        copy.setAllCaps(false);
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", report));
                toast("Copied");
            }
        });
        Button cont = new Button(this);
        cont.setText("Continue");
        cont.setAllCaps(false);
        cont.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                com.mydecompilers.utils.CrashLog.clear(MainActivity.this);
                recreate();
            }
        });
        row.addView(copy, weight(1f));
        row.addView(cont, weight(1f));
        col.addView(row, matchWrap());
        setContentView(col);
    }

    private void buildMain(Bundle savedInstanceState) {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(10), dp(7), dp(10), dp(7));
        bar.setBackgroundColor(isDarkTheme() ? 0xff202a38 : 0xffffffff);
        tabApps = tabButton("▦  Apps", 0);
        tabHistory = tabButton("◷  History", 1);
        tabSettings = tabButton("⚙  Settings", 2);
        bar.addView(tabApps, weight(1f));
        bar.addView(tabHistory, weight(1f));
        bar.addView(tabSettings, weight(1f));
        root.addView(bar, matchWrap());

        setContentView(root);

        home = new HomeScreen(this);
        history = new HistoryScreen(this);
        settings = new SettingsScreen(this);

        requestStoragePermission();
        selectTab(savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : 0);
        handleIncoming(getIntent());
    }

    private Button tabButton(String label, final int index) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinHeight(dp(48));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(index);
            }
        });
        return b;
    }

    private void selectTab(int index) {
        currentTab = index;
        Button[] tabs = new Button[] { tabApps, tabHistory, tabSettings };
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabs[i].setTextColor(selected ? 0xffffffff : (isDarkTheme() ? 0xffb5c1d1 : UiStyle.MUTED));
            tabs[i].setBackground(UiStyle.shape(this, selected ? UiStyle.BLUE : (isDarkTheme() ? 0xff202a38 : 0xffffffff), 12));
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
        tabApps.setEnabled(index != 0);
        tabHistory.setEnabled(index != 1);
        tabSettings.setEnabled(index != 2);
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
