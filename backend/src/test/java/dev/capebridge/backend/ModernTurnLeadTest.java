package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModernTurnLeadTest {
 @Test void versionProfileKeepsSmallTurnsExactAndHasBoundedExtraCompensation(){
  var legacy=new ContinuousTurnLead(60);var recent=new ContinuousTurnLead(90,1.25f);
  for(int i=0;i<40;i++){float phase=i*8;assertEquals(legacy.sample(phase),recent.sample(phase),.0001);}
  legacy.reset();recent.reset();float a=0,b=0,phase=0;
  for(int i=0;i<30;i++){phase=i*54;a=legacy.sample(phase);b=recent.sample(phase);}
  assertEquals(54,ContinuousTurnLead.delta(a-phase),.0001);assertEquals(67.5f,ContinuousTurnLead.delta(b-phase),.0001);
 }
 @Test void recentProfileRemainsDirectedAcrossWrapsAndReturnsToOriginalStopBound(){
  for(int sign:new int[]{-1,1}){
   var lead=new ContinuousTurnLead(90,1.25f);var guard=new BoundedDirectionGuard();float raw=0,shown=0;
   for(int i=0;i<200;i++){
    raw=ContinuousTurnLead.delta(sign*i*54);float next=guard.sample(raw,lead.sample(raw));
    if(i>0)assertTrue(ContinuousTurnLead.delta(next-shown)*sign>=0);shown=next;
   }
   for(int i=0;i<100;i++)shown=guard.sample(raw,lead.sample(raw));
   assertTrue(Math.abs(ContinuousTurnLead.delta(shown-raw))<=12.001);
  }
 }
}
