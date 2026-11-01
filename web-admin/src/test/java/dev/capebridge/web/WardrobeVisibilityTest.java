package dev.capebridge.web;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class WardrobeVisibilityTest {
 @TempDir Path root;
 @Test void visibilityPersistsAcrossReplacementAndFailedRefreshRestoresTheCatalog()throws Exception{
  Path folder=Files.createDirectory(root.resolve("proxy"));Files.createDirectory(folder.resolve("capes"));Files.writeString(folder.resolve("capes.yml"),"capes: {}\n");
  var store=new SelfTest.FakeStore(folder);var owner=new SelfTest.FakeOwner(folder);
  var service=new CapeService(owner,folder,store,store.getClass().getDeclaredMethod("add",String.class,String.class,boolean.class),owner.getClass().getDeclaredMethod("reload"),root.resolve("web"),10);
  byte[] png=SelfTest.png(64,32,java.awt.Color.RED);service.install("sample","sample",png,false);assertTrue(service.list().get(0).wardrobeVisible());
  byte[] pack=Files.readAllBytes(folder.resolve("resource-pack.zip"));int builds=owner.reloads;
  service.wardrobe("sample",false);assertFalse(service.list().get(0).wardrobeVisible());assertEquals(builds,owner.reloads);assertArrayEquals(pack,Files.readAllBytes(folder.resolve("resource-pack.zip")));
  byte[] catalog=Files.readAllBytes(folder.resolve("capes.yml"));owner.failNext=true;assertThrows(Exception.class,()->service.wardrobe("sample",true));assertArrayEquals(catalog,Files.readAllBytes(folder.resolve("capes.yml")));
  service.install("sample","renamed",png,true);assertFalse(service.list().get(0).wardrobeVisible());service.wardrobe("sample",true);assertTrue(service.list().get(0).wardrobeVisible());
  assertThrows(IllegalArgumentException.class,()->service.wardrobe("../escape",false));assertThrows(IllegalArgumentException.class,()->service.wardrobe("missing",false));
 }
}
