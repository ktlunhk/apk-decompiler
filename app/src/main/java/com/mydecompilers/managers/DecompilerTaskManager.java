/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.managers;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.mydecompilers.Constants;
import com.mydecompilers.data.PackageInfo;
import com.mydecompilers.decompilers.DecompilePipeline;
import com.mydecompilers.decompilers.StoppedException;
import com.mydecompilers.decompilers.TaskHost;
import com.mydecompilers.decompilers.TaskParams;
import com.mydecompilers.engines.DecompilerEngine;
import com.mydecompilers.engines.DecompilerRegistry;
import com.mydecompilers.services.DecompilerService;
import com.mydecompilers.utils.FileUtils;
import com.mydecompilers.utils.Storage;
import com.mydecompilers.utils.UserPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Process-wide owner of the single active decompile task. UI components register a
 * {@link Listener} and re-read the snapshot getters whenever it fires.
 */
public final class DecompilerTaskManager {

    public enum State { IDLE, RUNNING, COMPLETED, FAILED, STOPPED }

    public interface Listener {
        void onTaskChanged();
    }

    private static final int MAX_LOG_LINES = 3000;
    private static final Pattern PROGRESS = Pattern.compile("\\[(\\d+)/(\\d+)\\]");

    private static final Object LOCK = new Object();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<Listener>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile State state = State.IDLE;
    private static volatile boolean stopping = false;
    private static volatile int generation = 0;

    private static PackageInfo activePackage;
    private static String activeEngine = "";
    private static String folderName = "";
    private static String statusText = "";
    private static String memoryText = "";
    private static int phase = 0;
    private static int progress = 0;
    private static final ArrayList<String> LOG = new ArrayList<String>();
    private static Thread worker;
    private static boolean notifyScheduled = false;

    private DecompilerTaskManager() {
    }

    // ------------------------------------------------------------------ listeners

