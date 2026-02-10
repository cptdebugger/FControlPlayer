package me.morok.fControlPlayer.command;

import me.morok.fControlPlayer.config.Messages;
import me.morok.fControlPlayer.control.ControlService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ControlCommand implements CommandExecutor, TabCompleter {
    private static final String ARG_STOP = "off";

    private final ControlService controlService;
    private final Messages messages;

    public ControlCommand(ControlService controlService, Messages messages) {
        this.controlService = controlService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "only-player");
            return true;
        }
        Player player = (Player) sender;

        if (args.length != 1) {
            messages.send(player, "usage");
            return true;
        }

        if (ARG_STOP.equalsIgnoreCase(args[0])) {
            if (!controlService.stop(player)) {
                messages.send(player, "not-controlling");
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            messages.send(player, "player-not-found");
            return true;
        }

        if (controlService.isController(player)) {
            Player currentTarget = controlService.getTarget(player);
            if (currentTarget != null && currentTarget.getUniqueId().equals(target.getUniqueId())) {
                controlService.stop(player);
                return true;
            }
            controlService.stop(player);
        }

        if (controlService.start(player, target)) {
            return true;
        }

        messages.send(player, "unable-start");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase();
        List<String> matches = new ArrayList<>();
        if (ARG_STOP.startsWith(prefix)) {
            matches.add(ARG_STOP);
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            if (name.toLowerCase().startsWith(prefix)) {
                matches.add(name);
            }
        }
        return matches;
    }
}
