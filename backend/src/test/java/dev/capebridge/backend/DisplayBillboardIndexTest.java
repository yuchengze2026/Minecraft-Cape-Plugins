package dev.capebridge.backend;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DisplayBillboardIndexTest {
 @Test void resolvesOneChangedByteEvenWhenDefaultValueWasOmitted(){
  assertEquals(15,DisplayState.billboardIndex(Map.of(0,(byte)32,9,2),Map.of(0,(byte)32,9,2,15,(byte)1)));
 }
 @Test void refusesMissingOrAmbiguousMetadata(){
  assertThrows(IllegalStateException.class,()->DisplayState.billboardIndex(Map.of(),Map.of()));
  assertThrows(IllegalStateException.class,()->DisplayState.billboardIndex(Map.of(),Map.of(15,(byte)1,16,(byte)2)));
 }
}
