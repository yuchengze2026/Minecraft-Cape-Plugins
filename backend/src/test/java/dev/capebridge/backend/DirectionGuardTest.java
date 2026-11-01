package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DirectionGuardTest {
 @Test void doesNotRetractUntilTheActualBodyReverses(){
  var g=new DirectionGuard();assertEquals(0,g.sample(0,0));assertEquals(40,g.sample(30,40));
  assertEquals(70,g.sample(60,70));assertEquals(70,g.sample(60,60));
  assertEquals(40,g.sample(50,40));
 }
 @Test void wrapsAndResets(){
  var g=new DirectionGuard();g.sample(700,710);assertEquals(730,g.sample(0,10));
  assertEquals(730,g.sample(0,0));g.reset();assertEquals(5,g.sample(5,5));
 }
}
