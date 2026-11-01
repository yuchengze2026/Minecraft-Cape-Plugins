package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import static org.junit.jupiter.api.Assertions.*;
class CapeFittingTest {
 @Test void stationaryCapeOpensAwayFromBackAndCrouchRaisesHem(){
  assertEquals(6,CapeFitting.lift(0,false));assertEquals(18,CapeFitting.lift(0,true));assertEquals(55,CapeFitting.lift(55,true));
  assertEquals(.015f,CapeFitting.clearance(.00075f));
  for(boolean crouch:new boolean[]{false,true})for(boolean armour:new boolean[]{false,true})for(boolean jacket:new boolean[]{false,true}){
   float riding=crouch?1.125f:1.35f,surface=CapePose.backSurface(jacket,armour),back=surface+CapeFitting.clearance(.00075f);
   var t=CapePose.hinged(35,CapeFitting.lift(0,crouch),crouch,riding,back);
   float root=1.501f*.9375f-(crouch?.125f+.2f*.9375f:0)-riding;
   var inverse=new Quaternionf().rotationY((float)-Math.toRadians(35)).rotateX(crouch?.5f:0).invert();
   for(float x:new float[]{-.3125f,.3125f})for(float y:new float[]{-1,0})for(float z:new float[]{0,.0625f}){
    Vector3f point=new Vector3f(x,y,z).rotate(t.getRightRotation()).rotate(t.getLeftRotation()).add(t.getTranslation()).sub(0,root,0).rotate(inverse);
    assertTrue(point.z<=-surface-.00799f,"Outside outer skin/armour: "+point);
   }
  }
 }
 @Test void ordinaryTurnsKeepTheHingeOutsideArmourBetweenTicks(){
  for(boolean crouch:new boolean[]{false,true})for(float fraction:new float[]{0,.25f,.5f,.75f,1}){
   float surface=CapePose.backSurface(true,true),back=surface+CapeFitting.clearance(.008f);
   var a=CapePose.hinged(0,18,crouch,1.35f,back);var b=CapePose.hinged(30,18,crouch,1.35f,back);
   var pivot=new Vector3f(a.getTranslation()).lerp(b.getTranslation(),fraction);
   float root=1.501f*.9375f-(crouch?.125f+.2f*.9375f:0)-1.35f;
   pivot.sub(0,root,0).rotate(new Quaternionf().rotationY((float)-Math.toRadians(30*fraction)).rotateX(crouch?.5f:0).invert());
   assertTrue(pivot.z <= -surface-1f/128,"Mid-turn clearance must retain at least 1/8 model pixel");
  }
 }
 @Test void ownCapeStaysRenderedAndPreviewRemainsPrivate(){
  assertTrue(CapeFitting.visibleTo(true,false));assertTrue(CapeFitting.visibleTo(false,false));
  assertTrue(CapeFitting.visibleTo(true,true));assertFalse(CapeFitting.visibleTo(false,true));
 }
}
