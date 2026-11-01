package dev.capebridge.proxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
class AnimatedPackTest {
 @Test void frameTimingsSurviveCatalogReloadAndBecomeNativeResourcePackMetadata()throws Exception{
  Files.createDirectory(root.resolve("capes"));Files.writeString(root.resolve("capes.yml"),"capes: {}\n");var image=new BufferedImage(64,96,2);image.setRGB(1,1,0xffff0000);ImageIO.write(image,"png",root.resolve("capes/gif.png").toFile());
  var store=new CatalogStore(root);store.addAnimated("gif","gif.png",2,java.util.List.of(2,4,1));PackBuilder.build(root,store.load());
  try(var zip=new ZipFile(root.resolve("resource-pack.zip").toFile())){String meta=new String(zip.getInputStream(zip.getEntry("assets/capebridge/textures/item/gif.png.mcmeta")).readAllBytes());var times=com.google.gson.JsonParser.parseString(meta).getAsJsonObject().getAsJsonObject("animation").getAsJsonArray("frames");assertEquals(3,times.size());assertEquals(4,times.get(1).getAsJsonObject().get("time").getAsInt());}
  store.addAnimated("gif","gif.png",3);var config=net.md_5.bungee.config.ConfigurationProvider.getProvider(net.md_5.bungee.config.YamlConfiguration.class).load(root.resolve("capes.yml").toFile());assertTrue(config.getIntList("capes.gif.animation.frame-times").isEmpty());
 }
 @TempDir Path root;
 @Test void animationUsesOneCapeModelAndExplicitRectangularClientFrames()throws Exception{
  Files.createDirectory(root.resolve("capes"));Files.writeString(root.resolve("capes.yml"),"capes: {}\n");
  var image=new BufferedImage(64,96,2);image.setRGB(1,1,0xffff0000);image.setRGB(1,33,0xff00ff00);image.setRGB(1,65,0xff0000ff);
  ImageIO.write(image,"png",root.resolve("capes/fire.png").toFile());
  var store=new CatalogStore(root);store.addAnimated("fire","fire.png",4);var capes=store.load();
  assertEquals(1,capes.size());assertEquals("ANIMATED",capes.get(0).kind());int model=capes.get(0).model();
  String hash=PackBuilder.build(root,capes);
  try(var zip=new ZipFile(root.resolve("resource-pack.zip").toFile())){
   var metadata=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/capebridge/textures/item/fire.png.mcmeta")).readAllBytes())).getAsJsonObject().getAsJsonObject("animation");
   assertEquals(64,metadata.get("width").getAsInt());assertEquals(32,metadata.get("height").getAsInt());assertEquals(4,metadata.get("frametime").getAsInt());
   var texture=ImageIO.read(zip.getInputStream(zip.getEntry("assets/capebridge/textures/item/fire.png")));assertEquals(0xff00ff00,texture.getRGB(1,33));
   assertNotNull(zip.getEntry("assets/capebridge/models/item/fire/smooth_0.json"));
  }
  assertEquals(hash,PackBuilder.build(root,store.load()));
  store.addAnimated("fire","fire.png",2);assertEquals(model,store.load().get(0).model());assertNotEquals(hash,PackBuilder.build(root,store.load()));
 }
}
