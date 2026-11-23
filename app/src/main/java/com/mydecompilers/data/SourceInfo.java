/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.data;

import com.mydecompilers.utils.FileUtils;
import com.mydecompilers.utils.Storage;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Metadata describing one decompiled project folder (stored as info.json inside it). */
public class SourceInfo {

    public String packageLabel = "";
    public String packageName = "";
    public boolean hasJavaSource;
    public boolean hasXmlSource;
    public String createdAt = Storage.getDate();
    public String updatedAt = Storage.getDate();
    public long sourceSize;
    public File sourceDirectory = new File("");
    public String decompilerEngine = "";
    public String decompilerVersion = "";

    public SourceInfo() {
    }

    public SourceInfo(File dir) {
        this.sourceDirectory = dir;
    }

    public SourceInfo setPackageLabel(String v) { packageLabel = v; return this; }
    public SourceInfo setPackageName(String v) { packageName = v; return this; }
    public SourceInfo setJavaSourcePresence(boolean v) { hasJavaSource = v; return this; }
    public SourceInfo setXmlSourcePresence(boolean v) { hasXmlSource = v; return this; }
    public SourceInfo setSourceSize(long v) { sourceSize = v; return this; }

    public synchronized SourceInfo persist() {
        updatedAt = Storage.getDate();
        try {
            if (sourceDirectory.exists() && sourceDirectory.isDirectory()) {
                JSONObject json = new JSONObject();
                json.put("package_label", packageLabel);
                json.put("package_name", packageName);
                json.put("has_java_sources", hasJavaSource);
                json.put("has_xml_sources", hasXmlSource);
                json.put("created_at", createdAt);
                json.put("updated_at", updatedAt);
                json.put("source_size", sourceSize);
                json.put("decompiler_engine", decompilerEngine);
                json.put("decompiler_version", decompilerVersion);
                json.put("source_directory", sourceDirectory.getAbsolutePath());
                FileUtils.writeText(new File(sourceDirectory, "info.json"), json.toString(2));
            }
        } catch (Exception ignored) {
        }
        return this;
    }

    public boolean exists() {
        return hasJavaSource || hasXmlSource;
    }

    /** "dd-MM-yyyy HH:mm (engine)". */
    public String getDisplayTag() {
        String engine = decompilerEngine;
        if (engine == null || engine.length() == 0) {
            Matcher m = Pattern.compile("[\\[\\(]([a-zA-Z0-9_\\-]+)[\\]\\)]").matcher(sourceDirectory.getName());
            engine = m.find() ? m.group(1) : "jadx";
        }
        String date;
        try {
            String s = updatedAt.length() > 0 ? updatedAt : createdAt;
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = parser.parse(s);
            date = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(d);
        } catch (Exception e) {
            long last = sourceDirectory.exists() ? sourceDirectory.lastModified() : System.currentTimeMillis();
            date = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(new Date(last));
        }
        return date + " (" + engine + ")";
    }

    /** True if the directory looks like a decompiled project. */
    public static boolean exists(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        if (new File(dir, "info.json").exists()) return true;
        boolean hasSrc = new File(dir, "src").exists() || new File(dir, "java").exists()
                || new File(dir, "AndroidManifest.xml").exists();
        String[] kids = dir.list();
        return hasSrc && kids != null && kids.length > 0;
    }

    public static SourceInfo from(File dir) {
        SourceInfo info = new SourceInfo(dir);
        File infoFile = new File(dir, "info.json");
        info.packageLabel = dir.getName();
        info.packageName = dir.getName();
        info.hasJavaSource = true;
        if (infoFile.exists()) {
            try {
                JSONObject json = new JSONObject(FileUtils.readText(infoFile));
                info.packageLabel = json.optString("package_label", dir.getName());
                info.packageName = json.optString("package_name", dir.getName());
                info.hasJavaSource = json.optBoolean("has_java_sources", true);
                info.hasXmlSource = json.optBoolean("has_xml_sources", false);
                info.createdAt = json.optString("created_at", info.createdAt);
                info.updatedAt = json.optString("updated_at", info.updatedAt);
                info.sourceSize = json.optLong("source_size", 0L);
                info.decompilerEngine = json.optString("decompiler_engine", "");
                info.decompilerVersion = json.optString("decompiler_version", "");
            } catch (Exception ignored) {
            }
        }
        return info;
    }
}
