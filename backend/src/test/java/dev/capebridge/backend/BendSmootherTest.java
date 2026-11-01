package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BendSmootherTest {
 @Test void startStopFlightAndCrouchTransitionsDoNotWhipTheCloth(){
  var s=new BendSmoother();float previous=s.sample(6,2.5f);
  for(float target:new float[]{55,65,18,6,65,6,18,6})for(int i=0;i<40;i++){
   float next=s.sample(target,2.5f);
   assertTrue(Math.abs(next-previous)<=2.50001f);
   assertTrue(next>=Math.min(target,previous)&&next<=Math.max(target,previous));
   previous=next;
  }
  assertEquals(6,previous);
 }
 @Test void gentleMotionAndSteadyRunningKeepTheirOriginalLift(){
  var s=new BendSmoother();assertEquals(6,s.sample(6,2.5f));
  for(int i=1;i<=12;i++)assertEquals(6+i*2,s.sample(6+i*2,2.5f));
  for(int i=0;i<100;i++)s.sample(36.4f,2.5f);
  assertEquals(36.4f,s.sample(36.4f,2.5f));
 }
 @Test void aNewMountAndWorldResetUseTheCurrentPostureImmediately(){
  var s=new BendSmoother();assertEquals(18,s.sample(18,2.5f));s.sample(65,2.5f);s.reset();
  assertEquals(6,s.sample(6,2.5f));
 }
}
