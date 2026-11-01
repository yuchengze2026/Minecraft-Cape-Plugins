package dev.capebridge.backend.pose;

import org.junit.jupiter.api.Test;
import dev.capebridge.common.pose.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScaleReaderTest {
    @Test void readsFinalEffectiveValueWithoutReapplyingModifiers() {
        var modifiers=new ArrayList<>(List.of(new ScaleState.Modifier("test:scale",.5,"MULTIPLY_SCALAR_1")));
        var s=ScaleReader.measured(2,3,modifiers);modifiers.clear();
        assertEquals(2,s.base().value());assertEquals(3,s.effective().value());assertEquals(1,s.modifiers().value().size());
        assertEquals(Observation.Source.SERVER_API,s.effective().source());
    }
    @Test void invalidScaleDoesNotSilentlyBecomeOne() {
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){
            var s=ScaleReader.measured(1,bad,List.of());assertEquals(ScaleState.Support.UNAVAILABLE,s.support());assertNull(s.effective().value());
        }
    }
}
