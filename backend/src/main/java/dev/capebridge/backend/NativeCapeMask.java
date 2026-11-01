package dev.capebridge.backend;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.entity.data.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.common.client.WrapperCommonClientSettings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.*;
/** Only the native cape bit is masked; jacket, hat, sleeves and real equipment remain intact. */
final class NativeCapeMask implements AutoCloseable {
 private final JavaPlugin plugin;
 private final Map<UUID,Set<Integer>> masked=new ConcurrentHashMap<>();
 private final Map<Integer,UUID> owners=new ConcurrentHashMap<>();
 private final Map<Integer,Integer> indices=new ConcurrentHashMap<>();
 private final Map<UUID,Byte> originals=new ConcurrentHashMap<>();
 private final Set<Class<?>> warned=ConcurrentHashMap.newKeySet();
 private long corrections;
 private final PacketListenerAbstract listener;
 private volatile boolean closed;
 NativeCapeMask(JavaPlugin plugin){
  this.plugin=plugin;
  listener=new PacketListenerAbstract(PacketListenerPriority.HIGHEST){
   @Override public void onPacketSend(PacketSendEvent event){
    if(closed||event.isCancelled())return;
    var type=event.getPacketType();
    if(type==PacketType.Play.Server.RESPAWN){afterSpawn(event,event.getUser().getEntityId());return;}
    if(type==PacketType.Play.Server.SPAWN_PLAYER){afterSpawn(event,new WrapperPlayServerSpawnPlayer(event).getEntityId());return;}
    if(type==PacketType.Play.Server.SPAWN_ENTITY){afterSpawn(event,new WrapperPlayServerSpawnEntity(event).getEntityId());return;}
    if(type!=PacketType.Play.Server.ENTITY_METADATA)return;
    var packet=new WrapperPlayServerEntityMetadata(event);UUID owner=owners.get(packet.getEntityId());Integer index=indices.get(packet.getEntityId());
    if(owner==null||index==null)return;boolean hide=masked.getOrDefault(event.getUser().getUUID(),Set.of()).contains(packet.getEntityId());List<EntityData<?>> copy=new ArrayList<>(packet.getEntityMetadata());boolean changed=false;
    for(int i=0;i<copy.size();i++){EntityData<?> data=copy.get(i);if(data.getIndex()==index&&data.getValue() instanceof Byte flags){originals.compute(owner,(id,old)->remember(flags,old));if(hide){copy.set(i,new EntityData<>(index,EntityDataTypes.BYTE,withoutCape(flags)));changed=true;}}}
    if(changed){packet.setEntityMetadata(copy);event.markForReEncode(true);}
   }
   @Override public void onPacketReceive(PacketReceiveEvent event){
    if(closed||event.isCancelled())return;var type=event.getPacketType();
    if(type!=PacketType.Play.Client.CLIENT_SETTINGS&&type!=PacketType.Configuration.Client.CLIENT_SETTINGS)return;
    var settings=new WrapperCommonClientSettings<>(event);byte flags=settings.getSkinMask();UUID id=event.getUser().getUUID();
    if(id!=null)originals.put(id,flags);
    if(event.getPlayer() instanceof Player owner)plugin.getServer().getScheduler().runTask(plugin,()->{if(!closed&&owner.isOnline()){register(owner);for(Player viewer:plugin.getServer().getOnlinePlayers())send(viewer,owner,masked.getOrDefault(viewer.getUniqueId(),Set.of()).contains(owner.getEntityId()));}});
   }
   @Override public void onUserDisconnect(UserDisconnectEvent event){UUID id=event.getUser().getUUID();if(id!=null)originals.remove(id);}
  };PacketEvents.getAPI().getEventManager().registerListener(listener);
 }
 private void afterSpawn(PacketSendEvent event,int entity){
  UUID owner=owners.get(entity);Integer index=indices.get(entity);if(owner==null||index==null||!masked.getOrDefault(event.getUser().getUUID(),Set.of()).contains(entity))return;
  event.getTasksAfterSend().add(()->{
   if(closed||!owner.equals(owners.get(entity))||!masked.getOrDefault(event.getUser().getUUID(),Set.of()).contains(entity))return;
   byte flags=withoutCape(originals.getOrDefault(owner,(byte)0));
   event.getUser().sendPacketSilently(new WrapperPlayServerEntityMetadata(entity,List.of(new EntityData<>(index,EntityDataTypes.BYTE,flags))));
  });
 }
 void join(Player player){
  register(player);
 }
 private void register(Player player){
  if(owners.putIfAbsent(player.getEntityId(),player.getUniqueId())!=null)return;
  int index=SkinMetadata.index(player);indices.put(player.getEntityId(),index);
  if(NativeMetadata.read(player).get(index) instanceof Byte flags)originals.putIfAbsent(player.getUniqueId(),flags);
 }
 static byte remember(byte server,Byte preference){return (byte)((server&~1)|(preference==null?server&1:preference&1));}
 String inspect(Player player){
  try{return Messages.t("diagnostic.skin-format","index",SkinMetadata.index(player),"flags",NativeSkinState.read(player)&255,"cape",(NativeSkinState.read(player)&1)!=0,"corrections",corrections);}
  catch(IllegalStateException failure){return Messages.t("diagnostic.skin-unavailable");}
 }
 static byte withoutCape(byte original){return (byte)(original&~1);}
 byte original(Player owner){return originals.getOrDefault(owner.getUniqueId(),(byte)0);}
 void set(Player viewer,Player owner,boolean hide){
  register(owner);Set<Integer> values=masked.computeIfAbsent(viewer.getUniqueId(),k->ConcurrentHashMap.newKeySet());
  boolean changed=hide?values.add(owner.getEntityId()):values.remove(owner.getEntityId());if(changed)send(viewer,owner,hide);
 }
 private void send(Player viewer,Player owner,boolean hide){
  Integer index=indices.get(owner.getEntityId());if(index==null)return;byte flags=original(owner);if(hide)flags=withoutCape(flags);
  PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer,new WrapperPlayServerEntityMetadata(owner.getEntityId(),List.of(new EntityData<>(index,EntityDataTypes.BYTE,flags))));
 }
 void forget(UUID id){
  masked.remove(id);originals.remove(id);Set<Integer> gone=new HashSet<>();owners.entrySet().removeIf(e->{if(e.getValue().equals(id)){gone.add(e.getKey());indices.remove(e.getKey());return true;}return false;});
  for(Set<Integer> set:masked.values())set.removeAll(gone);
 }
 @Override public void close(){
  closed=true;PacketEvents.getAPI().getEventManager().unregisterListener(listener);
  for(Player viewer:plugin.getServer().getOnlinePlayers())for(int entity:new ArrayList<>(masked.getOrDefault(viewer.getUniqueId(),Set.of()))){UUID id=owners.get(entity);Player owner=id==null?null:plugin.getServer().getPlayer(id);if(owner!=null)send(viewer,owner,false);}
  masked.clear();owners.clear();indices.clear();originals.clear();
 }
}
