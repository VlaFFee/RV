package com.realview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realview.Config;
import com.realview.RealView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws your own body in first person (look down and you see torso, legs and armor).
 *
 * The real player renderer is called once per frame right after the world entities, with the head, arms and held items
 * left out (the head would sit inside the camera, the arms and items are already drawn by the first-person hand render),
 * and the body moved a little backwards so the chest does not cut through the view. Because it is the normal renderer,
 * walking / sneaking animations, skins, armor and other mods' player animations (ParCool's included) all apply by themselves.
 *
 * If anything throws, the feature switches itself off for the rest of the session instead of breaking rendering.
 */
@Mod.EventBusSubscriber(modid = RealView.MODID, value = Dist.CLIENT)
public final class BodyView {
    private static boolean rendering, failed;

    private BodyView() {}

    private static boolean want(Minecraft mc, LocalPlayer p) {
        if (failed || !Config.ENABLED.get() || !Config.BODY_ON.get()) return false;
        if (!mc.options.getCameraType().isFirstPerson() || mc.level == null) return false;
        if (p.isSpectator() || p.isInvisible() || p.isSleeping() || p.isFallFlying() || p.isPassenger()) return false;
        Pose pose = p.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return false;     // swimming, crawling, spin attack...
        if (FpCompat.activeNow()) return false;                                // FP Studio draws its own view
        if (ParCoolCompat.active() && Config.PC_HIDE_BODY.get() && ParCoolCompat.busy() > 0.15f) return false;
        return true;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !want(mc, p)) return;

        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        PoseStack ps = e.getPoseStack();
        Camera cam = e.getCamera();
        float pt = e.getPartialTick();
        Vec3 cp = cam.getPosition();

        // same interpolation the camera uses, so the body never jitters against the view
        double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
        double rad = Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
        float down = Math.max(0f, p.getViewXRot(pt)) / 90f;
        float back = Config.BODY_BACK.get().floatValue() + 0.12f * down;     // lean away a bit more the further you look down
        x += Math.sin(rad) * back;
        z -= Math.cos(rad) * back;

        Inventory inv = p.getInventory();
        ItemStack head = inv.armor.get(3);
        ItemStack main = inv.items.get(inv.selected);
        ItemStack off = inv.offhand.get(0);
        boolean hideItems = Config.BODY_HIDE_ITEMS.get();
        rendering = true;
        try {
            inv.armor.set(3, ItemStack.EMPTY);          // a helmet would surround the camera
            if (hideItems) {
                inv.items.set(inv.selected, ItemStack.EMPTY);
                inv.offhand.set(0, ItemStack.EMPTY);
            }
            d.setRenderShadow(false);
            d.render(p, x - cp.x, y - cp.y, z - cp.z, Mth.rotLerp(pt, p.yRotO, p.getYRot()), pt, ps, buf,
                    d.getPackedLightCoords(p, pt));
            buf.endBatch();
        } catch (RuntimeException ex) {
            failed = true;
            RealView.LOGGER.error("First-person body rendering failed and was switched off for this session", ex);
        } finally {
            inv.armor.set(3, head);
            if (hideItems) {
                inv.items.set(inv.selected, main);
                inv.offhand.set(0, off);
            }
            d.setRenderShadow(mc.options.entityShadows().get());
            rendering = false;
        }
    }

    /** Hide what must not be drawn on our own body. Runs after the renderer reset the model's visibility. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPre(RenderLivingEvent.Pre<?, ?> e) {
        if (!rendering || e.getEntity() != Minecraft.getInstance().player) return;
        if (e.getRenderer().getModel() instanceof PlayerModel<?> m) {
            m.head.visible = false;
            m.hat.visible = false;
            shrink(m.rightArm);
            shrink(m.leftArm);
            shrink(m.rightSleeve);
            shrink(m.leftSleeve);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPost(RenderLivingEvent.Post<?, ?> e) {
        if (!rendering || e.getEntity() != Minecraft.getInstance().player) return;
        if (e.getRenderer().getModel() instanceof PlayerModel<?> m) {
            restore(m.rightArm);
            restore(m.leftArm);
            restore(m.rightSleeve);
            restore(m.leftSleeve);
        }
    }

    /** Arms are already shown by the hand render. Scaled (not just hidden) so armor sleeves that copy the pose vanish too. */
    private static void shrink(net.minecraft.client.model.geom.ModelPart part) {
        part.visible = false;
        part.xScale = part.yScale = part.zScale = 0.001f;
    }

    private static void restore(net.minecraft.client.model.geom.ModelPart part) {
        part.visible = true;
        part.xScale = part.yScale = part.zScale = 1f;
    }
}
