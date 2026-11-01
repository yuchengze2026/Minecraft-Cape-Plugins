package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackUrlTest {
 private final String hash="0123456789012345678901234567890123456789";
 @Test void versionedContentUsesDifferentClientCacheKeys(){
  assertEquals("http://localhost/capes.zip?capebridge="+hash,PackUrl.versioned("http://localhost/capes.zip",hash));
  assertNotEquals(PackUrl.versioned("http://localhost/capes.zip",hash),PackUrl.versioned("http://localhost/capes.zip","a".repeat(40)));
 }
 @Test void preservesQueriesFragmentsAndEmptyConfiguration(){
  assertEquals("https://host/pack.zip?key=keep&capebridge="+hash+"#fragment",PackUrl.versioned("https://host/pack.zip?key=keep#fragment",hash));
  assertEquals("",PackUrl.versioned("",hash));
 }
}
