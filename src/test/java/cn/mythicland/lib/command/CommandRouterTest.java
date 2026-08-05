package cn.mythicland.lib.command;

import cn.mythicland.lib.api.LibApi;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRouterTest {

    @Test
    void defaultSubcommandHandlesBareRootCommand() {
        AtomicBoolean executed = new AtomicBoolean();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "edit");
        router.registerDefault(new TestSubcommand(executed));

        assertTrue(router.onCommand(sender(), null, "edit", new String[0]));
        assertTrue(executed.get());
    }

    @Test
    void commonMessageColorsAreAvailable() {
        assertTrue(VanillaCommandMessages.red("message").startsWith(ChatColor.RED.toString()));
        assertTrue(VanillaCommandMessages.yellow("message").startsWith(ChatColor.YELLOW.toString()));
        assertTrue(VanillaCommandMessages.green("message").startsWith(ChatColor.GREEN.toString()));
    }

    @Test
    void explicitHelpListsCommandsWithoutRootPlaceholder() {
        List<String> messages = new ArrayList<>();
        CommandRouter router = new CommandRouter(
                Logger.getLogger("CommandRouterTest"),
                "worldmanager"
        );
        router.register(new TestSubcommand(new AtomicBoolean()));

        assertTrue(router.onCommand(sender(messages), null, "worldmanager", new String[]{"help"}));
        assertEquals(1, messages.size());
        assertEquals(VanillaCommandMessages.usage("/edit"), messages.getFirst());
        assertTrue(messages.stream().noneMatch(message -> message.contains("<子命令>")));
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
        return sender(new ArrayList<>());
    }

    private CommandSender sender(List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage")) {
                        messages.add((String) arguments[0]);
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) return false;
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

    private record TestSubcommand(AtomicBoolean executed) implements Subcommand {

        @Override
        public String name() {
            return "edit";
        }

        @Override
        public String usage() {
            return "/edit";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            executed.set(true);
        }
    }

}
