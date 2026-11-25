/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.mydecompilers.Constants;
import com.mydecompilers.R;
import com.mydecompilers.data.FileItem;
import com.mydecompilers.utils.FileUtils;
import com.mydecompilers.utils.ShareUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Browse a decompiled project folder. */
public class NavigatorActivity extends BaseActivity {

    private static final Set<String> IMAGE_EXT = new HashSet<String>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp"));
    private static final Set<String> TEXT_EXT = new HashSet<String>(Arrays.asList(
            "java", "kt", "xml", "json", "txt", "smali", "gradle", "properties", "html", "css", "js", "md",
            "yml", "yaml", "pro", "cfg", "ini", "list", "csv", "log", "mf", "version", "sh", "c", "h", "cpp"));

    private File root;
    private File current;
    private TextView pathView;
    private ListView listView;
    private View emptyView;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String r = getIntent().getStringExtra(Constants.EXTRA_ROOT);
        if (r == null) {
            finish();
            return;
        }
        root = new File(r);
        current = root;
        String title = getIntent().getStringExtra(Constants.EXTRA_TITLE);
        setTitle(title != null ? title : root.getName());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(p.bg);
        col.setPadding(dp(UiStyle.GUTTER), dp(12), dp(UiStyle.GUTTER), 0);

        // current path
        LinearLayout pathBar = new LinearLayout(this);
        pathBar.setOrientation(LinearLayout.HORIZONTAL);
        pathBar.setGravity(Gravity.CENTER_VERTICAL);
        pathBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        pathBar.setBackground(UiStyle.outlined(this, p.surface, p.outline, UiStyle.CONTROL_RADIUS));
        pathBar.addView(UiStyle.icon(this, R.drawable.ic_folder_open, p.accent, 20));
        pathView = UiStyle.text(this, "", 13, p.text, false);
        pathView.setTypeface(android.graphics.Typeface.MONOSPACE);
        pathView.setSingleLine(true);
        pathView.setEllipsize(TextUtils.TruncateAt.START);
        pathBar.addView(pathView, UiStyle.lp(0, -2, dp(10), 0, 0, 0));
        ((LinearLayout.LayoutParams) pathView.getLayoutParams()).weight = 1f;
        col.addView(pathBar, UiStyle.lp(-1, -2, 0, 0, 0, dp(12)));

        emptyView = UiStyle.emptyState(this, p, R.drawable.ic_folder, "This folder is empty", null);
        col.addView(emptyView, matchWrap());

        listView = new ListView(this);
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setDivider(null);
        listView.setDividerHeight(dp(8));
        listView.setSelector(android.R.color.transparent);
        listView.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(16));
        col.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                FileItem item = adapter.getItem(pos);
                if (item.isParent) {
                    goUp();
                } else if (item.file.isDirectory()) {
                    current = item.file;
                    refresh();
                } else {
                    openFile(item.file);
                }
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View v, int pos, long id) {
                FileItem item = adapter.getItem(pos);
                if (!item.isParent) showActions(item.file);
                return true;
            }
        });
        refresh();
    }

    private void goUp() {
        File parent = current.getParentFile();
        if (parent != null && !current.equals(root)) {
            current = parent;
            refresh();
        }
    }

    @Override
    public void onBackPressed() {
        if (!current.equals(root)) goUp();
        else super.onBackPressed();
    }

    private void refresh() {
        String rel = current.getAbsolutePath().substring(Math.min(root.getParentFile() == null ? 0
                : root.getParentFile().getAbsolutePath().length(), current.getAbsolutePath().length()));
        pathView.setText(rel.length() == 0 ? "/" : rel);
        File[] kids = current.listFiles();
        List<File> files = new ArrayList<File>();
        if (kids != null) files.addAll(Arrays.asList(kids));
        Collections.sort(files, new Comparator<File>() {
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().toLowerCase(Locale.US).compareTo(b.getName().toLowerCase(Locale.US));
            }
        });
        List<FileItem> items = new ArrayList<FileItem>();
        if (!current.equals(root)) items.add(new FileItem(current.getParentFile(), true));
        for (File f : files) items.add(new FileItem(f, false));
        adapter.setItems(items);
        emptyView.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openFile(File f) {
        String ext = FileUtils.extension(f.getName());
        if (IMAGE_EXT.contains(ext)) {
            Intent i = new Intent(this, ImageViewerActivity.class);
            i.putExtra(Constants.EXTRA_PATH, f.getAbsolutePath());
            startActivity(i);
        } else if (TEXT_EXT.contains(ext) || ext.length() == 0) {
            Intent i = new Intent(this, CodeViewerActivity.class);
            i.putExtra(Constants.EXTRA_PATH, f.getAbsolutePath());
            startActivity(i);
        } else {
            ShareUtils.openExternally(this, f);
        }
    }

    private void showActions(final File f) {
        String[] items = f.isDirectory()
                ? new String[] { "Delete folder" }
                : new String[] { "Open", "Open with another app", "Share", "Delete" };
        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (f.isDirectory()) {
                            confirmDelete(f);
                        } else if (which == 0) {
                            openFile(f);
                        } else if (which == 1) {
                            ShareUtils.openExternally(NavigatorActivity.this, f);
                        } else if (which == 2) {
                            ShareUtils.shareFile(NavigatorActivity.this, f, f.getName());
                        } else {
                            confirmDelete(f);
                        }
                    }
                }).show();
    }

    private void confirmDelete(final File f) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + f.getName() + "?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        FileUtils.deleteRecursively(f);
                        refresh();
                    }
                }).show();
    }

    private class Adapter extends BaseAdapter {
        private List<FileItem> items = new ArrayList<FileItem>();

        void setItems(List<FileItem> n) {
            items = n;
            notifyDataSetChanged();
        }

        public int getCount() { return items.size(); }
        public FileItem getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int position, View convertView, ViewGroup parent) {
            ListRow row = convertView == null ? new ListRow(NavigatorActivity.this) : (ListRow) convertView;
            FileItem item = items.get(position);
            if (item.isParent) {
                row.setTile(R.drawable.ic_folder_up, p.textSecondary, p.bg);
                row.setChevronVisible(true);
                row.bind("..", "Parent folder", null, null);
            } else if (item.file.isDirectory()) {
                String[] kids = item.file.list();
                int n = kids == null ? 0 : kids.length;
                row.setTile(R.drawable.ic_folder, p.accent, p.accentSoft);
                row.setChevronVisible(true);
                row.bind(item.file.getName(), n == 1 ? "1 item" : n + " items", null, null);
            } else {
                String ext = FileUtils.extension(item.file.getName());
                if (IMAGE_EXT.contains(ext)) {
                    row.setTile(R.drawable.ic_image, p.success, p.successSoft);
                } else if (TEXT_EXT.contains(ext)) {
                    row.setTile(R.drawable.ic_code, p.warning, p.warningSoft);
                } else {
                    row.setTile(R.drawable.ic_file, p.textSecondary, p.bg);
                }
                row.setChevronVisible(false);
                row.bind(item.file.getName(), FileUtils.formatSize(item.file.length()), null, null);
            }
            return row;
        }
    }
}
