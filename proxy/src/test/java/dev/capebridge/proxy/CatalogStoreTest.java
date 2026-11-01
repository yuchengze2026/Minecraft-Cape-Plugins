package dev.capebridge.proxy;
import dev.capebridge.common.Wire;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CatalogStoreTest{
 @TempDir Path folder;
 @Test void publicByDefaultAndExplicitHiddenSettingSurvivesReload()throws Exception{
  Files.createDirectories(folder.resolve("capes"));Files.writeString(folder.resolve("capes.yml"),"capes:\n  public:\n    type: BLOCK\n  hidden:\n    type: BLOCK\n    wardrobe-visible: false\n");
  var store=new CatalogStore(folder);for(int i=0;i<2;i++){var capes=store.load();assertTrue(capes.stream().filter(c->c.id().equals("public")).findFirst().orElseThrow().wardrobeVisible());assertFalse(capes.stream().filter(c->c.id().equals("hidden")).findFirst().orElseThrow().wardrobeVisible());}
 }
 @Test void invalidIdIsPreservedWithoutBlockingOtherCapes()throws Exception{
  Files.createDirectories(folder.resolve("capes"));
  Files.writeString(folder.resolve("capes.yml"),"capes:\n  white:\n    type: BLOCK\n  Moon:\n    type: IMAGE\n    model: 10018\n    permission: custom.moon\n");
  List<String> warnings=new ArrayList<>();
  assertEquals(List.of("white"),new CatalogStore(folder,warnings::add).load().stream().map(Wire.Cape::id).toList());
  assertEquals(1,warnings.size());assertTrue(warnings.get(0).contains("Moon"));
  String saved=Files.readString(folder.resolve("capes.yml"));
  assertTrue(saved.contains("Moon:")&&saved.contains("model: 10018")&&saved.contains("custom.moon"));
 }
 @Test void newPngImportsWithStableModelAndConfiguredPermission()throws Exception{
  Files.createDirectories(folder.resolve("capes"));Files.writeString(folder.resolve("capes.yml"),"capes:\n  black:\n    type: BLOCK\n    name: 黑色\n    material: BLACK_CONCRETE\n    permission: capebridge.vip\n");
  ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",folder.resolve("capes/aurora.png").toFile());
  CatalogStore store=new CatalogStore(folder);List<Wire.Cape> first=store.load(),second=store.load();assertEquals(first,second);assertEquals(10001,first.stream().filter(c->c.id().equals("aurora")).findFirst().orElseThrow().model());assertEquals("capebridge.cape.black",first.stream().filter(c->c.id().equals("black")).findFirst().orElseThrow().permission());
 }
 @Test void disabledCapeIsExcludedAndInvalidTitleRejected()throws Exception{
  Files.createDirectories(folder.resolve("capes"));Files.writeString(folder.resolve("capes.yml"),"capes:\n  hidden:\n    enabled: false\n    type: BLOCK\n");assertTrue(new CatalogStore(folder).load().isEmpty());
  Files.writeString(folder.resolve("capes.yml"),"capes:\n  broken:\n    type: BLOCK\n    name: '"+"x".repeat(21)+"'\n");assertThrows(java.io.IOException.class,()->new CatalogStore(folder).load());
 }
 private void missingImageConfig()throws Exception{
  Files.createDirectories(folder.resolve("capes"));
  Files.writeString(folder.resolve("capes.yml"),"capes:\n  black:\n    type: BLOCK\n    material: BLACK_CONCRETE\n  aurora:\n    enabled: true\n    type: IMAGE\n    name: 极光\n    model: 10017\n");
 }
 @Test void missingConfiguredPngDoesNotPreventCatalogOrPackFromLoading()throws Exception{
  missingImageConfig();List<String> warnings=new ArrayList<>();CatalogStore store=new CatalogStore(folder,warnings::add);
  List<Wire.Cape> capes=store.load();assertEquals(List.of("black"),capes.stream().map(Wire.Cape::id).toList());
  assertEquals(1,warnings.size());assertTrue(warnings.get(0).contains("aurora.png")&&warnings.get(0).contains("bcape reload"));
  assertNotNull(PackBuilder.build(folder,capes));assertTrue(Files.isRegularFile(folder.resolve("resource-pack.zip")));
  String yaml=Files.readString(folder.resolve("capes.yml"));assertTrue(yaml.contains("enabled: true")&&yaml.contains("model: 10017"));
 }
 @Test void restoredPngReappearsWithOriginalIdAndModelWithoutEditingConfig()throws Exception{
  missingImageConfig();CatalogStore store=new CatalogStore(folder);assertEquals(1,store.load().size());
  ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",folder.resolve("capes/aurora.png").toFile());
  Wire.Cape restored=store.load().stream().filter(c->c.id().equals("aurora")).findFirst().orElseThrow();
  assertEquals(10017,restored.model());assertEquals("极光",restored.title());assertNotNull(PackBuilder.build(folder,store.load()));
 }
 @Test void brokenConfiguredAndUnregisteredPngsDoNotBlockValidAutoImport()throws Exception{
  missingImageConfig();Files.writeString(folder.resolve("capes/aurora.png"),"corrupt PNG");Files.writeString(folder.resolve("capes/broken.png"),"corrupt PNG");
  ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",folder.resolve("capes/valid.png").toFile());
  List<String> warnings=new ArrayList<>();List<Wire.Cape> capes=new CatalogStore(folder,warnings::add).load();
  assertEquals(Set.of("black","valid"),new HashSet<>(capes.stream().map(Wire.Cape::id).toList()));assertEquals(2,warnings.size());
  assertNotNull(PackBuilder.build(folder,capes));assertFalse(Files.readString(folder.resolve("capes.yml")).contains("broken:"));
 }
 @Test void catalogWithOnlyMissingImagesStillBuildsAnEmptyFallbackPack()throws Exception{
  Files.createDirectories(folder.resolve("capes"));Files.writeString(folder.resolve("capes.yml"),"capes:\n  aurora:\n    type: IMAGE\n    model: 10001\n");
  List<Wire.Cape> capes=new CatalogStore(folder).load();assertTrue(capes.isEmpty());assertNotNull(PackBuilder.build(folder,capes));
 }
}
