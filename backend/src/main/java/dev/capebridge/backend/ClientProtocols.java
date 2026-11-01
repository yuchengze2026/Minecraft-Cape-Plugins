package dev.capebridge.backend;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.util.*;
/** Cache the original viewer protocol once per connection, including translated connections. */
final class ClientProtocols {
 private record Version(int id,String source){}
 private final Map<UUID,Version> versions=new java.util.concurrent.ConcurrentHashMap<>();
 private final Object viaApi;
 private final Method viaVersion;
 private final int nativeVersion;
 ClientProtocols(JavaPlugin plugin){
  nativeVersion=PacketEvents.getAPI().getServerManager().getVersion().toClientVersion().getProtocolVersion();
  Object api=null;Method method=null;var via=plugin.getServer().getPluginManager().getPlugin("ViaVersion");
  if(via!=null&&via.isEnabled())try{
   ClassLoader loader=via.getClass().getClassLoader();Class<?> type=Class.forName("com.viaversion.viaversion.api.Via",true,loader);
   api=type.getMethod("getAPI").invoke(null);
   method=Class.forName("com.viaversion.viaversion.api.ViaAPI",true,loader).getMethod("getPlayerVersion",UUID.class);
  }catch(ReflectiveOperationException|LinkageError failure){plugin.getLogger().warning(Messages.t("log.protocol-fallback"));}
  viaApi=api;viaVersion=method;
 }
 private Version read(Player viewer){
  if(viaVersion!=null)try{int id=((Number)viaVersion.invoke(viaApi,viewer.getUniqueId())).intValue();if(id>0)return new Version(id,"ViaVersion original");}catch(ReflectiveOperationException|RuntimeException ignored){}
  var client=PacketEvents.getAPI().getPlayerManager().getClientVersion(viewer);int id=client==null?-1:client.getProtocolVersion();
  return new Version(id>0?id:nativeVersion,id>0?"PacketEvents":"server fallback");
 }
 private Version version(Player viewer){return versions.computeIfAbsent(viewer.getUniqueId(),id->read(viewer));}
 boolean modern(Player viewer){return version(viewer).id>=ServerVersion.V_1_20_2.toClientVersion().getProtocolVersion();}
 boolean modernTurnTiming(Player viewer){return version(viewer).id>=ServerVersion.valueOf("V_1_21_11").toClientVersion().getProtocolVersion();}
 // Protocol 767 is shared by 1.21 and 1.21.1; use the negotiated version, not the server version.
 boolean smoothBend(Player viewer){return version(viewer).id>=767;}
 String describe(Player viewer){Version v=version(viewer);return v.id+"/"+v.source;}
 void forget(UUID id){versions.remove(id);}
 void clear(){versions.clear();}
}
