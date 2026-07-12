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

---

## Auto chat centering: the `<center>` marker

Chat lines used to be "centered" with hardcoded leading spaces, which breaks as soon as a
line contains a variable-width placeholder (`{quest}`, `{reward}`, `{level}`, …). Prefix a
line with **`<center>`** instead: it is centered **at render time, after placeholders are
resolved**, and the marker is removed from the output.

```yaml
quest-complete-message:
  message:
    - ' '
    - '<center>&#FFAA00&lQUÊTE TERMINÉE'
    - '<center>&fVous avez terminé'
    - '<center>&#55FFFF&l{quest}'
    - component:rewards
    - ' '

display-components:
  rewards:
    title: ' '
    line: '<center>{reward}'
```

Supported everywhere the plugin sends chat: `quest-complete-message` (global and per-quest
override), `level-up-message`, `display-components.*.title`/`.line`, and every
`messages_xx.yml` entry. Non-chat renders are untouched: menu lore drops the marker
without padding, and title/actionbar/scoreboard keep their own systems. Lines without the
marker are never modified (you can mix centered and left-aligned lines in one block).

### `config.yml` options

```yaml
# Half chat width in pixels the text is centered on.
# 154 = vanilla default (320px wide chat).
chat-center-px: 154
# Width used for characters missing from the vanilla font table (glyphs, CJK, ...)
unknown-char-width: 8
# Optional per-glyph widths (resource pack glyphs)
glyph-widths:
  'ꕾ': 14      # raw glyph character
  lys: 12      # width applied to <glyph:lys> tags
```

`chat-center-px` **must** be tuned per server: the effective chat width depends on the
client chat settings / GUI scale / resource pack (e.g. ~107 instead of 154 on some setups).

### How the width is measured

Standard Bukkit "center message" algorithm: formatting codes take 0px — legacy (`&x`,
`&#RRGGBB`, `§x§F§F…`) and MiniMessage tags (`<gold>`, `<#FFAA00>`, `<b>`, …) are skipped —
while the **bold state** (`&l` / `<b>`, +1px per character; legacy colors reset it,
MiniMessage colors don't) is tracked. Characters use the vanilla font advance (most letters
6px, space 4px); accented Latin letters (`é`, `È`, `ç`, …) are measured from their base
letter automatically. The padding is emitted as spaces (4px granularity, so ±2px).

