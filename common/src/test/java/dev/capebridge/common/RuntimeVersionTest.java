package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeVersionTest {
 private final String secret="test-version-secret-with-at-least-32-characters";
 @Test void carriesActualVersionAndPlayerWithoutChangingStateProtocol()throws Exception{
  var version=new RuntimeVersion(UUID.randomUUID(),"0.6.5.2");
  assertEquals(version,RuntimeVersion.decode(version.encode(secret),secret));
  assertFalse(RuntimeVersion.matches(Wire.request()));
  assertFalse(RuntimeVersion.matches(new byte[0]));
 }
 @Test void rejectsForgedExpiredAndTruncatedVersions()throws Exception{
  byte[] bytes=new RuntimeVersion(UUID.randomUUID(),"0.6.5.2").encode(secret);
  assertThrows(IOException.class,()->RuntimeVersion.decode(bytes,secret,Clock.offset(Clock.systemUTC(),Duration.ofSeconds(31))));
  assertThrows(IOException.class,()->RuntimeVersion.decode(Arrays.copyOf(bytes,40),secret));
  bytes[30]^=1;assertThrows(IOException.class,()->RuntimeVersion.decode(bytes,secret));
 }
 @Test void rejectsUnboundedOrFormattedVersionStrings(){
  for(String value:List.of("","a".repeat(65),"&aSECRET","version\nline"))
   assertThrows(IOException.class,()->new RuntimeVersion(UUID.randomUUID(),value).encode(secret));
 }
}
