package me.morok.fControlPlayer.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;

public final class Messages {
    private static final String ROOT = "messages.";

    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null) {
            return;
        }
        String message = resolve(key, placeholders);
        if (message == null) {
            return;
        }
        sender.sendMessage(message);
    }

    public String resolve(String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString(ROOT + key, "");
        if (raw == null) {
            return null;
        }
        String resolved = raw;
        String prefix = plugin.getConfig().getString(ROOT + "prefix", "");
        if (prefix != null) {
            resolved = resolved.replace("{prefix}", prefix);
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue();
                resolved = resolved.replace("{" + entry.getKey() + "}", value == null ? "" : value);
            }
        }
        resolved = ChatColor.translateAlternateColorCodes('&', resolved);
        if (ChatColor.stripColor(resolved).trim().isEmpty()) {
            return null;
        }
        return resolved;
    }
}
