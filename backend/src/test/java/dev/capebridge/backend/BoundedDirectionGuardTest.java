package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoundedDirectionGuardTest {
 @Test void stopsWithBoundedOffsetAndDoesNotOscillate(){
  var g=new BoundedDirectionGuard();g.sample(0,0);g.sample(30,45);g.sample(60,75);
  assertEquals(75,g.sample(60,60),.001);assertEquals(72,g.sample(60,60),.001);
  for(int i=0;i<20;i++)assertEquals(72,g.sample(60,60),.001);
 }
 @Test void reversalCanFollowBodyAndWrap(){
  var g=new BoundedDirectionGuard();g.sample(700,700);g.sample(710,725);g.sample(10,25);
  float before=g.sample(10,10),after=g.sample(0,-10);assertTrue(after<before);
 }
 @Test void isolatedMissingSamplesDoNotReverseEitherTurnDirection(){
  for(int direction:new int[]{-1,1}){
   var g=new BoundedDirectionGuard();float phase=0,output=g.sample(phase,phase);
   for(int i=1;i<200;i++){
    boolean gap=i%5==0;if(!gap)phase+=direction*36;
    float next=g.sample(phase,phase+(gap?18:36)*direction);
    assertTrue(ContinuousTurnLead.delta(next-output)*direction>=0,"Uninterrupted turn reversed at sample "+i);
    output=next;
   }
  }
 }
 @Test void realStopReleasesAfterOneGraceTickAtOriginalRate(){
  var g=new BoundedDirectionGuard();g.sample(0,0);g.sample(60,120);
  assertEquals(120,g.sample(60,60),.001);
  for(int i=1;i<=16;i++)assertEquals(120-3*i,g.sample(60,60),.001);
  assertEquals(72,g.sample(60,60),.001);
 }
 @Test void movingSampleClearsRestConfirmationAndResetClearsAllState(){
  var g=new BoundedDirectionGuard();g.sample(0,0);g.sample(30,60);
  assertEquals(60,g.sample(30,30),.001);
  assertEquals(90,g.sample(60,90),.001);
  assertEquals(90,g.sample(60,60),.001);
  assertEquals(87,g.sample(60,60),.001);
  g.reset();assertEquals(-700,g.sample(-700,-700),.001);
 }
 @Test void smallTurnsRemainExactAcrossPhaseBoundaries(){
  for(int direction:new int[]{-1,1}){
   var g=new BoundedDirectionGuard();
   for(int i=0;i<3000;i++){
    float phase=ContinuousTurnLead.delta(direction*i*4);
    assertEquals(0,ContinuousTurnLead.delta(g.sample(phase,phase)-phase),.001);
   }
  }
 }
 @Test void longSessionsRetainFineAnglesAndDirectedMotion(){
  for(int direction:new int[]{-1,1}){
   var g=new BoundedDirectionGuard();float phase=0,previous=0;
   for(int i=0;i<240000;i++){
    phase=(phase+direction*37.12345f)%720;
    float output=g.sample(phase,phase);
    assertTrue(Math.abs(output)<720);
    assertEquals(0,ContinuousTurnLead.delta(output-phase),.001,"Long session lost angle precision at "+i);
    if(i>0)assertTrue(ContinuousTurnLead.delta(output-previous)*direction>0);
    previous=output;
   }
  }
 }
}
