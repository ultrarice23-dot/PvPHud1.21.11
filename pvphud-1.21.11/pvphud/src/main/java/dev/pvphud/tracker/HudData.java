package dev.pvphud.tracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.UUID;

/** Snapshot the renderer draws from. */
public final class HudData {
    public static final class Card {
        public String name = "";
        public UUID uuid;
        /** Used to draw the head; null => placeholder box. */
        public PlayerListEntry entry;
        public float hp, abs, max = 20f;
        public final ItemStack[] armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    }

    public static final HudData EMPTY = new HudData();

    public Card me = new Card(), opp = new Card();
    public long ticks;
    public int combo, best, swings, hits, hitsTaken, armorDealt, armorTaken;
    public float dealt, taken;
    public double armorDealtSaved, armorTakenSaved;
    public float ratio = 0.5f;
    public boolean paused, hasTarget;

    // last player you hit
    public boolean hasLastHit, lastHitLive;
    public String lastHitName = "";
    public String lastHitDim = "overworld";
    public double lx, ly, lz;
    public float lastHitDist, lastHitBearing;

    public static float balance(Card a, Card b, boolean withAbs) {
        float pa = pct(a, withAbs), pb = pct(b, withAbs);
        float sum = pa + pb;
        return sum <= 0.0001f ? 0.5f : pa / sum;
    }

    public static float pct(Card c, boolean withAbs) {
        float max = Math.max(1f, c.max);
        return Math.max(0f, (c.hp + (withAbs ? c.abs : 0f)) / max);
    }

    public void fillDemoCoords() {
        hasLastHit = true; lastHitLive = true; lastHitName = "Opponent"; lastHitDim = "overworld";
        lx = 123; ly = 64; lz = -456; lastHitDist = 12.3f; lastHitBearing = 40f;
    }

    public static HudData demo() {
        MinecraftClient mc = MinecraftClient.getInstance();
        HudData d = new HudData();
        d.me.name = mc.player != null ? mc.player.getName().getString() : "You";
        d.me.uuid = mc.player != null ? mc.player.getUuid() : null;
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (nh != null && d.me.uuid != null) d.me.entry = nh.getPlayerListEntry(d.me.uuid);
        d.me.hp = 17f; d.me.abs = 0f;
        d.opp.name = "Opponent";
        d.opp.hp = 14.5f; d.opp.abs = 4f;
        ItemStack[] a = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
        int[] dmg = {40, 120, 90, 20};
        for (int i = 0; i < 4; i++) { a[i].setDamage(dmg[i]); d.opp.armor[i] = a[i]; d.me.armor[i] = a[3 - i]; }
        d.ticks = 1250; d.combo = 3; d.best = 7; d.swings = 21; d.hits = 13; d.hitsTaken = 9;
        d.dealt = 34.5f; d.taken = 27f; d.armorDealt = 46; d.armorTaken = 33;
        d.armorDealtSaved = 31; d.armorTakenSaved = 22;
        d.hasTarget = true;
        d.ratio = balance(d.me, d.opp, true);
        d.fillDemoCoords();
        return d;
    }
}
