/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.data;

import java.io.File;

/** One row in the source explorer. */
public class FileItem {
    public final File file;
    public final boolean isParent;

    public FileItem(File file, boolean isParent) {
        this.file = file;
        this.isParent = isParent;
    }

    public String getName() {
        return isParent ? ".." : file.getName();
    }
}
