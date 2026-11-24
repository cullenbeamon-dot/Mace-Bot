package dev.pvpbot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A humanoid practice dummy that fights with a mace.
 *  MACE        - launches itself (wind-charge style) and dives onto you
 *  ELYTRA_MACE - launches much higher and faster, then dives
 *  SPEAR_MACE  - spear dash to close distance, swaps to mace, launches and smashes
 * Smash damage is real: it uses vanilla mace logic (fall distance > 1.5 blocks).
 */
public class PvpBot extends Zombie {
    public enum Mode { MACE, ELYTRA_MACE, SPEAR_MACE }
    private enum Phase { APPROACH, DASH, AIR }

    private Mode mode = Mode.MACE;
    private BotConfig.Tier tier = BotConfig.tier(5);
    private ItemStack maceStack = ItemStack.EMPTY;
    private ItemStack spearStack = ItemStack.EMPTY;
    private Phase phase = Phase.APPROACH;
    private int cd = 40, phaseTicks = 0, hitCd = 0;

    public PvpBot(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Zombie.createAttributes()
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public void configure(ServerLevel level, Mode mode, String kitName, int lvl) {
        this.mode = mode;
        this.tier = BotConfig.tier(lvl);
        BotConfig.Kit kit = BotConfig.get().kits.get(kitName);
        if (kit == null) kit = BotConfig.get().kits.values().iterator().next();
        var access = level.registryAccess();
        maceStack = BotConfig.stack(kit.mainhand, access);
        spearStack = BotConfig.stack(kit.spear, access);
        setItemSlot(EquipmentSlot.MAINHAND, maceStack.copy());
        setItemSlot(EquipmentSlot.OFFHAND, BotConfig.stack(kit.offhand, access));
        setItemSlot(EquipmentSlot.HEAD, BotConfig.stack(kit.head, access));
        setItemSlot(EquipmentSlot.CHEST, BotConfig.stack(kit.chest, access));
        setItemSlot(EquipmentSlot.LEGS, BotConfig.stack(kit.legs, access));
        setItemSlot(EquipmentSlot.FEET, BotConfig.stack(kit.feet, access));
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0.0f);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tier.health);
        setHealth((float) tier.health);
        setPersistenceRequired();
        setCustomName(Component.literal("PvP Bot [" + mode.name().toLowerCase() + " Lv" + lvl + "]"));
        setCustomNameVisible(true);
        cd = 40;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL)) return false; // bot never dies to its own dives
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;

        if (cd > 0) cd--;
        if (hitCd > 0) hitCd--;
        phaseTicks++;
        getLookControl().setLookAt(t, 30f, 30f);

        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double nx = horiz > 1e-4 ? dx / horiz : 0, nz = horiz > 1e-4 ? dz / horiz : 0;

        switch (phase) {
            case APPROACH -> {
                getNavigation().moveTo(t, tier.speed);
                if (onGround() && horiz < 3.0 && hitCd <= 0 && cd > 0) { // small ground poke while waiting
                    doHurtTarget(sl, t);
                    hitCd = tier.attackInterval;
                }
                if (cd <= 0 && onGround()) {
                    if (mode == Mode.SPEAR_MACE && !spearStack.isEmpty() && horiz > 4.0 && horiz < 14.0) {
                        phase = Phase.DASH;
                        phaseTicks = 0;
                        getNavigation().stop();
                        setItemSlot(EquipmentSlot.MAINHAND, spearStack.copy());
                    } else if (horiz < (mode == Mode.ELYTRA_MACE ? 24.0 : 10.0)) {
                        launch(nx, nz);
                    }
                }
            }
            case DASH -> {
                setDeltaMovement(nx * 0.9, getDeltaMovement().y, nz * 0.9);
                this.hasImpulse = true;
                if (horiz < 3.4 || phaseTicks > 14) {
                    if (hitCd <= 0 && horiz < 3.8) { // spear jab
                        doHurtTarget(sl, t);
                        hitCd = tier.attackInterval;
                    }
                    setItemSlot(EquipmentSlot.MAINHAND, maceStack.copy()); // swap to mace
                    launch(nx, nz);
                }
            }
            case AIR -> {
                double h = mode == Mode.ELYTRA_MACE ? 0.55 : 0.32;
                var v = getDeltaMovement();
                if (v.y < 0.1) { // descending: dive at the target
                    setDeltaMovement(nx * h, v.y - 0.04, nz * h);
                    this.hasImpulse = true;
                }
                if (hitCd <= 0 && fallDistance > 1.5 && distanceToSqr(t) < 9.0) {
                    doHurtTarget(sl, t); // vanilla mace smash applies here
                    hitCd = tier.attackInterval;
                    phase = Phase.APPROACH;
                    cd = tier.launchCooldown;
                } else if (onGround() && phaseTicks > 5) { // missed
                    phase = Phase.APPROACH;
                    cd = tier.launchCooldown / 2;
                }
            }
        }
    }

    private void launch(double nx, double nz) {
        double vy = mode == Mode.ELYTRA_MACE ? 1.5 : 1.0;
        double h = mode == Mode.ELYTRA_MACE ? 0.55 : 0.18;
        if (random.nextDouble() < tier.mistakeChance) { // badly aimed launch
            double a = (random.nextDouble() - 0.5) * 1.8;
            double c = Math.cos(a), s = Math.sin(a);
            double rx = nx * c - nz * s, rz = nx * s + nz * c;
            nx = rx; nz = rz;
        }
        setDeltaMovement(nx * h, vy, nz * h);
        this.hasImpulse = true;
        getNavigation().stop();
        phase = Phase.AIR;
        phaseTicks = 0;
    }
}
