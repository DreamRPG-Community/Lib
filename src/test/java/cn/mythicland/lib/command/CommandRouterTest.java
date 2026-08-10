package cn.mythicland.lib.command;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.CommandCompleter;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRouterTest {

    @Test
    void annotatedRootHandlerHandlesBareRootCommand() {
        AtomicBoolean executed = new AtomicBoolean();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "edit");
        router.registerAnnotated(new RootCommand(executed));

        assertTrue(router.onCommand(sender(), null, "edit", new String[0]));
        assertTrue(executed.get());
    }

    @Test
    void annotatedRootHandlerReceivesPositionalArguments() {
        AtomicReference<List<String>> received = new AtomicReference<>();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "gm");
        router.registerAnnotated(new RootArgumentCommand(received));

        assertTrue(router.onCommand(sender(), null, "gm", new String[]{"0"}));
        assertEquals(List.of("0"), received.get());
        assertEquals(
                List.of("0", "1", "2", "3"),
                router.onTabComplete(sender(), null, "gm", new String[]{""})
        );
    }

    @Test
    void annotatedPermissionAndCompleterAreApplied() {
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "example");
        router.registerAnnotated(new AnnotatedCommand());

        assertEquals(
                List.of("one"),
                router.onTabComplete(sender(), null, "example", new String[]{"reload", ""})
        );
    }

    @Test
    void nestedAnnotatedPathsRouteArgumentsAndCompletion() {
        List<String> received = new ArrayList<>();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "nested");
        router.registerAnnotated(new NestedCommand(received));

        assertTrue(router.onCommand(
                sender(),
                null,
                "nested",
                new String[]{"cmd", "add", "player", "say", "hello"}
        ));
        assertEquals(List.of("player", "say", "hello"), received);
        assertEquals(
                List.of("player", "console"),
                router.onTabComplete(sender(), null, "nested", new String[]{"cmd", "add", ""})
        );
    }

    @Test
    void dynamicPermissionIsResolvedAtInvocationTime() {
        DynamicPermissionCommand command = new DynamicPermissionCommand();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "dynamic");
        router.registerAnnotated(command);

        assertTrue(router.onCommand(
                sender(new ArrayList<>(), permission -> permission.equals("first")),
                null,
                "dynamic",
                new String[0]
        ));
        command.permission = "second";
        List<String> messages = new ArrayList<>();
        assertTrue(router.onCommand(
                sender(messages, permission -> permission.equals("first")),
                null,
                "dynamic",
                new String[0]
        ));
        assertTrue(messages.stream().anyMatch(message -> message.contains("没有执行此命令的权限")));
    }

    @Test
    void commonMessageColorsAreAvailable() {
        assertTrue(VanillaCommandMessages.red("message").startsWith(ChatColor.RED.toString()));
        assertTrue(VanillaCommandMessages.yellow("message").startsWith(ChatColor.YELLOW.toString()));
        assertTrue(VanillaCommandMessages.green("message").startsWith(ChatColor.GREEN.toString()));
    }

    @Test
    void multilineUsagePrefixesEveryCommandLine() {
        String usage = VanillaCommandMessages.usage("/worldregion landmark set <id> <显示名>\n"
                + "/worldregion landmark delete <id>\n"
                + "/worldregion landmark list");

        assertEquals(
                ChatColor.RED + "用法: /worldregion landmark set <id> <显示名>\n"
                        + ChatColor.RED + "用法: /worldregion landmark delete <id>\n"
                        + ChatColor.RED + "用法: /worldregion landmark list",
                usage
        );
    }

    @Test
    void rootCauseMessageUsesTheDeepestCause() {
        Throwable error = new IllegalStateException(
                "outer",
                new IllegalArgumentException("inner")
        );

        assertEquals("inner", LibApi.rootCauseMessage(error));
    }

    private CommandSender sender() {
        return sender(new ArrayList<>(), permission -> true);
    }

    private CommandSender sender(List<String> messages, Predicate<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage")) {
                        messages.add((String) arguments[0]);
                        return null;
                    }
                    if (method.getName().equals("hasPermission")) {
                        return permissions.test((String) arguments[0]);
                    }
                    if (method.getReturnType() == boolean.class) return true;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0.0F;
                    if (method.getReturnType() == double.class) return 0.0D;
                    if (method.getReturnType() == short.class) return (short) 0;
                    if (method.getReturnType() == byte.class) return (byte) 0;
                    if (method.getReturnType() == char.class) return (char) 0;
                    return null;
                }
        );
    }

    @CommandComponent("edit")
        private record RootCommand(AtomicBoolean executed) {

        @CommandHandler
            private void execute(CommandContext context) {
                context.requireArguments(0);
                executed.set(true);
            }
        }

    @CommandComponent("gm")
        private record RootArgumentCommand(AtomicReference<List<String>> received) {

        @CommandHandler
            private void execute(CommandContext context) {
                received.set(context.arguments());
            }

            @CommandCompleter
            private List<String> complete(CommandContext context) {
                return List.of("0", "1", "2", "3");
            }
        }

    @CommandComponent("example")
    private static final class AnnotatedCommand {

        @CommandHandler(value = "reload", permission = "example.reload")
        private void reload(CommandContext context) {
            context.requireArguments(1);
        }

        @CommandCompleter("reload")
        private List<String> complete(CommandContext context) {
            return List.of("one");
        }
    }

    @CommandComponent("nested")
        private record NestedCommand(List<String> received) {

        @CommandHandler(value = "cmd add", permission = "nested.add")
            private void add(CommandContext context) {
                context.requireAtLeast(3);
                received.addAll(context.arguments());
            }

            @CommandCompleter("cmd add")
            private List<String> complete(CommandContext context) {
                return List.of("player", "console");
            }
        }

    @CommandComponent("dynamic")
    private static final class DynamicPermissionCommand {

        private String permission = "first";

        @CommandHandler(permissionMethod = "permission")
        private void execute(CommandContext context) {
            context.requireArguments(0);
        }

        private String permission() {
            return permission;
        }
    }
}
