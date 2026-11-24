package dev.pvpbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ZombieRenderer;

public class PvpBotClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Reuse the vanilla zombie model so armor and held items render.
        EntityRendererRegistry.register(PvpBotMod.BOT, ZombieRenderer::new);
    }
}
