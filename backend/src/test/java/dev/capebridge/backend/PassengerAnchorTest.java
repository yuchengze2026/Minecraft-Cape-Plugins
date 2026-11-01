package dev.capebridge.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PassengerAnchorTest {
    @Test void legacyMountUsesThreeQuartersOfPoseHeight() {
        assertEquals(1.35f,PassengerAnchor.height(1.8,false),.000001);
        assertEquals(1.125f,PassengerAnchor.height(1.5,false),.000001);
    }
    @Test void modernMountUsesFullPoseHeight() {
        assertEquals(1.8f,PassengerAnchor.height(1.8,true),.000001);
        assertEquals(1.5f,PassengerAnchor.height(1.5,true),.000001);
    }
    @Test void nativeMountPlusTransformationKeepsSameShoulderAcrossVersions() {
        // Native mount heights independently read from 1.20.1, 1.20.4 and 1.21.4.
        for(int version=0;version<3;version++)for(boolean crouch:new boolean[]{false,true})
            for(float yaw:new float[]{0,50,180,270})for(float lift:new float[]{6,18,65}) {
                float nativeMount=version==0?(crouch?1.125f:1.35f):(crouch?1.5f:1.8f);
                float height=crouch?1.5f:1.8f,back=.15f;
                var pose=CapePose.hinged(yaw,lift,crouch,PassengerAnchor.height(height,version!=0),back);
                float expected=1.501f*CapePose.PLAYER_SCALE
                        -(crouch?.125f+.2f*CapePose.PLAYER_SCALE:0)
                        +(crouch?back*(float)Math.sin(.5):0);
                assertEquals(expected,nativeMount+pose.getTranslation().y,.000001,
                        "World-space shoulder must include the client's native mount origin");
            }
    }
    @Test void reproducesOldShoulderOvershootOnModernClients() {
        for(boolean crouch:new boolean[]{false,true}) {
            float height=crouch?1.5f:1.8f;
            var old=CapePose.hinged(0,6,crouch,PassengerAnchor.height(height,false),.15f);
            var fixed=CapePose.hinged(0,6,crouch,PassengerAnchor.height(height,true),.15f);
            assertEquals(crouch?.375f:.45f,old.getTranslation().y-fixed.getTranslation().y,.000001);
        }
    }
    @Test void translatedViewersKeepSameWorldHingeInBothDirectionsAndPoses(){
        for(boolean serverModern:new boolean[]{false,true})for(boolean viewerModern:new boolean[]{false,true})
            for(boolean crouch:new boolean[]{false,true})for(float yaw:new float[]{0,90,225})for(float lift:new float[]{6,18,65}){
                double height=crouch?1.5:1.8;float serverMount=PassengerAnchor.height(height,serverModern);
                var pose=CapePose.hinged(yaw,lift,crouch,serverMount,.18f);
                float corrected=pose.getTranslation().y+PassengerAnchor.viewerOffset(height,serverModern,viewerModern);
                assertEquals(serverMount+pose.getTranslation().y,PassengerAnchor.height(height,viewerModern)+corrected,.000001);
            }
    }
    @Test void nativeViewerNeedsNoAdjustmentAndMixedVersionShiftIsPoseSpecific(){
        for(boolean modern:new boolean[]{false,true})assertEquals(0,PassengerAnchor.viewerOffset(1.8,modern,modern));
        assertEquals(-.45f,PassengerAnchor.viewerOffset(1.8,false,true),.000001);
        assertEquals(-.375f,PassengerAnchor.viewerOffset(1.5,false,true),.000001);
        assertEquals(.45f,PassengerAnchor.viewerOffset(1.8,true,false),.000001);
        assertEquals(.375f,PassengerAnchor.viewerOffset(1.5,true,false),.000001);
    }
}
