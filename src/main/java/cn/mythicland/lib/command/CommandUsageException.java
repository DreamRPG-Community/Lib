package cn.mythicland.lib.command;

import java.io.Serial;

public final class CommandUsageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String usage;

    public CommandUsageException(String usage) {
        super("Invalid command usage: " + usage);
        this.usage = usage;
    }

    public String usage() {
        return usage;
    }
}
