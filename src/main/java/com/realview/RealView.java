package com.realview;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(RealView.MODID)
public class RealView {
    public static final String MODID = "realview";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealView() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC, "realview-client.toml");
        if (FMLEnvironment.dist.isClient()) com.realview.client.ClientInit.init();
    }
}
