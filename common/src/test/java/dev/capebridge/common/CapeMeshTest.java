package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CapeMeshTest {
    @Test void smoothProfilesStayInsideTheirOwnStableModelRange(){
        Set<Integer> models=new HashSet<>();
        for(int base=10001;base<10005;base++)for(int profile=0;profile<6;profile++)assertTrue(models.add(CapeMesh.smoothModel(base,profile)));
        assertEquals(160024,CapeMesh.smoothModel(10001,0));
        assertEquals(160029,CapeMesh.smoothModel(10001,5));
        assertThrows(IllegalArgumentException.class,()->CapeMesh.smoothModel(10001,6));
    }
    @Test void defaultModelRangeHasUniqueStableStripIds() {
        Set<Integer> ids=new HashSet<>();
        for(int base=10001;base<10129;base++)for(int part=0;part<CapeMesh.PARTS;part++)assertTrue(ids.add(CapeMesh.model(base,part)));
        assertEquals(160017,CapeMesh.model(10001,0));assertEquals(160020,CapeMesh.model(10001,3));
    }
    @Test void invalidAndOverflowingModelIdsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->CapeMesh.model(0,0));
        assertThrows(IllegalArgumentException.class,()->CapeMesh.model(10001,4));
        assertThrows(ArithmeticException.class,()->CapeMesh.model(Integer.MAX_VALUE,0));
    }
}
