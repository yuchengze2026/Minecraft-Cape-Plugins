package dev.capebridge.common.pose;

import java.util.List;
import java.util.Objects;

public record ScaleState(Support support, Observation<Double> base, Observation<Double> effective,
                         Observation<List<Modifier>> modifiers) {
    public enum Support { SUPPORTED, UNSUPPORTED, UNAVAILABLE }
    public record Modifier(String id, double amount, String operation) {
        public Modifier {
            Objects.requireNonNull(id); Objects.requireNonNull(operation);
            if (!Double.isFinite(amount)) throw new IllegalArgumentException("Invalid modifier");
        }
    }
    public ScaleState {
        Objects.requireNonNull(support); Objects.requireNonNull(base); Objects.requireNonNull(effective); Objects.requireNonNull(modifiers);
        if (base.available() && base.value() <= 0 || effective.available() && effective.value() <= 0) throw new IllegalArgumentException("Nonpositive scale");
        if (modifiers.available()) modifiers = Observation.of(List.copyOf(modifiers.value()), modifiers.source(), modifiers.detail());
    }
    public static ScaleState unsupported() {
        return new ScaleState(Support.UNSUPPORTED,
            Observation.missing(Observation.Availability.UNSUPPORTED, "scale.attribute.unsupported"),
            Observation.of(1d, Observation.Source.VERSION_DEFAULT, "scale.legacy.unit_default"),
            Observation.missing(Observation.Availability.UNSUPPORTED, "scale.attribute.unsupported"));
    }
}
