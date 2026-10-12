package net.viraxis.supplycrates.command;

import net.viraxis.supplycrates.manager.SupplyCrateManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class SupplyCrateCommand implements CommandExecutor, TabCompleter {
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        SupplyCrateManager manager = SupplyCrateManager.get();
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "start" -> sender.sendMessage(manager.spawn(true) ? "Supply Crate spawned." : "A Supply Crate is already active or no safe Warzone location was found.");
            case "stop" -> { if (manager.active()) { manager.remove(true); sender.sendMessage("Supply Crate removed."); } else sender.sendMessage("No Supply Crate is active."); }
            case "status" -> {
                if (manager.active()) sender.sendMessage("Supply Crate active with " + manager.hitsRemaining() + " hits remaining at " + format(manager.location()) + ".");
                else if (manager.nextSpawnAt() == null) sender.sendMessage("Supply Crate scheduler is initializing.");
                else sender.sendMessage("Next Supply Crate attempt in " + Math.max(0, Duration.between(Instant.now(), manager.nextSpawnAt()).toMinutes()) + " minutes.");
            }
            default -> sender.sendMessage("Usage: /supplycrate <start|stop|status>");
        }
        return true;
    }
    private static String format(org.bukkit.Location location) { return location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(); }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("start", "stop", "status").stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList() : List.of();
    }
}
