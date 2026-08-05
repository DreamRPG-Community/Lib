package cn.mythicland.lib.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared executor and tab completer for simple Bukkit subcommand trees.
 *
 * <p>All command execution is expected to happen on Bukkit's primary thread. The router owns
 * permission checks, usage handling, error logging, and command help formatting.</p>
 */
public final class CommandRouter implements CommandExecutor, TabCompleter {

    private final Logger logger;
    private final String rootCommand;
    private final Map<String, Subcommand> commands = new LinkedHashMap<>();
    private Subcommand defaultCommand;

    /**
     * Creates a router for one root command.
     *
     * @param owner       the plugin whose logger handles unexpected command failures
     * @param rootCommand the root command used in usage messages
     * @throws NullPointerException if an argument is null
     */
    public CommandRouter(JavaPlugin owner, String rootCommand) {
        this(Objects.requireNonNull(owner, "owner").getLogger(), rootCommand);
    }

    CommandRouter(Logger logger, String rootCommand) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.rootCommand = Objects.requireNonNull(rootCommand, "rootCommand");
    }

    /**
     * Registers a named subcommand and all of its aliases.
     *
     * @param subcommand the command to register
     * @throws NullPointerException     if {@code subcommand} is null
     * @throws IllegalArgumentException if a name is blank or already registered
     */
    public void register(Subcommand subcommand) {
        Objects.requireNonNull(subcommand, "subcommand");
        registerName(subcommand.name(), subcommand);
        for (String alias : subcommand.aliases()) {
            registerName(alias, subcommand);
        }
    }

    /**
     * Registers the action executed when the root command has no arguments.
     *
     * <p>This supports commands whose primary action is the root command itself,
     * while keeping permission checks, usage errors, and exception handling in
     * the shared router.</p>
     *
     * @param subcommand the default action; its permission and usage are still applied
     * @throws NullPointerException  if {@code subcommand} is null
     * @throws IllegalStateException if a default action has already been registered
     */
    public void registerDefault(Subcommand subcommand) {
        Objects.requireNonNull(subcommand, "subcommand");
        if (defaultCommand != null) {
            throw new IllegalStateException("A default command is already registered");
        }
        defaultCommand = subcommand;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length == 0) {
            if (defaultCommand == null) {
                sendShortHelp(sender);
            } else {
                executeSubcommand(sender, defaultCommand, List.of(), label, arguments);
            }
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

        List<String> subcommandArguments = Arrays.asList(arguments).subList(1, arguments.length);
        executeSubcommand(sender, subcommand, List.copyOf(subcommandArguments), label, arguments);
        return true;
    }

    private void executeSubcommand(
            CommandSender sender,
            Subcommand subcommand,
            List<String> arguments,
            String label,
            String[] rawArguments
    ) {
        if (!subcommand.permission().isBlank() && !sender.hasPermission(subcommand.permission())) {
            sender.sendMessage(VanillaCommandMessages.red("你没有执行此命令的权限。"));
            return;
        }

        try {
            subcommand.execute(sender, arguments);
        } catch (CommandUsageException exception) {
            sender.sendMessage(VanillaCommandMessages.usage(exception.usage()));
        } catch (Exception exception) {
            logger.log(
                    Level.SEVERE,
                    "Command execution failed for /" + label + " " + String.join(" ", rawArguments),
                    exception
            );
            sender.sendMessage(VanillaCommandMessages.red("命令执行失败, 请查看服务端日志。"));
        }
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
        for (Subcommand subcommand : uniqueCommands()) {
            if (hasPermission(sender, subcommand)) {
                sender.sendMessage(VanillaCommandMessages.usage(subcommand.usage()));
            }
        }
    }

    private Collection<Subcommand> uniqueCommands() {
        Set<Subcommand> uniqueCommands = new LinkedHashSet<>();
        if (defaultCommand != null) uniqueCommands.add(defaultCommand);
        uniqueCommands.addAll(commands.values());
        return uniqueCommands;
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
