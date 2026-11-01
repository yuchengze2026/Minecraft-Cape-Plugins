package dev.capebridge.proxy;

import dev.capebridge.common.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Full official cape atlas; no front-only crop or invented elytra coordinates. */
public final class PackBuilder {
    private PackBuilder() {}
    static BufferedImage validate(Path path) throws IOException {
        return validate(path,false);
    }
    static BufferedImage validate(Path path,boolean animated)throws IOException{return CapeTextures.read(SafeFiles.readImage(path),animated);}
    private static BufferedImage solid(int color) {
        BufferedImage image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<64;x++)image.setRGB(x,y,color);
        return image;
    }
    private static int blockColor(String material) {
        String[] names={"WHITE","ORANGE","MAGENTA","LIGHT_BLUE","YELLOW","LIME","PINK","GRAY","LIGHT_GRAY","CYAN","PURPLE","BLUE","BROWN","GREEN","RED","BLACK"};
        int[] colors={0xffffff,0xf9801d,0xc74ebd,0x3ab3da,0xfed83d,0x80c71f,0xf38baa,0x474f52,0x9d9d97,0x169c9c,0x8932b8,0x3c44aa,0x835432,0x5e7c16,0xb02e26,0x000000};
        for(int i=0;i<names.length;i++)if(material.equals(names[i]+"_CONCRETE")||material.equals(names[i]+"_WOOL"))return 0xff000000|colors[i];
        return 0xff777781;
    }
    static String build(Path folder,Collection<Wire.Cape> capes)throws Exception {
        Path output=SafeFiles.checked(folder,folder.resolve("resource-pack.zip"));
        Path temp=SafeFiles.temporary(folder,"pack-");
        Path settings=SafeFiles.checked(folder,folder.resolve("capes.yml"));
        var configuration=Files.exists(settings)?net.md_5.bungee.config.ConfigurationProvider.getProvider(net.md_5.bungee.config.YamlConfiguration.class)
            .load(settings.toFile()):new net.md_5.bungee.config.Configuration();
        try {
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(temp))) {
            com.google.gson.JsonObject meta=com.google.gson.JsonParser.parseString(resource("/compat/pack.mcmeta")).getAsJsonObject();meta.getAsJsonObject("pack").addProperty("description",Messages.raw("pack.description"));put(zip,"pack.mcmeta",meta.toString());
            put(zip,"CAPEBRIDGE-COMPATIBILITY.json",resource("/compat/provenance.json"));
            cameraShaders(zip);
            SortedMap<Integer,String> models=new TreeMap<>();
            for(Wire.Cape cape:capes.stream().sorted(Comparator.comparingInt(Wire.Cape::model)).toList()) {
                boolean animated=cape.kind().equals("ANIMATED");
                BufferedImage texture=!cape.kind().equals("BLOCK")?validate(SafeFiles.checked(folder,folder.resolve("capes").resolve(cape.id()+".png")),animated):solid(blockColor(cape.material()));
                String atlas="capebridge:item/"+cape.id();
                putImage(zip,"assets/capebridge/textures/item/"+cape.id()+".png",texture);
                if(animated)put(zip,"assets/capebridge/textures/item/"+cape.id()+".png.mcmeta",CapeTextures.animation(texture.getWidth(),texture.getHeight(),configuration.getInt("capes."+cape.id()+".animation.frame-time",2),configuration.getIntList("capes."+cape.id()+".animation.frame-times")));
                String full="capebridge:item/"+cape.id()+"/cape";
                register(models,cape.model(),full);put(zip,"assets/capebridge/models/item/"+cape.id()+"/cape.json",cape(atlas,-1));
                String attached="capebridge:item/"+cape.id()+"/attached";
                register(models,CapeMesh.attachedModel(cape.model()),attached);
                // Item display origin is (8,8,8). Put the inner upper edge exactly there.
                put(zip,"assets/capebridge/models/item/"+cape.id()+"/attached.json",model(atlas,"[3,-8,8]","[13,8,9]",CapeAtlas.cape(-1)).replace("\"texture\":\"#cloth\"","\"texture\":\"#cloth\",\"tintindex\":0"));
                for(int profile=0;profile<6;profile++){
                    CapePivot.Origin origin=CapePivot.origin(profile);
                    String name="capebridge:item/"+cape.id()+"/smooth_"+profile;
                    register(models,CapeMesh.smoothModel(cape.model(),profile),name);
                    String from=String.format(Locale.ROOT,"[3,%.9f,%.9f]",-8-origin.y()*16,7-origin.z()*16);
                    String to=String.format(Locale.ROOT,"[13,%.9f,%.9f]",8-origin.y()*16,8-origin.z()*16);
                    put(zip,"assets/capebridge/models/item/"+cape.id()+"/smooth_"+profile+".json",model(atlas,from,to,CapeAtlas.cape(-1)).replace("\"texture\":\"#cloth\"","\"texture\":\"#cloth\",\"tintindex\":0"));
                }
                for(int i=0;i<CapeMesh.PARTS;i++) {
                    String model="capebridge:item/"+cape.id()+"/strip_"+i;
                    register(models,CapeMesh.model(cape.model(),i),model);
                    put(zip,"assets/capebridge/models/item/"+cape.id()+"/strip_"+i+".json",cape(atlas,i));
                }
            }
            StringJoiner legacy=new StringJoiner(","),modern=new StringJoiner(","),selfModern=new StringJoiner(",");
            for(var e:models.entrySet()) {
                legacy.add("{\"predicate\":{\"custom_model_data\":"+e.getKey()+"},\"model\":\""+e.getValue()+"\"}");
                modern.add("{\"threshold\":"+e.getKey()+",\"model\":{\"type\":\"minecraft:model\",\"model\":\""+e.getValue()+"\"}}");
                selfModern.add("{\"threshold\":"+e.getKey()+",\"model\":{\"type\":\"minecraft:model\",\"model\":\""+e.getValue()+"\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":"+CapeMesh.SELF_COLOR+"}]}}");
            }
            for(String layer:resource("/compat/layers.txt").lines().toList()){
                String prefix=layer.equals("root")?"":layer+"/";
                for(String item:List.of("paper","leather_horse_armor")){
                    put(zip,prefix+"assets/minecraft/models/item/"+item+".json",resource("/compat/"+layer+"/"+item+"-model.json").replace("\"__CAPE_MODELS__\"","["+legacy+"]"));
                    put(zip,prefix+"assets/minecraft/items/"+item+".json",resource("/compat/"+layer+"/"+item+"-item.json").replace("\"__CAPE_ENTRIES__\"","["+(item.equals("paper")?modern:selfModern)+"]"));
                }
            }
        }
        CatalogStore.move(temp,output);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(output)));
        }finally{Files.deleteIfExists(temp);}
    }
    private static String cape(String texture,int part) {
        return model(texture,"[3,"+(part<0?0:6)+",7.5]","[13,"+(part<0?16:10)+",8.5]",CapeAtlas.cape(part));
    }
    private static String model(String texture,String from,String to,Map<String,int[]> faces) {
        StringJoiner json=new StringJoiner(",");
        for(var e:faces.entrySet()) {
            int[] u=e.getValue();
            json.add("\""+e.getKey()+"\":{\"uv\":["+(u[0]/4d)+","+(u[1]/2d)+","+(u[2]/4d)+","+(u[3]/2d)+"],\"texture\":\"#cloth\"}");
        }
        return "{\"display\":{\"gui\":{\"rotation\":[10,155,0],\"translation\":[0,0,0],\"scale\":[0.9,0.9,0.9]}},\"textures\":{\"cloth\":\""+texture+"\",\"particle\":\""+texture+"\"},\"elements\":[{\"from\":"+from+",\"to\":"+to+",\"faces\":{"+json+"}}]}";
    }
    private static void register(Map<Integer,String> models,int id,String model)throws IOException {
        if(models.putIfAbsent(id,model)!=null)throw new IOException("CustomModelData collision: "+id+"; choose another base model ID");
    }
    private static void putImage(ZipOutputStream zip,String name,BufferedImage image)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        zip.putNextEntry(entry(name));zip.write(bytes.toByteArray());zip.closeEntry();
    }
    private static void put(ZipOutputStream zip,String name,String value)throws IOException {
        zip.putNextEntry(entry(name));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    private static ZipEntry entry(String name){ZipEntry e=new ZipEntry(name);e.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));return e;}
    private static String resource(String name)throws IOException{
        try(InputStream in=Objects.requireNonNull(PackBuilder.class.getResourceAsStream(name),name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}
    }
    private static void cameraShaders(ZipOutputStream zip)throws IOException{
        try(InputStream index=Objects.requireNonNull(PackBuilder.class.getResourceAsStream("/camera/index.txt"))){
            for(String path:new String(index.readAllBytes(),StandardCharsets.UTF_8).lines().toList()){
                try(InputStream source=Objects.requireNonNull(PackBuilder.class.getResourceAsStream("/camera/"+path))){put(zip,path,new String(source.readAllBytes(),StandardCharsets.UTF_8));}
            }
        }
        try(InputStream notice=Objects.requireNonNull(PackBuilder.class.getResourceAsStream("/camera/NOTICE.txt"))){put(zip,"CAMERA-SHADERS-NOTICE.txt",new String(notice.readAllBytes(),StandardCharsets.UTF_8));}
    }
}
