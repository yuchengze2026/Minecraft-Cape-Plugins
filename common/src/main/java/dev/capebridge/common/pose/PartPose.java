package dev.capebridge.common.pose;

import dev.capebridge.common.pose.PoseGeometry.*;
import java.util.Objects;

public record PartPose(BodyPart part, String parent,
                       Observation<Vec3> localPosition, Observation<Vec3> worldPosition,
                       Rotation localRotation, Rotation worldRotation,
                       Observation<Vec3> localScale, Observation<Vec3> worldScale,
                       Observation<Vec3> pivot, Observation<Vec3> dimensions,
                       Observation<Matrix4> localMatrix, Observation<Matrix4> worldMatrix) {
    public PartPose {
        Objects.requireNonNull(part);
        if (!part.parent().equals(parent)) throw new IllegalArgumentException("Invalid logical parent");
        Objects.requireNonNull(localPosition); Objects.requireNonNull(worldPosition);
        Objects.requireNonNull(localRotation); Objects.requireNonNull(worldRotation);
        Objects.requireNonNull(localScale); Objects.requireNonNull(worldScale);
        Objects.requireNonNull(pivot); Objects.requireNonNull(dimensions);
        Objects.requireNonNull(localMatrix); Objects.requireNonNull(worldMatrix);
    }
    public static PartPose unavailable(BodyPart part) {
        Observation<Vec3> v = Observation.missing(Observation.Availability.NOT_TRANSMITTED, "client.model.transform.not_transmitted");
        Observation<Matrix4> m = Observation.missing(Observation.Availability.NOT_TRANSMITTED, "client.model.matrix.not_transmitted");
        return new PartPose(part, part.parent(), v, v, Rotation.notTransmitted(), Rotation.notTransmitted(), v, v, v, v, m, m);
    }
}
