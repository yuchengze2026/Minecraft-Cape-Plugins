package dev.capebridge.proxy;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PackHostTest {
 private final byte[] pack="PK-test-resource-bytes-only".getBytes(StandardCharsets.UTF_8);
 private String raw(PackHost host,String request)throws Exception{
  try(Socket s=new Socket("127.0.0.1",host.port())){
   s.setSoTimeout(4000);s.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));s.getOutputStream().flush();
   return new String(s.getInputStream().readAllBytes(),StandardCharsets.ISO_8859_1);
  }
 }
 @Test void onlyExactRouteCanDownloadAndNoPathOrQueryBecomesAFile()throws Exception{
  try(var host=new PackHost("127.0.0.1",0)){
   host.publish(pack);
   for(String path:List.of("/","/config.yml","/assignments.properties","/../config.yml","/%2e%2e/config.yml","/capes.zip/../config.yml","/capes.zip/extra","/%63apes.zip","/cpae.zip","/capes.zip%00","/capes.zip%2f..%2fconfig.yml")){
    String response=raw(host,"GET "+path+" HTTP/1.1\r\nHost: localhost\r\n\r\n");
    assertTrue(response.startsWith("HTTP/1.1 404"),path+" "+response);
    assertFalse(response.contains("PK-test-resource"));
   }
   for(String query:List.of("?capebridge="+"a".repeat(40),"?file=../config.yml","?cmd=op%20attacker","?url=http://127.0.0.1/")){
    String response=raw(host,"GET /capes.zip"+query+" HTTP/1.1\r\nHost: localhost\r\n\r\n");
    assertTrue(response.startsWith("HTTP/1.1 200"));assertTrue(response.endsWith(new String(pack,StandardCharsets.UTF_8)));
   }
  }
 }
 @Test void methodsBodiesAndLargeUriAreRejectedWithoutExecutingAnything()throws Exception{
  try(var host=new PackHost("127.0.0.1",0)){
   host.publish(pack);
   for(String method:List.of("POST","PUT","DELETE","PATCH","OPTIONS","TRACE","CONNECT"))assertTrue(raw(host,method+" /capes.zip HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n").startsWith("HTTP/1.1 405"));
   assertTrue(raw(host,"GET /capes.zip HTTP/1.1\r\nHost: localhost\r\nContent-Length: 999999\r\n\r\n").startsWith("HTTP/1.1 400"));
   assertTrue(raw(host,"GET /capes.zip?"+"a".repeat(4100)+" HTTP/1.1\r\nHost: localhost\r\n\r\n").startsWith("HTTP/1.1 414"));
  }
 }
 @Test void headAndGetHaveSafeHeadersAndPublishCopiesItsInput()throws Exception{
  try(var host=new PackHost("127.0.0.1",0)){
   byte[] input=pack.clone();host.publish(input);input[0]=0;
   {
    var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    var uri=URI.create("http://127.0.0.1:"+host.port()+"/capes.zip");
    var get=client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200,get.statusCode());assertArrayEquals(pack,get.body());
    assertEquals("application/zip",get.headers().firstValue("Content-Type").orElseThrow());
    assertEquals("nosniff",get.headers().firstValue("X-Content-Type-Options").orElseThrow());
    var head=client.send(HttpRequest.newBuilder(uri).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200,head.statusCode());assertEquals(0,head.body().length);assertEquals(Integer.toString(pack.length),head.headers().firstValue("Content-Length").orElseThrow());
   }
  }
 }
 @Test void spoofedForwardedAddressesCannotEvadePerPeerLimit()throws Exception{
  try(var host=new PackHost("127.0.0.1",0,10)){
   host.publish(pack);
   for(int i=0;i<10;i++)assertTrue(raw(host,"GET /capes.zip HTTP/1.1\r\nHost: localhost\r\nX-Forwarded-For: 198.51.100."+i+"\r\n\r\n").startsWith("HTTP/1.1 200"));
   assertTrue(raw(host,"GET /capes.zip HTTP/1.1\r\nHost: localhost\r\nX-Forwarded-For: 203.0.113.1\r\n\r\n").startsWith("HTTP/1.1 429"));
  }
 }
}
