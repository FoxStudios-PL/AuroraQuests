# AuroraQuests  New Features

## Quest Placeholders

> **PlaceholderAPI prefix.** AuroraQuests exposes its placeholders through Aurora's
> shared PlaceholderAPI expansion, whose identifier is **`aurora`**. Every placeholder
> below is therefore prefixed with **`aurora_quests_`** (for example
> `%aurora_quests_tracked_name%`) — *not* `%quests_...%`. PlaceholderAPI must be installed.

### Tracked quest

These reflect the quest the player is currently tracking (`/quests track`).

| Placeholder | Description |
|---|---|
| `%aurora_quests_tracked_name%` | Name of the currently tracked quest |
| `%aurora_quests_tracked_quest_id%` | ID of the tracked quest |
| `%aurora_quests_tracked_pool_id%` | Pool ID of the tracked quest |
| `%aurora_quests_tracked_objective_current%` | Current objective number (1-based) |
| `%aurora_quests_tracked_objective_total%` | Total number of objectives in the quest |
| `%aurora_quests_tracked_objective_<number>%` | Display line for objective N (e.g. `%aurora_quests_tracked_objective_1%`)  shows locked lore if the objective is locked |
| `%aurora_quests_tracked_current_amount%` | Current progress of the active objective (formatted) |
| `%aurora_quests_tracked_required_amount%` | Required amount to complete the active objective (formatted) |
| `%aurora_quests_tracked_current_amount_raw%` | Current progress as raw integer |
| `%aurora_quests_tracked_required_amount_raw%` | Required amount as raw integer |

All tracked placeholders return an empty string when no quest is being tracked.

### Is the player at a given step?

| Placeholder | Returns |
|---|---|
| `%aurora_quests_is_at_<pool>_<quest>_<objective>%` | `true` if the player's current step in that quest is exactly `<objective>` (quest started and not completed), otherwise `false` |

- The `<pool>` is the quest pool folder name, `<quest>` the quest file path under `quests/` without `.yml`, and `<objective>` the task key.
- Ids may contain underscores (e.g. `tutoriel_edynn`); they are resolved against the real ids.
- An explicit colon form is also accepted: `%aurora_quests_is_at:<pool>:<quest>:<objective>%`.
- Most meaningful for `linear-objectives` quests, where there is a single current step.

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
