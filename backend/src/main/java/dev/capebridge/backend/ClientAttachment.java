package dev.capebridge.backend;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import dev.capebridge.common.CapeMesh;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
/** Per-viewer client passengers, implemented entirely with PacketEvents. */
final class ClientAttachment implements AutoCloseable {
 private record Link(UUID owner,int ownerId,int capeId,UUID capeUuid){}
 private record Pending(UUID viewer,UUID owner,int ownerId){}
 private final JavaPlugin plugin;
 private final java.util.function.Consumer<UUID> worldReset;
 private final ClientProtocols protocols;
 private final boolean modernServer;
 private final Map<Integer,Link> capes=new ConcurrentHashMap<>();
 private final Map<Integer,DisplayState> states=new ConcurrentHashMap<>();
 private final Map<Integer,Map<Integer,Link>> owners=new ConcurrentHashMap<>();
 private final Map<UUID,Set<Integer>> tracked=new ConcurrentHashMap<>(),allowed=new ConcurrentHashMap<>();
 private final Map<UUID,Map<Integer,Long>> shownItems=new ConcurrentHashMap<>();
 private final Set<Pending> pending=ConcurrentHashMap.newKeySet();
 private final Set<UUID> rebuilding=ConcurrentHashMap.newKeySet();
 private final LongAdder blocked=new LongAdder(),mounts=new LongAdder();
 private final PacketListenerAbstract listener;
 private volatile boolean closed;
 private long immediatePackets;
 ClientAttachment(JavaPlugin plugin){this(plugin,id->{});}
 ClientAttachment(JavaPlugin plugin,java.util.function.Consumer<UUID> worldReset){
  this.plugin=plugin;this.worldReset=worldReset;protocols=new ClientProtocols(plugin);
  modernServer=PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_20_2);
  listener=new PacketListenerAbstract(PacketListenerPriority.HIGHEST){
   @Override public void onPacketSend(PacketSendEvent event){
    if(closed||event.isCancelled()||!(event.getPlayer() instanceof Player player))return;
    var type=event.getPacketType();UUID viewer=player.getUniqueId();
    if(type==PacketType.Play.Server.RESPAWN){
     // Skin plugins can recreate only the client's world without restarting Bukkit tracking.
     tracked.remove(viewer);shownItems.remove(viewer);event.getTasksAfterSend().add(()->rebuild(viewer));return;
    }
    if(type==PacketType.Play.Server.SPAWN_PLAYER){
     int id=new WrapperPlayServerSpawnPlayer(event).getEntityId();
     for(Link link:owners.getOrDefault(id,Map.of()).values())event.getTasksAfterSend().add(()->queue(viewer,link));return;
    }
    if(type==PacketType.Play.Server.DESTROY_ENTITIES){Set<Integer> set=tracked.get(viewer);for(int id:new WrapperPlayServerDestroyEntities(event).getEntityIds()){if(set!=null)set.remove(id);Map<Integer,Long> items=shownItems.get(viewer);if(items!=null)items.remove(id);}return;}
    if(type==PacketType.Play.Server.SPAWN_ENTITY){
     int id=new WrapperPlayServerSpawnEntity(event).getEntityId();Link link=capes.get(id);
     if(link==null){for(Link passenger:owners.getOrDefault(id,Map.of()).values())event.getTasksAfterSend().add(()->queue(viewer,passenger));return;}
     event.getTasksAfterSend().add(()->{if(!closed){tracked.computeIfAbsent(viewer,k->ConcurrentHashMap.newKeySet()).add(id);queue(viewer,link);}});return;
    }
    if(type==PacketType.Play.Server.SET_PASSENGERS){var packet=new WrapperPlayServerSetPassengers(event);if(owners.containsKey(packet.getEntityId())){packet.setPassengers(withCapes(viewer,packet.getEntityId(),packet.getPassengers()));event.markForReEncode(true);}return;}
    if(type==PacketType.Play.Server.ENTITY_METADATA){int id=new WrapperPlayServerEntityMetadata(event).getEntityId();if(capes.containsKey(id))event.setCancelled(true);return;}
    if(type==PacketType.Play.Server.ENTITY_TELEPORT||type==PacketType.Play.Server.ENTITY_RELATIVE_MOVE||type==PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION||type==PacketType.Play.Server.ENTITY_ROTATION||type==PacketType.Play.Server.ENTITY_POSITION_SYNC){
     Object buffer=event.getByteBuf();int index=ByteBufHelper.readerIndex(buffer),id;try{id=ByteBufHelper.readVarInt(buffer);}finally{ByteBufHelper.readerIndex(buffer,index);}
     if(capes.containsKey(id)){event.setCancelled(true);blocked.increment();}
    }
   }
  };PacketEvents.getAPI().getEventManager().registerListener(listener);
 }
 void register(Player owner,Display display){
  if(closed)throw new IllegalStateException("Cape attachment closed");ItemDisplay shown=(ItemDisplay)display;
  ItemStack self=new ItemStack(Material.LEATHER_HORSE_ARMOR);LeatherArmorMeta meta=(LeatherArmorMeta)self.getItemMeta();meta.setCustomModelData(shown.getItemStack().getItemMeta().getCustomModelData());meta.setColor(Color.fromRGB(CapeMesh.SELF_COLOR));self.setItemMeta(meta);
  DisplayState state=DisplayState.capture(shown,SpigotConversionUtil.fromBukkitItemStack(self),owner.getHeight(),Boolean.getBoolean("capebridge.experiment.selfBillboard"));Link l=new Link(owner.getUniqueId(),owner.getEntityId(),display.getEntityId(),display.getUniqueId());
  states.put(l.capeId,state);capes.put(l.capeId,l);owners.computeIfAbsent(l.ownerId,k->new ConcurrentHashMap<>()).put(l.capeId,l);
 }
 boolean isTracked(int id){return capes.containsKey(id);}
 void item(Display display){
  DisplayState state=states.get(display.getEntityId());if(state==null)return;
  ItemStack paper=((ItemDisplay)display).getItemStack(),self=new ItemStack(Material.LEATHER_HORSE_ARMOR);
  LeatherArmorMeta meta=(LeatherArmorMeta)self.getItemMeta();meta.setCustomModelData(paper.getItemMeta().getCustomModelData());meta.setColor(Color.fromRGB(CapeMesh.SELF_COLOR));self.setItemMeta(meta);
  state.item(paper,SpigotConversionUtil.fromBukkitItemStack(self));
 }
 void flush(Player owner,Display display,Transformation pose,int duration){
  flush(owner,display,pose,duration,pose,duration);
 }
 void flush(Player owner,Display display,Transformation pose,int duration,Transformation observerPose,int observerDuration){
  int id=display.getEntityId();DisplayState state=states.get(id);if(state==null)return;state.update(pose,duration,observerPose,observerDuration,owner.getHeight());
  for(var e:tracked.entrySet()){
   UUID viewer=e.getKey();if(!e.getValue().contains(id)||!allowed.getOrDefault(viewer,Set.of()).contains(id))continue;
   Player p=plugin.getServer().getPlayer(viewer);if(p==null||p.getWorld()!=display.getWorld())continue;
   Map<Integer,Long> items=shownItems.computeIfAbsent(viewer,k->new ConcurrentHashMap<>());
   boolean itemChanged=!Objects.equals(items.get(id),state.itemRevision());
   boolean self=owner.getUniqueId().equals(viewer);
   var data=itemChanged?state.full(self,modernServer,protocols.modern(p)):state.frame(self,modernServer,protocols.modern(p));
   PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p,new WrapperPlayServerEntityMetadata(id,data));items.put(id,state.itemRevision());immediatePackets++;
  }
 }
 long immediatePackets(){return immediatePackets;}
 private void rebuild(UUID viewer){
  if(closed||!plugin.isEnabled()||!rebuilding.add(viewer))return;
  plugin.getServer().getScheduler().runTaskLater(plugin,()->{
   rebuilding.remove(viewer);if(closed)return;Player player=plugin.getServer().getPlayer(viewer);if(player==null)return;
   worldReset.accept(viewer);
   for(int id:new HashSet<>(allowed.getOrDefault(viewer,Set.of()))){
    Link link=capes.get(id);Player owner=link==null?null:plugin.getServer().getPlayer(link.owner);
    Entity entity=link==null?null:plugin.getServer().getEntity(link.capeUuid);
    if(owner==null||entity==null||entity.getWorld()!=player.getWorld()||!entity.isValid()||!player.canSee(owner)||!player.canSee(entity))continue;
    // Force a real spawn for this viewer; metadata alone cannot revive an entity the client discarded.
    player.hideEntity(plugin,entity);player.showEntity(plugin,entity);
   }
  },2);
 }
 void allow(Player viewer,int capeId,boolean show){Set<Integer> set=allowed.computeIfAbsent(viewer.getUniqueId(),k->ConcurrentHashMap.newKeySet());boolean changed=show?set.add(capeId):set.remove(capeId);Link l=capes.get(capeId);if(changed&&l!=null)queue(viewer.getUniqueId(),l);}
 private int[] withCapes(UUID viewer,int ownerId,int[] real){
  Set<Integer> ids=new LinkedHashSet<>();for(int id:real)if(!capes.containsKey(id))ids.add(id);
  for(Link l:owners.getOrDefault(ownerId,Map.of()).values())if(allowed.getOrDefault(viewer,Set.of()).contains(l.capeId)&&tracked.getOrDefault(viewer,Set.of()).contains(l.capeId))ids.add(l.capeId);
  return ids.stream().mapToInt(Integer::intValue).toArray();
 }
 private void queue(UUID viewer,Link link){
  if(closed||!plugin.isEnabled())return;Pending p=new Pending(viewer,link.owner,link.ownerId);if(!pending.add(p))return;
  plugin.getServer().getScheduler().runTask(plugin,()->{
   pending.remove(p);if(closed)return;Player v=plugin.getServer().getPlayer(p.viewer),owner=plugin.getServer().getPlayer(p.owner);
   if(v==null||owner==null||owner.getEntityId()!=p.ownerId||v.getWorld()!=owner.getWorld()||!v.canSee(owner))return;
   int[] ids=withCapes(p.viewer,p.ownerId,owner.getPassengers().stream().mapToInt(Entity::getEntityId).toArray());
   for(int id:ids){DisplayState state=states.get(id);Link l=capes.get(id);if(state!=null&&l!=null){PacketEvents.getAPI().getPlayerManager().sendPacketSilently(v,new WrapperPlayServerEntityMetadata(id,state.initial(l.owner.equals(p.viewer),modernServer,protocols.modern(v))));shownItems.computeIfAbsent(p.viewer,k->new ConcurrentHashMap<>()).put(id,state.itemRevision());}}
   PacketEvents.getAPI().getPlayerManager().sendPacketSilently(v,new WrapperPlayServerSetPassengers(p.ownerId,ids));mounts.increment();
  });
 }
 void unregister(Display display){
  int id=display.getEntityId();states.remove(id);Link l=capes.remove(id);if(l==null)return;Map<Integer,Link> owner=owners.get(l.ownerId);if(owner!=null){owner.remove(id);if(owner.isEmpty())owners.remove(l.ownerId,owner);}
  for(UUID viewer:tracked.keySet()){Set<Integer> known=tracked.get(viewer);if(known!=null)known.remove(id);Set<Integer> set=allowed.get(viewer);if(set!=null)set.remove(id);Map<Integer,Long> items=shownItems.get(viewer);if(items!=null)items.remove(id);queue(viewer,l);}
 }
 void quit(UUID id){tracked.remove(id);allowed.remove(id);shownItems.remove(id);rebuilding.remove(id);protocols.forget(id);pending.removeIf(p->p.viewer.equals(id)||p.owner.equals(id));}
 String protocol(Player viewer){return protocols.describe(viewer);}
 boolean modernTurnTiming(Player viewer){return protocols.modernTurnTiming(viewer);}
 boolean smoothBend(Player viewer){return protocols.smoothBend(viewer);}
 long blockedMoves(){return blocked.sum();}long mounts(){return mounts.sum();}
 @Override public void close(){closed=true;PacketEvents.getAPI().getEventManager().unregisterListener(listener);capes.clear();states.clear();owners.clear();tracked.clear();allowed.clear();shownItems.clear();pending.clear();rebuilding.clear();protocols.clear();}
}
