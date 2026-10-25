package dev.pvphud.tracker;

import dev.pvphud.config.PvpConfig;
import dev.pvphud.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * All fight logic. Everything is observed client-side:
 *  - swings are counted from the attack mixins (cooldown-filtered => anti-spam)
 *  - hits are confirmed when the locked target's hurtTime rises shortly after a valid swing
 *  - damage / armor durability are read as deltas of synced entity data, so Unbreaking RNG
 *    is automatically exact (we see what the server actually applied).
 */
public final class CombatTracker {
    public static final CombatTracker I = new CombatTracker();
    public enum State { OFF, ACTIVE, PAUSED }

    public static final class Popup {
        public final String text; public final int rgb; public final boolean dealt; public final float ox; public final long born;
        Popup(String t, int rgb, boolean dealt, float ox, long born) { this.text = t; this.rgb = rgb; this.dealt = dealt; this.ox = ox; this.born = born; }
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public State state = State.OFF;
    public final List<Popup> popups = new ArrayList<>();
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random rng = new Random();

    // lock
    private int targetId = -1;
    private String targetName;
    private UUID targetUuid;
    private boolean targetIsPlayer;
    private float tHpShown, tAbsShown, tMaxShown = 20f;
    private final ItemStack[] tArmorShown = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    // last player you hit (independent of the locked target)
    private int lastHitId = -1;
    private String lastHitName = "";
    private UUID lastHitUuid;
    private double lhX, lhY, lhZ;
    private String lhDim = "overworld";
    private boolean lhLive;

    // stats
    private long tick, fightTicks, lastCombatTick, lastSwingTick;
    private boolean started, pendingSwing;
    private int swings, hits, hitsTaken, combo, best, armorDealt, armorTaken;
    private float dealt, taken, ratioDisplay = 0.5f;
    private double armorDealtSaved, armorTakenSaved;

    // baselines
    private float lastMeHp = -1, lastTHp = -1;
    private int lastTHurt;
    private final Item[] meItems = new Item[4], tItems = new Item[4];
    private final int[] meDmg = new int[4], tDmg = new int[4];

    private CombatTracker() {}

    // ---------- control ----------
    public boolean isVisible() {
        return state == State.ACTIVE || (state == State.PAUSED && PvpConfig.get().showWhenPaused);
    }
    public boolean hasTarget() { return targetId >= 0; }
    public double idleSeconds() { return (tick - lastCombatTick) / 20.0; }

    public void refresh() {
        PvpConfig c = PvpConfig.get();
        state = State.ACTIVE;
        targetId = -1; targetName = null; targetUuid = null; targetIsPlayer = false;
        tHpShown = 0; tAbsShown = 0; tMaxShown = 20f;
        for (int i = 0; i < 4; i++) tArmorShown[i] = ItemStack.EMPTY;
        started = false; pendingSwing = false; fightTicks = 0;
        swings = hits = hitsTaken = combo = best = armorDealt = armorTaken = 0;
        dealt = taken = 0; armorDealtSaved = armorTakenSaved = 0;
        lastMeHp = -1; lastTHp = -1; lastTHurt = 0;
        java.util.Arrays.fill(meItems, null); java.util.Arrays.fill(tItems, null);
        popups.clear();
        lastCombatTick = tick;
        Entity e = mc.targetedEntity;
        if (c.lockOnRefresh && e instanceof LivingEntity le && (!c.lockPlayersOnly || e instanceof PlayerEntity)) lock(le);
    }

    public void pause() { if (state == State.ACTIVE) state = State.PAUSED; }

    public void resume() {
        if (state != State.PAUSED) return;
        state = State.ACTIVE;
        lastMeHp = -1; lastTHp = -1; pendingSwing = false;
        java.util.Arrays.fill(meItems, null); java.util.Arrays.fill(tItems, null);
        lastCombatTick = tick;
    }

    private void lock(LivingEntity e) {
        targetId = e.getId();
        targetName = e.getName().getString();
        targetUuid = e.getUuid();
        targetIsPlayer = e instanceof PlayerEntity;
        lastTHp = e.getHealth() + e.getAbsorptionAmount();
        lastTHurt = e.hurtTime;
        java.util.Arrays.fill(tItems, null);
    }

    // ---------- events from mixins ----------
    public void onAttack(PlayerEntity p, Entity target) {
        PvpConfig c = PvpConfig.get();
        if (target instanceof PlayerEntity tp && tp != p) recordLastHit(tp, c);
        if (state == State.OFF && c.autoStart) refresh();
        if (state != State.ACTIVE) return;
        if (!(target instanceof LivingEntity le)) return;
        if (c.lockPlayersOnly && !(target instanceof PlayerEntity)) return;
        if (targetId < 0) lock(le);
        if (target.getId() != targetId) return;                       // different entity: ignore completely
        if (p.getAttackCooldownProgress(0.5f) < c.minCooldown) return; // spam click: ignore for accuracy
        swings++; started = true; pendingSwing = true; lastSwingTick = tick; lastCombatTick = tick;
    }

