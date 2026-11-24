package dev.pvpbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads config/pvpbot.json. Edit it to make your own kits and levels, then run /pvpbot reload. */
public class BotConfig {
    public static class Kit {
        // Item spec: "namespace:item|namespace:enchant=level,namespace:enchant=level"
        public String mainhand = "", offhand = "", head = "", chest = "", legs = "", feet = "";
        public String spear = ""; // only used by spear_mace
    }

    public static class Tier {
        public int attackInterval = 12;     // ticks between bot attacks
        public int launchCooldown = 40;     // ticks between mace launches
        public double speed = 1.2;          // ground chase speed
        public double mistakeChance = 0.2;  // chance a launch is badly aimed
        public double health = 20;
    }

    public Map<String, Kit> kits = new LinkedHashMap<>();
    public List<Tier> levels = new ArrayList<>(); // index 0 = level 1

    private static BotConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static BotConfig get() {
        if (INSTANCE == null) reload();
        return INSTANCE;
    }

    public static void reload() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("pvpbot.json");
        BotConfig cfg = null;
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) { cfg = GSON.fromJson(r, BotConfig.class); }
            }
        } catch (Exception e) {
            System.err.println("[pvpbot] Could not read config, using defaults: " + e);
        }
        if (cfg == null || cfg.kits == null || cfg.kits.isEmpty() || cfg.levels == null || cfg.levels.isEmpty()) {
            cfg = defaults();
            try (Writer w = Files.newBufferedWriter(file)) { GSON.toJson(cfg, w); } catch (Exception ignored) {}
        }
        INSTANCE = cfg;
    }

    public static Tier tier(int level) {
        List<Tier> l = get().levels;
        return l.get(Math.max(0, Math.min(l.size() - 1, level - 1)));
    }

    private static Kit netherite(String main, String chest, String spear) {
        Kit k = new Kit();
        k.mainhand = main;
        k.head = "minecraft:netherite_helmet|minecraft:protection=4,minecraft:unbreaking=3";
        k.chest = chest;
        k.legs = "minecraft:netherite_leggings|minecraft:protection=4,minecraft:unbreaking=3";
        k.feet = "minecraft:netherite_boots|minecraft:protection=4,minecraft:feather_falling=4,minecraft:unbreaking=3";
        k.spear = spear;
        return k;
    }

    private static BotConfig defaults() {
        BotConfig c = new BotConfig();
        String mace = "minecraft:mace|minecraft:density=5,minecraft:unbreaking=3";
        String chestplate = "minecraft:netherite_chestplate|minecraft:protection=4,minecraft:unbreaking=3";
        c.kits.put("mace", netherite(mace, chestplate, ""));
        c.kits.put("elytra_mace", netherite(mace, "minecraft:elytra|minecraft:unbreaking=3", ""));
        c.kits.put("spear_mace", netherite(mace, chestplate, "minecraft:netherite_spear|minecraft:unbreaking=3"));
        for (int n = 1; n <= 10; n++) {
            Tier t = new Tier();
            t.attackInterval = 30 - (int) (2.2 * n);
            t.launchCooldown = 80 - 6 * n;
            t.speed = 0.9 + 0.06 * n;
            t.mistakeChance = Math.max(0.02, 0.5 - 0.048 * n);
            t.health = 20;
            c.levels.add(t);
        }
        return c;
    }

    /** Parses "minecraft:mace|minecraft:density=5,minecraft:breach=4" into a stack. */
    public static ItemStack stack(String spec, RegistryAccess access) {
        if (spec == null || spec.isBlank()) return ItemStack.EMPTY;
        String[] parts = spec.split("\\|", 2);
        Identifier id = Identifier.tryParse(parts[0].trim());
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        if (parts.length > 1) {
            var reg = access.lookupOrThrow(Registries.ENCHANTMENT);
            for (String e : parts[1].split(",")) {
                String[] kv = e.trim().split("=");
                Identifier eid = Identifier.tryParse(kv[0]);
                if (eid == null) continue;
                int lvl = kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 1;
                reg.get(ResourceKey.create(Registries.ENCHANTMENT, eid)).ifPresent(h -> s.enchant(h, lvl));
            }
        }
        return s;
    }
}
