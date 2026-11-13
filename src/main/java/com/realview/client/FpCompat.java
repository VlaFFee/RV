package com.realview.client;

import com.realview.Config;
import com.realview.RealView;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Soft link to FP Studio (mod id "fpstudio"). Everything is reached by reflection, so RealView loads and works without it.
 *
 * FP Studio takes over the first-person view while a clip plays or the studio is open: it cancels the vanilla hand render,
 * draws its own hands, and moves the camera through ViewportEvent. To stay out of its way, RealView fades its own
 * camera / hand motion out smoothly (and back in) instead of cutting, so there is never a double shake or a pop.
 */
public final class FpCompat {
    private static boolean tried, present;
    private static Method active;
    private static float camW = 1f, handW = 1f;

    private FpCompat() {}

    public static boolean present() {
        if (!tried) {
            tried = true;
            try {
                if (ModList.get().isLoaded("fpstudio")) {
                    Class<?> pb = Class.forName("com.fpstudio.client.Playback");
                    active = pb.getMethod("active");
                    present = true;
                    RealView.LOGGER.info("FP Studio found: smooth cinematic compatibility enabled.");
                }
            } catch (Throwable t) {
                present = false;
                RealView.LOGGER.warn("FP Studio found but its API could not be read, compatibility off: {}", t.toString());
            }
        }
        return present;
    }

    private static boolean fpActive() {
        try {
            return (boolean) active.invoke(null);
        } catch (Throwable t) {
            present = false;   // API changed: stop touching it
            return false;
        }
    }

    /** True right now while an FP Studio clip plays or the studio is open. */
    public static boolean activeNow() { return present() && fpActive(); }

    /** Call once per frame. */
    public static void update(float dt) {
        float tc = 1f, th = 1f;
        if (present() && fpActive()) {
            switch (Config.FP_MODE.get()) {
                case PAUSE_ALL -> { tc = 0f; th = 0f; }
                case KEEP_CAMERA -> th = 0f;
                case KEEP_ALL -> { }
            }
        }
        float rate = 1f / Math.max(0.05f, Config.FP_FADE.get().floatValue());
        camW = approach(camW, tc, dt * rate);
        handW = approach(handW, th, dt * rate);
    }

    private static float approach(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }

    private static float ease(float t) { return t * t * (3f - 2f * t); }

    /** 0..1 multiplier for camera effects. */
    public static float camera() { return ease(camW); }

    /** 0..1 multiplier for hand effects. */
    public static float hands() { return ease(handW); }
}
