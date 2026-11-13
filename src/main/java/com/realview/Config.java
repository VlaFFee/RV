package com.realview;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/** Client config (config/realview-client.toml). Every value is also listed in {@link #OPTIONS} for the in-game screen. */
public final class Config {
    public enum FpMode { PAUSE_ALL, KEEP_CAMERA, KEEP_ALL }

    /** One row of the in-game settings screen. */
    public static final class Opt {
        public final String tab, key;
        public final ForgeConfigSpec.BooleanValue bool;
        public final ForgeConfigSpec.DoubleValue num;
        public final ForgeConfigSpec.EnumValue<FpMode> mode;
        public final double min, max, def;
        public final boolean strength;   // scaled by presets

        Opt(String tab, String key, ForgeConfigSpec.BooleanValue b, ForgeConfigSpec.DoubleValue n,
            ForgeConfigSpec.EnumValue<FpMode> m, double min, double max, double def, boolean strength) {
            this.tab = tab; this.key = key; this.bool = b; this.num = n; this.mode = m;
            this.min = min; this.max = max; this.def = def; this.strength = strength;
        }
    }

    public static final List<Opt> OPTIONS = new ArrayList<>();
    public static final String[] TABS = {"general", "walk", "reactions", "hands", "body", "parcool", "fpstudio"};

    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    private static ForgeConfigSpec.BooleanValue bool(String tab, String key, boolean def, String comment) {
        var v = B.comment(comment).define(key, def);
        OPTIONS.add(new Opt(tab, key, v, null, null, 0, 1, def ? 1 : 0, false));
        return v;
    }

    private static ForgeConfigSpec.DoubleValue num(String tab, String key, double def, double min, double max, boolean strength, String comment) {
        var v = B.comment(comment).defineInRange(key, def, min, max);
        OPTIONS.add(new Opt(tab, key, null, v, null, min, max, def, strength));
        return v;
    }

    // ---------------------------------------------------------------- general
    public static final ForgeConfigSpec.BooleanValue ENABLED = bool("general", "enabled", true, "Master switch for the whole mod.");
    public static final ForgeConfigSpec.DoubleValue INTENSITY = num("general", "intensity", 1.0, 0.0, 2.0, false, "Global multiplier of every effect.");
    public static final ForgeConfigSpec.DoubleValue SMOOTHNESS = num("general", "smoothness", 1.0, 0.4, 3.0, false, "Higher = softer, slower reaction of the camera and hands.");
    public static final ForgeConfigSpec.BooleanValue CAMERA_ON = bool("general", "cameraEffects", true, "Camera motion (bob, tilt, impacts).");
    public static final ForgeConfigSpec.BooleanValue HANDS_ON = bool("general", "handEffects", true, "Hand / held item motion.");
    public static final ForgeConfigSpec.BooleanValue FOV_ON = bool("general", "fovEffects", true, "Speed and heartbeat FOV changes.");
    public static final ForgeConfigSpec.BooleanValue NO_VANILLA_BOB = bool("general", "replaceVanillaBobbing", true, "Switch vanilla view bobbing off while this mod animates the view (your video setting is not changed).");
    public static final ForgeConfigSpec.DoubleValue MAX_ANGLE = num("general", "maxAngle", 8.0, 1.0, 20.0, false, "Limit (degrees) of the extra pitch/yaw so the crosshair stays honest.");

    // ---------------------------------------------------------------- walking
    public static final ForgeConfigSpec.DoubleValue BOB = num("walk", "headBob", 1.0, 0.0, 3.0, true, "Head bob while walking.");
    public static final ForgeConfigSpec.DoubleValue BOB_SPEED = num("walk", "stepRate", 1.0, 0.5, 2.0, false, "Step frequency multiplier.");
    public static final ForgeConfigSpec.DoubleValue SPRINT = num("walk", "sprintBob", 1.0, 0.0, 3.0, true, "Extra bob and forward lean while sprinting.");
    public static final ForgeConfigSpec.DoubleValue SNEAK = num("walk", "sneakCalm", 1.0, 0.0, 2.0, false, "How much bob calms down when sneaking.");
    public static final ForgeConfigSpec.DoubleValue STRAFE = num("walk", "strafeTilt", 1.0, 0.0, 3.0, true, "Camera roll when moving sideways.");
    public static final ForgeConfigSpec.DoubleValue LEAN = num("walk", "forwardLean", 1.0, 0.0, 3.0, true, "Camera dips slightly forward when running.");
    public static final ForgeConfigSpec.DoubleValue LOOK_TILT = num("walk", "turnTilt", 0.8, 0.0, 3.0, true, "Camera roll when turning quickly.");
    public static final ForgeConfigSpec.DoubleValue BREATH = num("walk", "breathing", 1.0, 0.0, 3.0, true, "Idle breathing sway (stronger when hungry or hurt).");

