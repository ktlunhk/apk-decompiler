/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import android.content.Context;

import com.mydecompilers.data.PackageInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Extracting and sharing installed APKs (plain or split bundles). */
public final class ApkUtils {

    private ApkUtils() {
    }

    private static File uniqueFile(File dir, String base, String ext) {
        File initial = new File(dir, base + "." + ext);
        if (!initial.exists()) return initial;
        int i = 1;
        while (true) {
            File candidate = new File(dir, base + " (" + i + ")." + ext);
            if (!candidate.exists()) return candidate;
            i++;
        }
    }

    /** Copies the app's APK (or bundles its splits into an .apks zip) into the "extracted" folder. */
    public static File extractPackage(Context context, PackageInfo info) {
        try {
            File targetDir = Storage.extractedDir(context);
            String base = info.label.replaceAll("[^a-zA-Z0-9_.-]", "_") + "_" + info.version.replaceAll("[^a-zA-Z0-9_.-]", "_");

            if (info.isSplitApk && info.splitSourceDirs != null && info.splitSourceDirs.length > 0) {
                File zipFile = uniqueFile(targetDir, base, "apks");
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
                try {
                    File baseFile = new File(info.filePath);
                    if (baseFile.exists()) addEntry(zos, "base.apk", baseFile);
                    for (int i = 0; i < info.splitSourceDirs.length; i++) {
                        File split = new File(info.splitSourceDirs[i]);
                        if (split.exists()) {
                            addEntry(zos, split.getName().endsWith(".apk") ? split.getName() : "split_" + i + ".apk", split);
                        }
                    }
                } finally {
                    zos.close();
                }
                return zipFile;
            }

            File src = new File(info.filePath);
            if (!src.exists()) return null;
            File dst = uniqueFile(targetDir, base, "apk");
            FileUtils.copy(src, dst);
            return dst;
        } catch (Exception e) {
            return null;
        }
    }

    private static void addEntry(ZipOutputStream zos, String name, File f) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        FileInputStream in = new FileInputStream(f);
        try {
            FileUtils.copy(in, zos);
        } finally {
            in.close();
        }
        zos.closeEntry();
    }

    /** Shares the installed package. Installed APKs live outside our storage, so a copy is made first. */
    public static void sharePackage(Context context, PackageInfo info) {
        File extracted = extractPackage(context, info);
        if (extracted == null) {
            android.widget.Toast.makeText(context, "Could not read the package file", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        List<File> one = new ArrayList<File>();
        one.add(extracted);
        ShareUtils.shareFile(context, extracted, info.label);
    }
}
