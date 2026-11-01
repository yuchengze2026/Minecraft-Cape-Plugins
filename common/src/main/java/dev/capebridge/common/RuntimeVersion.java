package dev.capebridge.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Optional authenticated advertisement; the existing v5 state layout is unchanged. */
public record RuntimeVersion(UUID player,String version) {
 public static boolean matches(byte[] bytes){return bytes.length>=2&&bytes[0]==Wire.VERSION&&bytes[1]==5;}
 public byte[] encode(String secret)throws IOException{
  validate(version);
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
  out.writeByte(Wire.VERSION);out.writeByte(5);out.writeLong(System.currentTimeMillis());
  out.writeLong(player.getMostSignificantBits());out.writeLong(player.getLeastSignificantBits());out.writeUTF(version);
  bytes.writeBytes(sign(bytes.toByteArray(),secret));return bytes.toByteArray();
 }
 public static RuntimeVersion decode(byte[] bytes,String secret)throws IOException{return decode(bytes,secret,Clock.systemUTC());}
 static RuntimeVersion decode(byte[] bytes,String secret,Clock clock)throws IOException{
  if(bytes.length<61||bytes.length>124||!matches(bytes))throw new IOException("Invalid version message");
  byte[] body=Arrays.copyOf(bytes,bytes.length-32);
  if(!MessageDigest.isEqual(sign(body,secret),Arrays.copyOfRange(bytes,body.length,bytes.length)))throw new IOException("Invalid version signature");
  var in=new DataInputStream(new ByteArrayInputStream(body));in.skipNBytes(2);long time=in.readLong();
  if(time<clock.millis()-30000||time>clock.millis()+5000)throw new IOException("Expired version message");
  UUID player=new UUID(in.readLong(),in.readLong());String version=in.readUTF();validate(version);
  if(in.available()!=0)throw new IOException("Trailing version data");return new RuntimeVersion(player,version);
 }
 private static void validate(String version)throws IOException{if(!version.matches("[a-zA-Z0-9._+\\-]{1,64}"))throw new IOException("Invalid version");}
 private static byte[] sign(byte[] body,String secret)throws IOException{
  if(secret.length()<32)throw new IOException("Missing sync secret");
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return mac.doFinal(body);}
  catch(Exception e){throw new IOException(e);}
 }
}
