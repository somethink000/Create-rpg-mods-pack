package net.expedition.route;

/**
 * The gear a player can have made by each chapter (what the boss balance is measured against): chapters I-II iron
 * (a crafting table), III-IV diamond (diamonds from chests further out, a mechanical crafter), V-VII netherite (scrap
 * from tier-2 chests on). Bosses' own gear (their hearts, on Create machines) is better: a bonus, not the measure.
 */
public final class ChapterGear {
    private ChapterGear() {
    }

    /** Index: the chapter (1-7). */
    static final float[] ARMOR = {0, 15, 15, 20, 20, 20, 20, 20};
    static final float[] TOUGHNESS = {0, 0, 0, 8, 8, 12, 12, 12};
    /** The chapter's sword: iron, iron, diamond, diamond, netherite... (attack damage, the player's 1 included). */
    static final float[] SWORD = {0, 6, 6, 7, 7, 8, 8, 8};
    /** A sword's attacks a second. */
    static final float SWORD_SPEED = 1.6f;

    public static float armor(int act) {
        return ARMOR[Math.max(1, Math.min(7, act))];
    }

    public static float toughness(int act) {
        return TOUGHNESS[Math.max(1, Math.min(7, act))];
    }

    public static float sword(int act) {
        return SWORD[Math.max(1, Math.min(7, act))];
    }

    /** Damage left after armor (vanilla's formula: CombatRules.getDamageAfterAbsorb). */
    public static float afterArmor(float damage, float armor, float toughness) {
        float f = 2 + toughness / 4;
        float reduction = Math.max(armor * 0.2f, Math.min(armor - damage / f, 20));
        return damage * (1 - reduction / 25);
    }
}
