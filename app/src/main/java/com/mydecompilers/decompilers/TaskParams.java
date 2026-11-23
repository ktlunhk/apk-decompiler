/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.decompilers;

import com.mydecompilers.data.PackageInfo;

import java.io.File;

/** Everything the pipeline needs to know about one decompile run. */
public class TaskParams {
    public String decompiler = "jadx";
    public String name = "";
    public String label = "";
    public String targetFolderName = "";
    public File inputFile;
    public PackageInfo.Type type = PackageInfo.Type.APK;
    public boolean isExternalPackage;
    public String[] splitSourceDirs;
    public boolean ignoreLibs = true;
    public String customIgnoredList = "";
    public int chunkSize = 4000;
    public boolean keepIntermediateFiles;
    public boolean keepResourcePaths = true;
}
