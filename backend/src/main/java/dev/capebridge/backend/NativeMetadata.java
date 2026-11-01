package dev.capebridge.backend;
import java.lang.reflect.*;
import java.util.*;
/** Read an initial metadata snapshot only. Packet serialization stays in PacketEvents. */
final class NativeMetadata {
 record Access(Method handle,Method watcher,Method full){}
 record ValueAccess(Method id,Method value){}
 private static final Map<Class<?>,Access> access=new HashMap<>();
 private static final Map<Class<?>,ValueAccess> values=new HashMap<>();
 private NativeMetadata(){}
 static Access resolve(Class<?> craft) throws ReflectiveOperationException {
  Method handle=craft.getMethod("getHandle"),watcher=null;
  try{watcher=handle.getReturnType().getMethod("getEntityData");}catch(NoSuchMethodException ignored){}
  if(watcher==null)for(Method m:handle.getReturnType().getMethods())if(m.getParameterCount()==0
    &&(m.getReturnType().getSimpleName().equals("DataWatcher")||m.getReturnType().getSimpleName().equals("SynchedEntityData"))){watcher=m;break;}
  if(watcher==null)throw new NoSuchMethodException("Entity metadata watcher");
  Method full;
  try{full=watcher.getReturnType().getMethod("getNonDefaultValues");}catch(NoSuchMethodException missing){full=watcher.getReturnType().getMethod("c");}
  if(!List.class.isAssignableFrom(full.getReturnType()))throw new NoSuchMethodException("Full metadata snapshot");
  return new Access(handle,watcher,full);
 }
 static Map<Integer,Object> read(Object entity){
  try{
   Access a=access.get(entity.getClass());if(a==null){a=resolve(entity.getClass());access.put(entity.getClass(),a);}
   Object watcher=a.watcher.invoke(a.handle.invoke(entity));List<?> list=(List<?>)a.full.invoke(watcher);Map<Integer,Object> result=new TreeMap<>();
   if(list==null)return result;
   for(Object value:list){ValueAccess v=values.get(value.getClass());if(v==null){
    Method id,data;try{id=value.getClass().getMethod("id");data=value.getClass().getMethod("value");}
    catch(NoSuchMethodException legacy){id=value.getClass().getMethod("a");data=value.getClass().getMethod("c");}
    v=new ValueAccess(id,data);values.put(value.getClass(),v);
   }result.put((int)v.id.invoke(value),v.value.invoke(value));}
   return result;
  }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot capture native entity metadata",ex);}
 }
}
