/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.decompilers;

import com.mydecompilers.engines.EngineCallback;

/** Receives progress from a running decompile pipeline. */
public interface TaskHost extends EngineCallback {
    /** Called when a major phase (1 = bytecode extraction, 2 = java, 3 = resources) begins. */
    void phase(int phase, String title);
}
