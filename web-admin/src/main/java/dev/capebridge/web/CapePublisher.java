package dev.capebridge.web;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Asset refresh deliberately leaves the running HTTP listener and its executor intact. */
final class CapePublisher {
 private final Object owner,store;
 private final Path folder;
 private final Field stopping,host,secret,url,hash,catalog;
 private final Method load,build,versioned,publish,send;
 private final Supplier<? extends Collection<ProxiedPlayer>> players;
 CapePublisher(Plugin owner,Object store,Path folder) throws ReflectiveOperationException {
  this(owner,store,folder,
   Class.forName("dev.capebridge.proxy.PackBuilder",true,owner.getClass().getClassLoader()),
   Class.forName("dev.capebridge.common.PackUrl",true,owner.getClass().getClassLoader()),
   ()->owner.getProxy().getPlayers());
 }
 CapePublisher(Object owner,Object store,Path folder,Class<?> builder,Class<?> urls,Supplier<? extends Collection<ProxiedPlayer>> players) throws ReflectiveOperationException {
  this.owner=owner;this.store=store;this.folder=folder;this.players=players;
  stopping=field(owner.getClass(),"stopping");host=field(owner.getClass(),"host");
  secret=field(owner.getClass(),"secret");url=field(owner.getClass(),"packUrl");
  hash=field(owner.getClass(),"packHash");catalog=field(owner.getClass(),"catalog");
  load=method(store.getClass(),"load");build=method(builder,"build",Path.class,Collection.class);
  versioned=method(urls,"versioned",String.class,String.class);
  publish=method(host.getType(),"publish",byte[].class);
  send=method(owner.getClass(),"send",ProxiedPlayer.class,String.class);
 }
 private static Field field(Class<?> type,String name) throws NoSuchFieldException {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static Method method(Class<?> type,String name,Class<?>...params) throws NoSuchMethodException {Method m=type.getDeclaredMethod(name,params);m.setAccessible(true);return m;}
 void refresh() throws Exception {
  try{Method assets=owner.getClass().getMethod("refreshAssets");assets.invoke(owner);return;}
  catch(NoSuchMethodException previousVersion){/* Compatibility with the previous standalone web plugin. */}
  // Match CapeProxy.reloadData's owner -> store lock order, including rollback.
  synchronized(owner){synchronized(store){
   if(stopping.getBoolean(owner))throw new IOException(WebMessages.raw("server.text-008"));
   Configuration settings=ConfigurationProvider.getProvider(YamlConfiguration.class).load(SafeFiles.inside(folder,"config.yml").toFile());
   String nextSecret=settings.getString("sync-secret",""),base=settings.getString("resource-pack.public-url","");
   if(nextSecret.length()<32)throw new IOException("sync-secret must contain at least 32 characters");
   if(base.length()>2048||!(base.isBlank()||base.startsWith("http://")||base.startsWith("https://")))throw new IOException("Invalid public-url");
   Object liveHost=host.get(owner);
   if(liveHost==null&&settings.getBoolean("resource-pack.embedded-host",true))
    throw new IOException(WebMessages.raw("server.text-009"));
   Object next=load.invoke(store);
   String nextHash=(String)build.invoke(null,folder,next);
   byte[] bytes=Files.readAllBytes(SafeFiles.inside(folder,"resource-pack.zip"));
   String nextUrl=(String)versioned.invoke(null,base,nextHash);
   if(liveHost!=null)publish.invoke(liveHost,(Object)bytes);
   secret.set(owner,nextSecret);url.set(owner,nextUrl);hash.set(owner,nextHash);catalog.set(owner,next);
   for(ProxiedPlayer player:players.get())send.invoke(owner,player,"");
  }}
 }
}
