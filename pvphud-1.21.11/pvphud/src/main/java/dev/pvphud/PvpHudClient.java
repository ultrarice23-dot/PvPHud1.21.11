package dev.pvphud;

import dev.pvphud.config.PvpConfig;
import dev.pvphud.gui.SettingsScreen;
import dev.pvphud.hud.HudRenderer;
import dev.pvphud.tracker.CombatTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PvpHudClient implements ClientModInitializer {
    public static KeyBinding OPEN, START, STOP, RESUME;

    @Override
    public void onInitializeClient() {
        PvpConfig.load();

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("pvphud", "main"));
        OPEN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pvphud.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, cat));
        START = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pvphud.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, cat));
        STOP = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pvphud.stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, cat));
        RESUME = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pvphud.resume", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_APOSTROPHE, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new SettingsScreen());
            while (START.wasPressed()) if (mc.player != null) CombatTracker.I.refresh();
            while (STOP.wasPressed()) CombatTracker.I.pause();
            while (RESUME.wasPressed()) CombatTracker.I.resume();
            CombatTracker.I.tick();
        });

        HudElementRegistry.addLast(Identifier.of("pvphud", "hud"), (ctx, tickCounter) -> HudRenderer.render(ctx));
    }
}
