package dev.capebridge.web;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class WebFeaturesTest {
 @TempDir Path root;
 byte[] png(int height)throws Exception{var i=new BufferedImage(64,height,2);i.setRGB(1,1,0xff112233);var b=new ByteArrayOutputStream();ImageIO.write(i,"png",b);return b.toByteArray();}
 @Test void deletingPublishesRemovalAndFailureRestoresExactImageAndCatalog()throws Exception{
  Path folder=Files.createDirectory(root.resolve("proxy"));Files.createDirectory(folder.resolve("capes"));Files.writeString(folder.resolve("capes.yml"),"capes: {}\n");
  var store=new SelfTest.FakeStore(folder);var owner=new SelfTest.FakeOwner(folder);
  var service=new CapeService(owner,folder,store,store.getClass().getDeclaredMethod("add",String.class,String.class,boolean.class),owner.getClass().getDeclaredMethod("reload"),root.resolve("web"),10);
  service.install("sample","测试",png(32),false);
  byte[] image=Files.readAllBytes(folder.resolve("capes/sample.png")),catalog=Files.readAllBytes(folder.resolve("capes.yml"));
  owner.failNext=true;assertThrows(Exception.class,()->service.delete("sample"));
  assertArrayEquals(image,Files.readAllBytes(folder.resolve("capes/sample.png")));assertArrayEquals(catalog,Files.readAllBytes(folder.resolve("capes.yml")));
  service.delete("sample");assertFalse(Files.exists(folder.resolve("capes/sample.png")));assertTrue(service.list().isEmpty());
  assertThrows(IllegalArgumentException.class,()->service.delete("../private"));assertThrows(IllegalArgumentException.class,()->service.delete("sample"));
 }
 @Test void uploadValidatorKeepsAnimationFramesAndRejectsStripAsStatic()throws Exception{
  byte[] result=ImageRules.canonicalPng(png(96),true);assertEquals(96,ImageIO.read(new ByteArrayInputStream(result)).getHeight());
  assertThrows(IOException.class,()->ImageRules.canonicalPng(png(96)));
  assertThrows(IOException.class,()->ImageRules.canonicalPng(png(32),true));
 }
}
