package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CadenceBlendTest {
 @Test void extraPredictionRampsAndReturnsWithoutAnAbruptCorrection(){
  var b=new CadenceBlend();float prev=b.sample(0,0,0,false);
  for(int i=0;i<12;i++){float out=b.sample(0,30,90,true);assertTrue(Math.abs(out-prev)<=18.0001);prev=out;}
  assertTrue(b.settled());assertEquals(90,prev,.01);
  for(int i=0;i<12;i++){float out=b.sample(0,0,0,false);assertTrue(Math.abs(out-prev)<=36.0001);prev=out;}
  assertTrue(b.settled());assertEquals(0,prev,.01);
 }
}
