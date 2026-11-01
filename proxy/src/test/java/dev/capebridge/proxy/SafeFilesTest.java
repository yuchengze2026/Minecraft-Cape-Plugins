package dev.capebridge.proxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import dev.capebridge.common.Wire;
import static org.junit.jupiter.api.Assertions.*;
class SafeFilesTest{
 @TempDir Path root;
 @Test void traversalAndDirectoriesAreRejected()throws Exception{
  assertThrows(IOException.class,()->SafeFiles.checked(root,root.resolve("../secret.png")));
  Path dir=Files.createDirectory(root.resolve("directory.png"));assertThrows(IOException.class,()->PackBuilder.validate(dir));
 }
 @Test void imageIsBoundedBeforeReadingOrDecoding()throws Exception{
  Path file=root.resolve("oversize.png");Files.write(file,new byte[1024*1024+1]);assertThrows(IOException.class,()->PackBuilder.validate(file));
 }
 @Test void linkedImageCannotEscapeOrEnterThePublishedZip()throws Exception{
  Path outside=Files.createDirectory(root.resolve("outside")),plugin=Files.createDirectory(root.resolve("plugin")),capes=Files.createDirectory(plugin.resolve("capes"));
  Path secret=outside.resolve("private.png");ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB),"png",secret.toFile());
  Files.createSymbolicLink(capes.resolve("leak.png"),secret);
  assertThrows(IOException.class,()->SafeFiles.checked(plugin,capes.resolve("leak.png")));
  assertThrows(IOException.class,()->PackBuilder.validate(capes.resolve("leak.png")));
  assertThrows(IOException.class,()->PackBuilder.build(plugin,List.of(new Wire.Cape("leak","ITEM","PAPER",10001))));
 }
 @Test void predictableOldTemporaryFileIsNotOverwrittenAndPackContainsOnlyResources()throws Exception{
  Files.writeString(root.resolve("resource-pack.zip.tmp"),"private-canary");
  Files.writeString(root.resolve("config.yml"),"sync-secret: NEVER_PUBLISH_THIS_SECRET");
  PackBuilder.build(root,List.of(new Wire.Cape("white","BLOCK","WHITE_CONCRETE",10001)));
  assertEquals("private-canary",Files.readString(root.resolve("resource-pack.zip.tmp")));
  try(var z=new java.util.zip.ZipFile(root.resolve("resource-pack.zip").toFile())){
   for(var e:Collections.list(z.entries())){
    assertFalse(e.getName().contains(".."));assertFalse(e.getName().endsWith(".yml"));
    assertFalse(new String(z.getInputStream(e).readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1).contains("NEVER_PUBLISH_THIS_SECRET"));
   }
  }
 }
}
