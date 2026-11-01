package dev.capebridge.backend.pose;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import dev.capebridge.common.pose.*;
import java.util.*;
import java.lang.reflect.*;
import static dev.capebridge.common.pose.Observation.*;

/** Uses the runtime attribute instance's final value; never applies modifiers a second time. */
public final class ScaleReader {
    private final Attribute attribute;
    private final boolean legacy;
    public ScaleReader(String version) {
        Attribute found = null;
        for (String name : List.of("SCALE", "GENERIC_SCALE")) try {
            Object value = Attribute.class.getField(name).get(null);
            if (value instanceof Attribute a) { found = a; break; }
        } catch (ReflectiveOperationException ignored) {}
        attribute = found;
        legacy = version.matches("1\\.20\\.[1-4](?:-.*)?");
    }
    public ScaleState read(Player player) {
        if (attribute == null) return legacy ? ScaleState.unsupported() : unavailable("scale.mapping_unavailable");
        try {
            var value = player.getAttribute(attribute);
            if (value == null) return unavailable("scale.attribute_missing");
            List<ScaleState.Modifier> modifiers = new ArrayList<>();
            for (var modifier : value.getModifiers()) {
                String id;
                try { id = String.valueOf(modifier.getClass().getMethod("getKey").invoke(modifier)); }
                catch (NoSuchMethodException oldApi) { id = modifier.getUniqueId().toString(); }
                modifiers.add(new ScaleState.Modifier(id, modifier.getAmount(), modifier.getOperation().name()));
            }
            return measured(value.getBaseValue(), value.getValue(), modifiers);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return unavailable("scale.read_failed"); }
    }
    /** Hot rendering path: the final attribute value already includes all modifiers. */
    public double effectiveValue(Player player){
        if(attribute==null)return 1;
        try{var instance=player.getAttribute(attribute);double value=instance==null?1:instance.getValue();return Double.isFinite(value)&&value>0?value:1;}
        catch(RuntimeException|LinkageError failure){return 1;}
    }
    static ScaleState measured(double base, double effective, List<ScaleState.Modifier> modifiers) {
        try {
            return new ScaleState(ScaleState.Support.SUPPORTED, of(base, Source.SERVER_API, "attribute.scale.base"),
                of(effective, Source.SERVER_API, "attribute.scale.effective"), of(List.copyOf(modifiers), Source.SERVER_API, "attribute.scale.modifiers"));
        } catch (IllegalArgumentException e) { return unavailable("scale.invalid_value"); }
    }
    private static ScaleState unavailable(String reason) {
        return new ScaleState(ScaleState.Support.UNAVAILABLE, missing(Availability.INVALID, reason),
            missing(Availability.INVALID, reason), missing(Availability.INVALID, reason));
    }
}
