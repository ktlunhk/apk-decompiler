/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.mydecompilers.data.PackageInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads app icons on a small thread pool and caches them in memory. */
public final class IconLoader {

    private static final ConcurrentHashMap<String, Drawable> CACHE = new ConcurrentHashMap<String, Drawable>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private IconLoader() {
    }

    public static void load(final Context context, final PackageInfo info, final ImageView view) {
        final String key = info.name + "|" + info.filePath;
        view.setTag(key);
        Drawable cached = CACHE.get(key);
        if (cached != null) {
            view.setImageDrawable(cached);
            return;
        }
        view.setImageDrawable(context.getPackageManager().getDefaultActivityIcon());
        POOL.execute(new Runnable() {
            public void run() {
                final Drawable d = info.loadIcon(context.getApplicationContext());
                if (d == null) return;
                CACHE.put(key, d);
                MAIN.post(new Runnable() {
                    public void run() {
                        if (key.equals(view.getTag())) view.setImageDrawable(d);
                    }
                });
            }
        });
    }
}
