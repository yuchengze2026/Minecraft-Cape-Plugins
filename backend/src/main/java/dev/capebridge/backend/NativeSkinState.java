package dev.capebridge.backend;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Keep the authoritative skin flag off, including refresh paths outside our outgoing listener. */
final class NativeSkinState {
 private record Access(NativeMetadata.Access entity,Method get,Method set){}
 private static final Map<Class<?>,Access> cache=new HashMap<>();
 private NativeSkinState(){}
 private static Access access(Player player){
  return cache.computeIfAbsent(player.getClass(),type->{
   try{
    NativeMetadata.Access entity=NativeMetadata.resolve(type);Class<?> watcher=entity.watcher().getReturnType();Class<?> key=SkinMetadata.key(player).getClass();
    return new Access(entity,method(watcher,Object.class,new String[]{"get","b","a"},key),method(watcher,void.class,new String[]{"set","b"},key,Object.class));
   }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot access native skin state",failure);}
  });
 }
 static Method method(Class<?> type,Class<?> result,String[] names,Class<?>...parameters) throws NoSuchMethodException {
  for(String name:names)try{Method method=type.getMethod(name,parameters);if(method.getReturnType()==result)return method;}catch(NoSuchMethodException ignored){}
  throw new NoSuchMethodException("Native skin metadata method");
 }
 private static Object watcher(Player player,Access access) throws ReflectiveOperationException {
  return access.entity.watcher().invoke(access.entity.handle().invoke(player));
 }
 static byte read(Player player){
  Access access=access(player);
  try{return (Byte)access.get.invoke(watcher(player,access),SkinMetadata.key(player));}
  catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot read native skin state",failure);}
 }
 static boolean cape(Player player,boolean enabled){
  Access access=access(player);
  try{
   Object watcher=watcher(player,access);byte before=(Byte)access.get.invoke(watcher,SkinMetadata.key(player));
   byte after=withCape(before,enabled);if(before==after)return false;
   access.set.invoke(watcher,SkinMetadata.key(player),after);return true;
  }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot update native skin state",failure);}
 }
 static byte withCape(byte flags,boolean enabled){return (byte)(enabled?(flags|1):(flags&~1));}
}
