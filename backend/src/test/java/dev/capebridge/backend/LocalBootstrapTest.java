package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LocalBootstrapTest {
 @Test void smallSavedHeadDirectionDoesNotBecomeAnInventedBodyRotation(){
  for(float yaw:new float[]{-49,-37,0,37,49})assertEquals(0,TorsoHeading.localInitialBody(yaw),.00001);
 }
 @Test void firstClientTickUsesTheNativeHeadConstraintAtLargeAngles(){
  assertEquals(40,TorsoHeading.localInitialBody(90),.00001);assertEquals(-40,TorsoHeading.localInitialBody(270),.00001);
  assertEquals(130,Math.abs(TorsoHeading.localInitialBody(180)),.00001);
 }
 @Test void fullTurnMakesBothInitialOriginsConvergeWithoutChangingTurnRules(){
  float self=TorsoHeading.localInitialBody(37),observer=37,previous=37;
  for(int i=1;i<=100;i++){
   float yaw=37+i*18;self=TorsoHeading.advance(self,previous,18,0,0,false,1).body();observer=TorsoHeading.advance(observer,previous,18,0,0,false,1).body();
   if(i>10)assertEquals(0,CapePose.wrap(self-observer),.00001);previous=yaw;
  }
 }
}
