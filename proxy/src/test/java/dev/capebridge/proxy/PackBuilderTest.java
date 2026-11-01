package dev.capebridge.proxy;
import dev.capebridge.common.Wire;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
class PackBuilderTest {
    @TempDir Path folder;
    @Test void editableDescriptionIsEscapedAsJsonAndDoesNotChangeModels()throws Exception{
        var defaults=TextFiles.defaults("messages.yml");String label="管理服 \"披风\"\\\n中文";Messages.use(new dev.capebridge.common.TextCatalog(defaults.values(),Map.of("pack.description",label)));
        try{PackBuilder.build(folder,List.of(new Wire.Cape("black","BLOCK","BLACK_CONCRETE",10001)));try(ZipFile zip=new ZipFile(folder.resolve("resource-pack.zip").toFile())){String meta=new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertEquals(label,com.google.gson.JsonParser.parseString(meta).getAsJsonObject().getAsJsonObject("pack").get("description").getAsString());assertNotNull(zip.getEntry("assets/capebridge/models/item/black/attached.json"));}}
        finally{Messages.use(defaults);}
    }
    @Test void cameraClippingIsOwnerMarkedAndHasVersionSpecificShaderOverlays()throws Exception{
        PackBuilder.build(folder,List.of(new Wire.Cape("black","BLOCK","BLACK_CONCRETE",10001)));
        try(ZipFile zip=new ZipFile(folder.resolve("resource-pack.zip").toFile())){
            for(String path:List.of("assets/minecraft/shaders/core/rendertype_entity_translucent_cull.fsh","cape_1205/assets/minecraft/shaders/core/rendertype_entity_translucent_cull.fsh","cape_1212/assets/minecraft/shaders/core/entity.fsh")){
                String source=new String(zip.getInputStream(zip.getEntry(path)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(source.contains("capeBridgeOwn > 0.5")&&source.contains("< 2.7225"));
            }
            String self=new String(zip.getInputStream(zip.getEntry("assets/minecraft/items/leather_horse_armor.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            String other=new String(zip.getInputStream(zip.getEntry("assets/minecraft/items/paper.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(self.contains(Integer.toString(dev.capebridge.common.CapeMesh.SELF_COLOR)));assertFalse(other.contains("minecraft:constant"));
        }
    }
    @Test void unchangedPacksHaveStableHashesAcrossRebuilds()throws Exception{
        var capes=List.of(new Wire.Cape("black","BLOCK","BLACK_CONCRETE",10001));
        String first=PackBuilder.build(folder,capes);byte[] bytes=Files.readAllBytes(folder.resolve("resource-pack.zip"));
        try(ZipFile zip=new ZipFile(folder.resolve("resource-pack.zip").toFile())){
            assertTrue(zip.stream().allMatch(e->e.getTimeLocal().equals(java.time.LocalDateTime.of(1980,1,1,0,0))));
        }
        assertEquals(first,PackBuilder.build(folder,capes));assertArrayEquals(bytes,Files.readAllBytes(folder.resolve("resource-pack.zip")));
    }
    @Test void packIncludesBothModelFormatsAndTexture() throws Exception {
        Files.createDirectories(folder.resolve("capes"));
        ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB), "png", folder.resolve("capes/test.png").toFile());
        String hash = PackBuilder.build(folder,List.of(new Wire.Cape("test","ITEM","PAPER",10001)));
        assertTrue(hash.matches("[a-f0-9]{40}"));
        try(ZipFile zip = new ZipFile(folder.resolve("resource-pack.zip").toFile())) {
            for(String entry:List.of("pack.mcmeta","assets/minecraft/models/item/paper.json","assets/minecraft/items/paper.json","assets/capebridge/models/item/test/cape.json","assets/capebridge/textures/item/test.png")) assertNotNull(zip.getEntry(entry),entry);
        }
    }
    @Test void rejectsWrongRatioAndNonPng() throws Exception {
        Path bad=folder.resolve("bad.png"); ImageIO.write(new BufferedImage(41,64,BufferedImage.TYPE_INT_RGB),"png",bad.toFile());
        assertThrows(java.io.IOException.class,()->PackBuilder.validate(bad));
        Files.writeString(bad,"not a PNG"); assertThrows(java.io.IOException.class,()->PackBuilder.validate(bad));
    }
    @Test void rejectsOversizedFile() throws Exception { Path p=folder.resolve("big.png"); Files.write(p,new byte[1024*1024+1]); assertThrows(java.io.IOException.class,()->PackBuilder.validate(p)); }
    @Test void clothStripsUseConsecutiveTextureBandsInBothModelFormats() throws Exception {
        Files.createDirectories(folder.resolve("capes"));ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",folder.resolve("capes/cloth.png").toFile());
        PackBuilder.build(folder,List.of(new Wire.Cape("cloth","ITEM","PAPER",10001)));
        try(ZipFile zip=new ZipFile(folder.resolve("resource-pack.zip").toFile())) {
            String legacy=new String(zip.getInputStream(zip.getEntry("assets/minecraft/models/item/paper.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            String modern=new String(zip.getInputStream(zip.getEntry("assets/minecraft/items/paper.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            for(int i=0;i<4;i++) {
                String id=Integer.toString(dev.capebridge.common.CapeMesh.model(10001,i));assertTrue(legacy.contains(id)&&modern.contains(id));
                String strip=new String(zip.getInputStream(zip.getEntry("assets/capebridge/models/item/cloth/strip_"+i+".json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(strip.contains("\"from\":[3,6,7.5]")&&strip.contains("\"to\":[13,10,8.5]"));
                assertTrue(strip.contains("\"uv\":[0.25,"+((1+i*4)/2d)+",2.75,"+((5+i*4)/2d)+"]"));
            }
        }
    }
    @Test void collidingMenuAndStripModelIdsFailBeforePublishing() throws Exception {
        Files.createDirectories(folder.resolve("capes"));
        for(String id:List.of("a","b"))ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",folder.resolve("capes/"+id+".png").toFile());
        assertThrows(java.io.IOException.class,()->PackBuilder.build(folder,List.of(new Wire.Cape("a","ITEM","PAPER",10001),new Wire.Cape("b","ITEM","PAPER",160017))));
    }
    @Test void officialAtlasKeepsSixFacesWithoutReplacingVanillaElytra()throws Exception{
        Files.createDirectories(folder.resolve("capes"));BufferedImage source=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1,1,0xffff0000);source.setRGB(12,1,0xff0000ff);source.setRGB(24,2,0xff00ff00);
        ImageIO.write(source,"png",folder.resolve("capes/all.png").toFile());
        PackBuilder.build(folder,List.of(new Wire.Cape("all","ITEM","PAPER",10001)));
        try(ZipFile zip=new ZipFile(folder.resolve("resource-pack.zip").toFile())){
            BufferedImage packed=ImageIO.read(zip.getInputStream(zip.getEntry("assets/capebridge/textures/item/all.png")));
            assertEquals(0xffff0000,packed.getRGB(1,1));assertEquals(0xff0000ff,packed.getRGB(12,1));assertEquals(0xff00ff00,packed.getRGB(24,2));
            String full=new String(zip.getInputStream(zip.getEntry("assets/capebridge/models/item/all/cape.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            for(String face:List.of("north","south","east","west","up","down"))assertTrue(full.contains("\""+face+"\""));
            String attached=new String(zip.getInputStream(zip.getEntry("assets/capebridge/models/item/all/attached.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(attached.contains("\"from\":[3,-8,8]")&&attached.contains("\"to\":[13,8,9]"));
            assertTrue(zip.stream().noneMatch(e->e.getName().contains("wing")||e.getName().contains("elytra")));
        }
    }
    @Test void acceptsScaledOfficialAtlasButRejectsFrontOnlyAndNonIntegerScale()throws Exception{
        Path image=folder.resolve("size.png");
        for(int width:new int[]{64,128,256,512}){ImageIO.write(new BufferedImage(width,width/2,BufferedImage.TYPE_INT_ARGB),"png",image.toFile());assertEquals(width,PackBuilder.validate(image).getWidth());}
        ImageIO.write(new BufferedImage(96,48,BufferedImage.TYPE_INT_ARGB),"png",image.toFile());assertThrows(java.io.IOException.class,()->PackBuilder.validate(image));
    }
    @Test void allSixSmoothModelProfilesAreRegisteredWithUnchangedSixFaceUv()throws Exception{
        Files.createDirectories(folder.resolve("capes"));ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB),"png",folder.resolve("capes/smooth.png").toFile());
        PackBuilder.build(folder,List.of(new Wire.Cape("smooth","ITEM","PAPER",10001)));
        try(ZipFile zip=new ZipFile(folder.resolve("resource-pack.zip").toFile())){
            String mappings=new String(zip.getInputStream(zip.getEntry("assets/minecraft/models/item/paper.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            for(int profile=0;profile<6;profile++){
                assertTrue(mappings.contains(Integer.toString(dev.capebridge.common.CapeMesh.smoothModel(10001,profile))));
                var entry=zip.getEntry("assets/capebridge/models/item/smooth/smooth_"+profile+".json");assertNotNull(entry);
                String json=new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                var from=java.util.regex.Pattern.compile("\"from\":\\[([^]]+)\\]").matcher(json);var to=java.util.regex.Pattern.compile("\"to\":\\[([^]]+)\\]").matcher(json);assertTrue(from.find()&&to.find());
                double[] a=java.util.Arrays.stream(from.group(1).split(",")).mapToDouble(Double::parseDouble).toArray(),b=java.util.Arrays.stream(to.group(1).split(",")).mapToDouble(Double::parseDouble).toArray();
                var origin=dev.capebridge.common.CapePivot.origin(profile);
                assertEquals(0,(b[1]-8)/16+origin.y(),.00000001,"Baked upper edge and runtime pivot must coincide");
                assertEquals(0,(b[2]-8)/16+origin.z(),.00000001,"Inner face must remain at the compensated shoulder pivot");
                assertEquals(16,b[1]-a[1],.00000001);assertEquals(1,b[2]-a[2],.00000001);
                for(String face:List.of("north","south","east","west","up","down"))assertTrue(json.contains("\""+face+"\""));
                assertTrue(json.contains("\"tintindex\":0"));
            }
        }
    }
}
