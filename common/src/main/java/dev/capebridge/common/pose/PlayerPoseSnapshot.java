package dev.capebridge.common.pose;

import dev.capebridge.common.pose.PoseGeometry.*;
import java.util.*;

/** One atomic SERVER tick sample. This is explicitly not a viewer's render frame. */
public record PlayerPoseSnapshot(UUID player, UUID session, UUID world, long sequence, int entityTick,
        long sampledAtEpochMillis, String serverVersion, String samplePhase,
        RootState root, Map<BodyPart, PartPose> parts, BodyState body,
        ScaleState scale, MotionState motion, boolean clientFrameAvailable) {
    public record RootState(Observation<Vec3> position, Observation<Vec3> velocity,
                            Observation<Double> entityYaw, Observation<Double> entityPitch,
                            Observation<Double> collisionWidth, Observation<Double> collisionHeight,
                            String posture, String mainHand) {}
    /** Server simulation angles are separate from PartPose's client MODEL transforms. */
    public record BodyState(Observation<Double> serverYaw, Observation<Double> previousServerYaw,
                            Observation<Double> serverHeadYaw,
                            Observation<Vec3> backTopLeft, Observation<Vec3> backTopCenter, Observation<Vec3> backTopRight) {}
    public PlayerPoseSnapshot {
        Objects.requireNonNull(player); Objects.requireNonNull(session); Objects.requireNonNull(world);
        Objects.requireNonNull(serverVersion); Objects.requireNonNull(samplePhase);
        Objects.requireNonNull(root); Objects.requireNonNull(body); Objects.requireNonNull(scale); Objects.requireNonNull(motion);
        if (sequence < 1) throw new IllegalArgumentException("Invalid sequence");
        EnumMap<BodyPart, PartPose> copy = new EnumMap<>(BodyPart.class); copy.putAll(parts);
        if (copy.size() != BodyPart.values().length) throw new IllegalArgumentException("Six body parts required");
        for (BodyPart key : BodyPart.values()) if (copy.get(key).part() != key) throw new IllegalArgumentException("Part/key mismatch");
        parts = Collections.unmodifiableMap(copy);
        if (clientFrameAvailable) throw new IllegalArgumentException("Server snapshot cannot claim a client render frame");
    }
}
