package dev.capebridge.backend.pose;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.capebridge.common.pose.Observation.*;

class NativeAnglesTest {
    public static class Native { public float yBodyRot=37,yHeadRot=90; }
    public static class Player { public Native getHandle(){return new Native();} }
    public static class Missing { public float yaw=123; }
    public static class MissingPlayer { public Missing getHandle(){return new Missing();} }
    public static class Legacy { public float aV=19,aX=72; }
    public static class LegacyPlayer { public Legacy getHandle(){return new Legacy();} }
    public static class ApiPlayer extends Player {public float getBodyYaw(){return -47;}}
    public static class ApiOnly {public float getBodyYaw(){return 12;}}
    public static class InvalidApi extends Player {public float getBodyYaw(){return Float.NaN;}}
    public static class BrokenApi extends Player {public float getBodyYaw(){throw new IllegalStateException();}}
    @Test void publicBodyApiTakesPriorityEvenWithoutNmsHandle(){
        var reader=new NativeAngles("1.21.11-R0.1");
        assertEquals(-47,reader.body(new ApiPlayer()).value());
        assertEquals(Source.SERVER_API,reader.body(new ApiPlayer()).source());
        assertEquals(12,reader.body(new ApiOnly()).value());
        assertEquals(90,reader.head(new ApiPlayer()).value());
    }
    @Test void brokenPublicApiIsReportedAndNeverHiddenByAFieldFallback(){
        var reader=new NativeAngles("1.21.11-R0.1");
        assertEquals(Availability.INVALID,reader.body(new InvalidApi()).availability());
        assertEquals(Availability.INVALID,reader.body(new BrokenApi()).availability());
    }
    @Test void readsBodyAndHeadSeparatelyAndNeverFallsBackToEntityYaw() {
        var reader=new NativeAngles("1.21.11-R0.1");
        assertEquals(37,reader.body(new Player()).value());assertEquals(90,reader.head(new Player()).value());
        assertEquals(Source.SERVER_FIELD,reader.body(new Player()).source());assertFalse(reader.body(new MissingPlayer()).available());
    }
    @Test void obfuscatedMappingIsRestrictedToVerifiedLegacyVersion() {
        assertEquals(19,new NativeAngles("1.20.1-R0.1").body(new LegacyPlayer()).value());
        assertFalse(new NativeAngles("1.21.11-R0.1").body(new LegacyPlayer()).available());
        assertEquals(72,new NativeAngles("1.20.1-R0.1").head(new LegacyPlayer()).value());
        assertFalse(new NativeAngles("1.21.11-R0.1").head(new LegacyPlayer()).available());
    }
}
