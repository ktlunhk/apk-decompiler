/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** ZIP helpers for exporting decompiled projects and unpacking decompiler output. */
public final class ZipUtils {

    private ZipUtils() {
    }

    /** Zips a directory (entries are rooted at the directory's own name). */
    public static File zipDir(File dir, File zipFile) throws IOException {
        if (zipFile.getParentFile() != null) zipFile.getParentFile().mkdirs();
        if (zipFile.exists()) zipFile.delete();
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            addToZip(dir.getName(), dir, zip, 0);
            zip.flush();
        } finally {
            zip.close();
        }
        return zipFile;
    }

    private static void addToZip(String path, File f, ZipOutputStream zip, int depth) throws IOException {
        if (depth > 60) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) return;
            for (File k : kids) {
                addToZip(path + "/" + k.getName(), k, zip, depth + 1);
            }
        } else {
            FileInputStream in = new FileInputStream(f);
            try {
                zip.putNextEntry(new ZipEntry(path));
                FileUtils.copy(in, zip);
                zip.closeEntry();
            } finally {
                in.close();
            }
        }
    }

    /** Extracts a ZIP file into targetDirectory (with zip-slip protection). */
    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        String targetCanonical = targetDirectory.getCanonicalPath();
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, entry.getName());
                String canonical = file.getCanonicalPath();
                if (!canonical.equals(targetCanonical) && !canonical.startsWith(targetCanonical + File.separator)) {
                    continue; // skip entries that try to escape the target directory
                }
                File dir = entry.isDirectory() ? file : file.getParentFile();
                if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to ensure directory: " + dir.getCanonicalPath());
                }
                if (entry.isDirectory()) continue;
                FileOutputStream out = new FileOutputStream(file);
                try {
                    FileUtils.copy(zis, out);
                } finally {
                    out.close();
                }
                long time = entry.getTime();
                if (time > 0) file.setLastModified(time);
            }
        } finally {
            zis.close();
        }
    }
}
