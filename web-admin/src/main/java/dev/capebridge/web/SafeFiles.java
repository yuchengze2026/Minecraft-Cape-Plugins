package dev.capebridge.web;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

final class SafeFiles {
 private SafeFiles(){}
 static Path root(Path path) throws IOException {
  Path absolute=path.toAbsolutePath().normalize();
  for(Path p=absolute;p!=null;p=p.getParent())if(link(p))throw new IOException(WebMessages.raw("server.text-026"));
  Files.createDirectories(absolute);return absolute.toRealPath();
 }
 static Path inside(Path root,String child) throws IOException {
  Path target=root.resolve(child).normalize();if(!target.startsWith(root)||target.equals(root))throw new IOException(WebMessages.raw("server.text-027"));
  for(Path p=target;!p.equals(root);p=p.getParent())if(link(p))throw new IOException(WebMessages.raw("server.text-028"));
  return target;
 }
 private static boolean link(Path path) throws IOException {return Files.isSymbolicLink(path)||(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther());}
 static void atomic(Path target,byte[] bytes) throws IOException {
  Path temp=Files.createTempFile(target.getParent(),".cape-web-",".tmp");
  try{Files.write(temp,bytes);move(temp,target);}finally{Files.deleteIfExists(temp);}
 }
 static void move(Path source,Path target) throws IOException {
  try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
  catch(AtomicMoveNotSupportedException ignored){Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
 }
 static String hex(byte[] bytes,String algorithm){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));}
  catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 static String nonce(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 static boolean id(String id){return id!=null&&id.matches("[a-z0-9_-]{1,32}")&&!id.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]");}
 static void removeBackup(Path root,Path directory) throws IOException {
  if(!directory.getParent().equals(root)||!directory.getFileName().toString().startsWith("snapshot-"))throw new IOException(WebMessages.raw("server.text-029"));
  // walk does not follow symbolic links; every deleted entry stays in this owned backup directory.
  try(var paths=Files.walk(directory)){
   for(Path p:paths.sorted(Comparator.reverseOrder()).toList()){
    if(!p.toAbsolutePath().normalize().startsWith(root))throw new IOException(WebMessages.raw("server.text-030"));
    Files.deleteIfExists(p);
   }
  }
 }
}
