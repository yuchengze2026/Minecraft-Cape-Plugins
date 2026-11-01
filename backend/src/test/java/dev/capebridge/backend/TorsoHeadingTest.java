package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TorsoHeadingTest {
    @Test void lookingThirtyDegreesEitherWayDoesNotRotateStationaryTorso(){
        float body=0;for(float look:new float[]{30,-30,15,-15,0})for(int i=0;i<20;i++){body=TorsoHeading.step(body,look,0,0,false);assertEquals(0,body);}
    }
    @Test void strafingTurnsBodyEvenWhenHeadAndServerBodyStayAtZero(){
        float body=TorsoHeading.step(0,0,.25,0,false);assertEquals(-27,body,.001);
        body=TorsoHeading.step(body,0,.25,0,false);assertEquals(-45.9,body,.001);
        for(int i=0;i<20;i++)body=TorsoHeading.step(body,0,.25,0,false);assertEquals(-50,body,.001);
    }
    @Test void stoppingRetainsTheBodyOrientationInsteadOfSnappingToHead(){
        float body=-50;for(int i=0;i<50;i++)body=TorsoHeading.step(body,0,0,0,false);assertEquals(-50,body);
    }
    @Test void changingTravelDirectionRotatesTorsoWithHeadStill(){
        float body=-50;for(int i=0;i<20;i++)body=TorsoHeading.step(body,0,-.25,0,false);assertEquals(50,body,.001);
    }
    @Test void walkingBackwardsKeepsTorsoFacingForward(){
        float body=0;for(int i=0;i<20;i++)body=TorsoHeading.step(body,0,0,-.25,false);assertEquals(0,body,.001);
    }
    @Test void largeHeadTurnFollowsNativeTorsoLimitRatherThanCopyingHead(){assertEquals(70,TorsoHeading.step(0,120,0,0,false),.001);}
    @Test void swingingTurnsTheNativeTorsoWithoutSnappingToHead(){assertEquals(-35,TorsoHeading.step(-50,0,0,0,true),.001);}
    @Test void subThresholdPositionNoiseDoesNotRotateBody(){assertEquals(0,TorsoHeading.step(0,20,.001,-.001,false));}
}
