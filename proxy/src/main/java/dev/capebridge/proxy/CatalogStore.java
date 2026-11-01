package dev.capebridge.proxy;
import dev.capebridge.common.Wire;
import net.md_5.bungee.config.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

final class CatalogStore {
    private final Path folder;
    private final ConfigurationProvider yaml=ConfigurationProvider.getProvider(YamlConfiguration.class);
    private final Consumer<String> warning;
    CatalogStore(Path folder){this(folder,message->{});}
    CatalogStore(Path folder,Consumer<String> warning){this.folder=folder;this.warning=warning;}
    private boolean usableImage(Path image,String id){return usableImage(image,id,false);}
    private boolean usableImage(Path image,String id,boolean animated){
        try{PackBuilder.validate(SafeFiles.checked(folder,image),animated);return true;}
        catch(NoSuchFileException ex){warning.accept(Messages.t("log.image-missing","id",id,"file",image.getFileName()));}
        catch(IOException ex){warning.accept(Messages.t("log.image-invalid","id",id,"file",image.getFileName(),"error",Messages.error(ex)));}
        return false;
    }
    synchronized List<Wire.Cape> load()throws IOException {
        Path configPath=SafeFiles.checked(folder,folder.resolve("capes.yml"));
        Path backup=SafeFiles.checked(folder,folder.resolve("capes-before-0.5.8.yml"));
        if(!Files.exists(backup))Files.copy(configPath,backup);
        Configuration config=yaml.load(configPath.toFile());
        Configuration section=config.getSection("capes");
        int next=10001;
        for(String id:section.getKeys())next=Math.max(next,section.getInt(id+".model",0)+1);
        try(var files=Files.list(SafeFiles.checked(folder,folder.resolve("capes")))){for(Path file:files.filter(p->p.getFileName().toString().endsWith(".png")).sorted().toList()){
            String id=file.getFileName().toString().replaceFirst("\\.png$","");
            if(id.matches("[a-z0-9_-]{1,32}")&&!section.contains(id)){
                if(!usableImage(file,id))continue;
                section.set(id+".type","IMAGE");section.set(id+".name",id);section.set(id+".enabled",true);section.set(id+".permission","");section.set(id+".model",next++);
            }
        }}
        if(section.getKeys().size()>Wire.MAX_CAPES)throw new IOException("Maximum "+Wire.MAX_CAPES+" cape definitions");
        List<Wire.Cape> result=new ArrayList<>();Set<Integer> models=new HashSet<>();
        for(String id:section.getKeys()){
            if(!id.matches("[a-z0-9_-]{1,32}")){
                warning.accept(Messages.t("log.id-invalid","id",id));
                continue;
            }
            section.set(id+".permission",CapeAccess.node(id));
            if(!section.getBoolean(id+".enabled",true))continue;
            String type=section.getString(id+".type","IMAGE").toUpperCase(Locale.ROOT);
            boolean animated=type.equals("ANIMATED"),image=type.equals("IMAGE")||animated;if(!image&&!type.equals("BLOCK"))throw new IOException("Invalid cape type: "+id);
            int model=section.getInt(id+".model",0);
            if(image&&!usableImage(folder.resolve("capes").resolve(id+".png"),id,animated))continue;
            if(model<10001){model=next++;section.set(id+".model",model);}if(!models.add(model))throw new IOException("Duplicate model ID: "+model);
            try{result.add(new Wire.Cape(id,animated?"ANIMATED":image?"ITEM":"BLOCK",image?"PAPER":section.getString(id+".material","BLACK_CONCRETE").toUpperCase(Locale.ROOT),model,section.getString(id+".name",id),section.getString(id+".permission",""),true,section.getBoolean(id+".wardrobe-visible",true)));}
            catch(IllegalArgumentException ex){throw new IOException("Invalid cape configuration: "+id,ex);}
        }
        save(config);
        return List.copyOf(result);
    }
    synchronized void add(String id,String file,boolean block)throws IOException {
        if(!id.matches("[a-z0-9_-]{1,32}"))throw new IOException("Resource ID must be lowercase a-z, 0-9, _ or -");
        Configuration config=yaml.load(SafeFiles.checked(folder,folder.resolve("capes.yml")).toFile());
        if(block){if(!file.matches("[A-Z_]{1,48}"))throw new IOException("Invalid material");config.set("capes."+id+".material",file);}
        else {if(!file.matches("[a-zA-Z0-9_.-]+\\.png"))throw new IOException("PNG must be directly inside capes/");Path from=SafeFiles.checked(folder,folder.resolve("capes").resolve(file)),to=SafeFiles.checked(folder,folder.resolve("capes").resolve(id+".png"));PackBuilder.validate(from);if(!from.equals(to))Files.copy(from,to,StandardCopyOption.REPLACE_EXISTING);}
        config.set("capes."+id+".type",block?"BLOCK":"IMAGE");config.set("capes."+id+".enabled",true);
        if(!config.contains("capes."+id+".name"))config.set("capes."+id+".name",id);
        save(config);
    }
    private void save(Configuration config)throws IOException{
        Path temp=SafeFiles.temporary(folder,"catalog-");
        try{yaml.save(config,temp.toFile());move(temp,SafeFiles.checked(folder,folder.resolve("capes.yml")));}
        finally{Files.deleteIfExists(temp);}
    }
    synchronized void addAnimated(String id,String file,int frameTicks)throws IOException{
        addAnimated(id,file,frameTicks,List.of());
    }
    synchronized void addAnimated(String id,String file,int frameTicks,List<Integer> frameTimes)throws IOException{
        if(!id.matches("[a-z0-9_-]{1,32}")||!file.equals(id+".png"))throw new IOException("Invalid cape configuration: "+id);
        var texture=PackBuilder.validate(SafeFiles.checked(folder,folder.resolve("capes").resolve(file)),true);
        if(frameTicks<1||frameTicks>200)throw new IOException("cape.animation.timing");
        try{dev.capebridge.common.CapeTextures.animation(texture.getWidth(),texture.getHeight(),frameTicks,frameTimes);}catch(IllegalArgumentException bad){throw new IOException("cape.animation.timing",bad);}
        Configuration config=yaml.load(SafeFiles.checked(folder,folder.resolve("capes.yml")).toFile());
        config.set("capes."+id+".type","ANIMATED");config.set("capes."+id+".enabled",true);config.set("capes."+id+".animation.frame-time",frameTicks);
        config.set("capes."+id+".animation.frame-times",frameTimes==null||frameTimes.isEmpty()?null:List.copyOf(frameTimes));
        if(!config.contains("capes."+id+".name"))config.set("capes."+id+".name",id);
        save(config);
    }
    static void move(Path from,Path to)throws IOException {try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
}
