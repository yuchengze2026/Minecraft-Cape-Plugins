package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MotionContinuityTest {
 @Test void continuousFastTurnsNeverReverseAtTheHeadFreedomBoundary(){
  for(float rate:new float[]{60,120,130,150,-130,-150}){
   float body=0,yaw=0;
   for(int tick=0;tick<2000;tick++){
    var next=TorsoHeading.advance(body,yaw,rate,0,0,false,1);
    if(tick>0)assertEquals(rate,next.rotation(),.001f,"A continuous turn must not change direction");
    body=next.body();yaw=CapePose.wrap(yaw+rate);
   }
  }
 }
 @Test void batchedLookPacketsRetainTheirArcAcrossMoreThanHalfATurn(){
  TurnPath path=new TurnPath();path.add(0,130);path.add(130,-100);
  assertArrayEquals(new float[]{130,130},path.take(0,-100));
  var next=TorsoHeading.advancePath(0,0,new float[]{130,130},0,0,false,1);assertEquals(210,next.rotation(),.001);
  assertEquals(0,path.size());
 }
 @Test void rejectedAndDiscontinuousPathsAreDiscardedWithoutReplay(){
  TurnPath path=new TurnPath();path.add(0,80);path.add(80,160);
  assertArrayEquals(new float[]{0},path.take(0,0));assertEquals(0,path.size());
  for(int i=0;i<1000;i++)path.add(i,i+1);assertEquals(64,path.size());
  assertArrayEquals(new float[]{CapePose.wrap(1000)},path.take(0,1000));assertEquals(0,path.size());
  assertArrayEquals(new float[]{0},path.take(1000,1000));
 }
 @Test void repeatedAnglesAndReversalsRetainTheirOrder(){
  TurnPath path=new TurnPath();float head=0;
  for(float delta:new float[]{120,120,120,-120,-120,-120,60}){float next=CapePose.wrap(head+delta);path.add(head,next);head=next;}
  float[] arc=path.take(0,head);assertArrayEquals(new float[]{120,120,120,-120,-120,-120,60},arc);
  var turn=TorsoHeading.advancePath(0,0,arc,0,0,false,1);
  assertEquals(50,turn.body(),.001);assertTrue(turn.travel()>360);
  assertEquals(0,path.size());assertArrayEquals(new float[]{0},path.take(head,head));
 }
 @Test void prolongedLargeTurnsStopImmediatelyAndReverseWithoutOldRotation(){
  float head=0,body=0;
  for(int tick=0;tick<200_000;tick++){
   float rate=(tick/2000)%2==0?170:-170;
   float[] deltas=tick%3==0?new float[]{rate,rate}:new float[]{rate};
   var turn=TorsoHeading.advancePath(body,head,deltas,0,0,false,1);
   for(float delta:deltas)head=CapePose.wrap(head+delta);
   body=turn.body();assertTrue(Math.abs(CapePose.wrap(head-body))<=50.001);
   if(tick%1000==999){
    var stop=TorsoHeading.advance(body,head,0,0,0,false,1);assertEquals(body,stop.body(),.001);assertEquals(0,stop.rotation(),.001);
    var reverse=TorsoHeading.advance(body,head,-rate,0,0,false,1);
    assertEquals(-Math.signum(rate),Math.signum(reverse.rotation()));
    body=reverse.body();head=CapePose.wrap(head-rate);
   }
  }
 }
 @Test void batchedFullTurnsDoNotChangeTheFinalHeadingWhenWorkIsLarge(){
  var turn=TorsoHeading.advance(0,0,810,0,0,false,1);
  assertEquals(40,turn.body(),.001);assertEquals(760,turn.rotation(),.001);
 }
 @Test void independentSmallHeadTurnsStillLeaveTheCapeStill(){
  float body=0,head=0;for(float next:new float[]{30,-30,15,-15,0}){var result=TorsoHeading.advance(body,head,CapePose.wrap(next-head),0,0,false,1);assertEquals(0,result.rotation(),.001);body=result.body();head=next;}
 }
 private void frame(CapeCloth cloth,float speed,float vertical){cloth.step(speed,vertical,0,false,false,1,65,0,1);}
 @Test void equalTravelRemainsSteadyWhenPositionPacketsArriveInPairs(){
  CapeCloth even=new CapeCloth(),bursty=new CapeCloth();
  for(int i=0;i<200_000;i++){
   frame(even,.3f,0);frame(bursty,i%2==0?.6f:0,i%2==0?.2f:-.2f);
   if(i>100){assertEquals(even.angle,bursty.angle,.001);assertEquals(39,bursty.angle,.001);assertEquals(0,bursty.side);}
  }
 }
 @Test void stoppingSettlesWithoutAnIdleOscillator(){
  CapeCloth cloth=new CapeCloth();for(int i=0;i<100;i++)frame(cloth,.3f,0);
  float previous=cloth.angle;for(int i=0;i<100;i++){frame(cloth,0,0);assertTrue(cloth.angle<=previous);previous=cloth.angle;}
  assertEquals(0,cloth.angle);for(int i=0;i<10000;i++){frame(cloth,0,0);assertEquals(0,cloth.angle);}
 }
 @Test void clockPhaseDriftDoesNotTurnEarlyPacketsIntoPeriodicBobbing(){
  CapeCloth cloth=new CapeCloth();float[] pattern={.2f,.4f,0,.2f,.2f,.2f};
  for(int i=0;i<10000;i++){frame(cloth,pattern[i%pattern.length],0);if(i>100)assertEquals(26,cloth.angle,.001);}
 }
}