    public static void addListener(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void notifyChanged(boolean immediate) {
        synchronized (LOCK) {
            if (notifyScheduled && !immediate) return;
            notifyScheduled = true;
        }
        MAIN.postDelayed(new Runnable() {
            public void run() {
                synchronized (LOCK) {
                    notifyScheduled = false;
                }
                for (Listener l : LISTENERS) {
                    try {
                        l.onTaskChanged();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, immediate ? 0 : 150);
    }

    // ------------------------------------------------------------------ snapshot

    public static State getState() { return state; }
    public static boolean isStopping() { return stopping; }
    public static boolean isRunning() { return state == State.RUNNING; }
    public static PackageInfo getActivePackage() { synchronized (LOCK) { return activePackage; } }
    public static String getActiveEngine() { synchronized (LOCK) { return activeEngine; } }
    public static String getFolderName() { synchronized (LOCK) { return folderName; } }
    public static String getStatusText() { synchronized (LOCK) { return statusText; } }
    public static String getMemoryText() { synchronized (LOCK) { return memoryText; } }
    public static int getProgress() { synchronized (LOCK) { return progress; } }
    public static int getPhase() { synchronized (LOCK) { return phase; } }

    public static String getLogText() {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < LOG.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(LOG.get(i));
            }
            return sb.toString();
        }
    }

    public static boolean isActiveFor(PackageInfo info) {
        synchronized (LOCK) {
            return activePackage != null && info != null && activePackage.name.equals(info.name)
                    && (state == State.RUNNING || state == State.COMPLETED || state == State.FAILED || state == State.STOPPED);
        }
    }

    /** Directory the active/last task writes into. */
    public static File getTargetDirectory(Context context) {
        synchronized (LOCK) {
            if (folderName.length() == 0) return null;
            return new File(Storage.sourcesDir(context), folderName);
        }
    }

    // ------------------------------------------------------------------ logging

    private static void appendLog(String message) {
        if (message == null) return;
        synchronized (LOCK) {
            String[] lines = message.split("\n");
            for (String line : lines) {
                if (line.trim().length() == 0) continue;
                if (LOG.size() > 0 && LOG.get(LOG.size() - 1).equals(line)) continue;
                LOG.add(line);
                Matcher m = PROGRESS.matcher(line);
                if (phase == 2 && m.find()) {
                    try {
                        long done = Long.parseLong(m.group(1));
                        long total = Long.parseLong(m.group(2));
                        if (total > 0) progress = (int) (25 + (done * 60L) / total);
                    } catch (NumberFormatException ignored) {
                    }
                }
                statusText = line.startsWith("[") ? line.substring(line.indexOf(']') + 1).trim() : line;
            }
            while (LOG.size() > MAX_LOG_LINES) LOG.remove(0);
        }
        notifyChanged(false);
    }

    // ------------------------------------------------------------------ control

    /** Starts a decompile. Returns false if another task is still busy. */
    public static boolean start(Context context, PackageInfo info, String engineKey) {
        final Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (state == State.RUNNING || (worker != null && worker.isAlive())) return false;

            DecompilerEngine engine = DecompilerRegistry.getEngine(engineKey);
            stopping = false;
            generation++;
            state = State.RUNNING;
            activePackage = info;
            activeEngine = engineKey;
            folderName = Storage.generateSourceFolderName(info.label, engineKey);
            LOG.clear();
            statusText = "Getting ready...";
            memoryText = "";
            phase = 0;
            progress = 0;
            if (info.isSystemPackage) {
                LOG.add("[WARN] System app: bytecode may be ODEX/VDEX stripped, results can be incomplete");
            }
            LOG.add("[STEP 1/3] Initializing decompilation engine: " + (engine != null ? engine.getName() : engineKey));
        }

        final int myGeneration = generation;
        final PackageInfo pkg = info;
        final String engineId = engineKey;
        final String targetFolder = folderName;
        final UserPreferences prefs = new UserPreferences(app);
        final TaskParams params = new TaskParams();
        params.decompiler = engineId;
        params.name = pkg.name;
        params.label = pkg.label;
        params.targetFolderName = targetFolder;
        params.type = pkg.type;
        params.isExternalPackage = pkg.isExternalPackage;
        params.splitSourceDirs = pkg.splitSourceDirs;
        params.ignoreLibs = prefs.getIgnoreLibraries();
        params.customIgnoredList = prefs.getCustomIgnoredList();
        params.chunkSize = prefs.getChunkSize();
        params.keepIntermediateFiles = prefs.getKeepIntermediateFiles();
        params.keepResourcePaths = prefs.getKeepResourcePaths();
        final boolean showMemory = prefs.getShowMemoryUsage();

        Intent svc = new Intent(app, DecompilerService.class);
        svc.setAction(Constants.ACTION_START_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(svc);
            } else {
                app.startService(svc);
            }
        } catch (Exception ignored) {
            // The task still runs; it just may be killed sooner in the background.
        }

        Runnable job = new Runnable() {
            public void run() {
                TaskHost host = new TaskHost() {
                    public void log(String message) {
                        if (generation == myGeneration) appendLog(message);
                    }

                    public boolean isStopping() {
                        return stopping || generation != myGeneration;
                    }

                    public void phase(int p, String title) {
                        synchronized (LOCK) {
                            phase = p;
                            progress = p == 1 ? 5 : (p == 2 ? 25 : 90);
                        }
                        if (p > 1) appendLog("[STEP " + p + "/3] " + title + "...");
                        notifyChanged(true);
                    }
                };

                Thread memThread = null;
                if (showMemory) {
                    memThread = new Thread(new Runnable() {
                        public void run() {
                            while (state == State.RUNNING && generation == myGeneration) {
                                Runtime rt = Runtime.getRuntime();
                                long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                                long max = rt.maxMemory() / (1024 * 1024);
                                synchronized (LOCK) {
                                    memoryText = used + " MB / " + max + " MB";
                                }
                                notifyChanged(false);
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    return;
                                }
                            }
                        }
                    }, "decompiler-memory");
                    memThread.setDaemon(true);
                    memThread.start();
                }

                State result;
                try {
                    params.inputFile = prepareTargetFile(app, pkg);
                    DecompilePipeline pipeline = new DecompilePipeline(app, params, host);
                    pipeline.run();
                    result = State.COMPLETED;
                    if (generation == myGeneration) appendLog("[INFO] Done. Sources saved to " + pipeline.getWorkingDirectory().getAbsolutePath());
                } catch (StoppedException se) {
                    result = State.STOPPED;
                } catch (Throwable t) {
                    if (stopping || generation != myGeneration) {
                        result = State.STOPPED;
                    } else {
                        result = State.FAILED;
                        if (t instanceof OutOfMemoryError) {
                            appendLog("[ERROR] Out of memory. Enable 'Ignore library packages', lower the DEX chunk size in Settings, or choose another engine.");
                        }
                        String msg = t.getLocalizedMessage() != null ? t.getLocalizedMessage() : t.getClass().getSimpleName();
                        appendLog("[ERROR] Decompilation error: " + msg);
                        if (t.getCause() != null && t.getCause().getLocalizedMessage() != null) {
                            appendLog("[ERROR] Cause: " + t.getCause().getLocalizedMessage());
                        }
                        StackTraceElement[] st = t.getStackTrace();
                        for (int i = 0; i < Math.min(6, st.length); i++) appendLog("[ERROR]   at " + st[i]);
                    }
                }

                if (memThread != null) memThread.interrupt();
                synchronized (LOCK) {
                    if (generation == myGeneration) {
                        state = result;
                        if (result == State.COMPLETED) progress = 100;
                        statusText = result == State.COMPLETED ? "Completed" : (result == State.STOPPED ? "Stopped" : "Failed");
                        if (result == State.STOPPED) LOG.add("[INFO] Stopped. Files generated so far were kept as partial output");
                    }
                }
                notifyChanged(true);
            }
        };
        Thread t = new Thread(null, job, "decompiler-task", 64L * 1024L * 1024L);
        synchronized (LOCK) {
            worker = t;
        }
        t.start();
        notifyChanged(true);
        return true;
    }

