package dev.capebridge.backend;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapeNoRollTest {
 @Test void simultaneousTurningAndBendingNeverRollsBetweenTicks(){
  for(boolean crouch:new boolean[]{false,true})for(int yaw=-180;yaw<=180;yaw+=15){
   var a=CapePose.hinged(yaw,6,crouch,1.35f,.15f);
   var b=CapePose.hinged(yaw+25,65,crouch,1.35f,.15f);
   for(int i=0;i<=20;i++){
    float t=i/20f;
    var left=new Quaternionf(a.getLeftRotation()).slerp(b.getLeftRotation(),t);
    var right=new Quaternionf(a.getRightRotation()).slerp(b.getRightRotation(),t);
    // The top edge stays horizontal and aligned to the interpolated torso yaw.
    var edge=new Vector3f(-1,0,0).rotate(right).rotate(left);
    var expected=new Vector3f(1,0,0).rotateY((float)-Math.toRadians(yaw+25*t));
    assertEquals(0,edge.y,.00001);
    assertTrue(edge.distance(expected)<.00002);
   }
  }
 }
}
