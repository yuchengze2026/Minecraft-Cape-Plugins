package dev.capebridge.backend;
import dev.capebridge.common.CapePivot;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapePivotTest {
 @Test void movingTheModelOriginPreservesAllCornersAndTheOnePixelThickness(){
  for(int armor=0;armor<3;armor++)for(boolean crouch:new boolean[]{false,true})for(int yaw=-180;yaw<=180;yaw+=15)for(float lift:new float[]{18,40,65,80}){
   int profile=CapePivot.profile(armor,crouch);float back=(float)CapePivot.back(armor)+.015f;
   var base=CapePose.hinged(yaw,lift,crouch,1.8f,back);var shifted=CapePose.pivoted(base,profile);var origin=CapePivot.origin(profile);
   for(float x:new float[]{-.3125f,.3125f})for(float y:new float[]{-1,0})for(float z:new float[]{-.0625f,0}){
    var original=render(base,new Vector3f(x,y,z));
    var actual=render(shifted,new Vector3f(x,y-(float)origin.y(),z-(float)origin.z()));
    assertTrue(original.distance(actual)<.000002f,"The six faces must keep their exact world positions");
   }
  }
 }
 @Test void interpolatedHighSpeedTurnsKeepShoulderClearanceDuringBendAndYawTogether(){
  float minimum=Float.POSITIVE_INFINITY,maximum=Float.NEGATIVE_INFINITY;
  for(int armor=0;armor<3;armor++)for(boolean crouch:new boolean[]{false,true})for(float clearance:new float[]{.015f,.02f,.03f}){
   int profile=CapePivot.profile(armor,crouch);float back=(float)CapePivot.back(armor)+clearance;var origin=CapePivot.origin(profile);
   float minLift=crouch?18:6;
   for(float start=minLift;start<=80;start+=7)for(float change:new float[]{-28,0,28})for(float turn:new float[]{-179.9f,-130,36,60,90,130,179.9f}){
    float end=Math.max(minLift,Math.min(80,start+change));
    var a=CapePose.pivoted(CapePose.hinged(175,start,crouch,1.8f,back),profile);
    var b=CapePose.pivoted(CapePose.hinged(CapePose.wrap(175+turn),end,crouch,1.8f,back),profile);
    assertEquals(1,PoseInterpolation.duration(a,b,false));
    for(int i=0;i<=20;i++){
     float t=i/20f;
     var pose=new Transformation(new Vector3f(a.getTranslation()).lerp(b.getTranslation(),t),new Quaternionf(a.getLeftRotation()).slerp(b.getLeftRotation(),t),new Vector3f(1),new Quaternionf(a.getRightRotation()).slerp(b.getRightRotation(),t));
     Vector3f hinge=render(pose,new Vector3f(0,(float)-origin.y(),(float)-origin.z()));
     float yaw=175+turn*t,lift=start+(end-start)*t;
     Vector3f normal=new Vector3f(0,0,-1).rotateX(crouch?.5f:0).rotateY((float)-Math.toRadians(yaw));
     Vector3f bodyBack=new Vector3f(CapePose.hinged(yaw,lift,crouch,1.8f,back).getTranslation()).sub(new Vector3f(normal).mul(clearance));
     float gap=new Vector3f(hinge).sub(bodyBack).dot(normal);minimum=Math.min(minimum,gap);maximum=Math.max(maximum,gap);
     assertTrue(gap>.0005f,"No shoulder penetration during interpolation: gap="+gap+" turn="+turn+" lift="+start+" -> "+end+" armor="+armor+" crouch="+crouch);
     assertTrue(gap<.06f,"No large separation even in an almost half-turn");
     for(float y:new float[]{-1,0})for(float z:new float[]{-.0625f,0}){
      Vector3f corner=render(pose,new Vector3f(0,y-(float)origin.y(),z-(float)origin.z()));
      assertTrue(new Vector3f(corner).sub(bodyBack).dot(normal)>.0005f,"The full thickness must remain outside the back");
     }
    }
   }
  }
 }
 @Test void smallHeadTurnsStillDoNotMoveTheStationaryBody(){
  float body=0;for(float head:new float[]{20,-20,30,-30,0})body=TorsoHeading.step(body,head,0,0,false);assertEquals(0,body);
 }
 private static Vector3f render(Transformation pose,Vector3f raw){
  return raw.rotateY((float)Math.PI).rotate(pose.getRightRotation()).rotate(pose.getLeftRotation()).add(pose.getTranslation());
 }
}
