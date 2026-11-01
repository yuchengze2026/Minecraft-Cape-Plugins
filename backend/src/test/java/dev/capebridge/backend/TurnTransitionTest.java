package dev.capebridge.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TurnTransitionTest {
 @Test void smallTurnsKeepTheAccepted082Behavior(){
  var beforeLead=new HeadingLead(true,1,60);
  var beforeGuard=new BoundedDirectionGuard();
  var afterLead=new ContinuousTurnLead(60);
  var afterGuard=new BoundedDirectionGuard();
  float[] phases={0,4,8,12,8,4,0,-4,-8,-12,-8,-4,0,0,0};
  for(float phase:phases)assertEquals(beforeGuard.sample(phase,beforeLead.sample(phase)),afterGuard.sample(phase,afterLead.sample(phase)),.0001);
 }
 @Test void hoveringAroundActivationBoundaryDoesNotSwitchAWholeTickOfPrediction(){
  var lead=new ContinuousTurnLead(60);float phase=0;lead.sample(phase);float previousLead=0;
  for(int i=0;i<20;i++){
   phase+=i%2==0?14:16;
   float nextLead=lead.sample(phase)-phase;
   assertTrue(Math.abs(nextLead-previousLead)<8,"threshold compensation must not switch a complete tick of lead");previousLead=nextLead;
  }
 }
 @Test void brakingWhileTurningDoesNotFreezeTheCape(){
  var guard=new BoundedDirectionGuard();var lead=new ContinuousTurnLead(60);float phase=0;float previous=guard.sample(phase,lead.sample(phase));
  for(int i=0;i<15;i++){phase+=30;previous=guard.sample(phase,lead.sample(phase));}
  for(int i=1;i<=10;i++){
   phase+=2;float next=guard.sample(phase,lead.sample(phase));
   assertTrue(next>previous,"a moving torso must not leave the cape frozen");
   previous=next;
  }
 }
 @Test void fastTurnRemainsEngagedAcrossTheOldFifteenDegreeBoundary(){
  var lead=new ContinuousTurnLead(60);float phase=0;lead.sample(phase);
  for(int i=0;i<12;i++){phase+=20;lead.sample(phase);}
  for(float step:new float[]{16,14,16,14,16,14}){
   phase+=step;assertTrue(lead.sample(phase)-phase>10,"small speed changes must not disable established compensation");
  }
 }
 @Test void fastTurnsStillReachTheOriginalPredictionAndResetOnTeleport(){
  var lead=new ContinuousTurnLead(60);float phase=0;lead.sample(phase);
  for(int i=0;i<30;i++){phase+=54;lead.sample(phase);}
  phase+=54;assertEquals(phase+54,lead.sample(phase),.001);
  lead.reset();assertEquals(-100,lead.sample(-100),.001);
 }
 @Test void phaseWrappingDoesNotCreateAHalfTurnJump(){
  var lead=new ContinuousTurnLead(60);lead.sample(710);
  assertEquals(4,lead.sample(4),.0001);assertEquals(18,lead.sample(18),.0001);
 }
 @Test void fastTurnsAndIsolatedTimingGapsRetainTheOriginalAlgorithm(){
  var before=new HeadingLead(true,1,60,false);var after=new ContinuousTurnLead(60);float phase=0;
  assertEquals(before.sample(phase),after.sample(phase));
  for(float movement:new float[]{54,54,54,52,56,0,54,54,14,54,54,36,54,54}){
   phase+=movement;assertEquals(before.sample(phase),after.sample(phase),.001);
  }
 }
}
