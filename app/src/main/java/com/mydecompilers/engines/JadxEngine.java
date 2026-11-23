/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.engines;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JadxDecompiler.ProgressListener;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.loader.JadxPluginLoader;
import jadx.plugins.input.dex.DexInputPlugin;

import com.mydecompilers.utils.FileUtils;

/** JaDX: optimised for Android dex / apk input. */
public class JadxEngine implements DecompilerEngine {

    public String getId() { return "jadx"; }
    public String getName() { return "JaDX"; }
    public String getVersion() { return "1.5.6"; }
    public String getDescription() { return "Decompiler optimized for working with Android dex and apk files"; }
    public boolean requiresJarInput() { return false; }

    private static long mb(long bytes) {
        return bytes / (1024L * 1024L);
    }

    public boolean decompile(File inputDirectory, File outputDirectory, boolean keepIntermediateFiles, EngineCallback callback) throws Exception {
        if (callback.isStopping()) return false;
        Runtime.getRuntime().gc();

        List<File> inputs = new ArrayList<File>();
        File[] files = inputDirectory.listFiles();
        long totalBytes = 0L;
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                if (f.isFile()) {
                    inputs.add(f);
                    totalBytes += f.length();
                }
            }
        }
        if (inputs.isEmpty()) return false;

        long maxHeap = Runtime.getRuntime().maxMemory();
        callback.log("[INFO] JaDX input: " + inputs.size() + " dex file(s), " + mb(totalBytes) + " MB; heap limit " + mb(maxHeap) + " MB");

        // JaDX needs roughly 15-20x the dex size in heap when everything is loaded at once.
        boolean perFile = inputs.size() > 1 && totalBytes * 20L > maxHeap;
        if (perFile) {
            callback.log("[WARN] Large app: decompiling one DEX chunk at a time to save memory");
        } else {
            try {
                runJadx(inputs, outputDirectory, callback, "");
            } catch (Throwable t) {
                if (callback.isStopping()) return false;
                reportFailure(callback, t);
                if (inputs.size() > 1) {
                    callback.log("[WARN] Retrying with the low-memory mode (one DEX chunk at a time)");
                    perFile = true;
                }
            }
        }

        if (perFile) {
            for (int i = 0; i < inputs.size(); i++) {
                if (callback.isStopping()) return false;
                Runtime.getRuntime().gc();
                try {
                    runJadx(inputs.subList(i, i + 1), outputDirectory, callback, "(dex " + (i + 1) + "/" + inputs.size() + ") ");
                } catch (Throwable t) {
                    if (callback.isStopping()) return false;
                    callback.log("[WARN] DEX chunk " + (i + 1) + " failed");
                    reportFailure(callback, t);
                }
            }
        }

        if (inputDirectory.exists() && inputDirectory.isDirectory() && !keepIntermediateFiles) {
            FileUtils.deleteRecursively(inputDirectory);
        }
        String[] out = outputDirectory.list();
        return out != null && out.length > 0;
    }

    private static void reportFailure(EngineCallback callback, Throwable t) {
        if (t instanceof OutOfMemoryError) {
            callback.log("[ERROR] JaDX ran out of memory (heap limit " + mb(Runtime.getRuntime().maxMemory())
                    + " MB). Enable 'Ignore library packages', lower the DEX chunk size, or try CFR / Procyon.");
        } else {
            callback.log("[ERROR] JaDX failed: " + (t.getLocalizedMessage() != null ? t.getLocalizedMessage() : t.getClass().getName()));
        }
    }

    private void runJadx(List<File> inputs, File outputDirectory, final EngineCallback callback, final String prefix) throws Exception {
        JadxArgs args = new JadxArgs();
        args.setOutDirSrc(outputDirectory);
        args.setSkipResources(true);
        args.setInputFiles(new ArrayList<File>(inputs));
        args.setThreadsCount(1);
        // Register the dex input plugin explicitly: AIDE does not package META-INF/services from jars.
        args.setPluginLoader(new JadxPluginLoader() {
            public List<JadxPlugin> load() {
                List<JadxPlugin> plugins = new ArrayList<JadxPlugin>();
                plugins.add(new DexInputPlugin());
                return plugins;
            }

            public void close() {
            }
        });

        JadxDecompiler jadx = null;
        try {
            jadx = new JadxDecompiler(args);
            callback.log("[STEP] " + prefix + "Loading DEX classes for JaDX decompilation...");
            jadx.load();
            final List<JavaClass> classes = jadx.getClasses();
            final int totalClasses = classes.size();
            callback.log("[STEP] " + prefix + "Decompiling " + totalClasses + " Java source classes");
            jadx.save(50, new ProgressListener() {
                public void progress(long done, long total) {
                    if (callback.isStopping() || Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException("Decompilation stopped by user");
                    }
                    int doneInt = (int) done;
                    int safeTotal = total > 0L ? (int) total : totalClasses;
                    int idx = Math.max(0, Math.min(doneInt - 1, Math.max(totalClasses - 1, 0)));
                    String clsName = (totalClasses > 0 && idx < totalClasses)
                            ? classes.get(idx).getFullName() + ".java"
                            : "Class " + doneInt;
                    callback.log("[INFO] " + prefix + "Decompiling Java [" + doneInt + "/" + safeTotal + "]: " + clsName);
                }
            });
        } finally {
            try {
                if (jadx != null) jadx.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
