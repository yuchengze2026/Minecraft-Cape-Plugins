package dev.capebridge.web;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class CapeService {
 record Cape(String id,String name,boolean enabled,int model,String permission,boolean image,long modified,boolean animated,int frameTicks,List<Integer> frameTimes,boolean wardrobeVisible){}
 record Installed(String id,String name,int model,String permission,String packSha1,long packBytes,String message){}
 private final Object owner,store;
 private final Method add;
 private final Refresh reload;
 @FunctionalInterface interface Refresh {void run() throws Exception;}
 private final Path folder,backups;
 private final int backupLimit;
 private final ConfigurationProvider yaml=ConfigurationProvider.getProvider(YamlConfiguration.class);
 CapeService(Plugin owner,Path data,int limit) throws ReflectiveOperationException,IOException {
  this(owner,owner.getDataFolder().toPath(),field(owner,"store"),method(field(owner,"store").getClass(),"add",String.class,String.class,boolean.class),new CapePublisher(owner,field(owner,"store"),owner.getDataFolder().toPath())::refresh,data,limit);
 }
 CapeService(Object owner,Path folder,Object store,Method add,Method reload,Path data,int limit) throws IOException {
  this(owner,folder,store,add,()->reload.invoke(owner),data,limit);
 }
 CapeService(Object owner,Path folder,Object store,Method add,Refresh reload,Path data,int limit) throws IOException {
  this.owner=owner;this.store=Objects.requireNonNull(store);this.add=add;this.reload=reload;this.folder=SafeFiles.root(folder);this.backups=SafeFiles.root(data.resolve("backups"));backupLimit=Math.max(1,Math.min(50,limit));
  if(!Files.isRegularFile(SafeFiles.inside(this.folder,"capes.yml")))throw new IOException(WebMessages.raw("server.text-010"));
 }
 private static Object field(Object object,String name) throws ReflectiveOperationException {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 private static Method method(Class<?> type,String name,Class<?>...params) throws NoSuchMethodException {Method m=type.getDeclaredMethod(name,params);m.setAccessible(true);return m;}
 Path folder(){return folder;}
 private Configuration load() throws IOException {return yaml.load(SafeFiles.inside(folder,"capes.yml").toFile());}
 List<Cape> list() throws IOException {
  synchronized(owner){synchronized(store){var config=load().getSection("capes");List<Cape> result=new ArrayList<>();
   for(String id:config.getKeys())if(SafeFiles.id(id)){
    Path file=SafeFiles.inside(folder,"capes/"+id+".png");boolean present=Files.isRegularFile(file);
    result.add(new Cape(id,config.getString(id+".name",id),config.getBoolean(id+".enabled",true),config.getInt(id+".model",0),"capebridge.cape."+id,present,present?Files.getLastModifiedTime(file).toMillis():0,config.getString(id+".type","").equalsIgnoreCase("ANIMATED"),config.getInt(id+".animation.frame-time",2),config.getIntList(id+".animation.frame-times"),config.getBoolean(id+".wardrobe-visible",true)));
   }return result;
  }}
 }
 Map<String,Object> status() throws IOException {
  synchronized(owner){synchronized(store){Path zip=SafeFiles.inside(folder,"resource-pack.zip");
   Configuration settings=yaml.load(SafeFiles.inside(folder,"config.yml").toFile());
   Map<String,Object> out=new LinkedHashMap<>();out.put("version","1.0");out.put("capeCount",list().size());out.put("packBytes",Files.exists(zip)?Files.size(zip):0);
   out.put("publicUrl",settings.getString("resource-pack.public-url",""));out.put("embeddedHost",settings.getBoolean("resource-pack.embedded-host",true));return out;
  }}
 }
 byte[] image(String id) throws IOException {
  if(!SafeFiles.id(id))throw new IOException(WebMessages.raw("server.text-011"));Path path=SafeFiles.inside(folder,"capes/"+id+".png");
  if(!Files.isRegularFile(path)||Files.size(path)>10*1024*1024)throw new IOException(WebMessages.raw("server.text-012"));return Files.readAllBytes(path);
 }
 Installed install(String id,String name,byte[] bytes,boolean replace) throws Exception {
  return install(id,name,bytes,replace,false,2);
 }
 Installed install(String id,String name,byte[] bytes,boolean replace,boolean animated,int frameTicks) throws Exception {
  return install(id,name,bytes,replace,animated,frameTicks,List.of());
 }
 Installed install(String id,String name,byte[] bytes,boolean replace,boolean animated,int frameTicks,List<Integer> frameTimes) throws Exception {
  if(!SafeFiles.id(id))throw new IllegalArgumentException(WebMessages.raw("server.text-013"));
  if(name==null||name.isBlank()||name.length()>20||name.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw new IllegalArgumentException(WebMessages.raw("server.text-014"));
  if(frameTicks<1||frameTicks>200)throw new IllegalArgumentException(WebMessages.raw("server.animation.timing"));
  byte[] png=ImageRules.canonicalPng(bytes,animated);
  Method timed=null;
  if(frameTimes!=null&&!frameTimes.isEmpty()){
   if(!animated)throw new IllegalArgumentException(WebMessages.raw("server.animation.timing"));
   try(var image=new java.io.ByteArrayInputStream(png)){var decoded=javax.imageio.ImageIO.read(image);try{dev.capebridge.common.CapeTextures.animation(decoded.getWidth(),decoded.getHeight(),frameTicks,frameTimes);}catch(IllegalArgumentException bad){throw new IllegalArgumentException(WebMessages.raw("server.animation.timing"));}}
   try{timed=method(store.getClass(),"addAnimated",String.class,String.class,int.class,List.class);}catch(NoSuchMethodException old){throw new IllegalArgumentException(WebMessages.raw("server.gif.proxy-update"));}
  }
  synchronized(owner){synchronized(store){
   Path image=SafeFiles.inside(folder,"capes/"+id+".png"),catalog=SafeFiles.inside(folder,"capes.yml");
   Configuration config=load();boolean existed=Files.exists(image),defined=config.getSection("capes").getKeys().contains(id);
   if((existed||defined)&&!replace)throw new Conflict(WebMessages.raw("server.text-015"));
   Path backup=SafeFiles.inside(backups,"snapshot-"+System.currentTimeMillis()+"-"+id+"-"+UUID.randomUUID());Files.createDirectory(backup);
   Files.copy(catalog,backup.resolve("capes.yml"));if(existed)Files.copy(image,backup.resolve("previous.png"));
   Files.writeString(backup.resolve("info.txt"),"id="+id+"\npreviousImage="+existed+"\n",StandardCharsets.UTF_8);
   try{
    SafeFiles.atomic(image,png);
    if(timed!=null)timed.invoke(store,id,id+".png",frameTicks,List.copyOf(frameTimes));
    else if(animated)method(store.getClass(),"addAnimated",String.class,String.class,int.class).invoke(store,id,id+".png",frameTicks);
    else add.invoke(store,id,id+".png",false);
    Configuration updated=load();updated.set("capes."+id+".name",name);StringWriter text=new StringWriter();yaml.save(updated,text);SafeFiles.atomic(catalog,text.toString().getBytes(StandardCharsets.UTF_8));
    reload.run();
    Configuration finalConfig=load();Path zip=SafeFiles.inside(folder,"resource-pack.zip");
    if(!Files.isRegularFile(zip))throw new IOException(WebMessages.raw("server.text-016"));
    String hash=packHash(zip);trimBackups();
    return new Installed(id,name,finalConfig.getInt("capes."+id+".model",0),"capebridge.cape."+id,hash,Files.size(zip),WebMessages.raw("server.text-017"));
   }catch(Exception failure){
    try{SafeFiles.atomic(catalog,Files.readAllBytes(backup.resolve("capes.yml")));if(existed)SafeFiles.atomic(image,Files.readAllBytes(backup.resolve("previous.png")));else Files.deleteIfExists(image);reload.run();}catch(Exception rollback){failure.addSuppressed(rollback);}
    try{trimBackups();}catch(IOException cleanup){failure.addSuppressed(cleanup);}
    Throwable reason=failure;
    while(reason instanceof InvocationTargetException&&reason.getCause()!=null)reason=reason.getCause();
    if(reason!=failure)for(Throwable suppressed:failure.getSuppressed())reason.addSuppressed(suppressed);
    throw new IOException(WebMessages.raw("server.text-018"),reason);
   }
  }}
 }
 private static String packHash(Path path) throws IOException {
  try{var digest=java.security.MessageDigest.getInstance("SHA-1");try(InputStream in=Files.newInputStream(path)){byte[] block=new byte[16384];int n;while((n=in.read(block))>=0)digest.update(block,0,n);}return HexFormat.of().formatHex(digest.digest());}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 Map<String,Object> wardrobe(String id,boolean visible)throws Exception{
  if(!SafeFiles.id(id))throw new IllegalArgumentException(WebMessages.raw("server.text-013"));
  synchronized(owner){synchronized(store){
   Configuration config=load();if(!config.getSection("capes").getKeys().contains(id))throw new IllegalArgumentException(WebMessages.raw("server.delete.missing"));
   // Require a matching proxy so a menu setting never rebuilds or reoffers the pack.
   Method refresh;
   try{refresh=method(owner.getClass(),"refreshCatalog");}catch(NoSuchMethodException old){throw new IllegalArgumentException(WebMessages.raw("server.wardrobe.proxy-update"));}
   Path catalog=SafeFiles.inside(folder,"capes.yml");byte[] original=Files.readAllBytes(catalog);
   if(config.getBoolean("capes."+id+".wardrobe-visible",true)==visible)return Map.of("id",id,"wardrobeVisible",visible,"message",WebMessages.raw("server.wardrobe.saved"));
   Path backup=SafeFiles.inside(backups,"snapshot-"+System.currentTimeMillis()+"-"+id+"-"+UUID.randomUUID());Files.createDirectory(backup);Files.write(backup.resolve("capes.yml"),original);
   try{
    config.set("capes."+id+".wardrobe-visible",visible);StringWriter text=new StringWriter();yaml.save(config,text);SafeFiles.atomic(catalog,text.toString().getBytes(StandardCharsets.UTF_8));
    refresh.invoke(owner);trimBackups();
    return Map.of("id",id,"wardrobeVisible",visible,"message",WebMessages.raw("server.wardrobe.saved"));
   }catch(Exception failure){
    try{SafeFiles.atomic(catalog,original);refresh.invoke(owner);}catch(Exception rollback){failure.addSuppressed(rollback);}throw failure;
   }
  }}
 }
 Map<String,Object> delete(String id)throws Exception{
  if(!SafeFiles.id(id))throw new IllegalArgumentException(WebMessages.raw("server.text-013"));
  synchronized(owner){synchronized(store){
   Configuration config=load();if(!config.getSection("capes").getKeys().contains(id))throw new IllegalArgumentException(WebMessages.raw("server.delete.missing"));
   Path image=SafeFiles.inside(folder,"capes/"+id+".png"),catalog=SafeFiles.inside(folder,"capes.yml");
   boolean existed=Files.exists(image);Path backup=SafeFiles.inside(backups,"snapshot-"+System.currentTimeMillis()+"-"+id+"-"+UUID.randomUUID());Files.createDirectory(backup);
   Files.copy(catalog,backup.resolve("capes.yml"));if(existed)Files.copy(image,backup.resolve("previous.png"));
   try{
    config.set("capes."+id,null);StringWriter text=new StringWriter();yaml.save(config,text);SafeFiles.atomic(catalog,text.toString().getBytes(StandardCharsets.UTF_8));
    Files.deleteIfExists(image);reload.run();trimBackups();
    return Map.of("id",id,"message",WebMessages.raw("server.delete.success"));
   }catch(Exception failure){
    try{SafeFiles.atomic(catalog,Files.readAllBytes(backup.resolve("capes.yml")));if(existed)SafeFiles.atomic(image,Files.readAllBytes(backup.resolve("previous.png")));reload.run();}
    catch(Exception rollback){failure.addSuppressed(rollback);}throw failure;
   }
  }}
 }
 private void trimBackups() throws IOException {
  List<Path> dirs;try(var files=Files.list(backups)){dirs=files.filter(p->p.getFileName().toString().startsWith("snapshot-")).sorted().toList();}
  long total=0;for(Path dir:dirs)try(var files=Files.walk(dir)){for(Path p:files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).toList())total+=Files.size(p);}
  int count=dirs.size();for(Path dir:dirs){if(count<=backupLimit&&total<=64L*1024*1024)break;long size;try(var files=Files.walk(dir)){size=0;for(Path p:files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).toList())size+=Files.size(p);}SafeFiles.removeBackup(backups,dir);total-=size;count--;}
 }
 static final class Conflict extends IOException {Conflict(String message){super(message);}}
}
