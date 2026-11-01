package dev.capebridge.common.pose;

import java.util.Objects;

/** A value with explicit provenance. Missing never means zero or identity. */
public record Observation<T>(T value, Source source, Availability availability, String detail) {
    public enum Source { SERVER_API, SERVER_FIELD, PROTOCOL, CLIENT_RENDER, DERIVED, VERSION_DEFAULT, NONE }
    public enum Availability { AVAILABLE, UNSUPPORTED, NOT_TRANSMITTED, NOT_SAMPLED, INVALID }
    public Observation {
        Objects.requireNonNull(source); Objects.requireNonNull(availability); Objects.requireNonNull(detail);
        if ((availability == Availability.AVAILABLE) != (value != null)) throw new IllegalArgumentException("Value/availability mismatch");
        if (availability == Availability.AVAILABLE && source == Source.NONE) throw new IllegalArgumentException("Missing provenance");
        if (value instanceof Number n && !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Non-finite value");
    }
    public static <T> Observation<T> of(T value, Source source, String detail) {
        return new Observation<>(Objects.requireNonNull(value), source, Availability.AVAILABLE, detail);
    }
    public static <T> Observation<T> missing(Availability why, String detail) {
        if (why == Availability.AVAILABLE) throw new IllegalArgumentException("Expected missing status");
        return new Observation<>(null, Source.NONE, why, detail);
    }
    public boolean available() { return availability == Availability.AVAILABLE; }
}
