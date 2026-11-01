package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeadingSmootherTest {
 @Test void ordinaryTurnIsUnchangedAndResetClearsHistory(){
  var s=new HeadingSmoother();assertEquals(0,s.sample(0,0,false));assertEquals(5,s.sample(5,5,false));
  s.reset();assertEquals(270,s.sample(270,270,false));
 }
 @Test void sustainedBurstMotionIsSmoothedAndEventuallySettles(){
  var s=new HeadingSmoother();s.sample(0,0,false);s.sample(50,68,true);
  float a=s.sample(120,138,true),b=s.sample(150,168,true);
  assertTrue(a>68&&a<=138);assertTrue(b>a);assertTrue(Math.abs(b-168)<25);
  float stopped=0;for(int i=0;i<80;i++)stopped=s.sample(150,150,false);
  assertEquals(150,stopped,.01);
 }
}
