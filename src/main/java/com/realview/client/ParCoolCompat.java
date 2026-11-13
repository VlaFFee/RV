package com.realview.client;

import com.realview.Config;
import com.realview.RealView;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Soft link to ParCool (mod id "parcool"), all by reflection, so RealView works with or without it.
 *
 * ParCool drives the player through "actions" (roll, vault, dodge, wall run, hang, crawl, ...) that move the player and
 * pose the model by themselves. While one runs, RealView's bob / landing kick / hand sway would be stacked on top of it, so
 * a smoothed "busy" value (0..1) lets the rest of the mod calm down and fade back in with no pops.
 *
 * Two layers, both failure-safe:
 *  1. ParCool's own state: Parkourability.get(player) -> its list of actions -> action.isDoing().
 *  2. Pose heuristic (works even if ParCool's internals change): crawling / sliding pose outside water.
 */
public final class ParCoolCompat {
    private static boolean tried, present, apiOk;
    private static Method getAbility;
    private static Object lastAbility;
    private static List<Object> actions = new ArrayList<>();
    private static final Map<Class<?>, Method> DOING = new HashMap<>();
    private static float busy, crawl;

    /** Actions that run all the time or are only "preparing": they must not calm the view. */
    private static final String[] IGNORE = {"fastrun", "sprint", "walk", "jump", "tap", "chargejump", "breakfallready", "skill"};
    /** Low-to-the-ground movement: keep the view alive but gentle. */
    private static final String[] CRAWLISH = {"crawl", "slide"};

    private ParCoolCompat() {}

    public static boolean present() {
        if (!tried) {
            tried = true;
            try {
                present = ModList.get().isLoaded("parcool");
                if (present) {
                    try {
                        Class<?> pk = Class.forName("com.alrex.parcool.common.capability.Parkourability");
                        getAbility = pk.getMethod("get", Player.class);
                        apiOk = true;
                        RealView.LOGGER.info("ParCool found: action-aware compatibility enabled.");
                    } catch (Throwable t) {
                        RealView.LOGGER.warn("ParCool found, but its API shape differs ({}); using the pose-based fallback only.", t.toString());
                    }
                }
            } catch (Throwable t) {
                present = false;
            }
        }
        return present;
    }

    /** True when ParCool is installed and the user left the compatibility on. */
    public static boolean active() { return present() && Config.PC_ON.get(); }

    /** 0..1: a ParCool action (roll, vault, wall run, ...) is in progress. */
    public static float busy() { return busy; }

    /** 0..1: crawling / sliding. */
    public static float crawl() { return crawl; }

    /** Call once per frame with the real frame time. */
    public static void update(Player p, float dt) {
        boolean doing = false, crawling = false;
        if (active()) {
            try {
                Pose pose = p.getPose();
                if (pose == Pose.SWIMMING && !p.isInWater() && !p.isFallFlying()) crawling = true;
                if (apiOk) {
                    for (Object a : actionsOf(p)) {
                        if (!isDoing(a)) continue;
                        String n = a.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                        if (matches(n, IGNORE)) continue;
                        if (matches(n, CRAWLISH)) crawling = true;
                        else doing = true;
                    }
                }
            } catch (Throwable t) {
                apiOk = false;   // layout changed under us: fall back to the pose heuristic only
                RealView.LOGGER.warn("ParCool reflection failed, using pose fallback: {}", t.toString());
            }
        }
        float up = 1f - (float) Math.exp(-dt * 14f), down = 1f - (float) Math.exp(-dt * 5f);
        busy += ((doing ? 1f : 0f) - busy) * (doing ? up : down);
        crawl += ((crawling ? 1f : 0f) - crawl) * (crawling ? up : down);
    }

    private static boolean matches(String name, String[] keys) {
        for (String k : keys) if (name.contains(k)) return true;
        return false;
    }

    private static boolean isDoing(Object a) throws Exception {
        Method m = DOING.get(a.getClass());
        if (m == null) {
            m = a.getClass().getMethod("isDoing");
            DOING.put(a.getClass(), m);
        }
        return (boolean) m.invoke(a);
    }

    /** Finds the list of actions inside the player's Parkourability (getList(), or any collection of objects with isDoing()). */
    private static List<Object> actionsOf(Player p) throws Exception {
        Object ab = getAbility.invoke(null, p);
        if (ab == null) return actions;           // not attached yet
        if (ab == lastAbility && !actions.isEmpty()) return actions;
        lastAbility = ab;
        List<Object> found = new ArrayList<>();
        try {
            Object r = ab.getClass().getMethod("getList").invoke(ab);
            collect(r, found);
        } catch (NoSuchMethodException ignored) { /* try the fields below */ }
        if (found.isEmpty()) {
            for (Class<?> c = ab.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    collect(f.get(ab), found);
                }
            }
        }
        actions = found;
        if (found.isEmpty()) {
            apiOk = false;
            RealView.LOGGER.warn("ParCool: no action list found, using the pose fallback only.");
        }
        return actions;
    }

    private static void collect(Object r, List<Object> out) {
        if (r == null) return;
        Collection<?> col = null;
        if (r instanceof Collection<?> c) col = c;
        else if (r instanceof Map<?, ?> m) col = m.values();
        else if (r instanceof Object[] arr) col = List.of(arr);
        if (col == null) return;
        for (Object o : col) {
            if (o == null) continue;
            try {
                o.getClass().getMethod("isDoing");
                out.add(o);
            } catch (NoSuchMethodException ignored) { /* not an action */ }
        }
    }
}
