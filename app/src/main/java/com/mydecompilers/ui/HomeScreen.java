/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mydecompilers.data.PackageInfo;
import com.mydecompilers.utils.ApkUtils;
import com.mydecompilers.utils.ShareUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Installed apps list with search, user/system filter and an "open file" button. */
public class HomeScreen {

    private final MainActivity activity;
    private final LinearLayout root;
    private final EditText search;
    private final Spinner filter;
    private final ListView list;
    private final ProgressBar loading;
    private final TextView empty;
    private final AppAdapter adapter;

    private static List<PackageInfo> cachedUser = new ArrayList<PackageInfo>();
    private static List<PackageInfo> cachedSystem = new ArrayList<PackageInfo>();
    private boolean loaded = false;
    private boolean loadingNow = false;

    public HomeScreen(MainActivity a) {
        this.activity = a;
        int pad = dp(16);

        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, 0);
        root.setBackgroundColor(activity.isDarkTheme() ? 0xff121820 : UiStyle.LIGHT);
        TextView heading = new TextView(a);
        heading.setText("Explore applications");
        UiStyle.label(heading, 23, activity.isDarkTheme() ? 0xfff2f5fc : UiStyle.INK, true);
        root.addView(heading);
        TextView subtitle = new TextView(a);
        subtitle.setText("Browse installed apps or import an APK file");
        UiStyle.label(subtitle, 13, activity.isDarkTheme() ? 0xffaab6c7 : UiStyle.MUTED, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.bottomMargin = dp(16);
        root.addView(subtitle, subtitleParams);

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(a);
        search.setHint("Search name or package…");
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setPadding(dp(14), dp(10), dp(10), dp(10));
        search.setBackground(UiStyle.outlined(a, activity.isDarkTheme() ? 0xff263140 : 0xffffffff, activity.isDarkTheme() ? 0xff455367 : 0xffdce3ed, 12));
        search.setSingleLine(true);
        top.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button open = new Button(a);
        open.setText("+ Import");
        open.setTextColor(0xffffffff);
        open.setBackground(UiStyle.shape(a, UiStyle.BLUE, 12));
        open.setAllCaps(false);
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                activity.pickFile();
            }
        });
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        openParams.leftMargin = dp(10);
        top.addView(open, openParams);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(-1, -2);
        topParams.bottomMargin = dp(12);
        root.addView(top, topParams);

        LinearLayout row2 = new LinearLayout(a);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        filter = new Spinner(a);
        filter.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item,
                new String[] { "User apps", "System apps" }));
        row2.addView(filter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button refresh = new Button(a);
        refresh.setText("↻ Refresh");
        refresh.setTextColor(UiStyle.BLUE);
        refresh.setBackground(UiStyle.outlined(a, activity.isDarkTheme() ? 0xff263140 : 0xffffffff, activity.isDarkTheme() ? 0xff455367 : 0xffdce3ed, 10));
        refresh.setAllCaps(false);
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reload();
            }
        });
        row2.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, -2);
        filterParams.bottomMargin = dp(12);
        root.addView(row2, filterParams);

        loading = new ProgressBar(a);
        loading.setIndeterminate(true);
        root.addView(loading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        loading.setVisibility(View.GONE);

        empty = new TextView(a);
        empty.setText("No applications found\nTry another search or refresh the list.");
        UiStyle.label(empty, 14, activity.isDarkTheme() ? 0xffaab6c7 : UiStyle.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(24), 0, 0);
        empty.setVisibility(View.GONE);
        root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new ListView(a);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(dp(7));
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                activity.openDecompiler(adapter.getItem(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showActions(adapter.getItem(position));
                return true;
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter();
            }

            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.getResources().getDisplayMetrics());
    }

    public View getView() {
        return root;
    }

    public void onShow() {
        if (!loaded) reload();
    }

    // ------------------------------------------------------------ data

    private void reload() {
        if (loadingNow) return;
        loadingNow = true;
        loading.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                final List<PackageInfo> user = new ArrayList<PackageInfo>();
                final List<PackageInfo> system = new ArrayList<PackageInfo>();
                try {
                    PackageManager pm = activity.getPackageManager();
                    List<android.content.pm.PackageInfo> all = pm.getInstalledPackages(0);
                    for (android.content.pm.PackageInfo pi : all) {
                        if (pi.applicationInfo == null || pi.applicationInfo.sourceDir == null) continue;
                        PackageInfo info = PackageInfo.fromInstalled(activity, pi);
                        if (info.isSystemPackage) system.add(info);
                        else user.add(info);
                    }
                } catch (Exception ignored) {
                }
                Comparator<PackageInfo> byLabel = new Comparator<PackageInfo>() {
                    public int compare(PackageInfo x, PackageInfo y) {
                        return x.label.toLowerCase(Locale.US).compareTo(y.label.toLowerCase(Locale.US));
                    }
                };
                Collections.sort(user, byLabel);
                Collections.sort(system, byLabel);
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        cachedUser = user;
                        cachedSystem = system;
                        loaded = true;
                        loadingNow = false;
                        loading.setVisibility(View.GONE);
                        applyFilter();
                    }
                });
            }
        }).start();
    }

    private void applyFilter() {
        List<PackageInfo> source = filter.getSelectedItemPosition() == 0 ? cachedUser : cachedSystem;
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        List<PackageInfo> out = new ArrayList<PackageInfo>();
        for (PackageInfo p : source) {
            if (q.length() == 0 || p.label.toLowerCase(Locale.US).contains(q) || p.name.toLowerCase(Locale.US).contains(q)) {
                out.add(p);
            }
        }
        adapter.setItems(out);
        empty.setVisibility(out.isEmpty() && !loadingNow ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ actions

    private void showActions(final PackageInfo info) {
        String[] items = new String[] { "Decompile", "Extract APK to storage", "Share APK", "Copy package name" };
        new AlertDialog.Builder(activity)
                .setTitle(info.label)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            activity.openDecompiler(info);
                        } else if (which == 1) {
                            extract(info);
                        } else if (which == 2) {
                            share(info);
                        } else {
                            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("package", info.name));
                            Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).show();
    }

    private void extract(final PackageInfo info) {
        final ProgressDialog dlg = new ProgressDialog(activity);
        dlg.setMessage("Extracting...");
        dlg.setCancelable(false);
        dlg.show();
        new Thread(new Runnable() {
            public void run() {
                final File f = ApkUtils.extractPackage(activity, info);
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        dlg.dismiss();
                        if (f == null) {
                            Toast.makeText(activity, "Extraction failed", Toast.LENGTH_LONG).show();
                        } else {
                            new AlertDialog.Builder(activity)
                                    .setTitle("Extracted")
                                    .setMessage(f.getAbsolutePath())
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    }
                });
            }
        }).start();
    }

    private void share(final PackageInfo info) {
        final ProgressDialog dlg = new ProgressDialog(activity);
        dlg.setMessage("Preparing...");
        dlg.setCancelable(false);
        dlg.show();
        new Thread(new Runnable() {
            public void run() {
                final File f = ApkUtils.extractPackage(activity, info);
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        dlg.dismiss();
                        if (f == null) Toast.makeText(activity, "Could not read the package", Toast.LENGTH_LONG).show();
                        else ShareUtils.shareFile(activity, f, info.label);
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------ adapter

    private class AppAdapter extends BaseAdapter {
        private List<PackageInfo> items = new ArrayList<PackageInfo>();

        void setItems(List<PackageInfo> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        public int getCount() { return items.size(); }
        public PackageInfo getItem(int position) { return items.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setBackground(UiStyle.outlined(activity, activity.isDarkTheme() ? 0xff202a38 : 0xffffffff, activity.isDarkTheme() ? 0xff344154 : 0xffe8edf5, 14));
                h = new Holder();
                h.icon = new ImageView(activity);
                row.addView(h.icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout col = new LinearLayout(activity);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(12), 0, 0, 0);
                h.label = new TextView(activity);
                UiStyle.label(h.label, 15, activity.isDarkTheme() ? 0xfff2f5fc : UiStyle.INK, true);
                h.label.setTypeface(h.label.getTypeface(), android.graphics.Typeface.BOLD);
                h.label.setSingleLine(true);
                h.pkg = new TextView(activity);
                UiStyle.label(h.pkg, 12, activity.isDarkTheme() ? 0xffb4c0d0 : UiStyle.MUTED, false);
                h.pkg.setSingleLine(true);
                h.meta = new TextView(activity);
                UiStyle.label(h.meta, 11, UiStyle.BLUE, false);
                h.meta.setSingleLine(true);
                col.addView(h.label);
                col.addView(h.pkg);
                col.addView(h.meta);
                row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.setTag(h);
                convertView = row;
            } else {
                h = (Holder) convertView.getTag();
            }
            PackageInfo info = items.get(position);
            h.label.setText(info.label);
            h.pkg.setText(info.name);
            h.meta.setText(info.version + "  \u2022  " + info.typeLabel());
            IconLoader.load(activity, info, h.icon);
            return convertView;
        }
    }

    private static class Holder {
        ImageView icon;
        TextView label, pkg, meta;
    }
}
