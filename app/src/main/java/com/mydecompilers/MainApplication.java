/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers;

import android.app.Application;

import com.mydecompilers.utils.FileUtils;

import java.io.File;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable ex) {
                com.mydecompilers.utils.CrashLog.save(MainApplication.this, ex);
                if (previous != null) previous.uncaughtException(thread, ex);
            }
        });
        // Remove temporary copies left behind by a previous run that was killed
        new Thread(new Runnable() {
            public void run() {
                try {
                    File[] files = getCacheDir().listFiles();
                    if (files == null) return;
                    for (File f : files) {
                        String n = f.getName();
                        if (n.startsWith("system_target_") || n.startsWith("res_base_")) {
                            FileUtils.deleteRecursively(f);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
