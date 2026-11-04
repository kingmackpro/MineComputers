package net.minecomputers.client;

import net.minecomputers.network.HackAction;
import net.minecomputers.network.HackState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The hacking mini game (SPEC section 8): a circle with a needle that sweeps around its edge, and a zone on the
 * circle. Press SPACE (or click) while the needle is inside the zone. The server picks the zone, runs the clock and
 * judges the press; this screen only draws what HackState says. Client only.
 */
public class HackScreen extends Screen {
    private static final int TEAL = 0xFF2BD1C4;
    private static final int TEXT = 0xFFD7DEE4;
    private static final int TEXT_DIM = 0xFF7C8791;
    private static final int RING = 0xFF3A444D;
    private static final int RED = 0xFFE5534B;
    private static final int NEEDLE = 0xFFFFFFFF;

    private BlockPos laptop;
    private int phase;
    private int round;
    private int rounds;
    private float zoneStart;
    private float zoneSize;
    private int periodMs;
    private long startNanos;
    private String text = "";
    private boolean pressed;
    private Button startButton;

    public HackScreen(HackState state) {
        super(Component.literal("Locked system"));
        apply(state);
    }

    public boolean isFor(BlockPos pos) {
        return laptop.equals(pos);
    }

    /** A new state from the server. */
    public void apply(HackState state) {
        this.laptop = state.laptop();
        this.phase = state.phase();
        this.round = state.round();
        this.rounds = state.rounds();
        this.text = state.text();
        if (phase == HackState.ROUND) {
            this.zoneStart = state.zoneStart();
            this.zoneSize = state.zoneSize();
            this.periodMs = Math.max(1, state.periodMs());
            this.startNanos = System.nanoTime() + state.delayMs() * 1_000_000L;
            this.pressed = false;
        }
    }

    @Override
    protected void init() {
        startButton = Button.builder(Component.literal("Start hack"), button -> {
            PacketDistributor.sendToServer(new HackAction(laptop, HackAction.BEGIN));
        }).bounds(width / 2 - 50, height - 40, 100, 20).build();
        addRenderableWidget(startButton);
    }

    /** The screen was closed or replaced: stops a running hack on the server (no Cipher is lost). */
    @Override
    public void removed() {
        PacketDistributor.sendToServer(new HackAction(laptop, HackAction.ABORT));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private void press() {
        if (phase != HackState.ROUND || pressed || elapsedMs() < 0) {
            return;
        }
        pressed = true;
        PacketDistributor.sendToServer(new HackAction(laptop, HackAction.PRESS));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            press();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            press();
            return true;
        }
        return false;
    }

    private static void dot(GuiGraphics g, double cx, double cy, double turn, double radius, int color) {
        double a = turn * Math.PI * 2.0;
        int x = (int) Math.round(cx + Math.sin(a) * radius);
        int y = (int) Math.round(cy - Math.cos(a) * radius);
        g.fill(x - 1, y - 1, x + 1, y + 1, color);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        startButton.visible = phase != HackState.ROUND;
        startButton.setMessage(Component.literal(phase == HackState.FAIL ? "Try again" : round > 0 ? "Continue hack" : "Start hack"));
        super.render(g, mouseX, mouseY, partialTick);

        double cx = width / 2.0;
        double cy = height / 2.0 - 10;
        double radius = Math.max(30, Math.min(70, height / 3.0));

        g.drawCenteredString(font, "System locked - hacking", width / 2, (int) (cy - radius - 34), TEXT);
        g.drawCenteredString(font, "Part " + Math.min(round + 1, rounds) + " of " + rounds, width / 2,
                (int) (cy - radius - 22), TEXT_DIM);

        // the ring
        for (int i = 0; i < 240; i++) {
            dot(g, cx, cy, i / 240.0, radius, RING);
        }

        if (phase == HackState.ROUND) {
            // the zone (it can wrap past the top of the circle)
            for (double t = zoneStart; t <= zoneStart + zoneSize; t += 1.0 / 720.0) {
                double turn = t % 1.0;
                dot(g, cx, cy, turn, radius - 2, TEAL);
                dot(g, cx, cy, turn, radius, TEAL);
                dot(g, cx, cy, turn, radius + 2, TEAL);
            }
            double elapsed = elapsedMs();
            double turn = elapsed < 0 ? 0.0 : (elapsed % periodMs) / periodMs;
            for (double r = 0; r <= radius + 4; r += 2) {
                dot(g, cx, cy, turn, r, r > radius - 4 ? RED : NEEDLE);
            }
            if (elapsed < 0) {
                g.drawCenteredString(font, "Get ready: " + String.format("%.1f", -elapsed / 1000.0), width / 2,
                        (int) (cy + radius + 10), TEXT);
            } else {
                g.drawCenteredString(font, pressed ? "..." : "SPACE or click: hit the zone", width / 2,
                        (int) (cy + radius + 10), TEXT);
            }
        } else if (!text.isEmpty()) {
            g.drawCenteredString(font, text, width / 2, (int) (cy + radius + 10), phase == HackState.FAIL ? RED : TEXT);
            g.drawCenteredString(font, "You need a Cipher in your inventory. A miss breaks it.", width / 2,
                    (int) (cy + radius + 22), TEXT_DIM);
        }
    }
}
