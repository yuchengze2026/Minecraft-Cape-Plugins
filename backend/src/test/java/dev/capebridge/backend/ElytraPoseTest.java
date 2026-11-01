package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class ElytraPoseTest {
 @Test void foldsAndCrouchesUsingVanillaAngles(){
  ElytraPose e=new ElytraPose();assertEquals(15,Math.toDegrees(e.roll),.001);
  for(int i=0;i<100;i++)e.step(false,true,0,0,0,1);
  assertEquals(40,Math.toDegrees(e.pitch),.01);assertEquals(45,Math.toDegrees(e.roll),.01);assertEquals(5,Math.toDegrees(e.yaw),.01);
  var pose=e.poses(0,0,1.125f,true,false);assertEquals((1.501f-.1875f)*.9375f-.125f-1.125f,pose[0].getTranslation().y,.00001);
 }
 @Test void flightOpensSymmetricWingsAndTiltsBody(){
  ElytraPose e=new ElytraPose();for(int i=0;i<100;i++)e.step(true,false,0,0,.8,1);
  assertEquals(90,Math.toDegrees(e.roll),.01);assertEquals(20,Math.toDegrees(e.pitch),.01);
  var pose=e.poses(0,0,.45f,false,true);
  assertEquals(.3125*.9375,pose[0].getTranslation().x,.00001);assertEquals(-.3125*.9375,pose[1].getTranslation().x,.00001);
  assertEquals(.125*.9375-.45,pose[0].getTranslation().y,.00001);assertEquals(1.501*.9375,pose[0].getTranslation().z,.00001);
  assertEquals(1,pose[0].getLeftRotation().lengthSquared(),.00001);
 }
 @Test void steepDiveReducesSpreadAndLandingFoldsAgain(){
  ElytraPose e=new ElytraPose();for(int i=0;i<100;i++)e.step(true,false,0,-1,0,1);
  assertEquals(15,Math.toDegrees(e.roll),.01);
  for(int i=0;i<80;i++)e.step(true,false,0,0,1,1);
  for(int i=0;i<100;i++)e.step(false,false,0,0,0,1);
  assertEquals(15,Math.toDegrees(e.roll),.01);assertEquals(15,Math.toDegrees(e.pitch),.01);
 }
 @Test void capeMaskPreservesEveryOtherSkinPartForAllByteValues(){
  for(int i=0;i<256;i++){int result=NativeCapeMask.withoutCape((byte)i)&255;assertEquals(i&254,result);}
 }
 @Test void renderedWingCornersMatchNativeClientIncludingItsHiddenItemFlip(){
  // Independent reference chain: LivingEntityRenderer -> PlayerRenderer ->
  // ElytraLayer -> ModelPart, including the real ItemDisplayRenderer Y=180 step.
  for(boolean flying:new boolean[]{false,true})for(boolean crouch:new boolean[]{false,true})for(int heading:new int[]{-175,0,73,180})for(int look:new int[]{-30,0,40}){
   ElytraPose e=new ElytraPose();e.step(flying,crouch,0,0,.5,100);
   float riding=flying?.45f:crouch?1.125f:1.35f;
   var poses=e.poses(heading,look,riding,crouch,flying);
   for(int i=0;i<2;i++)for(float x:new float[]{i==0?-11:-1,i==0?1:11})for(float y:new float[]{-1,21})for(float z:new float[]{-1,3}){
    float side=i==0?1:-1;
    Matrix4f nativeTransform=new Matrix4f().translate(0,crouch&&!flying?-.125f:0,0)
      .rotateY((float)Math.toRadians(180-heading));
    if(flying)nativeTransform.rotateX((float)Math.toRadians(-90-look));
    nativeTransform.scale(-1,-1,1).scale(.9375f).translate(0,-1.501f,0).translate(0,0,.125f)
      .translate(side*5/16,crouch&&!flying?3/16f:0,0)
      .rotateZ(-side*e.roll).rotateY(side*e.yaw).rotateX(e.pitch);
    Vector3f expected=nativeTransform.transformPosition(new Vector3f(x/16,y/16,z/16));
    var pose=poses[i];
    Vector3f actual=new Vector3f(x/16,-y/16,-z/16)
      .rotateY((float)Math.PI).rotate(pose.getRightRotation()).mul(pose.getScale())
      .rotate(pose.getLeftRotation()).add(pose.getTranslation()).add(0,riding,0);
    // Matrix and quaternion sin/cos paths differ close to 90 degrees; tolerance is <0.002 model pixel.
    assertTrue(expected.distance(actual)<.0001,"All native cube corners must coincide, including crouch/glide; "+expected+" vs "+actual+" delta="+expected.distance(actual)+" heading="+heading+" flying="+flying+" crouch="+crouch+" look="+look);
   }
  }
 }
 @Test void standingWingTipsRemainBehindTheBackInsteadOfStickingForward(){
  var poses=new ElytraPose().poses(0,0,1.35f,false,false);
  for(int i=0;i<2;i++){
   var p=poses[i];float x=i==0?-5:5;
   Vector3f tip=new Vector3f(x/16f,-21/16f,-1/16f).rotateY((float)Math.PI)
     .rotate(p.getRightRotation()).mul(p.getScale()).rotate(p.getLeftRotation()).add(p.getTranslation());
   assertTrue(tip.z<-.35f,"Hem must trail behind the back, not point toward the chest");
   assertTrue(Math.abs(tip.x)<.5f,"Folded tips must remain close to the body");
  }
 }
}

