package cn.mythicland.lib.command;

import cn.mythicland.lib.bootstrap.annotation.CommandCompleter;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    private final RouteNode commandRoot = new RouteNode();
    private final List<CommandNode> registeredCommands = new ArrayList<>();
    private CommandNode defaultCommand;

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

    private static String normalizeAnnotatedPath(String value) {
        String normalized = Objects.requireNonNull(value, "command path").trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return "";
        String[] parts = normalized.split("\\s+");
        for (String part : parts) {
            if (part.equals("help")) {
                throw new IllegalArgumentException("The help command is reserved");
            }
        }
        return String.join(" ", parts);
    }

    private static List<Method> annotatedMethods(
            Class<?> componentType,
            Class<? extends java.lang.annotation.Annotation> annotationType
    ) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type = componentType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotationType)) methods.add(method);
            }
        }
        methods.sort(Comparator
                .comparingInt((Method method) -> methodOrder(method, annotationType))
                .thenComparing(Method::getName)
                .thenComparing(CommandRouter::methodSignature));
        return methods;
    }

    private static int methodOrder(
            Method method,
            Class<? extends java.lang.annotation.Annotation> annotationType
    ) {
        if (annotationType == CommandHandler.class) return method.getAnnotation(CommandHandler.class).order();
        return 0;
    }

    private static String methodSignature(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private static void validateHandler(Method method) {
        if (Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != void.class
                || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommandContext.class})) {
            throw new IllegalStateException(
                    "@CommandHandler must be an instance void method(CommandContext): "
                            + method
            );
        }
        if (!method.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @CommandHandler method: " + method);
        }
    }

    private static void validateCompleter(Method method) {
        if (Modifier.isStatic(method.getModifiers())
                || !List.class.isAssignableFrom(method.getReturnType())
                || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommandContext.class})) {
            throw new IllegalStateException(
                    "@CommandCompleter must be an instance List<String> method(CommandContext): "
                            + method
            );
        }
        if (!method.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @CommandCompleter method: " + method);
        }
    }

    private static PermissionBinding resolvePermission(
            Object component,
            CommandHandler annotation,
            String defaultPermission
    ) {
        String permission = normalizePermission(annotation.permission());
        if (!annotation.permissionMethod().isBlank()) {
            if (!permission.isBlank()) {
                throw new IllegalStateException(
                        "@CommandHandler cannot declare both permission and permissionMethod"
                );
            }
            Method method = findMethod(component.getClass(), annotation.permissionMethod().trim());
            if (method == null
                    || Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || method.getReturnType() != String.class
                    || !method.trySetAccessible()) {
                throw new IllegalStateException(
                        "permissionMethod must be an instance String method with no arguments: "
                                + annotation.permissionMethod()
                );
            }
            return new PermissionBinding("", method);
        }
        if (permission.isBlank() && annotation.inheritPermission()) {
            return new PermissionBinding(defaultPermission, null);
        }
        return new PermissionBinding(permission, null);
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)) return method;
            }
        }
        return null;
    }

    private static String normalizePermission(String value) {
        return Objects.requireNonNull(value, "permission").trim();
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException exception) return exception;
        if (throwable instanceof Error error) throw error;
        return new IllegalStateException("Annotated command method failed", throwable);
    }

    /**
     * Registers all {@link CommandHandler} methods on one command component.
     *
     * <p>Handlers use {@link CommandContext} and are adapted to Bukkit's executor and tab
     * completer interfaces by this router. The component's {@link CommandComponent#permission()}
     * is inherited by handlers unless a handler overrides it.</p>
     *
     * @param component annotated command component
     * @throws IllegalStateException if the component contains an invalid or incomplete binding
     */
    public void registerAnnotated(Object component) {
        Objects.requireNonNull(component, "component");
        CommandComponent componentAnnotation = component.getClass().getAnnotation(CommandComponent.class);
        if (componentAnnotation == null) {
            throw new IllegalStateException(
                    "Annotated command component is missing @CommandComponent: "
                            + component.getClass().getName()
            );
        }

        Map<String, Method> completers = new LinkedHashMap<>();
        for (Method method : annotatedMethods(component.getClass(), CommandCompleter.class)) {
            CommandCompleter annotation = method.getAnnotation(CommandCompleter.class);
            validateCompleter(method);
            String name = normalizeAnnotatedPath(annotation.value());
            if (completers.putIfAbsent(name, method) != null) {
                throw new IllegalStateException("Duplicate command completer: " + name);
            }
        }

        List<Method> handlers = annotatedMethods(component.getClass(), CommandHandler.class);
        if (handlers.isEmpty()) {
            throw new IllegalStateException(
                    "Command component declares no @CommandHandler methods: "
                            + component.getClass().getName()
            );
        }

        String defaultPermission = normalizePermission(componentAnnotation.permission());
        Set<String> handledNames = new HashSet<>();
        for (Method method : handlers) {
            CommandHandler annotation = method.getAnnotation(CommandHandler.class);
            validateHandler(method);
            String name = normalizeAnnotatedPath(annotation.value());
            if (!handledNames.add(name)) {
                throw new IllegalStateException("Duplicate annotated command handler: " + name);
            }
            Method completer = completers.remove(name);
            AnnotatedCommandNode subcommand = new AnnotatedCommandNode(
                    component,
                    method,
                    completer,
                    annotation,
                    name,
                    resolveUsage(annotation.usage(), name),
                    resolvePermission(component, annotation, defaultPermission),
                    rootCommand
            );
            if (name.isBlank()) registerDefault(subcommand, annotation);
            else register(subcommand);
        }
        if (!completers.isEmpty()) {
            throw new IllegalStateException(
                    "Command completer has no matching handler: " + completers.keySet()
            );
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
            if (defaultCommand == null) {
                sendShortHelp(sender);
            } else {
                executeCommand(sender, defaultCommand, List.of(), label, arguments);
            }
            return true;
        }

        if (arguments[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        RouteMatch match = findRoute(arguments);
        if (match == null) {
            if (defaultCommand != null && unknownRootPath(arguments[0])) {
                executeCommand(sender, defaultCommand, List.copyOf(Arrays.asList(arguments)), label, arguments);
                return true;
            }
            sender.sendMessage(VanillaCommandMessages.red("未知子命令: " + arguments[0]));
            sendShortHelp(sender);
            return true;
        }

        List<String> subcommandArguments = Arrays.asList(arguments).subList(match.consumedTokens(), arguments.length);
        executeCommand(sender, match.command(), List.copyOf(subcommandArguments), label, arguments);
        return true;
    }

    private void executeCommand(
            CommandSender sender,
            CommandNode subcommand,
            List<String> arguments,
            String label,
            String[] rawArguments
    ) {
        if (!subcommand.permission().isBlank() && !sender.hasPermission(subcommand.permission())) {
            sender.sendMessage(VanillaCommandMessages.red("你没有执行此命令的权限。"));
            return;
        }

        try {
            subcommand.execute(sender, arguments, label);
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
        if (!arguments[0].equalsIgnoreCase("help")
                && defaultCommand != null
                && unknownRootPath(arguments[0])) {
            return completeCommand(sender, defaultCommand, List.copyOf(Arrays.asList(arguments)), alias);
        }

        RouteNode node = commandRoot;
        int consumedTokens = 0;
        while (consumedTokens < arguments.length) {
            String token = arguments[consumedTokens].toLowerCase(Locale.ROOT);
            RouteNode child = node.children.get(token);
            if (child == null) {
                RouteNode currentNode = node;
                Set<String> names = currentNode.children.keySet().stream()
                        .filter(name -> name.startsWith(token))
                        .filter(name -> hasAccessibleDescendant(sender, currentNode.children.get(name)))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (!node.children.isEmpty() && (!names.isEmpty() || consumedTokens == arguments.length - 1)) {
                    if (consumedTokens == 0) names.add("help");
                    return names.stream().filter(name -> name.startsWith(token) || name.equals("help")).toList();
                }
                break;
            }
            node = child;
            consumedTokens++;
        }

        if (consumedTokens < arguments.length && node.command != null && hasPermission(sender, node.command)) {
            List<String> subcommandArguments = Arrays.asList(arguments).subList(consumedTokens, arguments.length);
            return completeCommand(sender, node.command, List.copyOf(subcommandArguments), alias);
        }

        if (consumedTokens == arguments.length && !node.children.isEmpty()) {
            return node.children.entrySet().stream()
                    .filter(entry -> hasAccessibleDescendant(sender, entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        if (node.command != null && hasPermission(sender, node.command)) {
            return completeCommand(sender, node.command, List.of(), alias);
        }
        return List.of();
    }

    private List<String> completeCommand(
            CommandSender sender,
            CommandNode subcommand,
            List<String> arguments,
            String label
    ) {
        try {
            List<String> completions = subcommand.tabComplete(sender, arguments, label);
            return completions == null ? List.of() : List.copyOf(completions);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Command tab completion failed for /" + rootCommand, exception);
            return List.of();
        }
    }

    private void sendShortHelp(CommandSender sender) {
        sender.sendMessage(VanillaCommandMessages.usage("/" + rootCommand + " <子命令>"));
        sender.sendMessage(VanillaCommandMessages.red(
                "使用 /" + rootCommand + " help 查看所有命令。"
        ));
    }

    private void sendHelp(CommandSender sender) {
        for (CommandNode subcommand : uniqueCommands()) {
            if (hasPermission(sender, subcommand)) {
                sender.sendMessage(VanillaCommandMessages.usage(subcommand.usage()));
            }
        }
    }

    private Collection<CommandNode> uniqueCommands() {
        Set<CommandNode> uniqueCommands = new LinkedHashSet<>();
        if (defaultCommand != null) uniqueCommands.add(defaultCommand);
        uniqueCommands.addAll(registeredCommands);
        return uniqueCommands;
    }

    private boolean hasPermission(CommandSender sender, CommandNode subcommand) {
        return subcommand.permission().isBlank() || sender.hasPermission(subcommand.permission());
    }

    private void register(CommandNode command) {
        Objects.requireNonNull(command, "command");
        registerPath(command.name(), command);
        for (String alias : command.aliases()) {
            String normalizedAlias = normalizeAnnotatedPath(alias);
            if (normalizedAlias.isBlank()) {
                throw new IllegalArgumentException("Command alias cannot be blank");
            }
            String[] commandParts = command.name().split(" ");
            String aliasPath = normalizedAlias.indexOf(' ') >= 0 || commandParts.length == 1
                    ? normalizedAlias
                    : command.name().substring(0, command.name().lastIndexOf(' ') + 1) + normalizedAlias;
            registerPath(aliasPath, command);
        }
        registeredCommands.add(command);
    }

    private void registerDefault(CommandNode command, CommandHandler annotation) {
        Objects.requireNonNull(command, "command");
        if (annotation.aliases().length > 0) {
            throw new IllegalStateException("The root command cannot declare aliases");
        }
        if (defaultCommand != null) {
            throw new IllegalStateException("A default command is already registered");
        }
        defaultCommand = command;
    }

    private void registerPath(String name, CommandNode command) {
        String normalizedName = normalizeAnnotatedPath(name);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Command path cannot be blank");
        }
        String[] parts = normalizedName.split(" ");
        RouteNode node = commandRoot;
        for (String part : parts) {
            node = node.children.computeIfAbsent(part, ignored -> new RouteNode());
        }
        if (node.command != null && node.command != command) {
            throw new IllegalArgumentException("Duplicate command path: " + name);
        }
        node.command = command;
    }

    private RouteMatch findRoute(String[] arguments) {
        RouteNode node = commandRoot;
        CommandNode command = null;
        int consumedTokens = 0;
        for (String argument : arguments) {
            RouteNode child = node.children.get(argument.toLowerCase(Locale.ROOT));
            if (child == null) break;
            node = child;
            consumedTokens++;
            if (node.command != null) command = node.command;
        }
        return command == null ? null : new RouteMatch(command, consumedTokens);
    }

    private boolean hasAccessibleDescendant(CommandSender sender, RouteNode node) {
        if (node.command != null && hasPermission(sender, node.command)) return true;
        return node.children.values().stream().anyMatch(child -> hasAccessibleDescendant(sender, child));
    }

    private boolean unknownRootPath(String argument) {
        return !commandRoot.children.containsKey(argument.toLowerCase(Locale.ROOT));
    }

    private String resolveUsage(String usage, String name) {
        String declared = Objects.requireNonNull(usage, "usage").trim();
        if (!declared.isBlank()) return declared;
        return name.isBlank() ? "/" + rootCommand : "/" + rootCommand + " " + name;
    }

    private interface CommandNode {

        String name();

        String usage();

        Collection<String> aliases();

        String permission();

        void execute(CommandSender sender, List<String> arguments, String label);

        List<String> tabComplete(CommandSender sender, List<String> arguments, String label);
    }

    private record PermissionBinding(String value, Method dynamicMethod) {
    }

    private static final class RouteNode {

        private final Map<String, RouteNode> children = new LinkedHashMap<>();
        private CommandNode command;
    }

    private record RouteMatch(CommandNode command, int consumedTokens) {
    }

    private static final class AnnotatedCommandNode implements CommandNode {

        private final Object component;
        private final Method handler;
        private final Method completer;
        private final CommandHandler metadata;
        private final String name;
        private final String usage;
        private final String permission;
        private final String rootCommand;
        private final Method permissionMethod;

        private AnnotatedCommandNode(
                Object component,
                Method handler,
                Method completer,
                CommandHandler metadata,
                String name,
                String usage,
                PermissionBinding permissionBinding,
                String rootCommand
        ) {
            this.component = component;
            this.handler = handler;
            this.completer = completer;
            this.metadata = metadata;
            this.name = name;
            this.usage = usage;
            this.permission = permissionBinding.value();
            this.rootCommand = rootCommand;
            this.permissionMethod = permissionBinding.dynamicMethod();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String usage() {
            return usage;
        }

        @Override
        public Collection<String> aliases() {
            return List.of(metadata.aliases());
        }

        @Override
        public String permission() {
            if (permissionMethod == null) return permission;
            try {
                Object value = permissionMethod.invoke(component);
                if (!(value instanceof String string)) {
                    throw new IllegalStateException("Dynamic command permission returned a non-string");
                }
                return string.trim();
            } catch (InvocationTargetException exception) {
                throw propagate(exception.getCause());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot resolve command permission", exception);
            }
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments, String label) {
            try {
                handler.invoke(component, new CommandContext(sender, rootCommand, label, usage, arguments));
            } catch (InvocationTargetException exception) {
                throw propagate(exception.getCause());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot invoke @CommandHandler: " + handler, exception);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<String> tabComplete(CommandSender sender, List<String> arguments, String label) {
            if (completer == null) return List.of();
            try {
                Object value = completer.invoke(
                        component,
                        new CommandContext(sender, rootCommand, label, usage, arguments)
                );
                if (value == null) return List.of();
                return List.copyOf((List<String>) value);
            } catch (InvocationTargetException exception) {
                throw propagate(exception.getCause());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot invoke @CommandCompleter: " + completer, exception);
            }
        }
    }
}
