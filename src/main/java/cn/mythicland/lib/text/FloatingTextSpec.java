package cn.mythicland.lib.text;

import java.util.List;
import java.util.Objects;

/**
 * Immutable specification for client-side floating text.
 *
 * @param lines        lines rendered from top to bottom
 * @param lineSpacing  vertical distance between lines in blocks
 * @param viewDistance maximum distance at which a player receives the packets
 */
public record FloatingTextSpec(
        List<String> lines,
        double lineSpacing,
        double viewDistance
) {

    /**
     * Validates and defensively copies the specification.
     *
     * @throws NullPointerException     if {@code lines} or a line is null
     * @throws IllegalArgumentException if the line count, line spacing, or view distance is invalid
     */
    public FloatingTextSpec {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty() || lines.size() > 16) {
            throw new IllegalArgumentException("Floating text must contain between one and sixteen lines");
        }
        lines = lines.stream()
                .map(line -> Objects.requireNonNull(line, "line"))
                .toList();
        if (!Double.isFinite(lineSpacing) || lineSpacing <= 0.0D) {
            throw new IllegalArgumentException("lineSpacing must be finite and positive");
        }
        if (!Double.isFinite(viewDistance) || viewDistance <= 0.0D) {
            throw new IllegalArgumentException("viewDistance must be finite and positive");
        }
    }
}
