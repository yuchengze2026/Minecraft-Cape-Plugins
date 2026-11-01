package dev.capebridge.common.pose;

import java.util.List;

/** World: Minecraft X/Y/Z, blocks. Angles: degrees, yaw then pitch then roll.
 * Matrices: column-major 4x4, column vectors. A part's local frame is its parent's frame.
 * Model-space pivots/dimensions use blocks (one model pixel = 1/16 block before scale).
 */
public final class PoseGeometry {
    private PoseGeometry() {}
    public record Vec3(double x, double y, double z) {
        public Vec3 {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite vector");
        }
    }
    public record Matrix4(List<Double> columns) {
        public Matrix4 {
            columns = List.copyOf(columns);
            if (columns.size() != 16 || columns.stream().anyMatch(v -> !Double.isFinite(v))) throw new IllegalArgumentException("Invalid matrix");
        }
    }
    public record Rotation(Observation<Double> yaw, Observation<Double> pitch, Observation<Double> roll) {
        public Rotation { java.util.Objects.requireNonNull(yaw); java.util.Objects.requireNonNull(pitch); java.util.Objects.requireNonNull(roll); }
        public static Rotation notTransmitted() {
            Observation<Double> absent = Observation.missing(Observation.Availability.NOT_TRANSMITTED, "client.model.transform.not_transmitted");
            return new Rotation(absent, absent, absent);
        }
    }
}
