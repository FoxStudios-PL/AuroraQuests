package gg.auroramc.quests.placeholder;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.placeholder.PlaceholderHandler;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.data.QuestData;
import gg.auroramc.quests.api.objective.Objective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import gg.auroramc.quests.api.questpool.QuestPool;
import gg.auroramc.quests.util.DurationFormatter;
import gg.auroramc.quests.util.RomanNumber;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;

public class QuestPlaceholderHandler implements PlaceholderHandler {
    @Override
    public String getIdentifier() {
        return "quests";
    }

    @Override
    public String onPlaceholderRequest(Player player, String[] args) {
        if (args.length < 2) return null;
        var profile = AuroraQuests.getInstance().getProfileManager().getProfile(player);
        if(profile == null) return "";
        var full = String.join("_", args);

        if (full.startsWith("tracked_")) {
            return handleTrackedPlaceholder(player, full.substring(8));
        }

        if (full.startsWith("is_at:")) {
            return handleIsAtPlaceholder(profile, full.substring(6));
        }

        if (full.endsWith("total_completed_raw")) {
            var sum = profile.getQuestPools().stream().mapToLong(QuestPool::getCompletedQuestCount).sum();
            return String.valueOf(sum);
        } else if (full.endsWith("total_completed")) {
            var sum = profile.getQuestPools().stream().mapToLong(QuestPool::getCompletedQuestCount).sum();
            return AuroraAPI.formatNumber(sum);
        } else if (full.endsWith("level_roman")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 12));
            if (pool == null) return null;
            return RomanNumber.toRoman(pool.getLevel());
        } else if (full.endsWith("level_raw")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 10));
            if (pool == null) return null;
            return String.valueOf(pool.getLevel());
        } else if (full.endsWith("level")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 6));
            if (pool == null) return null;
            return AuroraAPI.formatNumber(pool.getLevel());
        } else if (full.endsWith("current_count")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 14));
            if (pool == null) return null;
            return AuroraAPI.formatNumber(pool.getActiveQuests().size());
        } else if (full.endsWith("current_completed")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 18));
            if (pool == null) return null;
            return AuroraAPI.formatNumber(pool.getActiveQuests().stream().filter(Quest::isCompleted).count());
        } else if (full.endsWith("count_raw")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 10));
            if (pool == null) return null;
            return String.valueOf(pool.getCompletedQuestCount());
        } else if (full.endsWith("count")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 6));
            if (pool == null) return null;
            return AuroraAPI.formatNumber(pool.getCompletedQuestCount());
        } else if (full.endsWith("countdown_long")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 15));
            if (pool == null) return null;
            if (pool.isGlobal()) return null;
            return DurationFormatter.format(player, pool.getDurationUntilNextRoll(), DurationFormatter.Type.LONG);
        } else if (full.endsWith("countdown")) {
            var pool = profile.getQuestPool(full.substring(0, full.length() - 10));
            if (pool == null) return null;
            if (pool.isGlobal()) return null;
            return DurationFormatter.format(player, pool.getDurationUntilNextRoll(), DurationFormatter.Type.SHORT);
        }

        return null;
    }

    private String handleTrackedPlaceholder(Player player, String key) {
        QuestData questData = AuroraAPI.getUser(player.getUniqueId()).getData(QuestData.class);

        if (!questData.hasTrackedQuest()) {
          return "";
        }
        Profile profile = AuroraQuests.getInstance().getProfileManager().getProfile(player);

        if (profile == null) {
          return "";
        }
        QuestPool pool = profile.getQuestPool(questData.getTrackedPoolId());

        if (pool == null) {
          return "";
        }
        Quest quest = pool.getQuest(questData.getTrackedQuestId());

        if (quest == null) {
          return "";
        }
      return switch (key) {
        case "name" -> quest.getDefinition().getName();
        case "quest_id" -> quest.getId();
        case "pool_id" -> pool.getId();
        case "objective_current" -> String.valueOf(quest.getCurrentObjectiveIndex() + 1);
        case "objective_total" -> String.valueOf(quest.getObjectives().size());
        case "current_amount" -> {
          Objective obj = getCurrentObjective(quest);
          yield obj != null ? AuroraAPI.formatNumber(obj.getProgress()) : "";
        }
        case "required_amount" -> {
          Objective obj = getCurrentObjective(quest);
          yield obj != null ? AuroraAPI.formatNumber(obj.getTarget()) : "";
        }
        case "current_amount_raw" -> {
          Objective obj = getCurrentObjective(quest);
          yield obj != null ? String.valueOf((long) obj.getProgress()) : "";
        }
        case "required_amount_raw" -> {
          Objective obj = getCurrentObjective(quest);
          yield obj != null ? String.valueOf((long) obj.getTarget()) : "";
        }
        default -> {
          if (key.startsWith("objective_")) {
            try {
              int index = Integer.parseInt(key.substring(10)) - 1;

              if (index < 0 || index >= quest.getObjectives().size()) {
                yield "";
              }
              Objective obj = quest.getObjectives().get(index);

              if (quest.isObjectiveLocked(index)) {
                String lockedLore = quest.getDefinition().getLockedObjectiveLore();
                yield lockedLore != null ? lockedLore : "";
              }
              yield obj.display();
            } catch (NumberFormatException exception) {
              yield null;
            }
          }
          yield null;
        }
      };
    }

    private Objective getCurrentObjective(Quest quest) {
        int index = quest.getCurrentObjectiveIndex();

        if (index >= 0 && index < quest.getObjectives().size()) {
            return quest.getObjectives().get(index);
        }
        return null;
    }

    /**
     * %quests_is_at:&lt;pool&gt;:&lt;quest&gt;:&lt;objective&gt;%  ->  "true" / "false"
     * <p>
     * Returns "true" only when the quest is active (started, not completed) and the
     * player's current step is exactly the given objective. Colon-separated so that
     * pool/quest/objective ids may freely contain underscores. Returns "false" for
     * any not-applicable case, or null if the placeholder is malformed.
     */
    private String handleIsAtPlaceholder(Profile profile, String params) {
        String[] parts = params.split(":", 3);
        if (parts.length != 3) return null;

        var pool = profile.getQuestPool(parts[0]);
        if (pool == null) return "false";

        var quest = pool.getQuest(parts[1]);
        if (quest == null || quest.isCompleted() || !quest.isStarted()) return "false";

        var objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i).getId().equals(parts[2])) {
                return String.valueOf(quest.getCurrentObjectiveIndex() == i);
            }
        }
        return "false";
    }

    @Override
    public List<String> getPatterns() {
        var manager = AuroraQuests.getInstance().getPoolManager();

        var list = new ArrayList<String>(manager.getPoolIds().size() * 7 + 15);

        list.add("total_completed_raw");
        list.add("total_completed");
        list.add("tracked_name");
        list.add("tracked_quest_id");
        list.add("tracked_pool_id");
        list.add("tracked_objective_current");
        list.add("tracked_objective_total");
        list.add("tracked_objective_<number>");
        list.add("tracked_current_amount");
        list.add("tracked_required_amount");
        list.add("tracked_current_amount_raw");
        list.add("tracked_required_amount_raw");
        list.add("is_at:<pool>:<quest>:<objective>");

        for (var pool : manager.getPoolIds()) {
            list.add(pool + "_level");
            list.add(pool + "_level_roman%");
            list.add(pool + "_level_raw");
            list.add(pool + "_count");
            list.add(pool + "_count_raw");
            list.add(pool + "_current_count");
            list.add(pool + "_current_completed");
            list.add(pool + "_countdown");
            list.add(pool + "_countdown_long");
        }

        return list;
    }
}
