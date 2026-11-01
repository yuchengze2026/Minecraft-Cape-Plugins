package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class PackWireTest {
 @Test void authenticatedPackMessagesAndSnapshotRoundTrip()throws Exception{
  String key="k".repeat(40),hash="a".repeat(40);UUID player=UUID.randomUUID(),session=UUID.randomUUID(),attempt=UUID.randomUUID();
  var report=new Wire.PackReport(player,session,attempt,hash,PackStatus.LOADED,true);
  byte[] data=Wire.packReport(report,key);assertEquals(report,Wire.packReport(data,key));data[50]^=1;assertThrows(IOException.class,()->Wire.packReport(data,key));
  var retry=new Wire.PackRetry(player,session,attempt,hash);assertEquals(retry,Wire.packRetry(Wire.packRetry(retry,key),key));
  var state=new Wire.State(player,"",List.of(),"https://example.com/capes.zip",hash,"",new PackSnapshot(session,attempt,3,PackStatus.LOADED,false,true));
  assertEquals(state,Wire.decode(Wire.encode(state,key),key));
 }
}