Characters outside the table (resource-pack glyphs like `ꕾ`, CJK, …) use `glyph-widths`
when configured, otherwise `unknown-char-width`. A `glyph-widths` key longer than one
character gives a width to the matching `<glyph:NAME>` tag (Nexo/Oraxen substitute those
client-side, so the plugin can't measure them; unconfigured tags count 0px).

Edge cases: a line wider than the whole chat (`>= 2 × chat-center-px`) is left unpadded so
it doesn't wrap even further; blank/unmarked lines are untouched; ACF-forwarded command
errors (`invalid-syntax`, `player-not-found`, …) can't be centered — the marker is simply
stripped there.

---

## Item turn-in: `DELIVER_ITEM` + `/quests deliver` + `has_items` placeholder

A quest step where the player must **bring back items** and an NPC / script decides when
to collect them. Unlike `TAKE_ITEM` (menu-click triggered, id-matched, takes partial
amounts), `DELIVER_ITEM` is triggered **only by a command** (typically from the console),
matches items on **material + name + lore + custom model data**, and is strictly
**all-or-nothing**: either the player holds the whole required amount and it is removed
from their inventory in one go (completing the objective), or **nothing happens at all**
(no items taken, no progress, no message to the player).

### Task definition

```yaml
tasks:
  livrer_cristaux:
    task: DELIVER_ITEM
    display: "{status} &fRapporte {required} Cristaux d'Edynn &b{current}&f/&b{required}"
    args:
      amount: 5                        # required amount
      item:
        material: PAPER                # vanilla material or custom item id ("nexo:cristal_edynn")
        name: "&bCristal d'Edynn"      # display name (legacy & codes and MiniMessage supported)
        lore:                          # exact lore, line by line, in order
          - "&7Un cristal rare."
          - "&7Objet de quête."
        custom-model-data: 1234
```

- **Every `item` criterion is optional** — only the configured ones are checked (but at
  least one is required, otherwise the task logs a warning and never matches).
- `material`: a vanilla id (`PAPER`, `minecraft:paper`) checks the Bukkit material; a
  namespaced id from another plugin (`nexo:...`, `oraxen:...`, …) is resolved through
  Aurora's item manager like `TAKE_ITEM` does.
- `name` / `lore`: compared **exactly, colors included**, after normalizing both sides to
  the same format — so `&b...`, hex and MiniMessage all work. Lore must have the same
  lines in the same order.
- The shared `filters` block (worlds, regions, …) applies at delivery time.

### The command

```
/quests deliver <player> <pool_id> <quest_id> [objective_id|all] [silent]
```

| Usage | Effect |
|---|---|
| `/quests deliver Steve tutoriel recolte` | Tries every `DELIVER_ITEM` task of the quest, each all-or-nothing |
| `/quests deliver Steve tutoriel recolte livrer_cristaux` | Tries only that task |
| `/quests deliver Steve tutoriel recolte all true` | Silent: no feedback to the sender |

- Permission `aurora.quests.admin.deliver` (default: op — the console always has it).
- Only works while the quest is **active** (started, not completed); with
  `linear-objectives`, a `DELIVER_ITEM` step responds only once it is the current step.
- Feedback goes to the **sender only** (delivered / player doesn't have the items / no
  active DELIVER_ITEM objective). The player sees nothing on failure — and on success only
  the usual task-completion side effects (`on-complete`, rewards, next step…).
- Typical NPC / script wiring: `quests deliver %player_name% tutoriel recolte all true`.
- Folia-safe: the inventory check/removal runs on the player's region thread.

### The placeholder

| Placeholder | Returns |
|---|---|
| `%aurora_quests_has_items_<pool>_<quest>%` | `true` if the quest is active and the player's inventory satisfies **all** its not-yet-completed `DELIVER_ITEM` tasks (completed ones count as satisfied), otherwise `false` |
| `%aurora_quests_has_items:<pool>:<quest>[:<objective>]%` | Explicit colon form; the optional third part targets a single task |

- `false` for every other case: unknown pool/quest, quest locked or completed, quest
  without `DELIVER_ITEM` tasks, missing items.
- Ids may contain underscores (resolved against the real ids, like `is_at`).
- Use it in DeluxeMenus/NPC conditions to only show a "turn in" button when it would
  succeed, then run the `deliver` command from it.

---

## New objective types

Three plugin-integration objectives. Each is only active when the matching plugin is
installed; the objective otherwise loads normally and simply never progresses.

### `PLACE_FURNITURE`  Nexo furniture

Progresses when the player places a **Nexo furniture**. Unlike `BLOCK_PLACE`, it does not
require a backing block, so it also counts display-entity furniture that has no barrier
hitbox. The `type` matched against `types` is the Nexo furniture id.

```yaml
place_furniture:
  task: PLACE_FURNITURE
  args:
    amount: 3
    types: [ "chair", "table" ]   # optional  omit / leave empty for any furniture
    # mode: whitelist              # or blacklist
    # multipliers: { chair: 2.0 }  # optional per-type multiplier
```

> **Plugin:** Nexo. The existing `BLOCK_PLACE` support for furniture (when it has a block)
> is unchanged, so old configs keep working.

### `CREATE_REALM`  LuxRealms

Progresses when the player **creates a realm**. Simple counting objective (no `types`).

```yaml
create_realm:
  task: CREATE_REALM
  args:
    amount: 1
```

> **Plugin:** LuxRealms (listens to `RealmCreationEvent`).

### `OPEN_MENU`  DeluxeMenus

Progresses when the player **opens a DeluxeMenus menu**. The `type` matched against `types`
is the menu id.

```yaml
open_menu:
  task: OPEN_MENU
  args:
    amount: 1
    types: [ "shop_main" ]        # optional  omit / leave empty for any menu
    # mode: whitelist             # or blacklist
```

> **Plugin:** DeluxeMenus. DeluxeMenus exposes no "menu opened" event, so opens are detected
> through Bukkit's `InventoryOpenEvent` and the DeluxeMenus `MenuHolder`.

`PLACE_FURNITURE` and `OPEN_MENU` are typed objectives, so they support `types`, `mode`
(`whitelist`/`blacklist`) and `multipliers`. `CREATE_REALM` is a plain counting objective
(no `types`). All three also honour the shared `filters` block (worlds, etc.).

---

## MythicMobs `questkill` mechanic

Harvest-node style mobs (custom ores, crops — e.g. LittleRoomDev packs) are never actually
killed: every damage modifier is `0.0`, mining is simulated with skill variables, and on
success the mob is **removed** and later respawned. `MythicMobDeathEvent` never fires for
them, so `KILL_MOB` / `KILL_LEVELLED_MOB` objectives can't progress.

AuroraQuests registers a custom MythicMobs mechanic that credits the kill from the skill
itself, without the mob dying:

```
questkill{type=<internal_name>;amount=<n>} @trigger
```

| Option | Aliases | Default | Description |
|---|---|---|---|
| `type` | `t`, `mob`, `m` | the casting mob's own internal name | Mythic mob id credited to the player (`mythicmobs:<type>` in quest configs) |
| `amount` | `a` | `1` | Number of kills to credit |

It fires the exact same `PlayerKillMobEvent` as a real mythic mob death (including the mob
level for `KILL_LEVELLED_MOB`), so objective `types`, `filters` and `multipliers` behave
identically. If no player has a matching active objective the event is a silent no-op —
no commands, no console spam.

Typical usage, in the node's harvest-success metaskill (the one that gives the loot and
removes the node), with the player available as `@trigger` from `~onDamaged`:

