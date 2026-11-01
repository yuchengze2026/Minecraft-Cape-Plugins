package dev.capebridge.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeadingLeadTest {
 @Test void onlyForecastsSustainedFastTurnsAndStopsImmediately(){
  var p=new HeadingLead();assertEquals(0,p.sample(0));assertEquals(40,p.sample(40));assertEquals(80,p.sample(80));assertEquals(160,p.sample(120));
  assertEquals(120,p.sample(120));assertEquals(80,p.sample(80));assertEquals(40,p.sample(40));assertEquals(-40,p.sample(0));
 }
 @Test void respectsPhaseWrapAndCapsLead(){
  var p=new HeadingLead();p.sample(690);p.sample(710);p.sample(10);assertEquals(50,p.sample(30));
  p.reset();p.sample(0);p.sample(100);p.sample(200);assertEquals(380,p.sample(300));
 }
 @Test void softCandidateLimitsLeadChangesWhenStopping(){
  var p=new HeadingLead(true);p.sample(0);p.sample(40);p.sample(80);assertEquals(138,p.sample(120));
  assertEquals(196,p.sample(160));assertEquals(178,p.sample(160));assertEquals(160,p.sample(160));
 }
 @Test void twoTickPredictionRemainsBoundedAndReleases(){
  var p=new HeadingLead(true,2,140);p.sample(0);p.sample(50);p.sample(100);
  assertEquals(168,p.sample(150));assertTrue(p.active());
  for(int i=0;i<12;i++)p.sample(150);assertFalse(p.active());assertEquals(150,p.sample(150));
 }
 @Test void cadenceStopsOnReversalAndSlowdown(){
  var p=new HeadingLead(true,2,140);p.sample(0);p.sample(50);p.sample(100);p.sample(150);assertTrue(p.consistent());
  p.sample(100);assertFalse(p.consistent());p.sample(100);assertFalse(p.consistent());
 }
 @Test void oneEmptyTickKeepsFastTurnButTwoEmptyTicksReleaseIt(){
  var p=new HeadingLead(true,1,80,true);p.sample(0);p.sample(54);p.sample(108);p.sample(162);assertTrue(p.consistent());
  float held=p.sample(162);assertTrue(p.consistent());assertEquals(180,held);
  p.sample(162);assertFalse(p.consistent());
 }
}
