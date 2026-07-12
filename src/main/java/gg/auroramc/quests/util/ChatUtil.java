package gg.auroramc.quests.util;

import gg.auroramc.aurora.api.message.Chat;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.message.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Drop-in replacement for {@link Chat#sendMessage(CommandSender, String, Placeholder[])}
 * that honors the {@code <center>} marker (see {@link ChatCenterer}) in messages_xx.yml
 * entries. Unmarked messages go through Aurora's Chat untouched; marked ones get their
 * placeholders (PlaceholderAPI included for player senders, mirroring Chat's own player
 * path) resolved first so the centering is computed on the final text.
 */
public final class ChatUtil {
    private ChatUtil() {
    }

    public static void sendMessage(CommandSender sender, String message, Placeholder<?>... placeholders) {
        if (message == null || !message.contains(ChatCenterer.MARKER)) {
            Chat.sendMessage(sender, message, placeholders);
            return;
        }

        var resolved = sender instanceof Player player
                ? Text.fillPlaceholders(player, message, placeholders)
                : Text.fillPlaceholders(message, placeholders);
        Chat.sendMessage(sender, ChatCenterer.centerLines(resolved));
    }
}
