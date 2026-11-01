package dev.capebridge.backend.pose;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.capebridge.common.pose.MotionState.Kind.*;

class MotionTrackerTest {
    MotionTracker.Input i(int t,double y,boolean ground,boolean jump){return new MotionTracker.Input(t,y,ground,false,false,false,false,jump,false);}
    @Test void jumpMeasuresOriginPeakAndLandingWithoutConfusingYWithHeight() {
        var m=new MotionTracker();m.sample(i(1,64,true,false),false);
        var up=m.sample(i(2,64.42,false,true),false);assertEquals(JUMPING,up.kind());assertEquals(.42,up.heightFromTakeoff().value(),1e-6);
        m.sample(i(3,65,false,false),false);
        var down=m.sample(i(4,64.8,false,false),false);assertEquals(FALLING,down.kind());assertEquals(65,down.peakY().value());
        var land=m.sample(i(5,64,true,false),false);assertTrue(land.landed());assertEquals(0,land.heightFromTakeoff().value());
        assertFalse(m.sample(i(6,64,true,false),false).takeoffY().available());
    }
    @Test void walkingOffEdgeIsNotJumpingAndUpwardImpulseIsNotConfirmedJump() {
        var m=new MotionTracker();m.sample(i(1,64,true,false),false);
        assertEquals(FALLING,m.sample(i(2,63.9,false,false),false).kind());
        var n=new MotionTracker();n.sample(i(1,64,true,false),false);
        assertEquals(AIRBORNE_UNKNOWN,n.sample(i(2,64.4,false,false),false).kind());
        assertEquals(EXTERNAL_IMPULSE,n.sample(new MotionTracker.Input(3,65,false,false,false,false,false,false,true),false).kind());
    }
    @Test void teleportGapsAndStaleSamplesCannotCreateArtificialJumpSpeed() {
        var m=new MotionTracker();m.sample(i(1,64,true,false),false);
        var reset=m.sample(i(2,200,false,false),true);assertTrue(reset.discontinuity());assertFalse(reset.observedVerticalSpeed().available());
        assertSame(reset,m.sample(i(1,64,true,false),false));
        var gap=m.sample(i(10,250,false,false),false);assertFalse(gap.takeoffY().available());assertFalse(gap.observedVerticalSpeed().available());
    }
    @Test void flyingSwimmingAndRidingResetGroundJumpHistory() {
        var m=new MotionTracker();m.sample(i(1,64,true,false),false);m.sample(i(2,64.4,false,true),false);
        var flying=m.sample(new MotionTracker.Input(3,70,false,true,false,false,false,false,false),false);
        assertEquals(FLYING,flying.kind());assertFalse(flying.takeoffY().available());
        assertEquals(SWIMMING,m.sample(new MotionTracker.Input(4,70,false,false,false,true,false,false,false),false).kind());
        assertEquals(RIDING,m.sample(new MotionTracker.Input(5,70,false,false,false,false,true,false,false),false).kind());
    }
}
