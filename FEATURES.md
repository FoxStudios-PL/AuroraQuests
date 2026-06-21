# AuroraQuests  New Features

## Tracked Quest Placeholders

| Placeholder | Description |
|---|---|
| `%quests_tracked_name%` | Name of the currently tracked quest |
| `%quests_tracked_quest_id%` | ID of the tracked quest |
| `%quests_tracked_pool_id%` | Pool ID of the tracked quest |
| `%quests_tracked_objective_current%` | Current objective number (1-based) |
| `%quests_tracked_objective_total%` | Total number of objectives in the quest |
| `%quests_tracked_objective_<number>%` | Display line for objective N (e.g. `tracked_objective_1`, `tracked_objective_2`)  shows locked lore if the objective is locked |
| `%quests_tracked_current_amount%` | Current progress of the active objective (formatted) |
| `%quests_tracked_required_amount%` | Required amount to complete the active objective (formatted) |
| `%quests_tracked_current_amount_raw%` | Current progress as raw integer |
| `%quests_tracked_required_amount_raw%` | Required amount as raw integer |

All placeholders return an empty string when no quest is being tracked.

---

## Extended `/quests complete`

```
/quests complete <player> <pool_id> <quest_id> [objective_id] [silent]
```

| Usage | Effect |
|---|---|
| `/quests complete <player> <pool> <quest>` | Completes the entire quest |
| `/quests complete <player> <pool> <quest> all` | Completes the entire quest |
| `/quests complete <player> <pool> <quest> all true` | Completes the entire quest silently |
| `/quests complete <player> <pool> <quest> <objective>` | Completes only the specified objective |
| `/quests complete <player> <pool> <quest> <objective> true` | Completes the specified objective silently |

Backwards compatible  passing `true`/`false` as the 4th argument still works as the old silent flag.
