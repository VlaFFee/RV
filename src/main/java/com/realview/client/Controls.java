package com.realview.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.realview.Config;
import com.realview.RealView;
import com.realview.client.gui.RvConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Keys and the /realview command. The same screen is also reachable from Mods -> RealView -> Config. */
public final class Controls {
    public static final KeyMapping OPEN = new KeyMapping("key.realview.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, "key.categories.realview");
    public static final KeyMapping TOGGLE = new KeyMapping("key.realview.toggle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.realview");

    private Controls() {}

    @Mod.EventBusSubscriber(modid = RealView.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent e) {
            e.register(OPEN);
            e.register(TOGGLE);
        }
    }

    @Mod.EventBusSubscriber(modid = RealView.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            boolean open = ViewFx.consumeOpenRequest();
            while (OPEN.consumeClick()) open = true;
            if (open && mc.player != null && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen))
                mc.setScreen(new RvConfigScreen(null));
            while (TOGGLE.consumeClick()) {
                boolean on = !Config.ENABLED.get();
                Config.ENABLED.set(on);
                Config.save();
                if (mc.player != null)
                    mc.player.displayClientMessage(Component.translatable(on ? "realview.msg.on" : "realview.msg.off"), true);
            }
        }

        @SubscribeEvent
        public static void onCommands(RegisterClientCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("realview").executes(c -> {
                ViewFx.requestOpenConfig();   // opened on the next tick, after the chat screen has closed
                return 1;
            }));
        }
    }
}
