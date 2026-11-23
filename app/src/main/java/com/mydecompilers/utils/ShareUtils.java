/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Helpers to share files with other apps or open them in an external viewer. */
public final class ShareUtils {

    private ShareUtils() {
    }

    public static void shareFile(Context context, File file, String chooserTitle) {
        try {
            Uri uri = FileShareProvider.uriFor(file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(FileShareProvider.guessMime(file.getName()));
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri(file.getName(), uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, chooserTitle);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Could not share: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void shareFiles(Context context, List<File> files, String chooserTitle) {
        try {
            ArrayList<Uri> uris = new ArrayList<Uri>();
            for (File f : files) uris.add(FileShareProvider.uriFor(f));
            Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
            send.setType("*/*");
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, chooserTitle);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Could not share: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void openExternally(Context context, File file) {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(FileShareProvider.uriFor(file), FileShareProvider.guessMime(file.getName()));
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(view);
        } catch (Exception e) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show();
        }
    }
}