```yaml
NODE_edynn_ore_harvest_success:
  Skills:
  # ... existing loot / effects ...
  - questkill @trigger        # credits mythicmobs:edynn_ore (caster's own type)
  # ... existing spawn of the "broken" node + remove ...
```

Because the type defaults to the casting mob, the same line can be copy-pasted into every
node's success skill (ores, crops, …) with no per-node configuration.

## FoxSkills `UNLOCK_FOXSKILLS_SKILL` task type

Progresses when a player unlocks a skill on a FoxSkills class weapon (FoxSkills ≥ 1.1.0,
which fires `PlayerWeaponSkillUnlockEvent`). Requires the FoxSkills plugin — the hook
registers itself automatically when FoxSkills is present.

```yaml
tasks:
  unlock_skill:
    task: UNLOCK_FOXSKILLS_SKILL
    display: "{status} &fDébloque une compétence d'arme &b{current}&f/&b{required}"
    args:
      amount: 1
```

Without `types`, **any** skill on **any** weapon counts. To target specific skills, use
`<weaponId>:<skillId>` entries (the ids from FoxSkills' `weapons` config section), with
the usual `mode: whitelist` (default) or `blacklist`:

```yaml
    args:
      amount: 1
      types:
        - "katana:dash"
```

Unlocks are only counted once per skill per weapon item: FoxSkills' unlock manager
guards against re-unlocking an already-unlocked skill, so the event never fires twice
for the same weapon + skill pair.

## FoxSkills `REACH_FOXSKILLS_WEAPON_LEVEL` task type

Progresses when a FoxSkills class weapon levels up **through XP gain** (FoxSkills ≥ 1.2.0,
which fires `PlayerWeaponLevelUpEvent` from its XP path only). `amount` is the level to
reach; on every level-up the progress jumps to the weapon's new level and **never
decreases** — another weapon leveling below the best one, or a prestige reset, can't
lower it. Admin `/foxskills setlevel`/`addlevel` don't fire the event, so they neither
credit nor reset the quest (admin `addxp` counts: it goes through the XP path).

```yaml
tasks:
  weapon_level:
    task: REACH_FOXSKILLS_WEAPON_LEVEL
    display: "{status} &fMonte une arme de classe au niveau {required} &b{current}&f/&b{required}"
    args:
      amount: 10
```

Without `types`, any class weapon counts (the quest tracks the highest level reached
across all weapons). To restrict it to specific weapons, use their FoxSkills ids:

```yaml
    args:
      amount: 10
      types:
        - "katana"
```
