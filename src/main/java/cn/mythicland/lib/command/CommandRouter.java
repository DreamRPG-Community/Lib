package cn.mythicland.lib.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public final class CommandRouter implements CommandExecutor, TabCompleter {

    private final JavaPlugin owner;
    private final String rootCommand;
    private final Map<String, Subcommand> commands = new LinkedHashMap<>();

    public CommandRouter(JavaPlugin owner, String rootCommand) {
        this.owner = owner;
        this.rootCommand = rootCommand;
    }

    public void register(Subcommand subcommand) {
        registerName(subcommand.name(), subcommand);
        for (String alias : subcommand.aliases()) {
            registerName(alias, subcommand);
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length == 0) {
            sendShortHelp(sender);
            return true;
        }

        if (arguments[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        Subcommand subcommand = commands.get(arguments[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            sender.sendMessage(VanillaCommandMessages.red("未知子命令: " + arguments[0]));
            sendShortHelp(sender);
            return true;
        }

        if (!subcommand.permission().isBlank() && !sender.hasPermission(subcommand.permission())) {
            sender.sendMessage(VanillaCommandMessages.red("你没有执行此命令的权限。"));
            return true;
        }

        List<String> subcommandArguments = Arrays.asList(arguments).subList(1, arguments.length);
        try {
            subcommand.execute(sender, List.copyOf(subcommandArguments));
        } catch (CommandUsageException exception) {
            sender.sendMessage(VanillaCommandMessages.usage(exception.usage()));
        } catch (Exception exception) {
            owner.getLogger().log(
                    Level.SEVERE,
                    "Command execution failed for /" + label + " " + String.join(" ", arguments),
                    exception
            );
            sender.sendMessage(VanillaCommandMessages.red("命令执行失败，请查看服务端日志。"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (arguments.length == 0) return List.of();

        if (arguments.length == 1) {
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            Set<String> names = new LinkedHashSet<>();
            names.add("help");
            names.addAll(commands.keySet());
            return names.stream()
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        Subcommand subcommand = commands.get(arguments[0].toLowerCase(Locale.ROOT));
        if (subcommand == null || !hasPermission(sender, subcommand)) return List.of();

        List<String> subcommandArguments = Arrays.asList(arguments).subList(1, arguments.length);
        return subcommand.tabComplete(sender, List.copyOf(subcommandArguments));
    }

    private void sendShortHelp(CommandSender sender) {
        sender.sendMessage(VanillaCommandMessages.usage("/" + rootCommand + " <子命令>"));
        sender.sendMessage(VanillaCommandMessages.red(
                "使用 /" + rootCommand + " help 查看所有命令。"
        ));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(VanillaCommandMessages.usage("/" + rootCommand + " <子命令>"));
        for (Subcommand subcommand : uniqueCommands()) {
            if (hasPermission(sender, subcommand)) {
                sender.sendMessage(VanillaCommandMessages.usage(subcommand.usage()));
            }
        }
    }

    private Collection<Subcommand> uniqueCommands() {
        return new LinkedHashSet<>(commands.values());
    }

    private boolean hasPermission(CommandSender sender, Subcommand subcommand) {
        return subcommand.permission().isBlank() || sender.hasPermission(subcommand.permission());
    }

    private void registerName(String name, Subcommand subcommand) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Command name cannot be blank");
        }
        if (commands.putIfAbsent(normalizedName, subcommand) != null) {
            throw new IllegalArgumentException("Duplicate command name: " + name);
        }
    }
}
