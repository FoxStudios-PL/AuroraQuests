# AuroraQuests  New Features

## Quest Placeholders

> **PlaceholderAPI prefix.** AuroraQuests exposes its placeholders through Aurora's
> shared PlaceholderAPI expansion, whose identifier is **`aurora`**. Every placeholder
> below is therefore prefixed with **`aurora_quests_`** (for example
> `%aurora_quests_tracked_name%`) — *not* `%quests_...%`. PlaceholderAPI must be installed.

### Tracked quest

These reflect the quest the player is currently tracking (`/quests track`).

> **Tracking queue:** a player can track several quests at once. They form an ordered
> queue and **only the first one is "visible"** — the `tracked_*` placeholders (and the
> scoreboard, when available) always reflect the **head** of the queue. When the head
> quest is completed it is removed and the next tracked quest takes its place
> automatically. The maximum queue size is set by `tracking.max-tracked-quests` in
> `config.yml` (default `5`, use `0` or less for unlimited).

| Placeholder | Description |
|---|---|
| `%aurora_quests_tracked_name%` | Name of the currently tracked quest |
| `%aurora_quests_tracked_chapter_name%` | `chapter` of the tracked quest (see Quest scoreboard) |
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

## Quest scoreboard

An optional sidebar shown **only while a player is tracking a quest** (`/quests track`). Configured entirely in `config.yml` under `scoreboard:` and rendered with legacy (`&`), MiniMessage (`<#hex>`, `<yellow>`) and PlaceholderAPI support. Max 15 lines (Minecraft limit).

### New quest fields

```yaml
name: "&6Tutoriel d'Edynn"
chapter: "&eChapitre 1"          # optional; exposed as {chapter} and %aurora_quests_tracked_chapter_name%
linear-objectives: true
tasks:
  choisir_classe:
    display: "{status} &fChoose your class"
    description:                  # optional; detailed lines for the current step on the scoreboard
      - "&7Go to Hortense at spawn."
      - "&7Pick the class that suits you."
```

### `config.yml`

```yaml
scoreboard:
  enabled: false                 # turning on requires a restart; then /quests reload tunes it live
  refresh-interval: 20           # ticks (20 = 1s)
  title: "&6&lQUEST"
  lines:
    - ""
    - "<#F7B700>{chapter} &7(&f{step}&7/&f{step_total}&7)"
    - "<#dedede>{quest_name}"
    - ""
    - " &8• &fObjective: <yellow>{display}"
    - " &8• &fRewards:"
    - "    &7- &f{reward}"        # this line repeats once per reward of the current step
    - ""
    - "<#F7B700>Description"
    - "{description}"             # this line repeats once per description line of the current step
    - ""
```

### Tokens

| Token | Value |
|---|---|
| `{chapter}` | the quest's `chapter` |
| `{quest_name}` | the quest's `name` |
| `{step}` / `{step_total}` | current step number / total steps |
| `{display}` | current step's `display`, without the `{status}` icon and `{current}`/`{required}` counters |
| `{reward}` | repeats the line once per reward of the current step (its `display`) |
| `{description}` | repeats the line once per `description` line of the current step |

Performance: only players with a tracked quest own a (per-player, Folia-safe) refresh task; everyone else costs nothing.

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