    /** Requests the running task to stop. */
    public static void stop() {
        Thread t;
        synchronized (LOCK) {
            if (state != State.RUNNING) return;
            stopping = true;
            state = State.STOPPED;
            statusText = "Stopped";
            LOG.add("[INFO] Stop requested - files generated so far are kept as partial output");
            t = worker;
        }
        if (t != null) t.interrupt();
        notifyChanged(true);
    }

    /** Forgets a finished task so the UI goes back to the idle state. */
    public static void clear() {
        synchronized (LOCK) {
            if (state == State.RUNNING) return;
            state = State.IDLE;
            activePackage = null;
            folderName = "";
            LOG.clear();
            progress = 0;
            statusText = "";
            memoryText = "";
        }
        notifyChanged(true);
    }

    // ------------------------------------------------------------------ input file

    /** System apps may not be readable directly, so they are copied into the cache first. */
    private static File prepareTargetFile(Context context, PackageInfo info) {
        File direct = new File(info.filePath);
        if (!info.isSystemPackage && direct.exists() && direct.canRead()) return direct;

        File dest = new File(context.getCacheDir(), "system_target_" + info.name + ".apk");
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(info.name, 0);
            String[] candidates = new String[] { ai.publicSourceDir, ai.sourceDir, info.filePath };
            for (String path : candidates) {
                if (path == null) continue;
                File c = new File(path);
                if (c.exists() && c.canRead()) {
                    FileUtils.copy(c, dest);
                    if (dest.exists() && dest.length() > 0) return dest;
                }
            }
        } catch (Exception ignored) {
        }
        return direct.exists() ? direct : dest;
    }
}
