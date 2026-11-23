/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.decompilers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.mydecompilers.data.PackageInfo;
import com.mydecompilers.data.SourceInfo;
import com.mydecompilers.engines.DecompilerEngine;
import com.mydecompilers.engines.DecompilerRegistry;
import com.mydecompilers.utils.FileUtils;
import com.mydecompilers.utils.Storage;
import com.googlecode.d2j.Method;
import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.dex.DexExceptionHandler;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.reader.DexFileReader;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ApkModuleXmlDecoder;
import com.reandroid.arsc.value.ResConfig;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedOdexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.objectweb.asm.MethodVisitor;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * The whole decompile run in three phases:
 *   1. APK / DEX / JAR  -> chunked .dex (and .jar for non-JaDX engines)
 *   2. bytecode         -> Java sources (with engine fallback)
 *   3. APK              -> AndroidManifest.xml + decoded resources + icon
 */
public class DecompilePipeline {

    private final Context context;
    private final TaskParams p;
    private final TaskHost host;

    private final File workingDirectory;
    private final File outputDexFiles;
    private final File outputJarFiles;
    private final File outputSrcDirectory;
    private final File outputJavaSrcDirectory;

    private final List<String> ignoredLibs = new ArrayList<String>();
    private boolean hasXml;

    public DecompilePipeline(Context context, TaskParams params, TaskHost host) {
        this.context = context;
        this.p = params;
        this.host = host;
        this.workingDirectory = new File(Storage.sourcesDir(context), params.targetFolderName);
        this.outputDexFiles = new File(workingDirectory, "dex-files");
        this.outputJarFiles = new File(workingDirectory, "jar-files");
        this.outputSrcDirectory = new File(workingDirectory, "src");
        this.outputJavaSrcDirectory = new File(outputSrcDirectory, "java");
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    private void checkStopped() {
        if (host.isStopping() || Thread.currentThread().isInterrupted()) throw new StoppedException();
    }

    private static void gc() {
        Runtime.getRuntime().gc();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ================================================================= run

    /** Runs every phase. Throws on fatal errors; returns normally on success. */
    public void run() throws Exception {
        FileUtils.deleteRecursively(workingDirectory);
        outputJavaSrcDirectory.mkdirs();
        outputDexFiles.mkdirs();
        outputJarFiles.mkdirs();

        // ---- phase 1
        host.phase(1, "Extracting bytecode");
        extractBytecode();
        checkStopped();
        boolean hasDex = listCount(outputDexFiles) > 0;
        boolean hasJar = listCount(outputJarFiles) > 0;
        if (!hasDex && !hasJar) {
            throw new Exception("No DEX bytecode or JAR files could be extracted from " + p.inputFile.getName()
                    + " (system app may be ODEX/VDEX stripped)");
        }

        // ---- phase 2
        host.phase(2, "Decompiling to Java");
        SourceInfo info = new SourceInfo(workingDirectory).setPackageLabel(p.label).setPackageName(p.name);
        info.decompilerEngine = p.decompiler;
        DecompilerEngine selected = DecompilerRegistry.getEngine(p.decompiler);
        info.decompilerVersion = selected != null ? selected.getVersion() : "1.0";
        info.persist();
        decompileJava();
        checkStopped();

        // ---- phase 3
        host.phase(3, "Extracting resources");
        extractResources();
        checkStopped();

        // ---- finish
        long javaFiles = FileUtils.countFilesWithExtension(outputJavaSrcDirectory, ".java");
        info.setJavaSourcePresence(javaFiles > 0)
                .setXmlSourcePresence(hasXml)
                .setSourceSize(FileUtils.sizeOf(workingDirectory))
                .persist();
        if (javaFiles == 0) {
            throw new Exception("No Java source files were generated. See the [ERROR]/[WARN] lines in the log above"
                    + " (large apps often need more memory: enable 'Ignore library packages' or try another engine).");
        }
        if (p.isExternalPackage && p.inputFile != null && p.inputFile.getParentFile() != null
                && p.inputFile.getParentFile().equals(context.getCacheDir())) {
            p.inputFile.delete();
        }
    }

    private static int listCount(File dir) {
        String[] l = dir.list();
        return l == null ? 0 : l.length;
    }

    // ================================================================= phase 1

    private void loadIgnoredLibs() {
        ignoredLibs.clear();
        if (!p.ignoreLibs) return;
        try {
            String raw = p.customIgnoredList == null ? "" : p.customIgnoredList;
            String[] lines;
            if (raw.trim().length() > 0) {
                lines = raw.split("\n");
            } else {
                InputStream in = context.getAssets().open("ignored.list");
                BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                List<String> tmp = new ArrayList<String>();
                String line;
                while ((line = r.readLine()) != null) tmp.add(line);
                r.close();
                lines = tmp.toArray(new String[tmp.size()]);
            }
            LinkedHashSet<String> parsed = new LinkedHashSet<String>();
            for (String l : lines) {
                String s = l.trim();
                if (s.length() == 0 || s.startsWith("#")) continue;
                if (s.startsWith("L")) s = s.substring(1);
                if (s.endsWith(";")) s = s.substring(0, s.length() - 1);
                s = s.replace('/', '.');
                while (s.startsWith(".")) s = s.substring(1);
                while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
                s = s.trim();
                if (s.length() > 0) parsed.add(s);
            }
            ignoredLibs.addAll(parsed);
            Collections.sort(ignoredLibs, new Comparator<String>() {
                public int compare(String a, String b) {
                    return b.length() - a.length();
                }
            });
            if (!ignoredLibs.isEmpty()) {
                host.log("[INFO] Ignoring " + ignoredLibs.size() + " third-party library package patterns");
            }
        } catch (Exception e) {
            host.log("[WARN] Could not load ignored libraries list: " + e.getMessage());
        }
    }

    private String findMatchedIgnoreRule(String classDescriptor) {
        if (!p.ignoreLibs || ignoredLibs.isEmpty()) return null;
        String n = classDescriptor;
        if (n.startsWith("L")) n = n.substring(1);
        if (n.endsWith(";")) n = n.substring(0, n.length() - 1);
        n = n.replace('/', '.');
        for (String rule : ignoredLibs) {
            if (n.equals(rule) || n.startsWith(rule + ".")) return rule;
        }
        return null;
    }

    private final List<ClassDef> classes = new ArrayList<ClassDef>();
    private final Map<String, Integer> skippedPackagesCount = new LinkedHashMap<String, Integer>();

    private void addClassesFromStream(InputStream in, String ext, boolean filterIgnored) {
        try {
            DexBackedDexFile dexFile;
            if ("dex".equals(ext)) {
                dexFile = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(in));
            } else {
                dexFile = DexBackedOdexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(in));
            }
            for (ClassDef classDef : dexFile.getClasses()) {
                String rule = (filterIgnored && p.ignoreLibs) ? findMatchedIgnoreRule(classDef.getType()) : null;
                if (rule != null) {
                    Integer c = skippedPackagesCount.get(rule);
                    skippedPackagesCount.put(rule, c == null ? 1 : c + 1);
                } else {
                    classes.add(classDef);
                }
            }
        } catch (Exception e) {
            host.log("[WARN] Failed to parse dex stream: " + e.getMessage());
        }
    }

