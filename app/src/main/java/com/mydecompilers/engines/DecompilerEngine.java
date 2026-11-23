/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.engines;

import java.io.File;

/** Contract for every pluggable decompiler engine. */
public interface DecompilerEngine {
    String getId();
    String getName();
    String getVersion();
    String getDescription();
    boolean requiresJarInput();

    boolean decompile(File inputDirectory, File outputDirectory, boolean keepIntermediateFiles, EngineCallback callback) throws Exception;
}
