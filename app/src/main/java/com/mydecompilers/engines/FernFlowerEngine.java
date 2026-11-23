/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.engines;

import java.io.File;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import com.mydecompilers.utils.ZipUtils;

/** FernFlower analytical decompiler. */
public class FernFlowerEngine implements DecompilerEngine {

    public String getId() { return "fernflower"; }
    public String getName() { return "FernFlower"; }
    public String getVersion() { return "1.0.0"; }
    public String getDescription() { return "The first working analytical decompiler for Java"; }
    public boolean requiresJarInput() { return true; }

    public boolean decompile(File inputDirectory, File outputDirectory, boolean keepIntermediateFiles, EngineCallback callback) throws Exception {
        Runtime.getRuntime().gc();
        callback.log("[STEP] Starting FernFlower decompiler on bytecode archives...");
        ConsoleDecompiler.main(new String[] { inputDirectory.getCanonicalPath(), outputDirectory.getCanonicalPath() });

        File[] outputJars = outputDirectory.listFiles();
        if (outputJars == null) outputJars = new File[0];
        callback.log("[INFO] Extracting " + outputJars.length + " decompiled JAR archives...");
        for (File jar : outputJars) {
            if (jar.exists() && jar.isFile() && jar.getName().endsWith(".jar")) {
                ZipUtils.unzip(jar, outputDirectory);
                jar.delete();
            }
        }
        String[] out = outputDirectory.list();
        return out != null && out.length > 0;
    }
}
