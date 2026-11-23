/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.database.Cursor;

import com.mydecompilers.utils.FileUtils;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Describes something that can be decompiled: an installed app, or an APK / JAR / DEX file. */
public class PackageInfo implements Parcelable {

    public enum Type { APK, JAR, DEX }

    public String label = "";
    public String name = "";
    public String version = "";
    public String filePath = "";
    public Type type = Type.APK;
    public boolean isSystemPackage;
    public boolean isExternalPackage;
    public boolean isSplitApk;
    public String[] splitSourceDirs;

    public PackageInfo() {
    }

    public PackageInfo(String label, String name, String version, String filePath, Type type,
                       boolean isSystemPackage, boolean isExternalPackage, boolean isSplitApk, String[] splitSourceDirs) {
        this.label = label;
        this.name = name;
        this.version = version;
        this.filePath = filePath;
        this.type = type;
        this.isSystemPackage = isSystemPackage;
        this.isExternalPackage = isExternalPackage;
        this.isSplitApk = isSplitApk;
        this.splitSourceDirs = splitSourceDirs;
    }

    // ---------------------------------------------------------------- Parcelable

    private PackageInfo(Parcel p) {
        label = p.readString();
        name = p.readString();
        version = p.readString();
        filePath = p.readString();
        type = Type.values()[p.readInt()];
        isSystemPackage = p.readInt() == 1;
        isExternalPackage = p.readInt() == 1;
        isSplitApk = p.readInt() == 1;
        splitSourceDirs = p.createStringArray();
    }

    public void writeToParcel(Parcel p, int flags) {
        p.writeString(label);
        p.writeString(name);
        p.writeString(version);
        p.writeString(filePath);
        p.writeInt(type.ordinal());
        p.writeInt(isSystemPackage ? 1 : 0);
        p.writeInt(isExternalPackage ? 1 : 0);
        p.writeInt(isSplitApk ? 1 : 0);
        p.writeStringArray(splitSourceDirs);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PackageInfo> CREATOR = new Parcelable.Creator<PackageInfo>() {
        public PackageInfo createFromParcel(Parcel in) {
            return new PackageInfo(in);
        }

        public PackageInfo[] newArray(int size) {
            return new PackageInfo[size];
        }
    };

    // ---------------------------------------------------------------- helpers

    public String typeLabel() {
        if (isSplitApk) return "APKS (" + (splitSourceDirs == null ? 1 : splitSourceDirs.length + 1) + ")";
        if (type == Type.JAR) return "JAR";
        if (type == Type.DEX) return "DEX";
        return "APK";
    }

    /** Loads the icon (installed app or archive). May return null. */
    public Drawable loadIcon(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            if (!isExternalPackage) {
                return pm.getApplicationIcon(name);
            }
        } catch (Exception ignored) {
        }
        try {
            android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(filePath, 0);
            if (pi != null && pi.applicationInfo != null) {
                pi.applicationInfo.sourceDir = filePath;
                pi.applicationInfo.publicSourceDir = filePath;
                return pi.applicationInfo.loadIcon(pm);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------- factories

    public static PackageInfo fromInstalled(Context context, android.content.pm.PackageInfo pack) {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo appInfo = pack.applicationInfo;
        String label = pack.packageName;
        try {
            CharSequence cs = pm.getApplicationLabel(appInfo);
            if (cs != null) label = cs.toString();
        } catch (Exception ignored) {
        }
        String[] splits = appInfo.splitPublicSourceDirs != null ? appInfo.splitPublicSourceDirs : appInfo.splitSourceDirs;
        boolean isSplit = splits != null && splits.length > 0;
        String version = pack.versionName != null ? pack.versionName : String.valueOf(pack.versionCode);
        boolean system = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        return new PackageInfo(label, pack.packageName, version, appInfo.sourceDir, Type.APK, system, false, isSplit, splits);
    }

    private static PackageInfo fromApk(Context context, File file) {
        PackageManager pm = context.getPackageManager();
        String label = file.getName();
        String name = jarPackageName(file.getName());
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        try {
            android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (pi != null) {
                name = pi.packageName;
                if (pi.versionName != null) version = pi.versionName;
                if (pi.applicationInfo != null) {
                    pi.applicationInfo.sourceDir = file.getAbsolutePath();
                    pi.applicationInfo.publicSourceDir = file.getAbsolutePath();
                    CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                    if (cs != null) label = cs.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return new PackageInfo(label, name, version, file.getAbsolutePath(), Type.APK, false, true, false, null);
    }

    private static PackageInfo fromArchiveBundle(File file) {
        // .apks / .xapk bundles: dex files are read straight from the zip, resources from the embedded base APK
        return new PackageInfo(file.getName(), jarPackageName(file.getName()),
                String.valueOf(System.currentTimeMillis() / 1000), file.getAbsolutePath(), Type.APK, false, true, false, null);
    }

    private static PackageInfo fromJarOrDex(File file, Type type, boolean external) {
        return new PackageInfo(file.getName(), jarPackageName(file.getName()),
                String.valueOf(System.currentTimeMillis() / 1000), file.getAbsolutePath(), type, false, external, false, null);
    }

    /** Builds a PackageInfo for a local file based on its extension, or null if unsupported. */
    public static PackageInfo fromFile(Context context, File file, boolean external) {
        String ext = FileUtils.extension(file.getName());
        if ("apk".equals(ext)) return fromApk(context, file);
        if ("apks".equals(ext) || "xapk".equals(ext) || "zip".equals(ext)) return fromArchiveBundle(file);
        if ("jar".equals(ext)) return fromJarOrDex(file, Type.JAR, external);
        if ("dex".equals(ext) || "odex".equals(ext)) return fromJarOrDex(file, Type.DEX, external);
        return null;
    }

    /** Copies a content/file Uri into the cache directory and describes it. Blocking: call from a worker thread. */
    public static PackageInfo fromUri(Context context, Uri uri) {
        String fileName = "imported_app.apk";
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null && new File(path).getName().length() > 0) fileName = new File(path).getName();
        } else if ("content".equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String n = cursor.getString(idx);
                        if (n != null && n.length() > 0) fileName = n;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        fileName = fileName.replace('/', '_');
        try {
            File temp = new File(context.getCacheDir(), fileName);
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            try {
                FileUtils.copy(in, temp);
            } finally {
                in.close();
            }
            PackageInfo info = fromFile(context, temp, true);
            if (info != null) info.isExternalPackage = true;
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    public static String jarPackageName(String fileName) {
        String slug = fileName.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() == 0) slug = "file";
        String hash = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(slug.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            hash = sb.substring(0, 8);
        } catch (Exception ignored) {
        }
        return slug + "-" + hash;
    }
}
