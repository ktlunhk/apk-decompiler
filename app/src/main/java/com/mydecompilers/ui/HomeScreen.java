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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mydecompilers.R;

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
    private final UiStyle.Palette p;
    private final LinearLayout root;
    private final EditText search;
    private final Segmented filter;
    private final ListView list;
    private final ProgressBar loading;
    private final View empty;
    private final TextView count;
    private final AppAdapter adapter;

    private static List<PackageInfo> cachedUser = new ArrayList<PackageInfo>();
    private static List<PackageInfo> cachedSystem = new ArrayList<PackageInfo>();
    private boolean loaded = false;
    private boolean loadingNow = false;

    public HomeScreen(MainActivity a) {
        this.activity = a;
        p = UiStyle.palette(a);
        int pad = dp(UiStyle.GUTTER);

        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, 0);
        root.setBackgroundColor(p.bg);
        root.addView(UiStyle.header(a, p, "Explore applications", "Browse installed apps or import an APK file"));

        // search + import
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout searchBox = UiStyle.inputBox(a, p, R.drawable.ic_search, "Search name or package");
        search = UiStyle.inputOf(searchBox);
        top.addView(searchBox, new LinearLayout.LayoutParams(0, dp(UiStyle.CONTROL_HEIGHT), 1f));
        View open = UiStyle.button(a, "Import", UiStyle.BTN_PRIMARY, R.drawable.ic_add);
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                activity.pickFile();
            }
        });
        top.addView(open, UiStyle.lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(UiStyle.CONTROL_HEIGHT), dp(10), 0, 0, 0));
        root.addView(top, UiStyle.lp(-1, -2, 0, 0, 0, dp(12)));

        // user / system switch + refresh
        LinearLayout row2 = new LinearLayout(a);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        filter = new Segmented(a, new String[] { "User apps", "System apps" }, 0);
        row2.addView(filter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View refresh = UiStyle.iconButton(a, p, R.drawable.ic_refresh, "Refresh");
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reload();
            }
        });
        row2.addView(refresh, UiStyle.lp(dp(UiStyle.CONTROL_HEIGHT), dp(UiStyle.CONTROL_HEIGHT), dp(10), 0, 0, 0));
        root.addView(row2, UiStyle.lp(-1, -2, 0, 0, 0, dp(12)));

        count = UiStyle.text(a, "", 12, p.textSecondary, false);
        count.setPadding(dp(4), 0, 0, dp(8));
        root.addView(count, matchWrapParams());

        loading = new ProgressBar(a);
        loading.setIndeterminate(true);
        root.addView(loading, UiStyle.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(16), 0, 0));
        ((LinearLayout.LayoutParams) loading.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        loading.setVisibility(View.GONE);

        empty = UiStyle.emptyState(a, p, R.drawable.ic_search, "No applications found",
                "Try another search or refresh the list.");
        empty.setVisibility(View.GONE);
        root.addView(empty, matchWrapParams());

        list = new ListView(a);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(dp(8));
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(16));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
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
        filter.setListener(new Segmented.Listener() {
            public void onSelected(int index) {
                applyFilter();
            }
        });
    }

    private static LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
        List<PackageInfo> source = filter.getSelectedIndex() == 0 ? cachedUser : cachedSystem;
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        List<PackageInfo> out = new ArrayList<PackageInfo>();
        for (PackageInfo p : source) {
            if (q.length() == 0 || p.label.toLowerCase(Locale.US).contains(q) || p.name.toLowerCase(Locale.US).contains(q)) {
                out.add(p);
            }
        }
        adapter.setItems(out);
        empty.setVisibility(out.isEmpty() && !loadingNow ? View.VISIBLE : View.GONE);
        count.setText(out.size() == 1 ? "1 app" : out.size() + " apps");
        count.setVisibility(out.isEmpty() ? View.GONE : View.VISIBLE);
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
            ListRow row;
            if (convertView == null) {
                row = new ListRow(activity);
                row.useAppIcon();
            } else {
                row = (ListRow) convertView;
            }
            PackageInfo info = items.get(position);
            row.bind(info.label, info.name, info.typeLabel(), info.version);
            IconLoader.load(activity, info, row.icon);
            return row;
        }
    }
}
