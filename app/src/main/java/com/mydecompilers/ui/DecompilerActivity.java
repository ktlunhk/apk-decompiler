/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
//import android.content.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mydecompilers.Constants;
import com.mydecompilers.R;
import com.mydecompilers.data.PackageInfo;
import com.mydecompilers.engines.DecompilerRegistry;
import com.mydecompilers.managers.DecompilerTaskManager;
import com.mydecompilers.utils.ShareUtils;
import com.mydecompilers.utils.Storage;
import com.mydecompilers.utils.UserPreferences;
import com.mydecompilers.utils.ZipUtils;

import java.io.File;
import android.content.res.*;

/** Configure, run, monitor and finish one decompilation. */
public class DecompilerActivity extends BaseActivity implements DecompilerTaskManager.Listener {

    private PackageInfo info;
    private UserPreferences prefs;

    private SelectField engineField;
    private TextView engineDesc;
    private View startButton, stopButton, openButton, zipButton, copyLogButton, againButton;
    private ProgressBar progressBar, stateSpinner;
    private ImageView stateIcon;
    private TextView statusText, memoryText, logText, stateText, percentText;
    private ScrollView logScroll;
    private View configRow, statusCard, logSection, idleHint, resultRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Decompiler");
        prefs = new UserPreferences(this);
        info = (PackageInfo) getIntent().getParcelableExtra(Constants.EXTRA_PACKAGE_INFO);
        if (info == null) {
            finish();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setPadding(dp(UiStyle.GUTTER), dp(12), dp(UiStyle.GUTTER), dp(16));

        // ---- app header card
        LinearLayout header = UiStyle.card(this, p);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(14), dp(14), dp(14));
        ImageView icon = new ImageView(this);
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        IconLoader.load(this, info, icon);
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(14), 0, 0, 0);
        TextView name = text(info.label, 18, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(name);
        TextView pkg = caption(info.name, 12);
        pkg.setSingleLine(true);
        pkg.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleCol.addView(pkg);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.addView(UiStyle.chip(this, info.typeLabel(), p.accent, p.accentSoft));
        if (info.isSystemPackage) {
            chips.addView(UiStyle.chip(this, "System app", p.warning, p.warningSoft), UiStyle.lp(-2, -2, dp(6), 0, 0, 0));
        }
        if (info.version != null && info.version.length() > 0) {
            TextView ver = caption(info.version, 12);
            ver.setSingleLine(true);
            ver.setEllipsize(TextUtils.TruncateAt.END);
            chips.addView(ver, UiStyle.lp(-2, -2, dp(8), 0, 0, 0));
        }
        titleCol.addView(chips, UiStyle.lp(-2, -2, 0, dp(8), 0, 0));
        header.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, matchWrap());

        // ---- engine selection
        LinearLayout config = UiStyle.card(this, p);
        configRow = config;
        config.addView(text("Decompiler engine", 14, true));
        int idx = prefs.getSelectedDecompilerIndex();
        if (idx < 0 || idx >= DecompilerRegistry.getEngineIds().length) idx = 0;
        if (info.type == PackageInfo.Type.JAR && "jadx".equals(DecompilerRegistry.getEngineIds()[idx])) idx = 1;
        engineField = new SelectField(this, "Decompiler engine", DecompilerRegistry.getEngineNames(), idx);
        config.addView(engineField, UiStyle.lp(-1, -2, 0, dp(10), 0, 0));
        engineDesc = caption(DecompilerRegistry.getAllEngines().get(idx).getDescription(), 12);
        engineDesc.setLineSpacing(dp(2), 1f);
        config.addView(engineDesc, UiStyle.lp(-1, -2, 0, dp(10), 0, 0));
        engineField.setListener(new SelectField.Listener() {
            public void onChanged(int position) {
                engineDesc.setText(DecompilerRegistry.getAllEngines().get(position).getDescription());
            }
        });
        root.addView(config, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));

        // ---- main action (only one is visible at a time)
        startButton = UiStyle.button(this, "Start decompiling", UiStyle.BTN_PRIMARY, R.drawable.ic_play);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startTask();
            }
        });
        stopButton = UiStyle.button(this, "Stop", UiStyle.BTN_DANGER, R.drawable.ic_stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                DecompilerTaskManager.stop();
            }
        });
        againButton = UiStyle.button(this, "Decompile again", UiStyle.BTN_TONAL, R.drawable.ic_refresh);
        againButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                DecompilerTaskManager.clear();
                render();
            }
        });
        root.addView(startButton, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));
        root.addView(stopButton, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));
        root.addView(againButton, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));

        // ---- status card
        LinearLayout status = UiStyle.card(this, p);
        statusCard = status;
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateIcon = new ImageView(this);
        stateRow.addView(stateIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        stateSpinner = new ProgressBar(this);
        stateSpinner.setIndeterminate(true);
        stateRow.addView(stateSpinner, new LinearLayout.LayoutParams(dp(22), dp(22)));
        stateText = text("", 15, true);
        stateRow.addView(stateText, UiStyle.lp(0, -2, dp(10), 0, 0, 0));
        ((LinearLayout.LayoutParams) stateText.getLayoutParams()).weight = 1f;
        percentText = UiStyle.text(this, "", 14, p.accent, true);
        stateRow.addView(percentText);
        status.addView(stateRow, matchWrap());
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        UiStyle.styleProgress(this, p, progressBar);
        status.addView(progressBar, UiStyle.lp(-1, dp(8), 0, dp(12), 0, 0));
        statusText = caption("", 12);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        status.addView(statusText, UiStyle.lp(-1, -2, 0, dp(10), 0, 0));
        memoryText = caption("", 12);
        status.addView(memoryText, matchWrap());
        root.addView(status, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));

        // ---- log
        LinearLayout logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logSection = logBox;
        logBox.addView(UiStyle.sectionLabel(this, p, "Log"), UiStyle.lp(-1, -2, dp(2), 0, 0, dp(8)));
        logScroll = new ScrollView(this);
        logScroll.setBackground(UiStyle.outlined(this, p.code, p.outline, UiStyle.CARD_RADIUS));
        logScroll.setClipToOutline(true);
        logScroll.setNestedScrollingEnabled(true);
        logText = new TextView(this);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logText.setTextColor(p.text);
        logText.setLineSpacing(dp(1), 1f);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(12), dp(10), dp(12), dp(10));
        logScroll.addView(logText);
        logBox.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, landscape ? dp(220) : 0, landscape ? 0f : 1f));

        idleHint = UiStyle.emptyState(this, p, R.drawable.ic_play, "Ready to decompile",
                "Choose an engine and tap Start. Progress and log will appear here.");
        LinearLayout.LayoutParams fill = landscape
                ? UiStyle.lp(-1, -2, 0, dp(12), 0, 0)
                : UiStyle.lp(-1, 0, 0, dp(12), 0, 0);
        LinearLayout.LayoutParams logFill = landscape
                ? UiStyle.lp(-1, -2, 0, dp(16), 0, 0)
                : UiStyle.lp(-1, 0, 0, dp(16), 0, 0);
        if (!landscape) {
            fill.weight = 1f;
            logFill.weight = 1f;
        }
        root.addView(idleHint, fill);
        root.addView(logBox, logFill);

        // ---- result actions
        openButton = UiStyle.button(this, "Open sources", UiStyle.BTN_PRIMARY, R.drawable.ic_folder_open);
        openButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openSources();
            }
        });
        zipButton = UiStyle.button(this, "Export ZIP", UiStyle.BTN_TONAL, R.drawable.ic_archive);
        zipButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                exportZip();
            }
        });
        copyLogButton = UiStyle.button(this, "Copy log", UiStyle.BTN_OUTLINE, R.drawable.ic_copy);
        copyLogButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("log", DecompilerTaskManager.getLogText()));
                toast("Log copied");
            }
        });
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        resultRow = result;
        result.addView(openButton, matchWrap());
        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.addView(zipButton, weight(1f));
        LinearLayout.LayoutParams copyLp = weight(1f);
        copyLp.leftMargin = dp(8);
        secondary.addView(copyLogButton, copyLp);
        result.addView(secondary, UiStyle.lp(-1, -2, 0, dp(8), 0, 0));
        root.addView(result, UiStyle.lp(-1, -2, 0, dp(12), 0, 0));

        if (landscape) {
            ScrollView outer = new ScrollView(this);
            outer.setFillViewport(true);
            outer.setBackgroundColor(p.bg);
            outer.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setContentView(outer);
        } else {
            setContentView(root);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        DecompilerTaskManager.addListener(this);
        render();
    }

    @Override
    protected void onStop() {
        DecompilerTaskManager.removeListener(this);
        super.onStop();
    }

    public void onTaskChanged() {
        render();
    }

    private void startTask() {
        int pos = engineField.getSelectedIndex();
        prefs.setSelectedDecompilerIndex(pos);
        String engine = DecompilerRegistry.getEngineIds()[pos];
        try {
            if (!DecompilerTaskManager.start(this, info, engine)) {
                toast("Another decompilation is still running");
            }
        } catch (Throwable t) {
            com.mydecompilers.utils.CrashLog.save(this, t);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Could not start")
                    .setMessage(String.valueOf(t))
                    .setPositiveButton(android.R.string.ok, null).show();
        }
        render();
    }

    private boolean isMine() {
        return DecompilerTaskManager.isActiveFor(info);
    }

    private void render() {
        if (info == null || startButton == null) return;
        DecompilerTaskManager.State s = DecompilerTaskManager.getState();
        boolean mine = isMine();
        boolean otherRunning = !mine && s == DecompilerTaskManager.State.RUNNING;
        boolean running = mine && s == DecompilerTaskManager.State.RUNNING;
        boolean finished = mine && (s == DecompilerTaskManager.State.COMPLETED
                || s == DecompilerTaskManager.State.FAILED || s == DecompilerTaskManager.State.STOPPED);
        boolean hasTask = mine && s != DecompilerTaskManager.State.IDLE;
        boolean hasOutput = mine && s == DecompilerTaskManager.State.COMPLETED || finished && hasPartialOutput();

        setVisible(configRow, !running && !finished);
        setVisible(startButton, !running && !finished);
        startButton.setEnabled(!otherRunning);
        setVisible(stopButton, running);
        setVisible(againButton, finished);
        setVisible(statusCard, hasTask || otherRunning);
        setVisible(progressBar, hasTask);
        setVisible(percentText, hasTask);
        setVisible(logSection, hasTask);
        setVisible(idleHint, !hasTask);
        setVisible(resultRow, mine);
        setVisible(openButton, hasOutput);
        setVisible(zipButton, hasOutput);
        setVisible(copyLogButton, mine);

        // state title + icon / spinner
        int tint = p.accent;
        int iconRes = 0;
        if (otherRunning) {
            PackageInfo other = DecompilerTaskManager.getActivePackage();
            stateText.setText("Another decompilation is running" + (other != null ? " (" + other.label + ")" : ""));
            iconRes = R.drawable.ic_info;
            tint = p.warning;
        } else if (!mine) {
            stateText.setText("Ready");
        } else if (s == DecompilerTaskManager.State.RUNNING) {
            stateText.setText("Decompiling with " + DecompilerTaskManager.getActiveEngine() + "...");
        } else if (s == DecompilerTaskManager.State.COMPLETED) {
            stateText.setText("Decompilation finished");
            iconRes = R.drawable.ic_check_circle;
            tint = p.success;
        } else if (s == DecompilerTaskManager.State.STOPPED) {
            stateText.setText("Stopped - partial output was kept");
            iconRes = R.drawable.ic_info;
            tint = p.warning;
        } else if (s == DecompilerTaskManager.State.FAILED) {
            stateText.setText("Decompilation failed");
            iconRes = R.drawable.ic_error;
            tint = p.danger;
        } else {
            stateText.setText("Ready");
        }
        if (iconRes != 0) stateIcon.setImageDrawable(UiStyle.tinted(this, iconRes, tint));
        setVisible(stateIcon, iconRes != 0);
        setVisible(stateSpinner, iconRes == 0 && running);
        percentText.setTextColor(tint);
        progressBar.setProgressTintList(ColorStateList.valueOf(running || !hasTask ? p.primary : tint));

        if (mine) {
            int progress = DecompilerTaskManager.getProgress();
            progressBar.setProgress(progress);
            percentText.setText(progress + "%");
            statusText.setText(DecompilerTaskManager.getStatusText());
            setVisible(statusText, statusText.length() > 0);
            String mem = DecompilerTaskManager.getMemoryText();
            memoryText.setText(mem.length() > 0 ? "Memory: " + mem : "");
            setVisible(memoryText, mem.length() > 0);
            logText.setText(colorize(DecompilerTaskManager.getLogText()));
            logScroll.post(new Runnable() {
                public void run() {
                    logScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        } else {
            setVisible(statusText, false);
            setVisible(memoryText, false);
            logText.setText("");
        }
    }

    /** Tints [ERROR], [WARN] and [STEP] lines so problems stand out; skipped for very large logs. */
    private CharSequence colorize(String log) {
        if (log == null) return "";
        int len = log.length();
        if (len > 120000) return log;
        SpannableStringBuilder sb = new SpannableStringBuilder(log);
        int start = 0;
        while (start < len) {
            int end = log.indexOf('\n', start);
            if (end < 0) end = len;
            int color = 0;
            boolean bold = false;
            if (log.startsWith("[ERROR]", start)) {
                color = p.danger;
            } else if (log.startsWith("[WARN]", start)) {
                color = p.warning;
            } else if (log.startsWith("[STEP", start)) {
                color = p.accent;
                bold = true;
            }
            if (color != 0 && end > start) {
                sb.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (bold) sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            start = end + 1;
        }
        return sb;
    }

    private boolean hasPartialOutput() {
        File dir = DecompilerTaskManager.getTargetDirectory(this);
        return dir != null && dir.exists() && dir.list() != null && dir.list().length > 0;
    }

    private void openSources() {
        File dir = DecompilerTaskManager.getTargetDirectory(this);
        if (dir == null || !dir.exists()) {
            toast("No output folder found");
            return;
        }
        Intent i = new Intent(this, NavigatorActivity.class);
        i.putExtra(Constants.EXTRA_ROOT, dir.getAbsolutePath());
        i.putExtra(Constants.EXTRA_TITLE, info.label);
        startActivity(i);
    }

    private void exportZip() {
        final File dir = DecompilerTaskManager.getTargetDirectory(this);
        if (dir == null || !dir.exists()) {
            toast("No output folder found");
            return;
        }
        final ProgressDialog dlg = new ProgressDialog(this);
        dlg.setMessage("Creating ZIP...");
        dlg.setCancelable(false);
        dlg.show();
        new Thread(new Runnable() {
            public void run() {
                File z = null;
                try {
                    z = ZipUtils.zipDir(dir, new File(Storage.archivesDir(DecompilerActivity.this), dir.getName() + ".zip"));
                } catch (Exception ignored) {
                }
                final File result = z;
                runOnUiThread(new Runnable() {
                    public void run() {
                        dlg.dismiss();
                        if (result == null) toast("ZIP export failed");
                        else ShareUtils.shareFile(DecompilerActivity.this, result, info.label);
                    }
                });
            }
        }).start();
    }
}
