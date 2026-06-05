package com.h4ck3r.basefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseFinderMod implements ClientModInitializer {
    public static final String MOD_ID = "basefinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static boolean enabled = true;
    public static boolean showTracers = true;
    public static boolean showHud = true;
    public static int scanRadius = 12;
    public static int maxCacheDistance = 10000;
    public static int maxDistance = 500;

    private static KeyBinding toggleKey;
    private static KeyBinding tracerKey;
    private static KeyBinding hudKey;
    private static KeyBinding radiusKey;
    private static boolean wasToggle = false;
    private static boolean wasTracer = false;
    private static boolean wasHud = false;
    private static boolean wasRadius = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BaseFinder V2] Inicializando... 10k+ chunks | HUD | Universal Compat");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.basefinder.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "category.basefinder"));
        tracerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.basefinder.tracers", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "category.basefinder"));
        hudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.basefinder.hud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "category.basefinder"));
        radiusKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.basefinder.radius", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "category.basefinder"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.isPressed() && !wasToggle) {
                enabled = !enabled;
                LOGGER.info("[BaseFinder] Estado: {}", enabled ? "ACTIVO" : "INACTIVO");
            }
            wasToggle = toggleKey.isPressed();

            if (tracerKey.isPressed() && !wasTracer) {
                showTracers = !showTracers;
                LOGGER.info("[BaseFinder] Tracers: {}", showTracers ? "ON" : "OFF");
            }
            wasTracer = tracerKey.isPressed();

            if (hudKey.isPressed() && !wasHud) {
                showHud = !showHud;
                LOGGER.info("[BaseFinder] HUD: {}", showHud ? "ON" : "OFF");
            }
            wasHud = hudKey.isPressed();

            if (radiusKey.isPressed() && !wasRadius) {
                scanRadius = scanRadius >= 32 ? 4 : scanRadius + 4;
                LOGGER.info("[BaseFinder] Radio: {} chunks", scanRadius);
            }
            wasRadius = radiusKey.isPressed();
        });

        WorldRenderEvents.LAST.register(context -> {
            if (enabled && context.gameRenderer().getClient().world != null && context.gameRenderer().getClient().player != null) {
                BaseFinderRenderer.renderWorld(context);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (showHud && enabled && drawContext.getClient().player != null) {
                BaseFinderHUD.render(drawContext);
            }
        });

        LOGGER.info("[BaseFinder V2] Listo! Controles: [-] Toggle | [=] Tracers | []] HUD | [[] Radio");
    }
}
