/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.engines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central registry of all decompiler engines. */
public final class DecompilerRegistry {

    private static final Map<String, DecompilerEngine> ENGINES = new LinkedHashMap<String, DecompilerEngine>();

    static {
        register(new JadxEngine());
        register(new CfrEngine());
        register(new ProcyonEngine());
        register(new FernFlowerEngine());
    }

    private DecompilerRegistry() {
    }

    public static synchronized void register(DecompilerEngine engine) {
        ENGINES.put(engine.getId().toLowerCase(), engine);
    }

    public static synchronized List<DecompilerEngine> getAllEngines() {
        return new ArrayList<DecompilerEngine>(ENGINES.values());
    }

    public static synchronized DecompilerEngine getEngine(String id) {
        if (id == null) return null;
        return ENGINES.get(id.toLowerCase());
    }

    public static synchronized String[] getEngineNames() {
        List<DecompilerEngine> all = getAllEngines();
        String[] out = new String[all.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = all.get(i).getName() + " " + all.get(i).getVersion();
        }
        return out;
    }

    public static synchronized String[] getEngineIds() {
        List<DecompilerEngine> all = getAllEngines();
        String[] out = new String[all.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = all.get(i).getId();
        }
        return out;
    }
}
