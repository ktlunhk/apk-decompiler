/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.decompilers;

/** Thrown inside the pipeline when the user pressed Stop. */
public class StoppedException extends RuntimeException {
    public StoppedException() {
        super("Decompilation stopped by user");
    }
}
