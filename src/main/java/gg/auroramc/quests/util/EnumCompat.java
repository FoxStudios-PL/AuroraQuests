package gg.auroramc.quests.util;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Resolves enum constants of third party APIs by name instead of referencing them directly.
 * <p>
 * A direct reference such as {@code Transaction.Result.NOT_ALL_ITEMS_ADDED} is compiled into a
 * field access, so it throws {@link NoSuchFieldError} at class initialization time as soon as the
 * plugin we hook into removes or renames that constant. Looking the constant up by name lets a
 * hook keep working across API versions and simply ignore constants that are not present.
 */
public final class EnumCompat {
    private EnumCompat() {
    }

    /**
     * Builds a set from the given constant names, skipping the ones the running API does not have.
     */
    public static <E extends Enum<E>> Set<E> setOf(Class<E> type, String... names) {
        var set = EnumSet.noneOf(type);

        for (var name : names) {
            var constant = resolve(type, name);
            if (constant != null) {
                set.add(constant);
            }
        }

        return Collections.unmodifiableSet(set);
    }

    /**
     * Returns the constant with the given name, or null if the running API does not have it.
     */
    public static <E extends Enum<E>> E resolve(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
