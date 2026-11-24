package dev.pvpbot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class PvpBotMod implements ModInitializer {
    public static final ResourceKey<EntityType<?>> KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("pvpbot", "pvp_bot"));

    public static final EntityType<PvpBot> BOT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.of(PvpBot::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f).clientTrackingRange(8).build(KEY));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(BOT, PvpBot.attributes());
        BotConfig.get();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pvpbot")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        new String[]{"mace", "elytra_mace", "spear_mace"}, b))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                        .executes(c -> spawn(c.getSource(),
                                                StringArgumentType.getString(c, "mode"),
                                                IntegerArgumentType.getInteger(c, "level"), null))
                                        .then(Commands.argument("kit", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        BotConfig.get().kits.keySet(), b))
                                                .executes(c -> spawn(c.getSource(),
                                                        StringArgumentType.getString(c, "mode"),
                                                        IntegerArgumentType.getInteger(c, "level"),
                                                        StringArgumentType.getString(c, "kit")))))))
                .then(Commands.literal("clear").executes(c -> {
                    ServerLevel lvl = c.getSource().getLevel();
                    var bots = lvl.getEntities(BOT, e -> true);
                    bots.forEach(Entity::discard);
                    c.getSource().sendSuccess(() -> Component.literal("Removed " + bots.size() + " bot(s)"), false);
                    return bots.size();
                }))
                .then(Commands.literal("reload").executes(c -> {
                    BotConfig.reload();
                    c.getSource().sendSuccess(() -> Component.literal("pvpbot.json reloaded"), false);
                    return 1;
                })));
    }

    private static int spawn(CommandSourceStack src, String modeName, int level, String kit) {
        PvpBot.Mode mode;
        try {
            mode = PvpBot.Mode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Mode must be mace, elytra_mace or spear_mace"));
            return 0;
        }
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        ServerLevel lvl = src.getLevel();
        PvpBot bot = BOT.create(lvl, EntitySpawnReason.COMMAND);
        if (bot == null) return 0;
        Vec3 look = p.getLookAngle();
        bot.setPos(p.getX() + look.x * 6, p.getY(), p.getZ() + look.z * 6);
        bot.configure(lvl, mode, kit != null ? kit : modeName.toLowerCase(Locale.ROOT), level);
        bot.setTarget(p);
        lvl.addFreshEntity(bot);
        src.sendSuccess(() -> Component.literal("Spawned " + mode + " bot, level " + level), false);
        return 1;
    }
}
