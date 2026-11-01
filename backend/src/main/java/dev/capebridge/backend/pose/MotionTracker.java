package dev.capebridge.backend.pose;

import dev.capebridge.common.pose.*;
import static dev.capebridge.common.pose.Observation.*;
import static dev.capebridge.common.pose.MotionState.Kind.*;

/** Observed movement, never a guess about a client animation or jump cause. Units: blocks/tick. */
public final class MotionTracker {
    public record Input(int tick, double y, boolean onGround, boolean flying, boolean gliding,
                        boolean swimming, boolean riding, boolean jumpEvent, boolean impulseEvent) {
        public Input { if (!Double.isFinite(y)) throw new IllegalArgumentException("Non-finite height"); }
    }
    private Input previous;
    private Double origin, peak;
    private boolean jumpConfirmed;
    private MotionState result;
    public MotionState sample(Input in, boolean reset) {
        if (!reset && previous != null && in.tick() <= previous.tick()) return result;
        boolean discontinuity = reset || previous == null || in.tick() - previous.tick() > 2;
        boolean special = in.flying() || in.gliding() || in.swimming() || in.riding();
        if (discontinuity || special) { origin = peak = null; jumpConfirmed = false; }
        Observation<Double> speed = discontinuity ? missing(Availability.NOT_SAMPLED, "motion.reset")
            : of((in.y() - previous.y()) / (in.tick() - previous.tick()), Source.DERIVED, "motion.position_delta.blocks_per_tick");
        boolean landed = !discontinuity && !special && !previous.onGround() && in.onGround();
        if (!discontinuity && !special && previous.onGround() && !in.onGround()) {
            origin = previous.y(); peak = Math.max(origin, in.y()); jumpConfirmed = in.jumpEvent();
        }
        if (!discontinuity && !special && !in.onGround() && in.jumpEvent()) jumpConfirmed = true;
        if (in.impulseEvent()) jumpConfirmed = false;
        if (origin != null) peak = Math.max(peak, in.y());
        MotionState.Kind kind = in.riding() ? RIDING : in.swimming() ? SWIMMING : in.gliding() ? GLIDING : in.flying() ? FLYING
            : discontinuity ? RESET : in.onGround() ? GROUNDED : in.impulseEvent() ? EXTERNAL_IMPULSE
            : jumpConfirmed && speed.value() >= 0 ? JUMPING : speed.value() < 0 ? FALLING : AIRBORNE_UNKNOWN;
        Observation<Double> takeoff = origin == null ? missing(Availability.NOT_SAMPLED, "motion.takeoff.not_observed") : of(origin, Source.DERIVED, "motion.last_grounded_y");
        Observation<Double> height = origin == null ? missing(Availability.NOT_SAMPLED, "motion.takeoff.not_observed") : of(in.y()-origin, Source.DERIVED, "motion.height_from_takeoff");
        Observation<Double> maximum = peak == null ? missing(Availability.NOT_SAMPLED, "motion.takeoff.not_observed") : of(peak, Source.DERIVED, "motion.observed_peak_y");
        result = new MotionState(kind, special || in.jumpEvent() || in.impulseEvent() ? Source.SERVER_API : Source.DERIVED,
                                 in.onGround(), takeoff, height, maximum, speed, landed, discontinuity);
        if (in.onGround()) { origin = peak = null; jumpConfirmed = false; }
        previous = in;
        return result;
    }
}
