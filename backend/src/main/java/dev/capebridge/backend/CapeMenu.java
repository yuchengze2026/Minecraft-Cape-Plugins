package dev.capebridge.backend;
import dev.capebridge.common.Wire;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.enchantments.Enchantment;
import java.util.*;

final class CapeMenu implements Listener {
 static final int[] CELLS={10,11,12,13,14,19,20,21,22,23,28,29,30,31,32};
 private final CapeBackend plugin;
 private final Map<UUID,Long> opened=new HashMap<>();
 private final Set<UUID> pendingClicks=new HashSet<>();
 static final class View implements InventoryHolder{
  final UUID owner;final Inventory inventory;boolean animated;int page;String focus="";
  View(UUID owner,String title){this.owner=owner;inventory=Bukkit.createInventory(this,54,title);}
  @Override public Inventory getInventory(){return inventory;}
 }
 CapeMenu(CapeBackend plugin){this.plugin=plugin;}
 boolean allowOpen(Player p){long now=System.nanoTime();Long old=opened.get(p.getUniqueId());if(old!=null&&now-old<150_000_000L)return false;opened.put(p.getUniqueId(),now);return true;}
 @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e){opened.remove(e.getPlayer().getUniqueId());pendingClicks.remove(e.getPlayer().getUniqueId());}
 private static Object[] arguments(Map<String,Object> vars){List<Object> out=new ArrayList<>();vars.forEach((k,v)->{out.add(k);out.add(v);});return out.toArray();}
 private String text(String key,Map<String,Object> vars){return plugin.gui().text(key,arguments(vars));}
 static List<Wire.Cape> entries(Wire.State state,boolean animated){return state.capes().stream().filter(Wire.Cape::wardrobeVisible).filter(c->c.kind().equals("ANIMATED")==animated).toList();}
 private Map<String,Object> context(Player p,Wire.State state,int page,boolean animated){
  Map<String,Object> out=new LinkedHashMap<>();out.put("player",p.getName());out.put("page",page+1);out.put("pages",Math.max(1,(entries(state,animated).size()+CELLS.length-1)/CELLS.length));out.put("count",entries(state,animated).size());
  String selectedName=state.capes().stream().filter(c->c.id().equals(state.selected())).map(Wire.Cape::title).findFirst().orElse(state.selected());
  out.put("cape",selectedName);out.put("selected-id",state.selected());out.put("selected",state.selected().isEmpty()?text("selected.none",out):text("selected.value",out));return out;
 }
 private ItemStack item(Material material,String section,Map<String,Object> vars){
  ItemStack item=new ItemStack(material);ItemMeta meta=Objects.requireNonNull(item.getItemMeta());String name=text(section+".name",vars);meta.setDisplayName(name.isEmpty()?" ":name);
  meta.setLore(plugin.gui().lines(section+".lore",arguments(vars)));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES,ItemFlag.HIDE_ENCHANTS);item.setItemMeta(meta);return item;
 }
 private ItemStack icon(Player p,Wire.Cape cape,boolean current,Map<String,Object> context){
  Material material=Material.matchMaterial(cape.material());
  if(!cape.kind().equals("BLOCK"))material=plugin.packReady(p.getUniqueId())?Material.PAPER:Material.MAP;
  else if(cape.material().endsWith("_CONCRETE")){Material banner=Material.matchMaterial(cape.material().replace("_CONCRETE","_BANNER"));if(banner!=null)material=banner;}
  if(material==null||!material.isItem())material=Material.PAPER;
  Map<String,Object> vars=new LinkedHashMap<>(context);vars.put("cape",cape.title());vars.put("id",cape.id());vars.put("permission",cape.permission());
  vars.put("name-color",text(cape.available()?"states.name-available":"states.name-locked",vars));vars.put("wear-state",text(current?"states.worn":"states.choose",vars));vars.put("access-state",text(cape.available()?"states.available":"states.locked",vars));
  vars.put("pack-state",text(!cape.kind().equals("BLOCK")&&!plugin.packReady(p.getUniqueId())?"states.pack-needed":"states.synced",vars));
  ItemStack item=item(material,"entry",vars);ItemMeta meta=item.getItemMeta();if(!cape.kind().equals("BLOCK")&&material==Material.PAPER)Objects.requireNonNull(meta).setCustomModelData(cape.model());
  Enchantment glow=Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));if(current&&glow!=null)Objects.requireNonNull(meta).addEnchant(glow,1,true);item.setItemMeta(meta);return item;
 }
 void open(Player p){open(p,false);}
 void open(Player p,boolean animated){Wire.State state=plugin.state(p);if(state==null){Messages.send(p,"menu.syncing");plugin.openWhenReady(p,animated);return;}
  View v=new View(p.getUniqueId(),text(animated?"dynamic-title":"title",context(p,state,0,animated)));v.animated=animated;v.focus=state.selected();render(p,v);p.openInventory(v.inventory);p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.3f,1.2f);
 }
 private void reopen(Player p,View previous){boolean animated=previous.animated;Wire.State state=plugin.state(p);if(state==null)return;int pages=Math.max(1,(entries(state,animated).size()+CELLS.length-1)/CELLS.length);int page=Math.max(0,Math.min(previous.page,pages-1));View next=new View(p.getUniqueId(),text(animated?"dynamic-title":"title",context(p,state,page,animated)));next.animated=animated;next.page=page;next.focus=previous.focus;render(p,next);p.openInventory(next.inventory);}
 void refresh(Player p){if(p.getOpenInventory().getTopInventory().getHolder() instanceof View v&&v.owner.equals(p.getUniqueId()))render(p,v);}
 void reloadTitles(){for(Player p:plugin.getServer().getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof View v&&v.owner.equals(p.getUniqueId()))reopen(p,v);}
 private void render(Player p,View v){
  boolean animated=v.animated;Wire.State state=plugin.state(p);if(state==null)return;List<Wire.Cape> capes=entries(state,animated);int pages=Math.max(1,(capes.size()+CELLS.length-1)/CELLS.length);v.page=Math.max(0,Math.min(v.page,pages-1));Map<String,Object> vars=context(p,state,v.page,animated);
  ItemStack glass=item(Material.GRAY_STAINED_GLASS_PANE,"frame",vars);for(int slot=0;slot<54;slot++)v.inventory.setItem(slot,glass);
  for(int slot:new int[]{15,24,33,42})v.inventory.setItem(slot,item(Material.CYAN_STAINED_GLASS_PANE,"divider",vars));
  for(int slot:CELLS)v.inventory.setItem(slot,null);
  v.inventory.setItem(4,item(Material.BOOK,animated?"dynamic-header":"header",vars));
  v.inventory.setItem(51,item(Material.CLOCK,animated?"static-menu":"dynamic-menu",vars));
  for(int i=0;i<CELLS.length;i++){int index=v.page*CELLS.length+i;v.inventory.setItem(CELLS[i],index<capes.size()?icon(p,capes.get(index),capes.get(index).id().equals(state.selected()),vars):null);}
  if(capes.isEmpty())v.inventory.setItem(22,item(Material.MAP,animated?"dynamic-empty":"empty",vars));
  Wire.Cape focus=capes.stream().filter(c->c.id().equals(v.focus)).findFirst().orElse(null);if(focus==null&&!capes.isEmpty()){focus=capes.get(Math.min(v.page*CELLS.length,capes.size()-1));v.focus=focus.id();}
  vars.put("focus-cape",focus==null?"":focus.title());vars.put("focus-id",focus==null?"":focus.id());
  v.inventory.setItem(16,item(Material.ITEM_FRAME,"preview-heading",vars));v.inventory.setItem(25,focus==null?item(Material.BARRIER,"no-focus",vars):icon(p,focus,focus.id().equals(state.selected()),vars));
  v.inventory.setItem(8,item(Material.ELYTRA,"instructions",vars));v.inventory.setItem(34,item(Material.ARMOR_STAND,"preview",vars));v.inventory.setItem(43,item(Material.LIME_DYE,"confirm",vars));v.inventory.setItem(35,item(Material.MILK_BUCKET,"end-preview",vars));
  v.inventory.setItem(45,item(Material.ARROW,"previous-page",vars));v.inventory.setItem(53,item(Material.ARROW,"next-page",vars));v.inventory.setItem(48,item(Material.BARRIER,"remove",vars));v.inventory.setItem(49,item(Material.PLAYER_HEAD,"selected",vars));v.inventory.setItem(50,item(Material.OAK_DOOR,"close",vars));
 }
 @EventHandler public void click(InventoryClickEvent event){
  if(!(event.getView().getTopInventory().getHolder() instanceof View v))return;event.setCancelled(true);
  if(!(event.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.owner)||event.getRawSlot()<0||event.getRawSlot()>=54)return;
  if(!pendingClicks.add(p.getUniqueId()))return;int slot=event.getRawSlot();plugin.getServer().getScheduler().runTask(plugin,()->{
   pendingClicks.remove(p.getUniqueId());if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=v.inventory)return;Wire.State state=plugin.state(p);if(state==null)return;List<Wire.Cape> choices=entries(state,v.animated);
   for(int i=0;i<CELLS.length;i++)if(slot==CELLS[i]){int index=v.page*CELLS.length+i;if(index<choices.size())v.focus=choices.get(index).id();render(p,v);p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.25f,1.4f);return;}
   if(slot==45){v.page--;v.focus="";reopen(p,v);return;}if(slot==53){v.page++;v.focus="";reopen(p,v);return;}
   if(slot==51){open(p,!v.animated);return;}if(slot==50){p.closeInventory();return;}if(slot==48){plugin.choose(p,"");return;}if(slot==35){plugin.cancelPreview(p);Messages.send(p,"cape.preview-ended");return;}
   Wire.Cape cape=choices.stream().filter(c->c.id().equals(v.focus)).findFirst().orElse(null);if(cape==null)return;
   if(slot==34){if(!cape.available()){Messages.send(p,"cape.locked");return;}if(!plugin.packReady(p.getUniqueId())){Messages.send(p,"cape.preview-pack");return;}p.closeInventory();plugin.preview(p,cape);}
   else if(slot==43){if(cape.available())plugin.choose(p,cape.id());else Messages.send(p,"cape.locked");}
  });
 }
 @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