    private void processInputFile(File file, boolean filterIgnored) {
        String ext = FileUtils.extension(file.getName());
        boolean zipLike = "apk".equals(ext) || "apks".equals(ext) || "xapk".equals(ext) || "zip".equals(ext)
                || p.type == PackageInfo.Type.APK;
        if (zipLike) {
            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(file);
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    checkStopped();
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String entryExt = FileUtils.extension(entry.getName());
                    if ("dex".equals(entryExt) || "odex".equals(entryExt)) {
                        InputStream in = zipFile.getInputStream(entry);
                        try {
                            addClassesFromStream(in, entryExt, filterIgnored);
                        } finally {
                            in.close();
                        }
                    } else if ("apk".equals(entryExt)) {
                        try {
                            ZipInputStream inner = new ZipInputStream(zipFile.getInputStream(entry));
                            try {
                                ZipEntry ie;
                                while ((ie = inner.getNextEntry()) != null) {
                                    if (!ie.isDirectory()) {
                                        String innerExt = FileUtils.extension(ie.getName());
                                        if ("dex".equals(innerExt) || "odex".equals(innerExt)) {
                                            addClassesFromStream(inner, innerExt, filterIgnored);
                                        }
                                    }
                                    inner.closeEntry();
                                }
                            } finally {
                                inner.close();
                            }
                        } catch (StoppedException se) {
                            throw se;
                        } catch (Exception e) {
                            host.log("[WARN] Error reading inner APK entry " + entry.getName() + ": " + e.getMessage());
                        }
                    }
                }
            } catch (StoppedException se) {
                throw se;
            } catch (Exception e) {
                host.log("[WARN] Error reading archive for " + file.getName() + ": " + e.getLocalizedMessage());
            } finally {
                if (zipFile != null) {
                    try { zipFile.close(); } catch (Exception ignored) { }
                }
            }
        } else if ("dex".equals(ext) || "odex".equals(ext) || p.type == PackageInfo.Type.DEX) {
            try {
                FileInputStream in = new FileInputStream(file);
                try {
                    addClassesFromStream(in, "odex".equals(ext) ? "odex" : "dex", filterIgnored);
                } finally {
                    in.close();
                }
            } catch (Exception e) {
                host.log("[WARN] Could not read " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private List<File> splitFiles() {
        List<File> out = new ArrayList<File>();
        if (p.splitSourceDirs != null) {
            for (String s : p.splitSourceDirs) {
                if (s == null || s.trim().length() == 0) continue;
                File f = new File(s);
                if (f.exists() && f.canRead()) out.add(f);
            }
        }
        return out;
    }

    private void convertApkToDex() throws Exception {
        gc();
        host.log("[INFO] Optimizing package...");
        classes.clear();
        skippedPackagesCount.clear();

        processInputFile(p.inputFile, p.ignoreLibs);
        List<File> splits = splitFiles();
        if (!splits.isEmpty()) {
            host.log("[INFO] Discovered " + splits.size() + " split APK packages to process");
            for (int i = 0; i < splits.size(); i++) {
                host.log("[INFO] Processing split APK [" + (i + 1) + "/" + splits.size() + "]: " + splits.get(i).getName());
                processInputFile(splits.get(i), p.ignoreLibs);
            }
        }

        if (p.ignoreLibs && !skippedPackagesCount.isEmpty()) {
            int total = 0;
            for (Map.Entry<String, Integer> e : skippedPackagesCount.entrySet()) {
                host.log("[INFO] Skipped " + e.getValue() + " classes in " + e.getKey());
                total += e.getValue();
            }
            host.log("[INFO] Ignored " + total + " library classes in " + skippedPackagesCount.size() + " packages");
        }

        if (classes.isEmpty() && p.ignoreLibs && !skippedPackagesCount.isEmpty()) {
            host.log("[WARN] Every class matched the ignore list; retrying without the ignore filter");
            skippedPackagesCount.clear();
            processInputFile(p.inputFile, false);
            for (File s : splits) processInputFile(s, false);
        }

        if (classes.isEmpty()) {
            host.log("[WARN] No DEX classes found in " + p.inputFile.getName());
            return;
        }
        checkStopped();

        host.log("[STEP] Writing DEX files (" + classes.size() + " classes)");
        int chunkSize = p.chunkSize <= 500 ? 4000 : p.chunkSize;
        int index = 0;
        for (int from = 0; from < classes.size(); from += chunkSize) {
            checkStopped();
            int to = Math.min(from + chunkSize, classes.size());
            List<ClassDef> chunk = new ArrayList<ClassDef>(classes.subList(from, to));
            if (!chunk.isEmpty()) {
                ImmutableDexFile dexFile = new ImmutableDexFile(Opcodes.getDefault(), chunk);
                DexFileFactory.writeDexFile(new File(outputDexFiles, index + ".dex").getCanonicalPath(), dexFile);
            }
            index++;
        }
        classes.clear();
    }

    private void convertDexToJar() throws Exception {
        checkStopped();
        gc();
        host.log("[STEP] Converting DEX to JAR");
        File[] dexFiles = outputDexFiles.listFiles();
        if (dexFiles == null) return;
        int index = 0;
        for (File dex : dexFiles) {
            checkStopped();
            if (dex.isFile()) {
                try {
                    DexFileReader reader = new DexFileReader(dex);
                    Dex2jar dex2jar = Dex2jar.from(reader)
                            .reUseReg(false)
                            .topoLogicalSort(false)
                            .skipDebug(true)
                            .optimizeSynchronized(true)
                            .printIR(false);
                    dex2jar.setExceptionHandler(new DexExceptionHandler() {
                        public void handleFileException(Exception e) {
                            host.log("[WARN] Dex2Jar: " + e);
                        }

                        public void handleMethodTranslateException(Method method, DexMethodNode node, MethodVisitor mv, Exception e) {
                            // single broken methods are skipped silently
                        }
                    });
                    dex2jar.to(new File(outputJarFiles, index + ".jar").toPath());
                } catch (Exception e) {
                    host.log("[WARN] Error converting " + dex.getName() + " to jar: " + e.getMessage());
                }
                if (!p.keepIntermediateFiles) dex.delete();
            }
            index++;
        }
    }

    private void extractBytecode() throws Exception {
        switch (p.type) {
            case APK:
            case DEX:
                loadIgnoredLibs();
                convertApkToDex();
                if (!"jadx".equals(p.decompiler)) {
                    convertDexToJar();
                }
                break;
            case JAR:
                FileUtils.copy(p.inputFile, new File(outputJarFiles, "0.jar"));
                if ("jadx".equals(p.decompiler)) {
                    host.log("[WARN] JaDX cannot read JAR input here; another engine will be used");
                }
                break;
        }
    }

    // ================================================================= phase 2

    private void decompileJava() {
        String target = p.decompiler == null ? "jadx" : p.decompiler;
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        keys.add(target);
        keys.add("jadx");
        keys.add("cfr");
        keys.add("procyon");
        keys.add("fernflower");

        boolean ok = false;
        for (String key : keys) {
            checkStopped();
            DecompilerEngine engine = DecompilerRegistry.getEngine(key);
            if (engine == null) continue;
            File inputDir = engine.requiresJarInput() ? outputJarFiles : outputDexFiles;
            if (!inputDir.exists() || listCount(inputDir) == 0) continue;
            try {
                host.log("[STEP 2/3] Decompiling Java classes using " + engine.getName() + "...");
                boolean success = engine.decompile(inputDir, outputJavaSrcDirectory, p.keepIntermediateFiles, host);
                checkStopped();
                long generated = FileUtils.countFilesWithExtension(outputJavaSrcDirectory, ".java");
                if (success || generated > 0) {
                    ok = true;
                    host.log("[INFO] Decompiled " + generated + " Java source files using " + engine.getName());
                    break;
                }
            } catch (StoppedException se) {
                throw se;
            } catch (Throwable t) {
                if (host.isStopping()) throw new StoppedException();
                if (t instanceof OutOfMemoryError) {
                    host.log("[ERROR] " + engine.getName() + " ran out of memory (heap limit "
                            + (Runtime.getRuntime().maxMemory() / (1024L * 1024L)) + " MB)");
                }
                host.log("[WARN] " + engine.getName() + " hit an issue: "
                        + (t.getLocalizedMessage() != null ? t.getLocalizedMessage() : t.getClass().getSimpleName())
                        + " (trying next engine)");
            }
        }

        if (!ok) {
            long generated = FileUtils.countFilesWithExtension(outputJavaSrcDirectory, ".java");
            if (generated > 0) {
                host.log("[INFO] Kept " + generated + " partially decompiled Java files");
            } else {
                host.log("[WARN] Decompilation finished with a notice: some classes could not be decompiled to Java");
            }
        }

        if (p.keepIntermediateFiles) {
            host.log("[INFO] Keeping intermediate .dex / .jar files");
        } else {
            FileUtils.deleteRecursively(outputDexFiles);
            FileUtils.deleteRecursively(outputJarFiles);
        }
        if (listCount(outputJavaSrcDirectory) == 0) {
            host.log("[WARN] No Java sources generated for " + p.inputFile.getName() + " (app may be resources-only or stripped)");
        }
    }

    // ================================================================= phase 3

    /** For APK bundles (.apks/.xapk) returns the embedded base APK; plain APKs are returned as is. */
    private File resolveApkForResources() throws Exception {
        String ext = FileUtils.extension(p.inputFile.getName());
        if ("apk".equals(ext)) return p.inputFile;
        ZipFile zf = new ZipFile(p.inputFile);
        try {
            ZipEntry best = null;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().toLowerCase().endsWith(".apk")) continue;
                if (e.getName().toLowerCase().endsWith("base.apk")) {
                    best = e;
                    break;
                }
                if (best == null || e.getSize() > best.getSize()) best = e;
            }
            if (best == null) return p.inputFile;
            File out = new File(context.getCacheDir(), "res_base_" + System.currentTimeMillis() + ".apk");
            InputStream in = zf.getInputStream(best);
            try {
                FileUtils.copy(in, out);
            } finally {
                in.close();
            }
            return out;
        } finally {
            zf.close();
        }
    }

    private void normalizeFile(File f) {
        if (!f.exists()) return;
        File temp = new File(f.getAbsolutePath() + ".normalized");
        BufferedReader in = null;
        BufferedWriter out = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                out.write(line);
                out.newLine();
            }
            in.close();
            out.close();
            in = null;
            out = null;
            f.delete();
            temp.renameTo(f);
        } catch (Exception e) {
            host.log("[WARN] Could not normalize " + f.getName() + ": " + e.getMessage());
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            if (temp.exists()) temp.delete();
        }
    }

    private Bitmap drawableToBitmap(Drawable d) {
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 192;
        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 192;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);
        return bmp;
    }

    private void saveIcon(ApkModule apkModule) {
        try {
            Bitmap bitmap = null;
            Drawable drawable = null;
            try {
                if (!p.isExternalPackage) drawable = context.getPackageManager().getApplicationIcon(p.name);
            } catch (Exception ignored) {
            }
            if (drawable != null) {
                bitmap = drawableToBitmap(drawable);
            } else {
                try {
                    android.content.pm.PackageInfo pi = context.getPackageManager()
                            .getPackageArchiveInfo(p.inputFile.getCanonicalPath(), 0);
                    if (pi != null && pi.applicationInfo != null) {
                        pi.applicationInfo.sourceDir = p.inputFile.getCanonicalPath();
                        pi.applicationInfo.publicSourceDir = p.inputFile.getCanonicalPath();
                        Drawable d = pi.applicationInfo.loadIcon(context.getPackageManager());
                        if (d != null) bitmap = drawableToBitmap(d);
                    }
                } catch (Exception ignored) {
                }
            }
            if (bitmap == null) {
                List<com.reandroid.apk.ResFile> resFiles = apkModule.listResFiles(
                        apkModule.getAndroidManifest().getIconResourceId(),
                        ResConfig.parse(ResConfig.Density.HDPI.toString()));
                if (resFiles != null && !resFiles.isEmpty()) {
                    InputStream in = resFiles.get(0).getInputSource().openStream();
                    try {
                        bitmap = BitmapFactory.decodeStream(in);
                    } finally {
                        in.close();
                    }
                }
            }
            if (bitmap != null) {
                FileOutputStream out = new FileOutputStream(new File(workingDirectory, "icon.png"));
                try {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } finally {
                    out.close();
                }
            }
        } catch (Exception e) {
            host.log("[WARN] Could not extract or save icon: " + e.getMessage());
        }
    }

    private void extractResources() {
        if (p.type != PackageInfo.Type.APK) return;
        File apkForRes = null;
        boolean tempApk = false;
        try {
            apkForRes = resolveApkForResources();
            tempApk = !apkForRes.equals(p.inputFile);
            ApkModule apkModule = ApkModule.loadApkFile(apkForRes);
            ApkModuleXmlDecoder xmlDecoder = new ApkModuleXmlDecoder(apkModule);
            xmlDecoder.setApkLogger(new APKLogger() {
                private long last = 0L;

                private void emit(String prefix, String msg) {
                    if (host.isStopping() || Thread.currentThread().isInterrupted()) {
                        throw new StoppedException();
                    }
                    if (msg == null || msg.trim().length() == 0) return;
                    long now = System.currentTimeMillis();
                    if (now - last >= 50L) {
                        host.log(prefix + msg);
                        last = now;
                    }
                }

                public void logMessage(String msg) {
                    emit("[INFO] ", msg);
                }

                public void logError(String msg, Throwable tr) {
                    emit("[WARN] ", msg);
                }

                public void logVerbose(String msg) {
                    emit("[INFO] ", msg);
                }
            });
            xmlDecoder.setKeepResPath(p.keepResourcePaths);
            if (p.keepResourcePaths) {
                host.log("[INFO] Keeping original resource file paths from the APK (files are under root/res)");
            }
            host.log("[STEP] Decoding AndroidManifest.xml");
            xmlDecoder.decodeAndroidManifest(workingDirectory);
            checkStopped();
            host.log("[STEP] Decoding resource table and XML layouts");
            xmlDecoder.decodeResourceTable(outputSrcDirectory);
            normalizeFile(new File(workingDirectory, "AndroidManifest.xml"));
            saveIcon(apkModule);
            hasXml = true;
        } catch (StoppedException se) {
            throw se;
        } catch (Throwable e) {
            if (host.isStopping()) throw new StoppedException();
            host.log("[WARN] Resources extraction notice: " + e.getLocalizedMessage());
            hasXml = new File(workingDirectory, "AndroidManifest.xml").exists();
        } finally {
            if (tempApk && apkForRes != null) apkForRes.delete();
        }
    }
}
