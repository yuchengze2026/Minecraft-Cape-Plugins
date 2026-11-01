package dev.capebridge.backend;
import dev.capebridge.common.*;
import dev.capebridge.common.TextCatalog;
import dev.capebridge.common.Notice;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.*;
import java.util.*;

public final class CapeBackend extends JavaPlugin implements Listener,PluginMessageListener,CommandExecutor {
    private final Map<UUID,String> proxyVersions=new HashMap<>();
    private final Map<UUID,Wire.State> states=new HashMap<>();private final Map<UUID,UUID> offers=new HashMap<>();
    private final Map<UUID,PackSnapshot> packs=new HashMap<>();
    private final Map<UUID,PackStatus> localPackStatus=new HashMap<>();
    private final Set<UUID> ready=new HashSet<>();private final Map<UUID,Boolean> waitingMenu=new HashMap<>();private final Map<UUID,Long> clicks=new HashMap<>();
    private CapeRenderer renderer;private CapeMenu menu;private String secret;private TextCatalog gui;
    private PoseDiagnostics poses;
    private String startupStage="configuration";
    Map<String,Object> renderDiagnostic(Player player){return renderer==null?Map.of():renderer.diagnostic(player);}
    @Override public void onEnable(){
        try{enableServices();}
        catch(RuntimeException|LinkageError failure){
            var dependency=getServer().getPluginManager().getPlugin("packetevents");
            String report=StartupReport.render(Messages.t("stages."+startupStage),getServer().getVersion(),System.getProperty("java.version"),dependency==null?Messages.t("diagnostic.dependency-missing"):dependency.getDescription().getVersion(),failure,secret);
            try{StartupReport.save(getDataFolder().toPath(),report);getLogger().severe(Messages.t("log.startup-saved"));}
            catch(IOException writeFailure){getLogger().severe(Messages.t("log.startup-save-failed","error",Messages.error(writeFailure)));}
            throw failure;
        }
    }
    private void enableServices(){saveDefaultConfig();loadTexts();loadSettings();
        startupStage="renderer";renderer=new CapeRenderer(this);menu=new CapeMenu(this);
        startupStage="listeners";poses=new PoseDiagnostics(this);
        getServer().getPluginManager().registerEvents(this,this);getServer().getPluginManager().registerEvents(menu,this);
        getServer().getMessenger().registerOutgoingPluginChannel(this,Wire.CHANNEL);getServer().getMessenger().registerIncomingPluginChannel(this,Wire.CHANNEL,this);
        Objects.requireNonNull(getCommand("cape")).setExecutor(this);Objects.requireNonNull(getCommand("dynamiccape")).setExecutor(this);Objects.requireNonNull(getCommand("capebridge")).setExecutor(this);
        for(World w:getServer().getWorlds())for(Chunk c:w.getLoadedChunks())renderer.cleanup(c);for(Player p:getServer().getOnlinePlayers())request(p);
        startupStage="ready";
        getLogger().info(Messages.t("log.ready","version",getDescription().getVersion()));
    }
    private void loadTexts(){
        try{var next=TextFiles.load(getDataFolder().toPath(),"messages.yml");var nextGui=TextFiles.load(getDataFolder().toPath(),"gui.yml");
            Messages.use(next);gui=nextGui;
            for(String name:List.of("cape","capebridge","dynamiccape")){var command=getCommand(name);if(command!=null){String section=name.equals("capebridge")?"admin":name.equals("dynamiccape")?"dynamic":"cape";command.setDescription(Messages.t("commands."+section+".description"));command.setUsage(Messages.t("commands."+section+".usage"));}}
        }catch(Exception failure){throw new IllegalStateException(failure.getMessage(),failure);}
    }
    private void loadSettings(){
        // This is an optional cosmetic pack, including when an old configuration required it.
        if(getConfig().getBoolean("resource-pack.required",false)){
            try{
                java.nio.file.Path backup=new File(getDataFolder(),"config-before-optional-pack.yml").toPath();
                if(!java.nio.file.Files.exists(backup))java.nio.file.Files.copy(new File(getDataFolder(),"config.yml").toPath(),backup);
            }catch(IOException ex){throw new IllegalStateException("Cannot preserve optional pack configuration",ex);}
            getConfig().set("resource-pack.required",false);saveConfig();
        }
        if(!getConfig().getString("render.motion-profile","").equals("rigid-v1")){
            try{
                java.nio.file.Path previous=new File(getDataFolder(),"config-before-0.5.1.yml").toPath();
                if(!java.nio.file.Files.exists(previous))java.nio.file.Files.copy(new File(getDataFolder(),"config.yml").toPath(),previous);
                getConfig().set("render.motion-profile","rigid-v1");
                getConfig().set("render.motion-scale",1.0);getConfig().set("render.max-bend-angle",65);
                getConfig().set("render.max-side-angle",0);getConfig().set("render.cloth-flutter-angle",null);
                getConfig().set("render.turn-lag-angle",.05);getConfig().set("render.turn-back-offset",.154);saveConfig();
                getLogger().info(Messages.t("log.motion-migrated"));
            }catch(IOException ex){throw new IllegalStateException("Cannot preserve the previous render configuration",ex);}
        }
        if(!getConfig().getString("render.fitting-profile","").equals("body-v2")){
            try{
                java.nio.file.Path previous=new File(getDataFolder(),"config-before-0.5.10.yml").toPath();
                if(!java.nio.file.Files.exists(previous))java.nio.file.Files.copy(new File(getDataFolder(),"config.yml").toPath(),previous);
                getConfig().set("render.fitting-profile","body-v2");getConfig().set("render.back-clearance",Math.max(.008,getConfig().getDouble("render.back-clearance",.008)));
                for(String key:List.of("back-offset","turn-back-offset","turn-lag-angle"))getConfig().set("render."+key,null);
                saveConfig();getLogger().info(Messages.t("log.fitting-migrated"));
            }catch(IOException ex){throw new IllegalStateException("Cannot preserve fitting configuration",ex);}
        }
        secret=getConfig().getString("sync-secret","");
    }
    @Override public void onDisable(){if(poses!=null)poses.close();for(Player p:getServer().getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof CapeMenu.View)p.closeInventory();if(renderer!=null)renderer.close();getServer().getMessenger().unregisterIncomingPluginChannel(this);getServer().getMessenger().unregisterOutgoingPluginChannel(this);}
    TextCatalog gui(){return gui;}Wire.State state(Player p){return states.get(p.getUniqueId());}boolean packReady(UUID id){return ready.contains(id);}
    void openWhenReady(Player p){openWhenReady(p,false);}
    void openWhenReady(Player p,boolean dynamic){waitingMenu.put(p.getUniqueId(),dynamic);request(p);}
    private void request(Player p){p.sendPluginMessage(this,Wire.CHANNEL,Wire.request());}
    @EventHandler public void join(PlayerJoinEvent e){renderer.join(e.getPlayer());for(int delay:new int[]{10,40,100})getServer().getScheduler().runTaskLater(this,()->{if(e.getPlayer().isOnline())request(e.getPlayer());},delay);}
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();renderer.forget(id);states.remove(id);proxyVersions.remove(id);offers.remove(id);packs.remove(id);localPackStatus.remove(id);ready.remove(id);waitingMenu.remove(id);clicks.remove(id);}
    @EventHandler public void world(PlayerChangedWorldEvent e){renderer.cancelPreview(e.getPlayer().getUniqueId());request(e.getPlayer());}
    @EventHandler public void interact(PlayerInteractEvent e){getServer().getScheduler().runTask(this,()->renderer.equipment(e.getPlayer()));}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void swing(PlayerAnimationEvent e){if(e.getAnimationType()==PlayerAnimationType.ARM_SWING)renderer.swing(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void equipmentClick(org.bukkit.event.inventory.InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p)getServer().getScheduler().runTask(this,()->renderer.equipment(p));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void equipmentDrag(org.bukkit.event.inventory.InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p)getServer().getScheduler().runTask(this,()->renderer.equipment(p));}
    @EventHandler public void chunk(ChunkLoadEvent e){renderer.cleanup(e.getChunk());}
    @EventHandler public void pack(PlayerResourcePackStatusEvent e){
        UUID id=e.getPlayer().getUniqueId();PackSnapshot pack=packs.get(id);if(pack==null||!pack.present())return;
        UUID eventId=PackOffer.eventId(e);if(eventId!=null&&!eventId.equals(pack.attempt()))return;
        if(eventId==null&&!offers.containsKey(id)&&pack.status()!=PackStatus.PENDING&&pack.status()!=PackStatus.ACCEPTED&&pack.status()!=PackStatus.DOWNLOADED)return;
        String name=e.getStatus().name();PackStatus status=switch(name){case "SUCCESSFULLY_LOADED"->PackStatus.LOADED;case "ACCEPTED"->PackStatus.ACCEPTED;case "DOWNLOADED"->PackStatus.DOWNLOADED;case "DECLINED"->PackStatus.DECLINED;case "DISCARDED"->PackStatus.DISCARDED;default->PackStatus.FAILED;};
        PackStatus previous=localPackStatus.getOrDefault(id,pack.status());if(!status.canFollow(previous))return;
        localPackStatus.put(id,status);applyReady(e.getPlayer(),status==PackStatus.LOADED);reportPack(e.getPlayer(),pack,status);
        if(status==PackStatus.FAILED)Messages.send(e.getPlayer(),name.equals("FAILED_DOWNLOAD")?"pack.download-failed":"pack.reload-failed");
    }
    private void applyReady(Player p,boolean value){boolean changed=value?ready.add(p.getUniqueId()):ready.remove(p.getUniqueId());if(changed){renderer.refreshViewer(p);renderer.refresh(p.getUniqueId());menu.refresh(p);}}
    private void reportPack(Player p,PackSnapshot pack,PackStatus status){
        Wire.State state=states.get(p.getUniqueId());if(state==null)return;
        try{p.sendPluginMessage(this,Wire.CHANNEL,Wire.packReport(new Wire.PackReport(p.getUniqueId(),pack.session(),pack.attempt(),state.packSha1(),status,PackOffer.identified()),secret));}
        catch(IOException failure){getLogger().warning(Messages.t("log.sync-discarded","error",Messages.error(failure)));}
    }
    private void offerPack(Player p,Wire.State state){
        PackSnapshot pack=state.pack();if(!pack.present()||!pack.offer())return;
        if(state.packUrl().isBlank()||state.packSha1().isBlank()){Messages.send(p,"pack.unconfigured");return;}
        if(pack.attempt().equals(offers.put(p.getUniqueId(),pack.attempt())))return;
        localPackStatus.put(p.getUniqueId(),PackStatus.PENDING);reportPack(p,pack,PackStatus.PENDING);
        PackOffer.send(p,state.packUrl(),HexFormat.of().parseHex(state.packSha1()),pack.attempt());
    }
    private void syncPack(Player p,Wire.State state){
        PackSnapshot next=state.pack(),old=packs.get(p.getUniqueId());
        if(old!=null&&old.session().equals(next.session())&&old.revision()>next.revision())return;
        packs.put(p.getUniqueId(),next);
        if(old==null||!old.attempt().equals(next.attempt())){offers.remove(p.getUniqueId());localPackStatus.remove(p.getUniqueId());}
        PackStatus local=localPackStatus.get(p.getUniqueId());
        PackStatus effective=local!=null&&local.canFollow(next.status())?local:next.status();
        applyReady(p,effective==PackStatus.LOADED);offerPack(p,state);
    }
    private void retryPack(Player p,Wire.State state){
        try{PackSnapshot pack=state.pack();p.sendPluginMessage(this,Wire.CHANNEL,Wire.packRetry(new Wire.PackRetry(p.getUniqueId(),pack.session(),pack.attempt(),state.packSha1()),secret));}
        catch(IOException failure){Messages.send(p,"pack.unconfigured");}
    }
    @Override public void onPluginMessageReceived(String channel,Player carrier,byte[] bytes){if(!channel.equals(Wire.CHANNEL)||secret.length()<32)return;
        try{if(RuntimeVersion.matches(bytes)){RuntimeVersion version=RuntimeVersion.decode(bytes,secret);if(version.player().equals(carrier.getUniqueId()))proxyVersions.put(version.player(),version.version());return;}
            Wire.State state=Wire.decode(bytes,secret);if(!state.player().equals(carrier.getUniqueId()))return;Player p=getServer().getPlayer(state.player());if(p==null)return;
            states.put(state.player(),state);renderer.permissions(state.player(),state.capes());Wire.Cape selected=state.capes().stream().filter(c->c.id().equals(state.selected())&&c.available()).findFirst().orElse(null);
            if(selected!=null&&!supported(selected)){getLogger().warning(Messages.t("log.material-unsupported","id",selected.id()));selected=null;}renderer.select(state.player(),selected);
            syncPack(p,state);
            if(!state.notice().isEmpty()){Notice notice=Notice.decode(state.notice());if(notice.endPreview())renderer.cancelPreview(state.player());if(!notice.text().isEmpty())p.sendMessage(notice.text());menu.refresh(p);if(notice.success())p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.35f,1.4f);}
            Boolean pending=waitingMenu.remove(state.player());if(pending!=null)menu.open(p,pending);else menu.refresh(p);
        }catch(Exception ex){getLogger().fine(Messages.t("log.sync-discarded","error",Messages.error(ex)));}
    }
    private boolean supported(Wire.Cape cape){Material m=Material.matchMaterial(cape.material());return m!=null&&(cape.kind().equals("BLOCK")?m.isBlock()&&!m.isAir():m.isItem());}
    void choose(Player p,String id){long now=System.currentTimeMillis();if(now-clicks.getOrDefault(p.getUniqueId(),0L)<500)return;Wire.State state=state(p);if(state==null){openWhenReady(p);return;}
        if(!id.isEmpty()&&state.capes().stream().noneMatch(c->c.id().equals(id)&&c.available())){Messages.send(p,"cape.unavailable");return;}
        try{clicks.put(p.getUniqueId(),now);p.sendPluginMessage(this,Wire.CHANNEL,Wire.select(p.getUniqueId(),id,secret));Messages.send(p,"cape.saving");}catch(IOException ex){Messages.send(p,"cape.sync-unconfigured");}
    }
    void preview(Player p,Wire.Cape cape){renderer.preview(p,cape);Messages.send(p,"cape.preview","cape",cape.title(),"id",cape.id(),"player",p.getName());}
    void cancelPreview(Player p){renderer.cancelPreview(p.getUniqueId());}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equalsIgnoreCase("cape")&&args.length==1&&(args[0].equalsIgnoreCase("ver")||args[0].equalsIgnoreCase("version"))){
            String proxy=sender instanceof Player p?proxyVersions.getOrDefault(p.getUniqueId(),Messages.t("version.unknown")):Messages.t("version.console");
            Messages.send(sender,"version.info","backend",getDescription().getVersion(),"proxy",proxy);return true;
        }
        if(command.getName().equalsIgnoreCase("cape")||command.getName().equalsIgnoreCase("dynamiccape")){if(sender instanceof Player p){if(!p.hasPermission("capebridge.use")){Messages.send(p,"menu.no-permission");return true;}if(menu.allowOpen(p)){
            if(args.length==1&&args[0].equalsIgnoreCase("pack")){Wire.State state=state(p);if(state==null){request(p);Messages.send(p,"menu.syncing-pack");}else{retryPack(p,state);}}
            else menu.open(p,command.getName().equalsIgnoreCase("dynamiccape")||(args.length==1&&args[0].equalsIgnoreCase("dynamic")));
        }}else Messages.send(sender,"admin.players-only");return true;}
        if(!sender.hasPermission("capebridge.admin")){Messages.send(sender,"admin.no-permission");return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("pose")){
            if(args.length==4&&args[1].equals("reference")){
                Player target=getServer().getPlayerExact(args[2]);if(target==null){Messages.send(sender,"admin.offline");return true;}
                try{Messages.send(sender,"pose.export-queued","file",poses.reference(target,args[3]));}
                catch(Exception failure){Messages.send(sender,"pose.command-failed","error",failure.getClass().getSimpleName());}
                return true;
            }
            if(args.length!=3||!Set.of("watch","stop","export").contains(args[1])){Messages.send(sender,"pose.usage");return true;}
            Player target=getServer().getPlayerExact(args[2]);if(target==null){Messages.send(sender,"admin.offline");return true;}
            try{
                switch(args[1]){
                    case "watch" -> Messages.send(sender,poses.watch(target)?"pose.watching":"pose.limit","player",target.getName());
                    case "stop" -> {poses.unwatch(target.getUniqueId());Messages.send(sender,"pose.stopped","player",target.getName());}
                    case "export" -> Messages.send(sender,"pose.export-queued","file",poses.export(target));
                }
            }catch(RuntimeException failure){Messages.send(sender,"pose.command-failed","error",failure.getClass().getSimpleName());}
            return true;
        }
        if(args.length>0&&(args[0].equalsIgnoreCase("texts")||args[0].equalsIgnoreCase("messages"))){try{loadTexts();menu.reloadTitles();Messages.send(sender,"admin.texts-reloaded");}catch(RuntimeException failure){Messages.send(sender,"admin.reload-failed","error",Messages.error(failure));}return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("reload")){try{loadTexts();reloadConfig();loadSettings();renderer.close();renderer=new CapeRenderer(this);for(Player p:getServer().getOnlinePlayers())request(p);menu.reloadTitles();Messages.send(sender,"admin.reloaded");}catch(RuntimeException failure){Messages.send(sender,"admin.reload-failed","error",Messages.error(failure));}}
        else if(args.length==2&&args[0].equalsIgnoreCase("inspect")){Player p=getServer().getPlayerExact(args[1]);Messages.send(sender,"admin.inspect","details",p==null?Messages.t("admin.offline"):renderer.inspect(p));}
        else if(args.length>0&&args[0].equalsIgnoreCase("resetstats")){renderer.reset();Messages.send(sender,"admin.resetstats");}
        else Messages.send(sender,"admin.stats","details",renderer.stats());return true;
    }
}