    private void recordLastHit(PlayerEntity tp, PvpConfig c) {
        boolean fresh = lastHitId != tp.getId();
        lastHitId = tp.getId();
        lastHitName = tp.getName().getString();
        lastHitUuid = tp.getUuid();
        lhX = tp.getX(); lhY = tp.getY(); lhZ = tp.getZ();
        lhLive = true;
        if (mc.world != null) lhDim = mc.world.getRegistryKey().getValue().getPath();
        if (fresh && c.coordsChat && mc.player != null) {
            mc.player.sendMessage(Text.literal(String.format(java.util.Locale.ROOT,
                    "[PvPHud] Hit %s at X %.0f  Y %.0f  Z %.0f (%s)", lastHitName, lhX, lhY, lhZ, lhDim)), false);
        }
    }

    public void onMiss(PlayerEntity p) {
        PvpConfig c = PvpConfig.get();
        if (state != State.ACTIVE || targetId < 0 || mc.world == null) return;
        if (p.getAttackCooldownProgress(0.5f) < c.minCooldown) return;
        Entity t = mc.world.getEntityById(targetId);
        if (t == null || p.distanceTo(t) > c.fightRange) return;
        swings++; started = true; lastCombatTick = tick;
        if (c.comboResetsOnMiss) combo = 0;
    }

    // ---------- per tick ----------
    public void tick() {
        tick++;
        PvpConfig c = PvpConfig.get();
        long now = Util.getMeasuringTimeMs();
        popups.removeIf(p -> now - p.born > c.popupDurationMs);

        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null) { lastMeHp = -1; lastTHp = -1; return; }
        // keep last-hit coordinates fresh while that player is loaded
        if (lastHitId >= 0) {
            Entity le = mc.world.getEntityById(lastHitId);
            if (le instanceof PlayerEntity lp && !lp.isRemoved()) {
                lhX = lp.getX(); lhY = lp.getY(); lhZ = lp.getZ(); lhLive = true;
                lhDim = mc.world.getRegistryKey().getValue().getPath();
            } else lhLive = false;
        }

        if (state != State.ACTIVE) return;

        LivingEntity t = null;
        if (targetId >= 0) {
            Entity e = mc.world.getEntityById(targetId);
            if (e instanceof LivingEntity le && !le.isRemoved()) t = le;
        }
        boolean count = c.countAllDamage || (t != null && me.distanceTo(t) <= c.fightRange);

        // --- you ---
        float meHp = me.getHealth() + me.getAbsorptionAmount();
        if (lastMeHp >= 0 && meHp < lastMeHp - 0.001f) {
            float d = lastMeHp - meHp;
            if (count && d >= c.minDamage) {
                taken += d; hitsTaken++; started = true; lastCombatTick = tick;
                if (c.comboResetsOnHit) combo = 0;
                popup(d, false); playTaken();
            }
        }
        lastMeHp = meHp;
        trackArmor(me, meItems, meDmg, true, count);

        // --- opponent ---
        if (t != null) {
            float tHp = t.getHealth() + t.getAbsorptionAmount();
            if (lastTHp >= 0 && tHp < lastTHp - 0.001f) {
                float d = lastTHp - tHp;
                if (d >= c.minDamage) { dealt += d; started = true; lastCombatTick = tick; popup(d, true); }
            }
            lastTHp = tHp;
            tHpShown = t.getHealth(); tAbsShown = t.getAbsorptionAmount(); tMaxShown = t.getMaxHealth();
            for (int i = 0; i < 4; i++) tArmorShown[i] = t.getEquippedStack(SLOTS[i]);

            if (t.hurtTime > lastTHurt && pendingSwing && tick - lastSwingTick <= c.hitWindowTicks) {
                hits++; combo++; best = Math.max(best, combo); pendingSwing = false; lastCombatTick = tick;
                playHit();
            }
            lastTHurt = t.hurtTime;
            trackArmor(t, tItems, tDmg, false, true);
        }
        if (pendingSwing && tick - lastSwingTick > c.hitWindowTicks) {
            pendingSwing = false;
            if (c.comboResetsOnMiss) combo = 0;
        }
        if (started) fightTicks++;

        HudData d = snapshot();
        float goal = HudData.balance(d.me, d.opp, c.balanceIncludeAbsorption);
        if (targetId < 0) goal = 0.5f;
        ratioDisplay += (goal - ratioDisplay) * Math.max(0.02f, c.balanceSmoothing);
    }

