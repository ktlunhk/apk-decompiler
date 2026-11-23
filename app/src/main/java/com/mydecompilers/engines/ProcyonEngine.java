/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.engines;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.DeobfuscationUtilities;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.assembler.metadata.MetadataParser;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.core.StringUtilities;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.java.JavaFormattingOptions;

/** Procyon: accurate analytical decompiler. */
public class ProcyonEngine implements DecompilerEngine {

    public String getId() { return "procyon"; }
    public String getName() { return "Procyon"; }
    public String getVersion() { return "0.6.0"; }
    public String getDescription() { return "Highly accurate analytical decompiler with clean output for complex Java classes"; }
    public boolean requiresJarInput() { return true; }

    /** Caches failed lookups so the same missing type is not retried forever. */
    private static class NoRetryMetadataSystem extends MetadataSystem {
        private final Set<String> failedTypes = new HashSet<String>();

        NoRetryMetadataSystem(ITypeLoader typeLoader) {
            super(typeLoader);
        }

        @Override
        protected TypeDefinition resolveType(String descriptor, boolean mightBePrimitive) {
            if (failedTypes.contains(descriptor)) return null;
            TypeDefinition result = super.resolveType(descriptor, mightBePrimitive);
            if (result == null) failedTypes.add(descriptor);
            return result;
        }
    }

    public boolean decompile(File inputDirectory, File outputDirectory, boolean keepIntermediateFiles, EngineCallback callback) throws Exception {
        Runtime.getRuntime().gc();
        File[] files = inputDirectory.listFiles();
        if (files == null) return false;

        boolean anyJar = false;
        for (File inJar : files) {
            if (!inJar.isFile() || !inJar.getName().endsWith(".jar")) continue;
            anyJar = true;
            JarFile jfile = null;
            try {
                jfile = new JarFile(inJar);
                DecompilerSettings settings = DecompilerSettings.javaDefaults();
                settings.setTypeLoader(new CompositeTypeLoader(new JarTypeLoader(jfile), new InputTypeLoader()));
                settings.setFormattingOptions(JavaFormattingOptions.createDefault());
                settings.setForceExplicitImports(true);
                settings.setShowSyntheticMembers(false);

                int classesDecompiled = 0;
                NoRetryMetadataSystem metadataSystem = new NoRetryMetadataSystem(settings.getTypeLoader());
                metadataSystem.setEagerMethodLoadingEnabled(true);

                Enumeration<JarEntry> entries = jfile.entries();
                while (entries.hasMoreElements()) {
                    if (callback.isStopping() || Thread.currentThread().isInterrupted()) {
                        return false;
                    }
                    if (++classesDecompiled % 100 == 0) {
                        metadataSystem = new NoRetryMetadataSystem(settings.getTypeLoader());
                        metadataSystem.setEagerMethodLoadingEnabled(true);
                    }
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) continue;

                    String internalName = StringUtilities.removeRight(name, ".class");
                    File outf = new File(outputDirectory, internalName + ".java");
                    if (outf.getParentFile() != null) outf.getParentFile().mkdirs();
                    outf.createNewFile();

                    FileOutputStream stream = new FileOutputStream(outf);
                    OutputStreamWriter writer = new OutputStreamWriter(stream, "UTF-8");
                    boolean keep = false;
                    try {
                        PlainTextOutput output = new PlainTextOutput(writer);
                        TypeReference type;
                        if (internalName.length() == 1) {
                            MetadataParser parser = new MetadataParser(IMetadataResolver.EMPTY);
                            TypeReference reference = parser.parseTypeDescriptor(internalName);
                            type = metadataSystem.resolve(reference);
                        } else {
                            type = metadataSystem.lookupType(internalName);
                        }

                        if (type != null) {
                            TypeDefinition resolvedType = type.resolve();
                            if (resolvedType != null) {
                                DeobfuscationUtilities.processType(resolvedType);
                                DecompilationOptions options = new DecompilationOptions();
                                options.setSettings(settings);
                                options.setFullDecompilation(true);
                                if (!(resolvedType.isAnonymous() || resolvedType.isSynthetic() || resolvedType.isNested())) {
                                    callback.log("[INFO] Decompiling Java: " + internalName.replace('/', '.') + ".java");
                                    settings.getLanguage().decompileType(resolvedType, output, options);
                                    keep = true;
                                }
                            }
                        } else {
                            output.writeLine("!!! ERROR: Failed to load class %s.", internalName);
                            keep = true;
                        }
                        writer.flush();
                    } finally {
                        try { writer.close(); } catch (Exception ignored) { }
                        try { stream.close(); } catch (Exception ignored) { }
                    }
                    if (!keep) outf.delete();
                }
            } catch (Exception e) {
                if (!callback.isStopping()) {
                    callback.log("[WARN] Procyon processing exception: " + e.getMessage());
                }
            } finally {
                if (jfile != null) {
                    try { jfile.close(); } catch (Exception ignored) { }
                }
            }
        }
        if (!anyJar) return false;
        String[] out = outputDirectory.list();
        return out != null && out.length > 0;
    }
}
