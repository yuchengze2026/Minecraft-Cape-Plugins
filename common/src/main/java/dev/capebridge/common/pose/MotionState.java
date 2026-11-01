package dev.capebridge.common.pose;

public record MotionState(Kind kind, Observation.Source classificationSource, boolean onGround,
                          Observation<Double> takeoffY, Observation<Double> heightFromTakeoff,
                          Observation<Double> peakY, Observation<Double> observedVerticalSpeed,
                          boolean landed, boolean discontinuity) {
    public enum Kind { GROUNDED, JUMPING, FALLING, AIRBORNE_UNKNOWN, FLYING, GLIDING, SWIMMING, RIDING, EXTERNAL_IMPULSE, RESET }
    public MotionState {
        java.util.Objects.requireNonNull(kind); java.util.Objects.requireNonNull(classificationSource);
        java.util.Objects.requireNonNull(takeoffY); java.util.Objects.requireNonNull(heightFromTakeoff);
        java.util.Objects.requireNonNull(peakY); java.util.Objects.requireNonNull(observedVerticalSpeed);
    }
}
