/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers;

/** Shared constants for notifications, services and extras. */
public final class Constants {

    private Constants() {
    }

    public static final String PROGRESS_CHANNEL = "com.mydecompilers.channel.progress";
    public static final String COMPLETION_CHANNEL = "com.mydecompilers.channel.completion";
    public static final int PROGRESS_NOTIFICATION_ID = 1094;
    public static final int COMPLETED_NOTIFICATION_ID = 1095;

    public static final String ACTION_STOP = "com.mydecompilers.action.STOP";
    public static final String ACTION_START_SERVICE = "com.mydecompilers.action.START_SERVICE";

    public static final String EXTRA_PACKAGE_INFO = "com.mydecompilers.extra.PACKAGE_INFO";
    public static final String EXTRA_PATH = "com.mydecompilers.extra.PATH";
    public static final String EXTRA_TITLE = "com.mydecompilers.extra.TITLE";
    public static final String EXTRA_ROOT = "com.mydecompilers.extra.ROOT";
}
