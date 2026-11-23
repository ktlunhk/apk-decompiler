/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.engines;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.benf.cfr.reader.api.CfrDriver;

/** CFR: modern Java feature support (up to Java 21+). */
public class CfrEngine implements DecompilerEngine {

    public String getId() { return "cfr"; }
    public String getName() { return "CFR"; }
    public String getVersion() { return "0.152"; }
    public String getDescription() { return "Fast decompiler supporting modern Java features up to and including Java 21"; }
    public boolean requiresJarInput() { return true; }

    public boolean decompile(File inputDirectory, File outputDirectory, boolean keepIntermediateFiles, EngineCallback callback) throws Exception {
        Runtime.getRuntime().gc();
        File[] files = inputDirectory.listFiles();
        List<String> jarFiles = new ArrayList<String>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) jarFiles.add(f.getCanonicalPath());
            }
        }
        if (jarFiles.isEmpty()) return false;

        callback.log("[STEP] Decompiling " + jarFiles.size() + " JAR bytecode files with CFR...");
        Map<String, String> options = new HashMap<String, String>();
        options.put("outputdir", outputDirectory.getCanonicalPath());
        options.put("lomem", "true");
        CfrDriver driver = new CfrDriver.Builder().withOptions(options).build();
        driver.analyse(jarFiles);
        callback.log("[INFO] Finished CFR analysis on bytecode archives");

        String[] out = outputDirectory.list();
        return out != null && out.length > 0;
    }
}
