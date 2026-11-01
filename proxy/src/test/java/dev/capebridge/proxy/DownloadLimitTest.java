package dev.capebridge.proxy;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class DownloadLimitTest{
 @Test void addressTableIsBoundedAndExpiredSlotsCanBeReused()throws Exception{
  var limit=new DownloadLimit(2,2);var a=InetAddress.getByName("192.0.2.1");var b=InetAddress.getByName("192.0.2.2");var c=InetAddress.getByName("192.0.2.3");
  assertTrue(limit.allow(a,0));assertTrue(limit.allow(a,1));assertFalse(limit.allow(a,2));assertTrue(limit.allow(b,2));assertFalse(limit.allow(c,3));assertEquals(2,limit.size());
  assertTrue(limit.allow(c,TimeUnit.MINUTES.toNanos(1)+1));assertTrue(limit.size()<=2);
 }
}
