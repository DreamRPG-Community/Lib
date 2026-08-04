package cn.mythicland.lib.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Writes small plugin-owned text files through a same-directory temporary file.
 */
public final class AtomicFileStore {

    private AtomicFileStore() {
    }

    /**
     * Replaces a UTF-8 text file as one filesystem operation where supported.
     *
     * @param target the target file
     * @param content the UTF-8 content
     * @throws IOException if the parent or replacement cannot be written
     * @throws NullPointerException if an argument is null
     */
    public static void writeUtf8(Path target, String content) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null) throw new IOException("Target has no parent directory: " + target);
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(
                parent,
                "." + normalizedTarget.getFileName() + ".",
                ".tmp"
        );
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(
                        temporary,
                        normalizedTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        normalizedTarget,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Saves a YAML configuration through {@link #writeUtf8(Path, String)}.
     *
     * @param target the target YAML file
     * @param configuration the configuration to save
     * @throws IOException if the replacement cannot be written
     * @throws NullPointerException if an argument is null
     */
    public static void writeYaml(Path target, YamlConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        writeUtf8(target, configuration.saveToString());
    }
}
