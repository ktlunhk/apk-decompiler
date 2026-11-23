/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Minimal read-only FileProvider replacement (no androidx needed).
 * URIs look like content://com.mydecompilers.share/<absolute path>.
 */
public class FileShareProvider extends ContentProvider {

    public static final String AUTHORITY = "com.mydecompilers.share";

    public static Uri uriFor(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .encodedPath(Uri.encode(file.getAbsolutePath(), "/"))
                .build();
    }

    public static String guessMime(String fileName) {
        String ext = FileUtils.extension(fileName);
        String mime = ext.length() == 0 ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime != null) return mime;
        if ("apk".equals(ext)) return "application/vnd.android.package-archive";
        if ("apks".equals(ext) || "xapk".equals(ext)) return "application/zip";
        if ("jar".equals(ext)) return "application/java-archive";
        if ("java".equals(ext) || "kt".equals(ext) || "smali".equals(ext) || "txt".equals(ext)) return "text/plain";
        return "application/octet-stream";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        Context c = getContext();
        if (c == null) throw new FileNotFoundException("No context");
        String path = uri.getPath();
        if (path == null || path.length() == 0) throw new FileNotFoundException("Empty path");
        try {
            File f = new File(path).getCanonicalFile();
            String p = f.getPath();
            File[] roots = new File[] {
                    Storage.appStorage(c), c.getExternalFilesDir(null), c.getFilesDir(), c.getCacheDir()
            };
            for (File root : roots) {
                if (root == null) continue;
                String rp = root.getCanonicalPath();
                if (p.equals(rp) || p.startsWith(rp + File.separator)) {
                    if (!f.exists()) throw new FileNotFoundException(p);
                    return f;
                }
            }
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        throw new FileNotFoundException("Path not shareable: " + path);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = resolve(uri);
            String[] cols = projection != null ? projection : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
            MatrixCursor cursor = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            }
            cursor.addRow(row);
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        return guessMime(path == null ? "" : new File(path).getName());
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
