package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class DirectedPoseTest {
 private Quaternionf rotation(Transformation t){return new Quaternionf(t.getLeftRotation()).mul(t.getRightRotation());}
 private Quaternionf interpolate(Transformation a,Transformation b,float t){return new Quaternionf(a.getLeftRotation()).slerp(b.getLeftRotation(),t).mul(new Quaternionf(a.getRightRotation()).slerp(b.getRightRotation(),t));}
 @Test void splitHeadingPreservesTheCapeCornersAndShoulderAtEveryPhase(){
  for(int phase=-720;phase<=720;phase+=15)for(boolean crouched:new boolean[]{false,true})for(int lift=0;lift<=65;lift+=5){
   var old=CapePose.hinged(CapePose.wrap(phase),lift,crouched,1.35f,.15f);
   var next=CapePose.smoothHinged(phase,lift,crouched,1.35f,.15f);
   assertEquals(old.getTranslation(),next.getTranslation());
   for(float x:new float[]{-.3125f,.3125f})for(float y:new float[]{-1,0})for(float z:new float[]{0,.0625f}){
    var a=new Vector3f(x,y,z).rotate(rotation(old));var b=new Vector3f(x,y,z).rotate(rotation(next));
    assertTrue(a.distance(b)<.000002);
   }
  }
 }
 @Test void clientInterpolationFollowsTheDirectedArcIncludingMergedTicksAndPhaseWrap(){
  for(float phase:new float[]{-710,-370,-190,-10,170,350,710})for(float delta:new float[]{-340,-260,-180,-170,170,180,260,340})for(float lift:new float[]{0,30,65}){
   var a=CapePose.smoothHinged(phase,lift,false,1.35f,.15f);
   var b=CapePose.smoothHinged((phase+delta)%720,lift,false,1.35f,.15f);
   assertEquals(1,PoseInterpolation.duration(a,b,false));
   for(int i=1;i<20;i++){
    float time=i/20f;Quaternionf actual=interpolate(a,b,time);
    Quaternionf expected=rotation(CapePose.hinged(CapePose.wrap(phase+delta*time),lift,false,1.35f,.15f));
    for(Vector3f axis:new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)})
     assertTrue(new Vector3f(axis).rotate(actual).distance(new Vector3f(axis).rotate(expected))<.00001,"Client must follow the directed arc without a sideways roll");
   }
  }
 }
 @Test void longSpinKeepsPhaseBoundedWithoutLosingDirection(){
  float phase=0;
  for(int i=0;i<200_000;i++){
   float delta=i%2==0?170:340,next=(phase+delta)%720;
   var a=CapePose.smoothHinged(phase,30,false,1.35f,.15f);
   var b=CapePose.smoothHinged(next,30,false,1.35f,.15f);
   var actual=new Vector3f(1,0,0).rotate(interpolate(a,b,.5f));
   var expected=new Vector3f(1,0,0).rotate(rotation(CapePose.hinged(CapePose.wrap(phase+delta*.5f),30,false,1.35f,.15f)));
   assertTrue(actual.distance(expected)<.00001);assertTrue(Math.abs(next)<720);phase=next;
  }
 }
}
