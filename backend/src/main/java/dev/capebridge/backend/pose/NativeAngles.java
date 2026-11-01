package dev.capebridge.backend.pose;

import dev.capebridge.common.pose.Observation;
import java.lang.reflect.*;
import java.util.*;
import static dev.capebridge.common.pose.Observation.*;

/** Verified field names only. A missing mapping does not fall back to entity/head yaw. */
public final class NativeAngles {
    private record Access(Method apiBody, Method handle, Field body, Field head) {}
    private final Map<Class<?>, Access> cache = new HashMap<>();
    private final boolean legacy1201;
    public NativeAngles(String serverVersion) { legacy1201 = serverVersion.startsWith("1.20.1-"); }
    private static Field field(Class<?> type, String... names) {
        for (String name : names) try {
            Field f = type.getField(name);
            if (f.getType() == float.class) return f;
        } catch (NoSuchFieldException ignored) {}
        return null;
    }
    private Access resolve(Class<?> player) {
        Method apiBody=null;
        try {Method m=player.getMethod("getBodyYaw");if(m.getReturnType()==float.class)apiBody=m;}
        catch(NoSuchMethodException ignored){}
        try {
            Method handle = player.getMethod("getHandle"); Class<?> type = handle.getReturnType();
            return new Access(apiBody, handle, legacy1201 ? field(type, "yBodyRot", "aV") : field(type, "yBodyRot"),
                legacy1201 ? field(type, "yHeadRot", "aX") : field(type, "yHeadRot"));
        } catch (ReflectiveOperationException e) { return new Access(apiBody, null, null, null); }
    }
    public Observation<Double> body(Object player) { return read(player, true); }
    public Observation<Double> head(Object player) { return read(player, false); }
    private Observation<Double> read(Object player, boolean body) {
        Access a = cache.computeIfAbsent(player.getClass(), this::resolve);
        if(body&&a.apiBody!=null)try {
            double value=((Number)a.apiBody.invoke(player)).doubleValue();
            return Double.isFinite(value)?of(value,Source.SERVER_API,"server.api.getBodyYaw"):
                missing(Availability.INVALID,"server.api.getBodyYaw.non_finite");
        }catch(ReflectiveOperationException|RuntimeException failure){return missing(Availability.INVALID,"server.api.getBodyYaw.read_failed");}
        Field f = body ? a.body : a.head;
        if (f == null) return missing(Availability.UNSUPPORTED, "server.angle.mapping_unavailable");
        try {
            double value = f.getFloat(a.handle.invoke(player));
            if (!Double.isFinite(value)) return missing(Availability.INVALID, "server.angle.non_finite");
            return of(value, Source.SERVER_FIELD, "server."+f.getName());
        } catch (ReflectiveOperationException | RuntimeException e) { return missing(Availability.INVALID, "server.angle.read_failed"); }
    }
}
