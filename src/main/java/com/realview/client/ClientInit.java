package com.realview.client;

import com.realview.client.gui.RvConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only registration (kept out of the main class so a dedicated server never loads client code). */
public final class ClientInit {
    private ClientInit() {}

    public static void init() {
        // Mods list -> RealView -> "Config" button opens our settings screen.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new RvConfigScreen(parent)));
    }
}
