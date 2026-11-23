/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Small replacement for commons-io so no extra library is needed. */
public final class FileUtils {

    private FileUtils() {
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }

    public static void copy(InputStream in, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(dst);
        try {
            copy(in, out);
        } finally {
            out.close();
        }
    }

    public static void copy(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        try {
            copy(in, dst);
        } finally {
            in.close();
        }
    }

    public static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursively(k);
            }
        }
        f.delete();
    }

    public static long sizeOf(File f) {
        return sizeOf(f, 0);
    }

    private static long sizeOf(File f, int depth) {
        if (f == null || !f.exists() || depth > 40) return 0L;
        if (f.isFile()) return f.length();
        long total = 0L;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) total += sizeOf(k, depth + 1);
        }
        return total;
    }

    public static long countFilesWithExtension(File dir, String ext) {
        if (dir == null || !dir.exists()) return 0L;
        if (dir.isFile()) return dir.getName().endsWith(ext) ? 1L : 0L;
        long n = 0L;
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File k : kids) n += countFilesWithExtension(k, ext);
        }
        return n;
    }

    public static String readText(File f) throws IOException {
        return readText(f, Long.MAX_VALUE);
    }

    /** Reads at most maxBytes from the file as UTF-8 text. */
    public static String readText(File f, long maxBytes) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(f.length() + 16, 1 << 20));
            byte[] buf = new byte[16384];
            long remaining = maxBytes;
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                bos.write(buf, 0, n);
                remaining -= n;
            }
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    public static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
