/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Typed wrapper around the app's SharedPreferences. */
public class UserPreferences {

    public static final String NAME = "user_preferences";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final boolean DEF_IGNORE_LIBRARIES = true;
    public static final boolean DEF_KEEP_INTERMEDIATE = false;
    public static final boolean DEF_SHOW_MEMORY = true;
    public static final int DEF_CHUNK_SIZE = 4000;

    private final SharedPreferences prefs;

    public UserPreferences(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean getIgnoreLibraries() { return prefs.getBoolean("ignoreLibraries", DEF_IGNORE_LIBRARIES); }
    public void setIgnoreLibraries(boolean v) { prefs.edit().putBoolean("ignoreLibraries", v).apply(); }

    public boolean getKeepIntermediateFiles() { return prefs.getBoolean("keepIntermediateFiles", DEF_KEEP_INTERMEDIATE); }
    public void setKeepIntermediateFiles(boolean v) { prefs.edit().putBoolean("keepIntermediateFiles", v).apply(); }

    /** Use the file paths stored in the APK (needed when resource names are obfuscated). Default on. */
    public boolean getKeepResourcePaths() { return prefs.getBoolean("keepResourcePaths", true); }
    public void setKeepResourcePaths(boolean v) { prefs.edit().putBoolean("keepResourcePaths", v).apply(); }

    public boolean getShowMemoryUsage() { return prefs.getBoolean("showMemoryUsage", DEF_SHOW_MEMORY); }
    public void setShowMemoryUsage(boolean v) { prefs.edit().putBoolean("showMemoryUsage", v).apply(); }

    public String getThemeMode() { return prefs.getString("themeMode", THEME_SYSTEM); }
    public void setThemeMode(String v) { prefs.edit().putString("themeMode", v).apply(); }

    public int getSelectedDecompilerIndex() { return prefs.getInt("selectedDecompilerIndex", 0); }
    public void setSelectedDecompilerIndex(int v) { prefs.edit().putInt("selectedDecompilerIndex", v).apply(); }

    public String getWorkingDirectoryPath() { return prefs.getString("workingDirectoryPath", ""); }
    public void setWorkingDirectoryPath(String v) { prefs.edit().putString("workingDirectoryPath", v == null ? "" : v).apply(); }

    public int getChunkSize() {
        try {
            int raw = Integer.parseInt(prefs.getString("chunkSize", String.valueOf(DEF_CHUNK_SIZE)).trim());
            return raw <= 500 ? DEF_CHUNK_SIZE : raw;
        } catch (Exception e) {
            return DEF_CHUNK_SIZE;
        }
    }
    public void setChunkSize(int v) { prefs.edit().putString("chunkSize", String.valueOf(v)).apply(); }

    /** Newline separated custom ignore list; empty means "use bundled ignored.list". */
    public String getCustomIgnoredList() { return prefs.getString("customIgnoredList", ""); }
    public void setCustomIgnoredList(String v) { prefs.edit().putString("customIgnoredList", v == null ? "" : v).apply(); }

    public Set<String> getHiddenHistoryPaths() {
        Set<String> s = prefs.getStringSet("hidden_history_paths", null);
        return s == null ? new HashSet<String>() : new HashSet<String>(s);
    }

    public void hideHistoryPath(String path) {
        Set<String> s = getHiddenHistoryPaths();
        s.add(path);
        prefs.edit().putStringSet("hidden_history_paths", s).apply();
    }

    public void unhideHistoryPath(String path) {
        Set<String> s = getHiddenHistoryPaths();
        s.remove(path);
        prefs.edit().putStringSet("hidden_history_paths", s).apply();
    }
}
