package net.expedition.route;

import java.util.List;
import java.util.Map;

/**
 * The story's acts. An act is a route tier: all its steps open together (their dungeons are placed and marked on the
 * player's way: the fireseed) and can be done in any order. Three quarters of them done (Route.checkActDone), the next
 * act opens. Texts: lang keys {@code expedition.story.*}; who speaks each line: letters
 * here: {@code T}, the Tree, which tells the whole story.
 */
public final class Acts {
    private Acts() {
    }

    public static final int LAST = 7;

    /**
     * @param intro speakers of the act's opening lines
     * @param keystones steps whose fall gets a scene of its own (their outro speakers)
     */
    public record Act(int number, String tier, String intro, Map<String, String> keystones) {
    }

    public static final List<Act> ALL = List.of(
        new Act(1, "T0", "TTTT", Map.of("03_wrought_chamber", "TT", "10_ocean_monument", "TT")),
        new Act(2, "T1", "TTTTT", Map.of("11_pirate_ship", "TT", "05_mossy_temple", "TT")),
        new Act(3, "T2", "TTTTT", Map.of("06_underworld_arena", "TT", "18_ender_dragon", "TTT")),
        new Act(4, "T3", "TTTTT", Map.of("19_ruined_citadel", "TT", "27_lich_tower", "TT")),
        new Act(5, "T4", "TTTTT", Map.of("14_burning_arena", "TT", "15_soul_blacksmith", "TT")),
        new Act(6, "T5", "TTTTT", Map.of("21_ancient_factory", "TT", "22_sunken_city", "TT", "31_wither", "TT")),
        new Act(7, "T6", "TTTTT", Map.of("24_malkuth", "TT", "25_chesed", "TT", "26_geburah", "TT")));

    /** Speakers of the finale, after the last act's key bosses all fell. */
    public static final String FINALE = "TTTTTT";

    public static Act get(int number) {
        return number >= 1 && number <= ALL.size() ? ALL.get(number - 1) : null;
    }

    /** The act of a route step: its tier (T0 is act 1). */
    public static int actOf(RouteStep step) {
        return Integer.parseInt(step.tier().substring(1)) + 1;
    }

    /** The speaker's lang key for a letter: the Tree tells the whole story. */
    public static String speaker(char letter) {
        return "expedition.story.speaker.tree";
    }
}