    // ---------------------------------------------------------------- reactions
    public static final ForgeConfigSpec.DoubleValue LAND = num("reactions", "landingImpact", 1.0, 0.0, 3.0, true, "Camera dip when landing.");
    public static final ForgeConfigSpec.DoubleValue JUMP = num("reactions", "jumpKick", 1.0, 0.0, 3.0, true, "Camera kick when jumping.");
    public static final ForgeConfigSpec.DoubleValue HURT = num("reactions", "hurtShake", 1.0, 0.0, 3.0, true, "Extra shake when taking damage.");
    public static final ForgeConfigSpec.DoubleValue SWIM = num("reactions", "swimSway", 1.0, 0.0, 3.0, true, "Slow sway while swimming.");
    public static final ForgeConfigSpec.DoubleValue ELYTRA = num("reactions", "elytraBank", 1.0, 0.0, 3.0, true, "Banking while gliding with elytra.");
    public static final ForgeConfigSpec.DoubleValue FALL = num("reactions", "fallRumble", 1.0, 0.0, 3.0, true, "Wind rumble while falling fast.");
    public static final ForgeConfigSpec.DoubleValue FOV_SPEED = num("reactions", "speedFov", 1.0, 0.0, 3.0, true, "FOV widening at speed.");
    public static final ForgeConfigSpec.DoubleValue HEART = num("reactions", "heartbeat", 1.0, 0.0, 2.0, true, "Heartbeat pulse at low health.");

    // ---------------------------------------------------------------- hands
    public static final ForgeConfigSpec.DoubleValue H_INERTIA = num("hands", "handInertia", 1.0, 0.0, 3.0, true, "Hands lag behind when you turn the camera.");
    public static final ForgeConfigSpec.DoubleValue H_BOB = num("hands", "handBob", 1.0, 0.0, 3.0, true, "Hands swing while walking.");
    public static final ForgeConfigSpec.DoubleValue H_SPRINT = num("hands", "handSprintPose", 1.0, 0.0, 3.0, true, "Hands drop and tilt while sprinting.");
    public static final ForgeConfigSpec.DoubleValue H_BREATH = num("hands", "handBreathing", 1.0, 0.0, 3.0, true, "Hands move gently with breathing.");
    public static final ForgeConfigSpec.DoubleValue H_AIR = num("hands", "handJumpLand", 1.0, 0.0, 3.0, true, "Hands react to jumps and landings.");
    public static final ForgeConfigSpec.DoubleValue H_STRAFE = num("hands", "handStrafe", 1.0, 0.0, 3.0, true, "Hands shift when moving sideways.");

    // ---------------------------------------------------------------- body
    public static final ForgeConfigSpec.BooleanValue BODY_ON = bool("body", "showBody", true, "Show your own body (torso, legs, armor) when you look down.");
    public static final ForgeConfigSpec.DoubleValue BODY_BACK = num("body", "bodyOffset", 0.25, 0.0, 0.8, false, "How far the body is moved back from the camera (blocks) so it does not clip into the view.");
    public static final ForgeConfigSpec.BooleanValue BODY_HIDE_ITEMS = bool("body", "bodyHideHeldItems", true, "Do not draw held items on the body (they are already in your hands).");

    // ---------------------------------------------------------------- ParCool
    public static final ForgeConfigSpec.BooleanValue PC_ON = bool("parcool", "parcoolCompat", true, "Cooperate with ParCool: calm down camera/hands during its actions (roll, vault, wall run, dodge, hang...).");
    public static final ForgeConfigSpec.DoubleValue PC_CAMERA = num("parcool", "parcoolCameraScale", 0.3, 0.0, 1.0, false, "Camera effects strength while a ParCool action is running (0 = off).");
    public static final ForgeConfigSpec.DoubleValue PC_HANDS = num("parcool", "parcoolHandScale", 0.3, 0.0, 1.0, false, "Hand effects strength while a ParCool action is running (0 = off).");
    public static final ForgeConfigSpec.DoubleValue PC_LANDING = num("parcool", "parcoolLandingScale", 0.3, 0.0, 1.0, false, "Landing impact strength when ParCool answers the landing (roll / breakfall).");
    public static final ForgeConfigSpec.BooleanValue PC_HIDE_BODY = bool("parcool", "parcoolHideBody", true, "Hide the first-person body during ParCool actions (its poses can look odd from inside).");

    // ---------------------------------------------------------------- FP Studio
    public static final ForgeConfigSpec.EnumValue<FpMode> FP_MODE;
    public static final ForgeConfigSpec.DoubleValue FP_FADE = num("fpstudio", "fpstudioFadeTime", 0.5, 0.05, 3.0, false, "Seconds to fade effects out/in when an FP Studio clip starts or ends.");

    static {
        var v = B.comment("What happens while FP Studio plays a clip / its studio is open:",
                "PAUSE_ALL = camera and hands fade out smoothly (the clip is shown exactly as authored),",
                "KEEP_CAMERA = camera effects stay, hands fade out, KEEP_ALL = nothing changes.")
                .defineEnum("fpstudioMode", FpMode.PAUSE_ALL);
        FP_MODE = v;
        OPTIONS.add(OPTIONS.size() - 1, new Opt("fpstudio", "fpstudioMode", null, null, v, 0, 0, 0, false));
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {}

    public static void save() {
        try { SPEC.save(); } catch (Exception ignored) { /* config not loaded yet */ }
    }

    public static void resetAll() {
        for (Opt o : OPTIONS) {
            if (o.bool != null) o.bool.set(o.def > 0.5);
            else if (o.num != null) o.num.set(o.def);
            else if (o.mode != null) o.mode.set(FpMode.PAUSE_ALL);
        }
        save();
    }

    /** Scale every "strength" slider relative to its default (0.5 subtle, 1 realistic, 1.6 cinematic). */
    public static void preset(double scale) {
        for (Opt o : OPTIONS) {
            if (o.num != null && o.strength) o.num.set(Math.min(o.max, Math.max(o.min, o.def * scale)));
        }
        save();
    }
}
