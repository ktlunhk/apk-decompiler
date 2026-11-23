/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.mydecompilers.Constants;
import com.mydecompilers.data.PackageInfo;
import com.mydecompilers.engines.DecompilerRegistry;
import com.mydecompilers.managers.DecompilerTaskManager;
import com.mydecompilers.utils.ShareUtils;
import com.mydecompilers.utils.Storage;
import com.mydecompilers.utils.UserPreferences;
import com.mydecompilers.utils.ZipUtils;

import java.io.File;

/** Configure, run, monitor and finish one decompilation. */
public class DecompilerActivity extends BaseActivity implements DecompilerTaskManager.Listener {

    private PackageInfo info;
    private UserPreferences prefs;

    private Spinner engineSpinner;
    private TextView engineDesc;
    private Button startButton, stopButton, openButton, zipButton, copyLogButton, againButton;
    private ProgressBar progressBar;
    private TextView statusText, memoryText, logText, stateText;
    private ScrollView logScroll;
    private LinearLayout configRow;

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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(8));

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        IconLoader.load(this, info, icon);
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, 0, 0);
        titleCol.addView(text(info.label, 18, true));
        TextView pkg = text(info.name, 12, false);
        titleCol.addView(pkg);
        titleCol.addView(text(info.version + "  \u2022  " + info.typeLabel() + (info.isSystemPackage ? "  \u2022  system app" : ""), 12, false));
        header.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, matchWrap());

        // engine selection
        configRow = new LinearLayout(this);
        configRow.setOrientation(LinearLayout.VERTICAL);
        configRow.setPadding(0, dp(12), 0, 0);
        configRow.addView(text("Decompiler engine", 14, true));
        engineSpinner = new Spinner(this);
        engineSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                DecompilerRegistry.getEngineNames()));
        int idx = prefs.getSelectedDecompilerIndex();
        if (idx < 0 || idx >= DecompilerRegistry.getEngineIds().length) idx = 0;
        if (info.type == PackageInfo.Type.JAR && "jadx".equals(DecompilerRegistry.getEngineIds()[idx])) idx = 1;
        engineSpinner.setSelection(idx);
        engineSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                engineDesc.setText(DecompilerRegistry.getAllEngines().get(position).getDescription());
            }

            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        configRow.addView(engineSpinner);
        engineDesc = text(DecompilerRegistry.getAllEngines().get(idx).getDescription(), 12, false);
        configRow.addView(engineDesc);
        root.addView(configRow, matchWrap());

        // buttons
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        startButton = button("Start", new View.OnClickListener() {
            public void onClick(View v) {
                startTask();
            }
        });
        stopButton = button("Stop", new View.OnClickListener() {
            public void onClick(View v) {
                DecompilerTaskManager.stop();
            }
        });
        againButton = button("Decompile again", new View.OnClickListener() {
            public void onClick(View v) {
                DecompilerTaskManager.clear();
                render();
            }
        });
        buttons.addView(startButton, weight(1f));
        buttons.addView(stopButton, weight(1f));
        buttons.addView(againButton, weight(1f));
        root.addView(buttons, matchWrap());

        // status
        stateText = text("", 14, true);
        root.addView(stateText, matchWrap());
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, matchWrap());
        statusText = text("", 12, false);
        statusText.setSingleLine(true);
        root.addView(statusText, matchWrap());
        memoryText = text("", 12, false);
        root.addView(memoryText, matchWrap());

        // log
        logScroll = new ScrollView(this);
        logText = new TextView(this);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(4), dp(4), dp(4), dp(4));
        logScroll.addView(logText);
        root.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // result buttons
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        openButton = button("Open sources", new View.OnClickListener() {
            public void onClick(View v) {
                openSources();
            }
        });
        zipButton = button("Export ZIP", new View.OnClickListener() {
            public void onClick(View v) {
                exportZip();
            }
        });
        copyLogButton = button("Copy log", new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("log", DecompilerTaskManager.getLogText()));
                toast("Log copied");
            }
        });
        result.addView(openButton, weight(1f));
        result.addView(zipButton, weight(1f));
        result.addView(copyLogButton, weight(1f));
        root.addView(result, matchWrap());

        setContentView(root);
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
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
        int pos = engineSpinner.getSelectedItemPosition();
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

        setVisible(configRow, !running && !finished);
        setVisible(startButton, !running && !finished);
        startButton.setEnabled(!otherRunning);
        setVisible(stopButton, running);
        setVisible(againButton, finished);
        setVisible(progressBar, mine && s != DecompilerTaskManager.State.IDLE);
        setVisible(openButton, mine && s == DecompilerTaskManager.State.COMPLETED || finished && hasPartialOutput());
        setVisible(zipButton, mine && s == DecompilerTaskManager.State.COMPLETED || finished && hasPartialOutput());
        setVisible(copyLogButton, mine);

        if (otherRunning) {
            PackageInfo other = DecompilerTaskManager.getActivePackage();
            stateText.setText("Another decompilation is running" + (other != null ? " (" + other.label + ")" : ""));
        } else if (!mine) {
            stateText.setText("Ready");
        } else if (s == DecompilerTaskManager.State.RUNNING) {
            stateText.setText("Decompiling with " + DecompilerTaskManager.getActiveEngine() + "...");
        } else if (s == DecompilerTaskManager.State.COMPLETED) {
            stateText.setText("Decompilation finished");
        } else if (s == DecompilerTaskManager.State.STOPPED) {
            stateText.setText("Stopped - partial output was kept");
        } else if (s == DecompilerTaskManager.State.FAILED) {
            stateText.setText("Decompilation failed");
        } else {
            stateText.setText("Ready");
        }

        if (mine) {
            progressBar.setProgress(DecompilerTaskManager.getProgress());
            statusText.setText(DecompilerTaskManager.getStatusText());
            memoryText.setText(DecompilerTaskManager.getMemoryText().length() > 0
                    ? "Memory: " + DecompilerTaskManager.getMemoryText() : "");
            logText.setText(DecompilerTaskManager.getLogText());
            logScroll.post(new Runnable() {
                public void run() {
                    logScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        } else {
            statusText.setText("");
            memoryText.setText("");
            logText.setText("");
        }
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
