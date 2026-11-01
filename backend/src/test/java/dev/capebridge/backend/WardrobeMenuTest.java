package dev.capebridge.backend;
import dev.capebridge.common.Wire;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WardrobeMenuTest {
 @Test void bothMenusExcludeHiddenEntriesWhileKeepingAnAlreadyWornCapeInState(){
  var publicStatic=new Wire.Cape("public","ITEM","PAPER",10001);
  var hiddenStatic=new Wire.Cape("secret","ITEM","PAPER",10002).wardrobe(false);
  var publicAnimated=new Wire.Cape("fire","ANIMATED","PAPER",10003);
  var hiddenAnimated=new Wire.Cape("ghost","ANIMATED","PAPER",10004).wardrobe(false);
  var state=new Wire.State(UUID.randomUUID(),"secret",List.of(publicStatic,hiddenStatic,publicAnimated,hiddenAnimated),"","");
  assertEquals(List.of(publicStatic),CapeMenu.entries(state,false));assertEquals(List.of(publicAnimated),CapeMenu.entries(state,true));
  assertTrue(state.capes().stream().anyMatch(c->c.id().equals(state.selected())&&c.available()));
 }
}
