package gg.auroramc.quests.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * A per-player sidebar rendered with Adventure Components (full RGB / MiniMessage,
 * no legacy length limit). Line content lives in a team prefix and is updated in
 * place to avoid flicker; the objective is only touched when the line count changes.
 * Limited to 15 lines by Minecraft.
 */
public class QuestSidebar {
    private static final List<String> ENTRIES = new ArrayList<>();

    static {
        // 15 unique, invisible entries (a colour code + reset render as nothing).
        for (char c : "0123456789abcde".toCharArray()) {
            ENTRIES.add("§" + c + "§r");
        }
    }

    private final Player player;
    private final Scoreboard board;
    private final Objective objective;
    private int shownLines = 0;

    public QuestSidebar(Player player, Component title) {
        this.player = player;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("aq_quests", Criteria.DUMMY, title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        // Hide the red score numbers shown on the right of each line (Paper 1.20.3+).
        this.objective.numberFormat(NumberFormat.blank());
        for (int i = 0; i < ENTRIES.size(); i++) {
            Team team = board.registerNewTeam("aq_line_" + i);
            team.addEntry(ENTRIES.get(i));
        }
        player.setScoreboard(board);
    }

    public void update(Component title, List<Component> lines) {
        objective.displayName(title);

        int n = Math.min(lines.size(), ENTRIES.size());
        for (int i = 0; i < n; i++) {
            Team team = board.getTeam("aq_line_" + i);
            if (team != null) {
                team.prefix(lines.get(i));
            }
            // Highest score is shown on top, so the first config line sits at the top.
            objective.getScore(ENTRIES.get(i)).setScore(n - i);
        }
        // Drop lines that are no longer used (current step has fewer rewards/descriptions).
        for (int i = n; i < shownLines; i++) {
            board.resetScores(ENTRIES.get(i));
        }
        shownLines = n;
    }

    public void destroy() {
        try {
            if (player.isOnline() && player.getScoreboard() == board) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        } catch (Exception ignored) {
        }
        for (Team team : new ArrayList<>(board.getTeams())) {
            try {
                team.unregister();
            } catch (Exception ignored) {
            }
        }
        try {
            objective.unregister();
        } catch (Exception ignored) {
        }
    }
}