    private void trackArmor(LivingEntity e, Item[] items, int[] dmg, boolean self, boolean count) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = e.getEquippedStack(SLOTS[i]);
            if (s.isEmpty() || !s.isDamageable()) { items[i] = null; continue; }
            int d = s.getDamage();
            if (items[i] == s.getItem() && d > dmg[i] && count) {
                int delta = d - dmg[i];
                double chance = armorDamageChance(s);               // P(durability actually consumed)
                double saved = delta / chance - delta;              // expected hits absorbed by Unbreaking
                if (self) { armorTaken += delta; armorTakenSaved += saved; }
                else { armorDealt += delta; armorDealtSaved += saved; }
                started = true; lastCombatTick = tick;
            }
            items[i] = s.getItem(); dmg[i] = d;
        }
    }

    /** Vanilla armor rule: 60% + 40%/(level+1) chance to take durability damage. */
    private double armorDamageChance(ItemStack s) {
        int lvl = 0;
        try {
            var comp = s.getEnchantments();
            for (var e : comp.getEnchantments()) {
                if (e.matchesKey(Enchantments.UNBREAKING)) lvl = comp.getLevel(e);
            }
        } catch (Throwable ignored) {}
        return 0.6 + 0.4 / (lvl + 1);
    }

    // ---------- popups & sound ----------
    private void popup(float dmg, boolean dealtDmg) {
        PvpConfig c = PvpConfig.get();
        if (!c.popupsEnabled || (dealtDmg ? !c.popupShowDealt : !c.popupShowTaken)) return;
        float ox = (dealtDmg ? 1 : -1) * (12 + rng.nextFloat() * Math.max(1, c.popupSpread));
        int rgb = Colors.rgb(Colors.parse(dealtDmg ? c.dealtColor : c.takenColor, 0xFFFFFFFF));
        popups.add(new Popup("-" + c.fmtDamage(dmg), rgb, dealtDmg, ox, Util.getMeasuringTimeMs()));
        if (popups.size() > 24) popups.remove(0);
    }

    public void testPopups() { popup(4f, true); popup(2.5f, false); }

    public void playHit() {
        PvpConfig c = PvpConfig.get();
        if (!c.soundsEnabled || !c.hitSoundEnabled) return;
        play(c.hitSound.event(), Math.min(c.maxPitch, c.hitPitch + combo * c.comboPitchStep), c.masterVolume * c.hitVolume);
    }

    public void playTaken() {
        PvpConfig c = PvpConfig.get();
        if (!c.soundsEnabled || !c.takenSoundEnabled) return;
        play(c.takenSound.event(), c.takenPitch, c.masterVolume * c.takenVolume);
    }

    private void play(SoundEvent ev, float pitch, float vol) {
        if (vol <= 0.001f) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(ev, Math.max(0.5f, pitch), Math.min(1f, vol)));
    }

    // ---------- snapshot ----------
    public HudData snapshot() {
        HudData d = new HudData();
        ClientPlayerEntity me = mc.player;
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (me != null) {
            d.me.name = me.getName().getString(); d.me.uuid = me.getUuid();
            d.me.entry = nh != null ? nh.getPlayerListEntry(me.getUuid()) : null;
            d.me.hp = me.getHealth(); d.me.abs = me.getAbsorptionAmount(); d.me.max = me.getMaxHealth();
            for (int i = 0; i < 4; i++) d.me.armor[i] = me.getEquippedStack(SLOTS[i]);
        }
        d.hasTarget = targetId >= 0;
        d.opp.name = targetName != null ? targetName : "Waiting for target...";
        d.opp.uuid = targetUuid;
        if (targetUuid != null && targetIsPlayer && nh != null) d.opp.entry = nh.getPlayerListEntry(targetUuid);
        d.opp.hp = tHpShown; d.opp.abs = tAbsShown; d.opp.max = tMaxShown;
        for (int i = 0; i < 4; i++) d.opp.armor[i] = tArmorShown[i];
        d.ticks = fightTicks; d.combo = combo; d.best = best; d.swings = swings; d.hits = hits; d.hitsTaken = hitsTaken;
        d.dealt = dealt; d.taken = taken; d.armorDealt = armorDealt; d.armorTaken = armorTaken;
        d.armorDealtSaved = armorDealtSaved; d.armorTakenSaved = armorTakenSaved;
        d.ratio = ratioDisplay; d.paused = state == State.PAUSED;

        if (lastHitId >= 0) {
            d.hasLastHit = true; d.lastHitLive = lhLive; d.lastHitName = lastHitName; d.lastHitDim = lhDim;
            d.lx = lhX; d.ly = lhY; d.lz = lhZ;
            if (me != null) {
                double dx = lhX - me.getX(), dy = lhY - me.getY(), dz = lhZ - me.getZ();
                d.lastHitDist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                double rel = targetYaw - me.getYaw();
                rel = ((rel % 360) + 540) % 360 - 180;
                d.lastHitBearing = (float) rel;
            }
        }
        return d;
    }
}
