package com.adikan;

import net.fabricmc.api.ClientModInitializer;  // ← change this
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineberryBanMod implements ClientModInitializer {  // ← and this
    public static final String MOD_ID = "mineberrybanmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {  // ← and this method name
        LOGGER.info("MineberryBanMod loaded!");
        ClientCommandHandler.register();
    }
}