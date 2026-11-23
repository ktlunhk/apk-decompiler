/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Where decompiled projects, extracted APKs and archives live. */
public final class Storage {

    private Storage() {
    }

    /** Public Download/ApkDecompilerPro folder (needs storage permission on Android 6+). */
    public static File defaultDownloadDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "myAPKDecompiler");
    }

    /** App-private external folder, always writable. */
    public static File privateDir(Context context) {
        File ext = context.getExternalFilesDir(null);
        if (ext != null) {
            if (!ext.exists()) ext.mkdirs();
            return ext;
        }
        return context.getFilesDir();
    }

    private static boolean usable(File f) {
        return (f.exists() || f.mkdirs()) && f.isDirectory() && f.canWrite();
    }

    /** Custom folder if set and writable, else Download/ApkDecompilerPro, else the app-private folder. */
    public static File appStorage(Context context) {
        String custom = new UserPreferences(context).getWorkingDirectoryPath();
        if (custom != null && custom.trim().length() > 0) {
            File f = new File(custom.trim());
            if (usable(f)) return f;
        }
        File dl = defaultDownloadDir();
        if (usable(dl)) return dl;
        return privateDir(context);
    }

    /** Every "sources" folder that may hold projects (current location + app-private fallback). */
    public static java.util.List<File> allSourceDirs(Context context) {
        java.util.List<File> out = new java.util.ArrayList<File>();
        File a = sourcesDir(context);
        out.add(a);
        File b = new File(privateDir(context), "sources");
        File c = new File(defaultDownloadDir(), "sources");
        try {
            if (b.isDirectory() && !b.getCanonicalPath().equals(a.getCanonicalPath())) out.add(b);
            if (c.isDirectory() && !c.getCanonicalPath().equals(a.getCanonicalPath()) && !c.getCanonicalPath().equals(b.getCanonicalPath())) out.add(c);
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Converts an ACTION_OPEN_DOCUMENT_TREE result into a file path, or null if not possible. */
    public static File treeUriToFile(Uri tree) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(tree);
            int colon = docId.indexOf(':');
            String vol = colon < 0 ? docId : docId.substring(0, colon);
            String rel = colon < 0 ? "" : docId.substring(colon + 1);
            File base = "primary".equalsIgnoreCase(vol) ? Environment.getExternalStorageDirectory() : new File("/storage/" + vol);
            return rel.length() == 0 ? base : new File(base, rel);
        } catch (Exception e) {
            return null;
        }
    }

    public static File sourcesDir(Context context) {
        File d = new File(appStorage(context), "sources");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File extractedDir(Context context) {
        File d = new File(appStorage(context), "extracted");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File archivesDir(Context context) {
        File d = new File(appStorage(context), "archives");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String getDate() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    public static String sanitize(String s, int maxLen) {
        String clean = s == null ? "" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (clean.length() > maxLen) clean = clean.substring(0, maxLen);
        clean = clean.replaceAll("^_+|_+$", "");
        return clean.length() == 0 ? "app" : clean;
    }

    /** e.g. MyApp_20260101_1200_[jadx] — the bracket tag is parsed back by SourceInfo. */
    public static String generateSourceFolderName(String label, String engine) {
        String time = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
        String cleanEngine = engine == null ? "" : engine.replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (cleanEngine.length() == 0) cleanEngine = "decompiler";
        return sanitize(label, 30) + "_" + time + "_[" + cleanEngine + "]";
    }
}
