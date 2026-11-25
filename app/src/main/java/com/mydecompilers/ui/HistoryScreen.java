/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.mydecompilers.Constants;
import com.mydecompilers.R;
import com.mydecompilers.data.SourceInfo;
import com.mydecompilers.utils.FileUtils;
import com.mydecompilers.utils.ShareUtils;
import com.mydecompilers.utils.Storage;
import com.mydecompilers.utils.UserPreferences;
import com.mydecompilers.utils.ZipUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Previously decompiled projects. */
public class HistoryScreen {

    private final MainActivity activity;
    private final UiStyle.Palette p;
    private final LinearLayout root;
    private final ListView list;
    private final View empty;
    private final HistoryAdapter adapter;

    public HistoryScreen(MainActivity a) {
        this.activity = a;
        p = UiStyle.palette(a);
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setPadding(dp(UiStyle.GUTTER), dp(16), dp(UiStyle.GUTTER), 0);
        root.addView(UiStyle.header(a, p, "History", "Previously decompiled projects"));

        empty = UiStyle.emptyState(a, p, R.drawable.ic_history, "Nothing decompiled yet",
                "Projects you decompile will show up here.");
        root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new ListView(a);
        adapter = new HistoryAdapter();
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
                openProject(adapter.getItem(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showActions(adapter.getItem(position));
                return true;
            }
        });
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.getResources().getDisplayMetrics());
    }

    public View getView() {
        return root;
    }

    public void onShow() {
        final Set<String> hidden = new UserPreferences(activity).getHiddenHistoryPaths();
        new Thread(new Runnable() {
            public void run() {
                final List<SourceInfo> found = new ArrayList<SourceInfo>();
                for (File sourcesDir : Storage.allSourceDirs(activity)) {
                    File[] dirs = sourcesDir.listFiles();
                    if (dirs == null) continue;
                    for (File d : dirs) {
                        if (d.isDirectory() && !hidden.contains(d.getAbsolutePath()) && SourceInfo.exists(d)) {
                            SourceInfo si = SourceInfo.from(d);
                            if (si.sourceSize <= 0) si.sourceSize = FileUtils.sizeOf(d);
                            found.add(si);
                        }
                    }
                }
                Collections.sort(found, new Comparator<SourceInfo>() {
                    public int compare(SourceInfo x, SourceInfo y) {
                        long a = x.sourceDirectory.lastModified();
                        long b = y.sourceDirectory.lastModified();
                        return a < b ? 1 : (a > b ? -1 : 0);
                    }
                });
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.setItems(found);
                        empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }).start();
    }

    private void openProject(SourceInfo info) {
        Intent i = new Intent(activity, NavigatorActivity.class);
        i.putExtra(Constants.EXTRA_ROOT, info.sourceDirectory.getAbsolutePath());
        i.putExtra(Constants.EXTRA_TITLE, info.packageLabel);
        activity.startActivity(i);
    }

    private void showActions(final SourceInfo info) {
        String[] items = new String[] { "Open", "Export ZIP and share", "Hide from history", "Delete" };
        new AlertDialog.Builder(activity)
                .setTitle(info.packageLabel)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) openProject(info);
                        else if (which == 1) exportZip(info);
                        else if (which == 2) {
                            new UserPreferences(activity).hideHistoryPath(info.sourceDirectory.getAbsolutePath());
                            onShow();
                        } else confirmDelete(info);
                    }
                }).show();
    }

    private void confirmDelete(final SourceInfo info) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete project?")
                .setMessage(info.sourceDirectory.getName() + "\n\nThis removes all decompiled files.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        FileUtils.deleteRecursively(info.sourceDirectory);
                        onShow();
                    }
                }).show();
    }

    private void exportZip(final SourceInfo info) {
        final ProgressDialog dlg = new ProgressDialog(activity);
        dlg.setMessage("Creating ZIP...");
        dlg.setCancelable(false);
        dlg.show();
        new Thread(new Runnable() {
            public void run() {
                File zip = null;
                try {
                    zip = ZipUtils.zipDir(info.sourceDirectory,
                            new File(Storage.archivesDir(activity), info.sourceDirectory.getName() + ".zip"));
                } catch (Exception ignored) {
                }
                final File result = zip;
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        dlg.dismiss();
                        if (result == null) Toast.makeText(activity, "ZIP export failed", Toast.LENGTH_LONG).show();
                        else ShareUtils.shareFile(activity, result, info.packageLabel);
                    }
                });
            }
        }).start();
    }

    private class HistoryAdapter extends BaseAdapter {
        private List<SourceInfo> items = new ArrayList<SourceInfo>();

        void setItems(List<SourceInfo> n) {
            items = n;
            notifyDataSetChanged();
        }

        public int getCount() { return items.size(); }
        public SourceInfo getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int position, View convertView, ViewGroup parent) {
            ListRow row;
            if (convertView == null) {
                row = new ListRow(activity);
                row.setTile(R.drawable.ic_folder, p.accent, p.accentSoft);
            } else {
                row = (ListRow) convertView;
            }
            SourceInfo si = items.get(position);
            row.bind(si.packageLabel, si.packageName, null,
                    si.getDisplayTag() + "  \u2022  " + FileUtils.formatSize(si.sourceSize));
            return row;
        }
    }
}
