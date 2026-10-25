package dev.pvphud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class PvpConfig {
    public enum Theme { DARK, LIGHT, CLEAR, CUSTOM }
    public enum FontChoice { DEFAULT, UNIFORM, ALT, ILLAGER, CUSTOM }
    public enum PopupAnchor { CROSSHAIR, TARGET_CARD }
    public enum DamageUnit { HP, HEARTS }
    public enum HeartStyle { SPRITES, NUMBERS, BOTH }

    public enum SoundChoice {
        ORB, ARROW_HIT, ITEM_PICKUP, CRIT, LEVEL_UP;
        public SoundEvent event() {
            return switch (this) {
                case ORB -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
                case ARROW_HIT -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
                case ITEM_PICKUP -> SoundEvents.ENTITY_ITEM_PICKUP;
                case CRIT -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
                case LEVEL_UP -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            };
        }
    }

    public static class ElementCfg {
        public float x = 0.5f, y = 0.5f, scale = 1f;
        public boolean visible = true;
        public ElementCfg() {}
        public ElementCfg(float x, float y) { this.x = x; this.y = y; }
    }

    // ---------- General ----------
    public boolean enabled = true;
    public boolean showWhenPaused = false;
    public boolean lockPlayersOnly = true;
    public boolean lockOnRefresh = true;
    public boolean autoStart = false;
    public boolean snapToCenter = true;
    public int autoHideSeconds = 0;

    // ---------- Theme ----------
    public Theme theme = Theme.DARK;
    public FontChoice font = FontChoice.DEFAULT;
    public String customFontId = "pvphud:custom";
    public boolean bold = false;
    public boolean textShadow = true;
    public String bgColor = "#0E0E16";
    public String textColor = "#FFFFFF";
    public String borderColor = "#3A3A55";
    public String accentColor = "#55AAFF";
    public String selfColor = "#55FF77";
    public String enemyColor = "#FF5566";
    public String absorbColor = "#FFD633";
    public float bgOpacity = 0.65f;
    public float textOpacity = 1f;
    public float borderOpacity = 1f;
    public boolean showBorder = true;
    public int cornerRadius = 3;
    public int padding = 4;
    public float globalScale = 1f;

    // ---------- Layout / cards ----------
    public ElementCfg balance = new ElementCfg(0.5f, 0.07f);
    public ElementCfg target = new ElementCfg(0.14f, 0.30f);
    public ElementCfg self = new ElementCfg(0.14f, 0.62f);
    public ElementCfg stats = new ElementCfg(0.87f, 0.30f);
    public ElementCfg coords = new ElementCfg(0.87f, 0.66f);

    public int cardWidth = 140;
    public boolean showHead = true;
    public boolean showName = true;
    public boolean showHpNumber = true;
    public HeartStyle heartStyle = HeartStyle.SPRITES;
    public boolean showAbsorptionRow = true;
    public boolean showArmor = true;

    public int balanceWidth = 180;
    public int balanceBarHeight = 8;
    public float balanceTilt = 6f;
    public boolean balanceShowPercent = true;
    public boolean balanceCenterMark = true;
    public boolean balanceIncludeAbsorption = true;
    public float balanceSmoothing = 0.2f;

    // ---------- Stats ----------
    public int statsWidth = 170;
    public boolean statsShowTitle = true;
    public boolean showTime = true;
    public boolean showCombo = true;
    public boolean showAccuracy = true;
    public boolean showHits = true;
    public boolean showHitDiff = true;
    public boolean showDamage = true;
    public boolean showArmorDamage = true;
    public boolean showUnbreaking = true;
    public boolean colorDiff = true;
    public DamageUnit damageUnit = DamageUnit.HP;
    public int damageDecimals = 1;

    // ---------- Last-hit coordinates ----------
    public int coordsWidth = 150;
    public boolean coordsAlways = true;
    public boolean coordsShowDistance = true;
    public boolean coordsShowDirection = true;
    public boolean coordsShowDimension = true;
    public boolean coordsChat = false;

    // ---------- Popups ----------
    public boolean popupsEnabled = true;
    public boolean popupShowDealt = true;
    public boolean popupShowTaken = true;
    public float popupSize = 1.2f;
    public int popupDurationMs = 1100;
    public int popupRise = 28;
    public int popupSpread = 24;
    public PopupAnchor popupAnchor = PopupAnchor.CROSSHAIR;
    public String dealtColor = "#55FF77";
    public String takenColor = "#FF5566";

    // ---------- Sound ----------
    public boolean soundsEnabled = true;
    public float masterVolume = 0.6f;
    public boolean hitSoundEnabled = true;
    public SoundChoice hitSound = SoundChoice.ORB;
    public float hitVolume = 0.8f;
    public float hitPitch = 0.9f;
    public float comboPitchStep = 0.05f;
    public float maxPitch = 2.0f;
    public boolean takenSoundEnabled = true;
    public SoundChoice takenSound = SoundChoice.ARROW_HIT;
    public float takenVolume = 0.5f;
    public float takenPitch = 0.8f;

    // ---------- Combat ----------
    public float minCooldown = 0.9f;
    public int hitWindowTicks = 8;
    public int fightRange = 16;
    public boolean countAllDamage = false;
    public float minDamage = 0.05f;
    public boolean comboResetsOnHit = true;
    public boolean comboResetsOnMiss = false;

    // ---------- helpers ----------
    public ElementCfg element(HudElementType t) {
        return switch (t) {
            case BALANCE -> balance;
            case TARGET -> target;
            case SELF -> self;
            case STATS -> stats;
            case COORDS -> coords;
        };
    }

    public void resetLayout() {
        balance = new ElementCfg(0.5f, 0.07f);
        target = new ElementCfg(0.14f, 0.30f);
        self = new ElementCfg(0.14f, 0.62f);
        stats = new ElementCfg(0.87f, 0.30f);
        coords = new ElementCfg(0.87f, 0.66f);
        globalScale = 1f;
    }

    public String fmtDamage(float hp) {
        float v = damageUnit == DamageUnit.HEARTS ? hp / 2f : hp;
        return String.format(Locale.ROOT, "%." + damageDecimals + "f", v);
    }

    public String unitSuffix() { return damageUnit == DamageUnit.HEARTS ? " hearts" : " HP"; }

    private void sanitize() {
        if (balance == null) balance = new ElementCfg(0.5f, 0.07f);
        if (target == null) target = new ElementCfg(0.14f, 0.30f);
        if (self == null) self = new ElementCfg(0.14f, 0.62f);
        if (stats == null) stats = new ElementCfg(0.87f, 0.30f);
        if (coords == null) coords = new ElementCfg(0.87f, 0.66f);
        if (theme == null) theme = Theme.DARK;
        if (font == null) font = FontChoice.DEFAULT;
        if (heartStyle == null) heartStyle = HeartStyle.SPRITES;
        if (damageUnit == null) damageUnit = DamageUnit.HP;
        if (popupAnchor == null) popupAnchor = PopupAnchor.CROSSHAIR;
        if (hitSound == null) hitSound = SoundChoice.ORB;
        if (takenSound == null) takenSound = SoundChoice.ARROW_HIT;
        if (customFontId == null) customFontId = "pvphud:custom";
    }

    // ---------- persistence ----------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PvpConfig INSTANCE = new PvpConfig();

    public static PvpConfig get() { return INSTANCE; }
    public static void reset() { INSTANCE = new PvpConfig(); save(); }

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("pvphud.json"); }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                PvpConfig c = GSON.fromJson(r, PvpConfig.class);
                if (c != null) INSTANCE = c;
            } catch (Exception e) { e.printStackTrace(); }
        }
        INSTANCE.sanitize();
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
