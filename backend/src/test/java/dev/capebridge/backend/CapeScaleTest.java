package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import org.joml.*;
import static org.junit.jupiter.api.Assertions.*;
class CapeScaleTest {
 @Test void entireCapeAndHingeScaleAroundPlayerFeetInBothMountFamilies(){
  for(float scale:new float[]{.25f,.5f,1,1.5f,2,4})for(boolean modern:new boolean[]{false,true})
   for(boolean crouch:new boolean[]{false,true})for(float yaw:new float[]{-359,0,85,180,359})for(int profile=0;profile<6;profile++){
    float baseHeight=crouch?1.5f:1.8f,mount=PassengerAnchor.height(baseHeight*scale,modern);
    var unmounted=CapePose.pivoted(CapePose.smoothHinged(yaw,18,crouch,0,.15f),profile);
    var adjusted=CapePose.scaled(unmounted,scale,mount);
    assertEquals(unmounted.getTranslation().y*scale,adjusted.getTranslation().y+mount,.000002);
    assertEquals(unmounted.getTranslation().x*scale,adjusted.getTranslation().x,.000002);
    assertEquals(unmounted.getTranslation().z*scale,adjusted.getTranslation().z,.000002);
    assertEquals(scale,adjusted.getScale().x,.000001);assertEquals(unmounted.getLeftRotation(),adjusted.getLeftRotation());
   }
 }
 @Test void unitScaleMatchesExistingCapeTransform(){
  for(float yaw:new float[]{0,90,359})for(int profile=0;profile<6;profile++){
   var original=CapePose.pivoted(CapePose.smoothHinged(yaw,6,false,1.8f,.15f),profile);
   var scaled=CapePose.scaled(CapePose.pivoted(CapePose.smoothHinged(yaw,6,false,0,.15f),profile),1,1.8f);
   assertTrue(original.getTranslation().equals(scaled.getTranslation(),.000001f));assertEquals(original.getScale(),scaled.getScale());
  }
 }
}
