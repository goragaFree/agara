package dev.lucid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the mod.
 * This is not an entrypoint - the mod is client-only, see {@link dev.lucid.client.LucidClient}.
 */
public final class Lucid {
    public static final String MOD_ID = "lucid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Lucid() {
    }
}
