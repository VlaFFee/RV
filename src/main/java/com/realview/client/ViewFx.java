package com.realview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.realview.Config;
import com.realview.RealView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The simulation + the three places where it is shown: camera angles, FOV and the first-person hands.
 *
 * Everything runs per rendered frame (not per tick), so it is smooth at any frame rate. Nothing here moves the player or
 * the camera position, so it cannot fight with FP Studio's camera (which shifts the player for one frame) - angles and FOV
 * are only ever added on top of whatever is already there.
 */
@Mod.EventBusSubscriber(modid = RealView.MODID, value = Dist.CLIENT)
public final class ViewFx {

    /** Damped spring; x is the value, v its speed. */
    private static final class Spring {
        float x, v;
        final float k, c;
        Spring(float k, float c) { this.k = k; this.c = c; }

        /** Free oscillation around 0 (impulses are added to v). */
        void step(float dt) { step(dt, 0f); }

        void step(float dt, float target) {
            int n = Math.max(1, (int) Math.ceil(dt / 0.008f));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                v += (k * (target - x) - c * v) * h;
                x += v * h;
            }
        }

        void reset() { x = 0f; v = 0f; }
    }

    // ---- raw outputs (before intensity / FP Studio weights)
    private static float camPitch, camYaw, camRoll, camFov;
    private static float hx, hy, hz, hrx, hry, hrz;

    // ---- state
    private static long lastNs;
    private static float time;
    private static boolean init;
    private static float lastYaw, lastPitch, yawRateS, pitchRateS;
    private static float spd, fwdS, rightS, phase;
    private static float groundW, sprintW, crouchW, swimW, flyW, fallW, speedFovS;
    private static float fallSpeed;
    private static boolean wasGround = true;
    private static int lastHurt;
    private static float sign = 1f;
    private static boolean pendingOpen;
    private static float pendingImpact, pendingT;

    private static final Spring landP = new Spring(120f, 14f), landR = new Spring(90f, 10f), jumpP = new Spring(140f, 16f);
    private static final Spring hurtP = new Spring(100f, 9f), hurtR = new Spring(100f, 9f);
    private static final Spring hLandY = new Spring(110f, 12f);
    private static final Spring hYaw = new Spring(70f, 9f), hPitch = new Spring(70f, 9f), hSide = new Spring(80f, 10f);

    private static int frame, handFrame = -1;
    private static boolean overridden;

    private ViewFx() {}

    public static void requestOpenConfig() { pendingOpen = true; }

    public static boolean consumeOpenRequest() {
        boolean b = pendingOpen;
        pendingOpen = false;
        return b;
    }

    private static float f(net.minecraftforge.common.ForgeConfigSpec.DoubleValue v) { return v.get().floatValue(); }

    private static float chase(float cur, float target, float dt, float rate) {
        return cur + (target - cur) * (1f - (float) Math.exp(-dt * rate));
    }

    private static float gauss(float x, float c, float w) {
        float d = (x - c) / w;
        return (float) Math.exp(-d * d);
    }

    private static void reset() {
        init = false;
        camPitch = camYaw = camRoll = camFov = 0f;
        hx = hy = hz = hrx = hry = hrz = 0f;
        spd = fwdS = rightS = 0f;
        groundW = sprintW = crouchW = swimW = flyW = fallW = speedFovS = 0f;
        fallSpeed = 0f;
        pendingImpact = 0f;
        yawRateS = pitchRateS = 0f;
        for (Spring s : new Spring[]{landP, landR, jumpP, hurtP, hurtR, hLandY, hYaw, hPitch, hSide}) s.reset();
    }

    // ------------------------------------------------------------------ per frame simulation

    private static void update(Minecraft mc, float dtReal, float pt) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); return; }

        float smooth = Math.max(0.2f, f(Config.SMOOTHNESS));
        float dt = dtReal / smooth;           // simulation time: bigger smoothness = slower, softer motion
        time += dtReal;

        // ---- turning speed (deg/s), ignoring teleports and respawns
        float yaw = p.getViewYRot(pt), pitch = p.getViewXRot(pt);
        float yawRate = 0f, pitchRate = 0f;
        if (init && dtReal > 1e-4f) {
            float dy = Mth.wrapDegrees(yaw - lastYaw), dp = pitch - lastPitch;
            if (Math.abs(dy) < 90f && Math.abs(dp) < 90f) {
                yawRate = Mth.clamp(dy / dtReal, -1500f, 1500f);
                pitchRate = Mth.clamp(dp / dtReal, -1500f, 1500f);
            }
        }
        lastYaw = yaw; lastPitch = pitch; init = true;
        yawRateS = chase(yawRateS, yawRate, dtReal, 14f);
        pitchRateS = chase(pitchRateS, pitchRate, dtReal, 14f);

        // ---- real movement over the last tick
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        if (dx * dx + dz * dz > 4.0) { dx = 0; dz = 0; }      // teleport
        float sp = (float) Math.sqrt(dx * dx + dz * dz) * 20f; // blocks per second
        double yr = Math.toRadians(p.getYRot());
        float fwd = (float) (dx * -Math.sin(yr) + dz * Math.cos(yr)) * 20f;
        float right = (float) (dx * -Math.cos(yr) + dz * -Math.sin(yr)) * 20f;
        spd = chase(spd, sp, dtReal, 12f);
        fwdS = chase(fwdS, fwd, dtReal, 10f);
        rightS = chase(rightS, right, dtReal, 10f);

        Vec3 dm = p.getDeltaMovement();
        boolean flying = p.getAbilities().flying;
        boolean inWater = p.isInWater();
        boolean ground = !p.level().noCollision(p.getBoundingBox().move(0, -0.03, 0));
        boolean fall = p.isFallFlying();

        // ---- landing / take-off
        float landS = f(Config.LAND), jumpS = f(Config.JUMP), hurtS = f(Config.HURT);
        float airS = f(Config.H_AIR);
        if (!ground) {
            if (!flying && !inWater && !fall) fallSpeed = Math.min(fallSpeed, (float) dm.y);
            if (wasGround && dm.y > 0.2 && !flying && !inWater) {
                jumpP.v -= 16f * jumpS;
                hLandY.v += 0.25f * airS;
            }
        } else {
            if (!wasGround && !flying && !inWater) {
                float impact = Mth.clamp((-fallSpeed - 0.30f) / 0.8f, 0f, 1.6f);
                if (impact > 0.03f) {
                    if (ParCoolCompat.active()) {
                        // ParCool may answer a hard landing with a roll a moment later: wait a few frames, then decide how hard to hit
                        pendingImpact = Math.max(pendingImpact, impact);
                        pendingT = 0.12f;
                    } else {
                        landKick(impact, 1f);
                    }
                }
            }
            fallSpeed = 0f;
        }
        wasGround = ground;

        ParCoolCompat.update(p, dtReal);
        if (pendingImpact > 0f) {
            pendingT -= dtReal;
            if (pendingT <= 0f) {
                landKick(pendingImpact, Mth.lerp(ParCoolCompat.busy(), 1f, f(Config.PC_LANDING)));
                pendingImpact = 0f;
            }
        }

        // ---- damage
        if (p.hurtTime > lastHurt) {
            sign = -sign;
            hurtP.v += 38f * hurtS;
            hurtR.v += sign * 55f * hurtS;
        }
        lastHurt = p.hurtTime;

        // ---- smoothed state weights
        boolean sprinting = p.isSprinting() && ground && sp > 2f;
        groundW = chase(groundW, ground && !inWater && !p.isPassenger() ? 1f : 0f, dt, 12f);
        sprintW = chase(sprintW, sprinting ? 1f : 0f, dt, 6f);
        crouchW = chase(crouchW, p.isCrouching() ? 1f : 0f, dt, 8f);
        swimW = chase(swimW, (p.isSwimming() || (inWater && !ground)) ? 1f : 0f, dt, 3f);
        flyW = chase(flyW, fall ? 1f : 0f, dt, 3f);
        float fallT = (!ground && !flying && !inWater && !fall) ? Mth.clamp((float) (-dm.y - 0.8) / 1.2f, 0f, 1f) : 0f;
        fallW = chase(fallW, fallT, dt, 4f);

        // ---- walk cycle phase (advances with distance, so steps match speed)
        float stride = Mth.lerp(sprintW, 1.9f, 1.6f);
        stride = Mth.lerp(crouchW, stride, 2.4f);
        phase += spd * stride * dtReal * f(Config.BOB_SPEED);
        if (phase > Mth.TWO_PI * 64f) phase -= Mth.TWO_PI * 64f;

        // ---- springs
        landP.step(dt); landR.step(dt); jumpP.step(dt);
        hurtP.step(dt); hurtR.step(dt); hLandY.step(dt);

        computeCamera(p, dm, dt);
        computeHands(p, dm, dt);
    }

    private static void landKick(float impact, float scale) {
        sign = -sign;
        float k = impact * scale;
        landP.v += k * 45f * f(Config.LAND);
        landR.v += sign * k * 22f * f(Config.LAND);
        hLandY.v -= k * 0.55f * f(Config.H_AIR);
    }

    private static void computeCamera(LocalPlayer p, Vec3 dm, float dt) {
        float amount = Mth.clamp(spd / 4.3f, 0f, 1.6f) * groundW;
        float calm = Mth.lerp(crouchW * Mth.clamp(f(Config.SNEAK), 0f, 1f), 1f, 0.45f) * Mth.lerp(ParCoolCompat.crawl(), 1f, 0.4f);
        float sprintStr = f(Config.SPRINT);
        float A = amount * calm * (1f + 0.8f * sprintW * sprintStr) * f(Config.BOB);

        float ph = phase;
        float pitch = A * (0.30f * Mth.sin(2f * ph) + 0.06f * Mth.sin(4f * ph + 1f));
        float roll = A * 0.55f * Mth.sin(ph);
        float yaw = A * 0.28f * Mth.cos(ph);

        // sideways tilt, forward lean, turn tilt
        roll += Mth.clamp(rightS / 4.3f, -1.5f, 1.5f) * 1.7f * f(Config.STRAFE) * groundW;
        pitch += (Mth.clamp(fwdS / 5.6f, -0.5f, 1.2f) * 0.35f + sprintW * 0.7f * sprintStr) * f(Config.LEAN) * groundW;
        roll += Mth.clamp(yawRateS * 0.006f, -1.6f, 1.6f) * f(Config.LOOK_TILT);

        // breathing: faster and deeper when hungry or hurt
        float food = p.getFoodData().getFoodLevel();
        float tired = Mth.clamp((8f - food) / 8f, 0f, 1f);
        float hp = p.getHealth(), maxHp = Math.max(1f, p.getMaxHealth());
        float hurtLow = Mth.clamp(1f - hp / (maxHp * 0.5f), 0f, 1f);
        float br = (1f + 1.5f * tired + 1.5f * hurtLow) * f(Config.BREATH);
        float bf = 0.25f * (1f + tired + hurtLow);
        float idle = 1f - Math.min(1f, amount * 0.7f);
        pitch += 0.12f * br * Mth.sin(Mth.TWO_PI * bf * time) * idle;
        roll += 0.08f * br * Mth.sin(Mth.TWO_PI * bf * 0.5f * time + 0.8f) * idle;

        // water, elytra, fast fall
        float swimS = f(Config.SWIM);
        roll += 0.9f * Mth.sin(1.3f * time) * swimS * swimW;
        pitch += 0.5f * Mth.sin(0.9f * time + 1f) * swimS * swimW;
        roll += Mth.clamp(yawRateS * 0.012f, -12f, 12f) * f(Config.ELYTRA) * flyW;
        float rumble = Mth.sin(37f * time) * Mth.sin(23.3f * time);
        pitch += 0.25f * rumble * f(Config.FALL) * fallW;
        roll += 0.25f * Mth.sin(41f * time + 1f) * Mth.sin(17f * time) * f(Config.FALL) * fallW;

        // impacts
        pitch += landP.x + jumpP.x + hurtP.x;
        roll += landR.x + hurtR.x;

        // heartbeat at low health
        float lowW = hp <= 6f ? Mth.clamp((7f - hp) / 5f, 0f, 1f) : 0f;
        float heartS = f(Config.HEART);
        float pulse = 0f;
        if (lowW > 0f) {
            float bpm = 70f + 60f * lowW;
            float hph = (time * bpm / 60f) % 1f;
            pulse = gauss(hph, 0.06f, 0.03f) + 0.6f * gauss(hph, 0.30f, 0.035f);
            pitch += 0.15f * pulse * lowW * heartS;
        }

        // FOV
        float horiz = Mth.clamp(spd / 6f, 0f, 1f);
        float fovT = (sprintW * 3.0f + horiz * 1.0f + fallW * 4f + flyW * Mth.clamp(spd / 30f, 0f, 1f) * 8f) * f(Config.FOV_SPEED);
        speedFovS = chase(speedFovS, fovT, dt, 5f);
        camFov = speedFovS - 1.2f * pulse * lowW * heartS;

        camPitch = pitch; camYaw = yaw; camRoll = roll;
    }

    private static void computeHands(LocalPlayer p, Vec3 dm, float dt) {
        float amount = Mth.clamp(spd / 4.3f, 0f, 1.6f) * groundW;
        float calm = Mth.lerp(crouchW * Mth.clamp(f(Config.SNEAK), 0f, 1f), 1f, 0.45f) * Mth.lerp(ParCoolCompat.crawl(), 1f, 0.4f);
        float A = amount * calm * (1f + 0.6f * sprintW) * f(Config.H_BOB);
        float ph = phase;

        float x = 0.018f * Mth.sin(ph) * A;
        float y = 0.014f * Mth.sin(2f * ph) * A;
        float z = 0.006f * Mth.cos(2f * ph) * A;
        float rx = 1.2f * Mth.sin(2f * ph + 0.5f) * A;
        float ry = 0f;
        float rz = 2.2f * Mth.sin(ph) * A;

        // sprint pose
        float sprintS = f(Config.H_SPRINT);
        y -= 0.06f * sprintW * sprintS;
        z += 0.03f * sprintW * sprintS;
        rx -= 7f * sprintW * sprintS;
        ry -= 5f * sprintW * sprintS;
        rz += 4f * sprintW * sprintS;

        // breathing
        float hb = f(Config.H_BREATH);
        y += 0.0035f * Mth.sin(Mth.TWO_PI * 0.28f * time) * hb;
        rx += 0.4f * Mth.sin(Mth.TWO_PI * 0.28f * time + 0.7f) * hb;
        rz += 0.3f * Mth.sin(Mth.TWO_PI * 0.2f * time) * hb;

        // jump / land / falling
        float airS = f(Config.H_AIR);
        y += hLandY.x;
        float airW = 1f - groundW;
        y += Mth.clamp((float) -dm.y, 0f, 1f) * 0.05f * airW * airS;

        // sideways drift
        float st = f(Config.H_STRAFE);
        float side = Mth.clamp(rightS / 4.3f, -1.5f, 1.5f);
        x -= side * 0.03f * st * groundW;
        rz -= side * 3f * st * groundW;

        // turning inertia: hands trail the view and swing back with a little overshoot
        float in = f(Config.H_INERTIA);
        hYaw.step(dt, Mth.clamp(yawRateS * 0.010f, -9f, 9f));
        hPitch.step(dt, Mth.clamp(pitchRateS * 0.010f, -7f, 7f));
        hSide.step(dt, Mth.clamp(yawRateS * 0.0006f, -0.05f, 0.05f));
        ry += hYaw.x * in;
        rx += hPitch.x * in;
        x -= hSide.x * in;
        rz += hYaw.x * 0.25f * in;

        hx = x; hy = y; hz = z; hrx = rx; hry = ry; hrz = rz;
    }

    // ------------------------------------------------------------------ weights

    private static boolean firstPerson(Minecraft mc) {
        return mc.options.getCameraType().isFirstPerson() && mc.player != null
                && !mc.player.isSleeping() && !mc.player.isSpectator();
    }

    private static float camWeight() {
        if (!Config.ENABLED.get() || !Config.CAMERA_ON.get()) return 0f;
        return f(Config.INTENSITY) * FpCompat.camera() * Mth.lerp(ParCoolCompat.busy(), 1f, f(Config.PC_CAMERA));
    }

    private static float handWeight() {
        if (!Config.ENABLED.get() || !Config.HANDS_ON.get()) return 0f;
        return f(Config.INTENSITY) * FpCompat.hands() * Mth.lerp(ParCoolCompat.busy(), 1f, f(Config.PC_HANDS));
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase == TickEvent.Phase.START) {
            frame++;
            long now = System.nanoTime();
            float dt = lastNs == 0 ? 0f : Mth.clamp((now - lastNs) / 1e9f, 0f, 0.1f);
            lastNs = now;
            if (mc.isPaused()) dt = 0f;
            try {
                FpCompat.update(dt);
                update(mc, dt, e.renderTickTime);
            } catch (RuntimeException ex) {
                RealView.LOGGER.error("RealView update failed, resetting", ex);
                reset();
            }
            // Our camera replaces vanilla view bobbing. The option itself is only overridden for this frame.
            overridden = false;
            if (Config.ENABLED.get() && Config.CAMERA_ON.get() && Config.NO_VANILLA_BOB.get()
                    && !(mc.screen instanceof OptionsSubScreen) && !(mc.screen instanceof OptionsScreen)
                    && mc.options.bobView().get()) {
                mc.options.bobView().set(false);
                overridden = true;
            }
        } else if (overridden) {
            mc.options.bobView().set(true);
            overridden = false;
        }
    }

    /** After FP Studio (NORMAL): we only add on top of its final angles. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (!firstPerson(mc)) return;
        float w = camWeight();
        if (w <= 0.0005f) return;
        float max = f(Config.MAX_ANGLE);
        e.setPitch(e.getPitch() + Mth.clamp(camPitch * w, -max, max));
        e.setYaw(e.getYaw() + Mth.clamp(camYaw * w, -max, max));
        e.setRoll(e.getRoll() + camRoll * w);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!e.usedConfiguredFov()) return;   // never touch the hand projection
        Minecraft mc = Minecraft.getInstance();
        if (!firstPerson(mc) || !Config.FOV_ON.get()) return;
        float w = camWeight();
        if (w <= 0.0005f) return;
        e.setFOV(Math.max(10.0, e.getFOV() + camFov * w));
    }

    /**
     * Runs first and never cancels: the transform goes onto the shared pose stack once per frame, so vanilla hands,
     * items and (when allowed) FP Studio's own hands all get it together.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onHand(RenderHandEvent e) {
        if (handFrame == frame) return;
        Minecraft mc = Minecraft.getInstance();
        if (!firstPerson(mc)) return;
        float w = handWeight();
        if (w <= 0.0005f) return;
        handFrame = frame;
        PoseStack ps = e.getPoseStack();
        ps.translate(hx * w, hy * w, hz * w);
        // rotate about a point near the middle of the hands so they pivot instead of orbiting the eye
        ps.translate(0f, -0.35f, -0.2f);
        ps.mulPose(Axis.YP.rotationDegrees(hry * w));
        ps.mulPose(Axis.XP.rotationDegrees(hrx * w));
        ps.mulPose(Axis.ZP.rotationDegrees(hrz * w));
        ps.translate(0f, 0.35f, 0.2f);
    }
}
