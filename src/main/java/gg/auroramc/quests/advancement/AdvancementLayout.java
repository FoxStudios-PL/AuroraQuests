package gg.auroramc.quests.advancement;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny tidy-tree layout used to place quest advancements on the tab grid.
 * <p>
 * Pure and allocation-light so it can run both once per reload (static tabs) and per
 * player sync (tabs containing rolled quests, whose visible subset changes per roll).
 * Rules: a node's column is its depth (root = 0); leaves take the next free row in
 * declaration order; a parent is centered on its children. Manual {@code position}
 * overrides win over the computed coordinates (applied by the caller).
 */
final class AdvancementLayout {
    private AdvancementLayout() {
    }

    record Entry(String key, @Nullable String parentKey) {
    }

    /**
     * Computes grid coordinates for every entry. Parents must reference keys present in
     * the list (the caller already remapped unknown/hidden parents to the root); entries
     * whose parent is {@code null} attach to the synthetic root {@code ""} placed at
     * column 0 and vertically centered on the whole tree.
     *
     * @return map of key (and {@code ""} for the root) to {x, y} grid floats
     */
    static Map<String, float[]> layout(List<Entry> entries) {
        Map<String, List<Entry>> children = new LinkedHashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Map<String, Entry> byKey = new HashMap<>();
        for (Entry e : entries) {
            byKey.put(e.key(), e);
        }

        for (Entry e : entries) {
            String parent = e.parentKey() != null && byKey.containsKey(e.parentKey()) ? e.parentKey() : "";
            children.computeIfAbsent(parent, k -> new ArrayList<>()).add(e);
        }

        Map<String, float[]> out = new HashMap<>();
        float[] nextRow = {0f};

        for (Entry root : children.getOrDefault("", List.of())) {
            place(root, 1, children, depth, out, nextRow);
        }

        // The synthetic root sits in column 0, centered on its direct children (or at
        // row 0 for an empty tab so the tab still renders its root icon).
        var direct = children.getOrDefault("", List.of());
        float rootY = 0f;
        if (!direct.isEmpty()) {
            rootY = (out.get(direct.getFirst().key())[1] + out.get(direct.getLast().key())[1]) / 2f;
        }
        out.put("", new float[]{0f, rootY});
        return out;
    }

    private static void place(Entry node, int col, Map<String, List<Entry>> children,
                              Map<String, Integer> depth, Map<String, float[]> out, float[] nextRow) {
        // Cycle guard: a node reached twice keeps its first placement.
        if (out.containsKey(node.key())) return;
        depth.put(node.key(), col);

        var kids = children.getOrDefault(node.key(), List.of());
        if (kids.isEmpty()) {
            out.put(node.key(), new float[]{col, nextRow[0]++});
            return;
        }

        // Reserve the node's slot marker first so cycles cannot recurse through it.
        out.put(node.key(), new float[]{col, 0f});
        for (Entry kid : kids) {
            place(kid, col + 1, children, depth, out, nextRow);
        }
        float first = out.get(kids.getFirst().key())[1];
        float last = out.get(kids.getLast().key())[1];
        out.get(node.key())[1] = (first + last) / 2f;
    }
}
