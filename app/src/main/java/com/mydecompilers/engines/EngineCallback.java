/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.engines;

/** Sink used by engines to report progress and to poll the stop flag. */
public interface EngineCallback {
    void log(String message);
    boolean isStopping();
}
