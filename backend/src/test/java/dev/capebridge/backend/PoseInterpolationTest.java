package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class PoseInterpolationTest {
 @Test void movingModelOriginPreservesEveryCapeCorner(){
  for(boolean crouch:new boolean[]{false,true})for(int yaw=-180;yaw<=180;yaw+=30)for(int lift=0;lift<=65;lift+=5){
   var old=CapePose.attached(false,yaw,0,lift,crouch,1.35f,.133f);
   var next=CapePose.hinged(yaw,lift,crouch,1.35f,.133f);
   for(float x:new float[]{-.3125f,.3125f})for(float y:new float[]{-.5f,.5f})for(float z:new float[]{-.03125f,.03125f}){
    Vector3f a=new Vector3f(x,y,z).rotate(old.getRightRotation()).rotate(old.getLeftRotation()).add(old.getTranslation());
    Vector3f b=new Vector3f(x,y-.5f,z+.03125f).rotate(next.getRightRotation()).rotate(next.getLeftRotation()).add(next.getTranslation());
    assertTrue(a.distance(b)<.000001f,"New origin must preserve thickness, size and fitting");
   }
  }
 }
 @Test void normalTurnsStaySmoothAtEveryLift(){
  for(float lift:new float[]{0,20,55,65}){
   var a=CapePose.hinged(0,lift,false,1.35f,.133f);
   var b=CapePose.hinged(27,lift,false,1.35f,.133f);
   assertEquals(1,PoseInterpolation.duration(a,b,false));
   assertEquals(a.getTranslation(),CapePose.hinged(0,0,false,1.35f,.133f).getTranslation());
   Vector3f interpolated=new Vector3f(a.getTranslation()).lerp(b.getTranslation(),.5f);
   assertTrue(interpolated.distance(CapePose.hinged(13.5f,lift,false,1.35f,.133f).getTranslation())<.004f);
  }
 }
 @Test void postureSpawnAndAmbiguousHalfTurnsSnap(){
  var a=CapePose.hinged(0,20,false,1.35f,.133f);var b=CapePose.hinged(8,20,false,1.35f,.133f);
  assertEquals(1,PoseInterpolation.duration(a,b,false));assertEquals(0,PoseInterpolation.duration(a,b,true));assertEquals(0,PoseInterpolation.duration(null,b,false));
  assertEquals(1,PoseInterpolation.duration(a,CapePose.hinged(90,20,false,1.35f,.133f),false));
  assertEquals(0,PoseInterpolation.duration(a,CapePose.hinged(180,20,false,1.35f,.133f),false));
 }
 @Test void highSpeedInterpolationHasNoThirtyDegreeCliff(){
  var a=CapePose.hinged(0,6,false,1.35f,.19078125f);
  assertEquals(1,PoseInterpolation.duration(a,CapePose.hinged(40,6,false,1.35f,.19078125f),false));
  assertEquals(1,PoseInterpolation.duration(a,CapePose.hinged(60,6,false,1.35f,.19078125f),false));
  assertEquals(1,PoseInterpolation.duration(a,CapePose.hinged(130,6,false,1.35f,.19078125f),false));
  assertEquals(1,PoseInterpolation.duration(CapePose.hinged(179,6,false,1.35f,.19078125f),CapePose.hinged(-179,6,false,1.35f,.19078125f),false));
 }
}
