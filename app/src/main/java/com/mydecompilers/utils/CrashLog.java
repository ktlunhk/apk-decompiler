/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import android.content.Context;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Saves the last crash so it can be shown on the next start. */
public final class CrashLog {

    private CrashLog() {
    }

    private static File file(Context c) {
        return new File(Storage.privateDir(c), "last_crash.txt");
    }

    public static void save(Context c, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            FileUtils.writeText(file(c), "Thread crash at " + Storage.getDate() + "\n\n" + sw.toString());
        } catch (Throwable ignored) {
        }
    }

    public static String read(Context c) {
        try {
            File f = file(c);
            return f.exists() ? FileUtils.readText(f) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    public static void clear(Context c) {
        file(c).delete();
    }
}
