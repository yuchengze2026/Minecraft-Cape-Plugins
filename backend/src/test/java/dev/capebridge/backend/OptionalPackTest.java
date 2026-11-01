package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OptionalPackTest {
    @Test void refusalFailureAndRemovalDisableCapes(){for(String s:new String[]{"DECLINED","FAILED_DOWNLOAD","FAILED_RELOAD","INVALID_URL","DISCARDED"})assertTrue(OptionalPack.unavailable(s));}
    @Test void progressDoesNotPretendThatPackIsReadyOrRemoveLoadedPack(){for(String s:new String[]{"ACCEPTED","DOWNLOADED","SUCCESSFULLY_LOADED"})assertFalse(OptionalPack.unavailable(s));}
    @Test void bothWearerAndViewerMustHaveLoadedPack(){assertTrue(OptionalPack.visible(true,true));assertFalse(OptionalPack.visible(true,false));assertFalse(OptionalPack.visible(false,true));assertFalse(OptionalPack.visible(false,false));}
}
