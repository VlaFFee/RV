package com.realview.client.gui;

import com.realview.Config;
import com.realview.client.FpCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** In-game settings: reachable from Mods -> RealView -> Config, the ';' key, or /realview. Saved to realview-client.toml. */
public final class RvConfigScreen extends Screen {
    private static final int ROW = 24;
    private static int lastTab;

    private final Screen parent;
    private int tab = lastTab;
    private int scroll, scrollMax, viewTop, viewBottom;
    private final List<AbstractWidget> rows = new ArrayList<>();

    public RvConfigScreen(Screen parent) {
        super(Component.translatable("realview.title"));
        this.parent = parent;
    }

    private static String t(String key) { return I18n.get("realview." + key); }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        int n = Config.TABS.length;
        int tw = Math.min(110, (width - 20) / n);
        int x0 = (width - tw * n) / 2;
        for (int i = 0; i < n; i++) {
            final int ti = i;
            Button b = Button.builder(Component.literal((tab == i ? "§e" : "") + t("tab." + Config.TABS[i])), bb -> {
                Config.save();
                tab = ti; lastTab = ti; scroll = 0; init();
            }).bounds(x0 + i * tw, 22, tw - 2, 18).build();
            addRenderableWidget(b);
        }

        int rw = Math.min(width - 40, 320);
        int rx = (width - rw) / 2;
        for (Config.Opt o : Config.OPTIONS) {
            if (!o.tab.equals(Config.TABS[tab])) continue;
            AbstractWidget w;
            if (o.num != null) {
                w = new NumSlider(rx, 0, rw, o);
            } else if (o.bool != null) {
                w = Button.builder(boolLabel(o), bb -> {
                    o.bool.set(!o.bool.get());
                    bb.setMessage(boolLabel(o));
                }).bounds(rx, 0, rw, 20).build();
            } else {
                w = Button.builder(modeLabel(o), bb -> {
                    Config.FpMode[] all = Config.FpMode.values();
                    o.mode.set(all[(o.mode.get().ordinal() + 1) % all.length]);
                    bb.setMessage(modeLabel(o));
                }).bounds(rx, 0, rw, 20).build();
            }
            w.setTooltip(Tooltip.create(Component.translatable("realview.opt." + o.key + ".tip")));
            rows.add(w);
            addRenderableWidget(w);
        }

        viewTop = 46;
        viewBottom = height - 52;
        scrollMax = Math.max(0, rows.size() * ROW - (viewBottom - viewTop));
        scroll = Mth.clamp(scroll, 0, scrollMax);
        layoutRows();

        int fy = height - 24, fw = Math.min(88, (width - 24) / 5);
        int fx = (width - fw * 5) / 2;
        addRenderableWidget(Button.builder(Component.literal(t("reset")), b -> { Config.resetAll(); init(); })
                .bounds(fx, fy, fw - 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal(t("preset.subtle")), b -> { Config.preset(0.5); init(); })
                .bounds(fx + fw, fy, fw - 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal(t("preset.real")), b -> { Config.preset(1.0); init(); })
                .bounds(fx + fw * 2, fy, fw - 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal(t("preset.cine")), b -> { Config.preset(1.6); init(); })
                .bounds(fx + fw * 3, fy, fw - 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§a" + t("done")), b -> onClose())
                .bounds(fx + fw * 4, fy, fw - 2, 18).build());
    }

    private void layoutRows() {
        for (int i = 0; i < rows.size(); i++) {
            AbstractWidget w = rows.get(i);
            int y = viewTop + i * ROW - scroll;
            w.setY(y);
            w.visible = y >= viewTop - 1 && y + 20 <= viewBottom + 1;
        }
    }

    private static Component boolLabel(Config.Opt o) {
        boolean on = o.bool.get();
        return Component.literal(t("opt." + o.key) + ": " + (on ? "§a" + t("on") : "§7" + t("off")));
    }

    private static Component modeLabel(Config.Opt o) {
        return Component.literal(t("opt." + o.key) + ": §b" + t("mode." + o.mode.get().name().toLowerCase(Locale.ROOT)));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (scrollMax > 0) {
            scroll = Mth.clamp(scroll - (int) (delta * 18), 0, scrollMax);
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
        String info = FpCompat.present() ? "§a" + t("fp.connected") : "§7" + t("fp.absent");
        g.drawCenteredString(font, info, width / 2, height - 38, 0xFFFFFFFF);
        if (scrollMax > 0) {
            int bx = width / 2 + Math.min(width - 40, 320) / 2 + 4;
            int bh = viewBottom - viewTop;
            int sh = Math.max(12, bh * bh / (bh + scrollMax));
            int sy = viewTop + (int) ((long) (bh - sh) * scroll / scrollMax);
            g.fill(bx, viewTop, bx + 3, viewBottom, 0x55000000);
            g.fill(bx, sy, bx + 3, sy + sh, 0xCCFFFFFF);
        }
    }

    @Override
    public void removed() { Config.save(); }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().setScreen(parent);
    }

    /** 0..1 slider bound to a double config value; steps of 0.05 (or 0.5 for wide ranges). */
    private static final class NumSlider extends AbstractSliderButton {
        private final Config.Opt o;

        NumSlider(int x, int y, int w, Config.Opt o) {
            super(x, y, w, 20, Component.empty(), (o.num.get() - o.min) / (o.max - o.min));
            this.o = o;
            updateMessage();
        }

        private double real() {
            double v = o.min + value * (o.max - o.min);
            double step = (o.max - o.min) > 10 ? 0.5 : 0.05;
            return Mth.clamp(Math.round(v / step) * step, o.min, o.max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(t("opt." + o.key) + ": §b" + String.format(Locale.ROOT, "%.2f", real())));
        }

        @Override
        protected void applyValue() {
            o.num.set(real());
        }
    }
}
