/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mydecompilers.Constants;
import com.mydecompilers.utils.FileUtils;
import com.mydecompilers.utils.ShareUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

/** Syntax-highlighted viewer / editor for text files. */
public class CodeViewerActivity extends BaseActivity {

    private static final long MAX_LOAD_BYTES = 2L * 1024 * 1024;
    private static final long MAX_EDIT_BYTES = 400L * 1024;
    private static final long MAX_HIGHLIGHT_BYTES = 600L * 1024;

    private File file;
    private String ext;
    private EditText editor;
    private TextView lineNumbers;
    private ScrollView vScroll;
    private FrameLayout contentHolder;
    private LinearLayout searchBar;
    private EditText searchInput;
    private ProgressBar progress;

    private boolean wrap = false;
    private boolean modified = false;
    private boolean editable = true;
    private boolean loaded = false;
    private float textSize = 12f;
    private CharSequence loadedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra(Constants.EXTRA_PATH);
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        ext = FileUtils.extension(file.getName());
        setTitle(file.getName());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchInput = new EditText(this);
        searchInput.setHint("Find in file");
        searchInput.setSingleLine(true);
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button prev = new Button(this);
        prev.setText("\u25B2");
        prev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                find(false);
            }
        });
        Button next = new Button(this);
        next.setText("\u25BC");
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                find(true);
            }
        });
        searchBar.addView(prev);
        searchBar.addView(next);
        searchBar.setVisibility(View.GONE);
        root.addView(searchBar, matchWrap());

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentHolder = new FrameLayout(this);
        root.addView(contentHolder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        load();
    }

    // ------------------------------------------------------------ loading

    private void load() {
        new Thread(new Runnable() {
            public void run() {
                String text;
                boolean truncated = false;
                boolean binary = false;
                try {
                    FileInputStream in = new FileInputStream(file);
                    try {
                        byte[] head = new byte[4096];
                        int n = in.read(head);
                        for (int i = 0; i < n; i++) {
                            if (head[i] == 0) {
                                binary = true;
                                break;
                            }
                        }
                    } finally {
                        in.close();
                    }
                    truncated = file.length() > MAX_LOAD_BYTES;
                    text = binary ? "" : FileUtils.readText(file, MAX_LOAD_BYTES);
                } catch (Exception e) {
                    text = "Could not read file: " + e.getMessage();
                    binary = false;
                }
                final boolean fBinary = binary;
                final boolean fTrunc = truncated;
                final String fText = text;
                final boolean canEdit = !truncated && file.length() <= MAX_EDIT_BYTES && !binary;
                final CharSequence shown = (!binary && file.length() <= MAX_HIGHLIGHT_BYTES && SyntaxHighlighter.supports(ext))
                        ? SyntaxHighlighter.highlight(fText, ext, isDarkTheme()) : fText;
                runOnUiThread(new Runnable() {
                    public void run() {
                        progress.setVisibility(View.GONE);
                        if (fBinary) {
                            new AlertDialog.Builder(CodeViewerActivity.this)
                                    .setTitle("Binary file")
                                    .setMessage("This file does not look like text.")
                                    .setPositiveButton("Open with another app", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface d, int w) {
                                            ShareUtils.openExternally(CodeViewerActivity.this, file);
                                            finish();
                                        }
                                    })
                                    .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface d, int w) {
                                            finish();
                                        }
                                    }).setCancelable(false).show();
                            return;
                        }
                        editable = canEdit;
                        loadedText = shown;
                        loaded = true;
                        buildContent();
                        if (fTrunc) toast("Large file: showing the first 2 MB (read-only)");
                        else if (!canEdit) toast("Large file: read-only");
                    }
                });
            }
        }).start();
    }

    private void buildContent() {
        CharSequence current = editor != null ? editor.getText() : loadedText;
        contentHolder.removeAllViews();

        editor = new EditText(this);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        editor.setBackgroundDrawable(null);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(!wrap);
        editor.setText(current);
        if (!editable) {
            editor.setKeyListener(null);
            editor.setTextIsSelectable(true);
        }
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                if (loaded) {
                    modified = true;
                    updateLineNumbers();
                }
            }
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        lineNumbers = new TextView(this);
        lineNumbers.setTypeface(Typeface.MONOSPACE);
        lineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        lineNumbers.setGravity(android.view.Gravity.END);
        lineNumbers.setPadding(dp(6), editor.getPaddingTop(), dp(6), 0);
        lineNumbers.setAlpha(0.5f);
        if (!wrap) row.addView(lineNumbers, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(editor, new LinearLayout.LayoutParams(wrap ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, wrap ? 1f : 0f));

        vScroll = new ScrollView(this);
        if (wrap) {
            vScroll.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            vScroll.addView(hs, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            vScroll.setFillViewport(true);
        }
        contentHolder.addView(vScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateLineNumbers();
        modified = false;
    }

    private void updateLineNumbers() {
        if (wrap || lineNumbers == null) return;
        String t = editor.getText().toString();
        int lines = 1;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == '\n') lines++;
        StringBuilder sb = new StringBuilder(lines * 5);
        for (int i = 1; i <= lines; i++) {
            if (i > 1) sb.append('\n');
            sb.append(i);
        }
        lineNumbers.setText(sb);
    }

    // ------------------------------------------------------------ search

    private void find(boolean forward) {
        if (editor == null) return;
        String q = searchInput.getText().toString();
        if (q.length() == 0) return;
        String hay = editor.getText().toString().toLowerCase(Locale.US);
        String needle = q.toLowerCase(Locale.US);
        int from = forward ? editor.getSelectionEnd() : editor.getSelectionStart() - 1;
        if (from < 0) from = forward ? 0 : hay.length();
        int idx = forward ? hay.indexOf(needle, from) : hay.lastIndexOf(needle, from);
        if (idx < 0) {
            idx = forward ? hay.indexOf(needle) : hay.lastIndexOf(needle);
            if (idx < 0) {
                toast("Not found");
                return;
            }
        }
        editor.requestFocus();
        editor.setSelection(idx, idx + needle.length());
        if (editor.getLayout() != null) {
            int line = editor.getLayout().getLineForOffset(idx);
            vScroll.scrollTo(0, Math.max(0, editor.getLayout().getLineTop(line) - dp(120)));
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }

    // ------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Search").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 2, 1, "Toggle word wrap");
        menu.add(0, 3, 2, "Text size +");
        menu.add(0, 4, 3, "Text size -");
        menu.add(0, 5, 4, "Copy all");
        menu.add(0, 6, 5, "Save").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 7, 6, "Share");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!loaded) return true;
        switch (item.getItemId()) {
            case 1:
                searchBar.setVisibility(searchBar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                if (searchBar.getVisibility() == View.VISIBLE) searchInput.requestFocus();
                return true;
            case 2:
                wrap = !wrap;
                loadedText = editor.getText();
                boolean wasModified = modified;
                buildContent();
                modified = wasModified;
                return true;
            case 3:
            case 4:
                textSize = Math.max(7f, Math.min(28f, textSize + (item.getItemId() == 3 ? 1.5f : -1.5f)));
                editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
                lineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
                return true;
            case 5:
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(file.getName(), editor.getText().toString()));
                toast("Copied");
                return true;
            case 6:
                save();
                return true;
            case 7:
                ShareUtils.shareFile(this, file, file.getName());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        if (!editable) {
            toast("This file is read-only");
            return;
        }
        try {
            FileUtils.writeText(file, editor.getText().toString());
            modified = false;
            toast("Saved");
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        if (modified && editable) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved changes")
                    .setMessage("Save changes to " + file.getName() + "?")
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            save();
                            finish();
                        }
                    })
                    .setNegativeButton("Discard", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            modified = false;
                            finish();
                        }
                    })
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}
