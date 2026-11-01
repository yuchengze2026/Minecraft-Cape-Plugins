package dev.capebridge.backend;
import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;
/** Resolve the player skin accessor rather than assuming a metadata index forever. */
final class SkinMetadata {
 private record Entry(int index,Object key){}
 private static final Map<Class<?>,Entry> indices=new HashMap<>();
 private SkinMetadata(){}
 static int index(Player player){return entry(player).index;}
 static Object key(Player player){return entry(player).key;}
 private static Entry entry(Player player){return indices.computeIfAbsent(player.getClass(),SkinMetadata::resolve);}
 private static Entry resolve(Class<?> craft){
  try{
   Class<?> handle=craft.getMethod("getHandle").getReturnType();List<Entry> candidates=new ArrayList<>();
   for(Class<?> c=handle;c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
    if(!Modifier.isStatic(f.getModifiers())||!(f.getGenericType() instanceof ParameterizedType type)
       ||type.getActualTypeArguments().length!=1||type.getActualTypeArguments()[0]!=Byte.class)continue;
    String name=c.getSimpleName();boolean named=f.getName().equals("DATA_PLAYER_MODE_CUSTOMISATION");
    if(!named&&!name.equals("EntityHuman")&&!name.equals("Player")&&!name.equals("Avatar"))continue;
    f.setAccessible(true);Object accessor=f.get(null);if(accessor==null)continue;
    for(String method:new String[]{"id","getId","a"})try{
     Method m=accessor.getClass().getMethod(method);if(m.getReturnType()!=int.class)continue;
     int index=(int)m.invoke(accessor);Entry entry=new Entry(index,accessor);if(named)return entry;candidates.add(entry);break;
    }catch(NoSuchMethodException ignored){}
   }
   if(!candidates.isEmpty())return candidates.stream().min(Comparator.comparingInt(Entry::index)).orElseThrow();
  }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot resolve native player skin accessor",failure);}
  throw new IllegalStateException("Native player skin accessor not found");
 }
}
