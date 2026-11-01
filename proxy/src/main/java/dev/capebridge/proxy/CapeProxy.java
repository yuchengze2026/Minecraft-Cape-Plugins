package dev.capebridge.proxy;

import dev.capebridge.common.*;
import dev.capebridge.common.Notice;
import net.md_5.bungee.api.*;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.*;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.*;
import net.md_5.bungee.config.*;
import net.md_5.bungee.event.EventHandler;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public final class CapeProxy extends Plugin implements Listener {
    private final PackSessions packSessions=new PackSessions();
    private final PackRoutes packRoutes=new PackRoutes();
    private final Properties assignments=new Properties();
    private final Map<UUID,Long> requests=new ConcurrentHashMap<>(),selections=new ConcurrentHashMap<>();
    private final StorageQueue io=new StorageQueue();
    private volatile boolean stopping;
    private volatile List<Wire.Cape> catalog=List.of();
    private volatile String secret="",packUrl="",packHash="";
    private final Map<UUID,String> accessState=new ConcurrentHashMap<>();
    private net.md_5.bungee.api.scheduler.ScheduledTask accessTask;
    private Path folder;private CatalogStore store;private PackHost host;
    private final ConfigurationProvider yaml=ConfigurationProvider.getProvider(YamlConfiguration.class);
    @Override public void onEnable(){
        folder=getDataFolder().toPath();
        try{
            Files.createDirectories(folder);Messages.use(TextFiles.load(folder,"messages.yml"));
            for(String name:List.of("capes","config.yml","capes.yml","config.properties","capes.properties","assignments.properties"))SafeFiles.checked(folder,folder.resolve(name));
            Files.createDirectories(folder.resolve("capes"));initialize();store=new CatalogStore(folder,getLogger()::warning);
            if(Files.exists(folder.resolve("assignments.properties")))try(InputStream in=Files.newInputStream(folder.resolve("assignments.properties"))){assignments.load(in);}
            reloadData();
        }catch(Exception ex){throw new IllegalStateException(Messages.error(new IOException("Cannot initialize CapeBridge")),ex);}
        getProxy().registerChannel(Wire.CHANNEL);getProxy().getPluginManager().registerListener(this,this);getProxy().getPluginManager().registerCommand(this,new Admin());
        accessTask=getProxy().getScheduler().schedule(this,()->{for(ProxiedPlayer p:getProxy().getPlayers()){String current=fingerprint(p);if(!current.equals(accessState.get(p.getUniqueId())))send(p,"");}},2,2,TimeUnit.SECONDS);
        getLogger().info(Messages.t("log.ready","version",getDescription().getVersion(),"count",catalog.size()));
    }
    private void initialize()throws IOException{
        if(!Files.exists(folder.resolve("config.yml"))){
            Configuration config=yaml.load(getResourceAsStream("config.yml"));Properties old=new Properties();
            if(Files.exists(folder.resolve("config.properties")))try(InputStream in=Files.newInputStream(folder.resolve("config.properties"))){old.load(in);}
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);config.set("sync-secret",old.getProperty("sync-secret",HexFormat.of().formatHex(bytes)));
            if(!old.getProperty("pack-url","").isBlank())config.set("resource-pack.public-url",old.getProperty("pack-url"));yaml.save(config,folder.resolve("config.yml").toFile());
        }
        if(!Files.exists(folder.resolve("capes.yml"))){
            Configuration config=yaml.load(getResourceAsStream("capes.yml"));
            if(Files.exists(folder.resolve("capes.properties"))){Properties old=new Properties();try(InputStream in=Files.newInputStream(folder.resolve("capes.properties"))){old.load(in);}
                for(String id:old.stringPropertyNames()){String[] p=old.getProperty(id).split(":");if(p.length!=3)continue;String base="capes."+id;boolean image=p[0].equals("ITEM");config.set(base+".type",image?"IMAGE":"BLOCK");config.set(base+".material",p[1]);config.set(base+".model",Integer.parseInt(p[2]));config.set(base+".enabled",!image||Files.exists(folder.resolve("capes").resolve(id+".png")));if(!config.contains(base+".name"))config.set(base+".name",id);}
            }yaml.save(config,folder.resolve("capes.yml").toFile());
        }
        try{
            Path help=folder.resolve("capes/README.txt");
            if(!Files.exists(help)||Files.readString(help).startsWith("把新披风 PNG 放进此 capes 文件夹"))
                try(InputStream in=getResourceAsStream("CAPES-README.txt")){Files.copy(in,help,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException ex){getLogger().warning(Messages.t("log.help-failed","error",Messages.error(ex)));}
    }
    private synchronized void reloadData()throws Exception{
        if(stopping)throw new IOException("Plugin is shutting down");
        Configuration settings=yaml.load(SafeFiles.checked(folder,folder.resolve("config.yml")).toFile());String nextSecret=settings.getString("sync-secret","");if(nextSecret.length()<32)throw new IOException("sync-secret must contain at least 32 characters");
        String url=settings.getString("resource-pack.public-url","");if(url.length()>2048||!(url.isBlank()||url.startsWith("http://")||url.startsWith("https://")))throw new IOException("Invalid public-url");
        configureRoutes(settings,url);
        List<Wire.Cape> next=store.load();String hash=PackBuilder.build(folder,next);byte[] bytes=Files.readAllBytes(folder.resolve("resource-pack.zip"));
        if(host!=null){host.close();host=null;}
        if(settings.getBoolean("resource-pack.embedded-host",true)){host=new PackHost(settings.getString("resource-pack.bind","127.0.0.1"),settings.getInt("resource-pack.port",25680),settings.getInt("resource-pack.requests-per-minute",600));host.publish(bytes);}
        secret=nextSecret;packUrl=dev.capebridge.common.PackUrl.versioned(url,hash);packHash=hash;catalog=next;
        for(ProxiedPlayer player:getProxy().getPlayers())send(player,"");
    }
    private void configureRoutes(Configuration settings,String url){
        packRoutes.configure(url,settings.getBoolean("resource-pack.local-routing.enabled",true),settings.getString("resource-pack.local-routing.url",url),
            settings.contains("resource-pack.local-routing.networks")?settings.getStringList("resource-pack.local-routing.networks"):List.of("127.0.0.0/8","10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","::1/128","fc00::/7"));
    }
    /** Publish only changed assets without interrupting the BC download listener. */
    public synchronized void refreshAssets()throws Exception{
        if(stopping)throw new IOException("Plugin is shutting down");
        var settings=yaml.load(SafeFiles.checked(folder,folder.resolve("config.yml")).toFile());
        String base=settings.getString("resource-pack.public-url","");configureRoutes(settings,base);
        List<Wire.Cape> next=store.load();String hash=PackBuilder.build(folder,next);
        byte[] bytes=Files.readAllBytes(folder.resolve("resource-pack.zip"));
        if(host!=null)host.publish(bytes);
        packUrl=PackUrl.versioned(base,hash);packHash=hash;catalog=next;
        for(ProxiedPlayer player:getProxy().getPlayers())send(player,"");
    }
    /** Visibility changes affect menus only; keep the pack and its download session intact. */
    public synchronized void refreshCatalog()throws Exception{
        if(stopping)throw new IOException("Plugin is shutting down");
        catalog=store.load();
        for(ProxiedPlayer player:getProxy().getPlayers())send(player,"");
    }
    @Override public synchronized void onDisable(){stopping=true;packSessions.clear();packRoutes.clear();getProxy().unregisterChannel(Wire.CHANNEL);if(accessTask!=null)accessTask.cancel();io.close();if(host!=null)host.close();}
    @EventHandler public void connected(ServerConnectedEvent event){getProxy().getScheduler().schedule(this,()->send(event.getPlayer(),""),600,TimeUnit.MILLISECONDS);}
    @EventHandler public void quit(PlayerDisconnectEvent event){packSessions.disconnect(event.getPlayer().getUniqueId(),event.getPlayer());packRoutes.disconnect(event.getPlayer().getUniqueId(),event.getPlayer());accessState.remove(event.getPlayer().getUniqueId());requests.remove(event.getPlayer().getUniqueId());selections.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void message(PluginMessageEvent event){
        if(!event.getTag().equals(Wire.CHANNEL))return;event.setCancelled(true);
        if(!(event.getSender() instanceof Server server)||!(event.getReceiver() instanceof ProxiedPlayer player)||player.getServer()!=server)return;
        long now=System.currentTimeMillis();UUID id=player.getUniqueId();
        if(Wire.isRequest(event.getData())){if(now-requests.getOrDefault(id,0L)<400)return;requests.put(id,now);send(player,"");return;}
        try{
            if(Wire.isPackReport(event.getData())){if(packSessions.report(id,player,server,Wire.packReport(event.getData(),secret)))send(player,"");return;}
            if(Wire.isPackRetry(event.getData())){if(packSessions.retry(id,player,Wire.packRetry(event.getData(),secret)))send(player,"");return;}
            Wire.Selection selection=Wire.selection(event.getData(),secret);if(!selection.player().equals(id))return;
            if(now-selections.getOrDefault(id,0L)<400)return;selections.put(id,now);
            if(!io.submit(()->{
                if(getProxy().getPlayer(id)!=player)return;
                Wire.Cape cape=catalog.stream().filter(c->c.id().equals(selection.cape())).findFirst().orElse(null);
                if(!selection.cape().isEmpty()&&(cape==null||!allowed(player,cape))){notice(player,"notice.unavailable",false,false);return;}
                try{saveSelection(id,selection.cape());if(selection.cape().isEmpty())notice(player,"notice.removed",false,true);else notice(player,"notice.equipped",true,true,"cape",cape.title());}
                catch(IOException ex){notice(player,"notice.save-failed",false,false);getLogger().warning(Messages.t("log.save-failed","error",Messages.error(ex)));}
            }))notice(player,"notice.busy",false,false);
        }catch(IOException ignored){/* Client-origin messages and forged requests never reach storage. */}
    }
    private boolean allowed(ProxiedPlayer player,Wire.Cape cape){return CapeAccess.allowed(cape.id(),player::hasPermission);}
    private String fingerprint(ProxiedPlayer p){return catalog.stream().map(c->{var personal=CapeAccess.forPlayer(c,p::hasPermission);return c.id()+":"+personal.available()+":"+personal.wardrobeVisible();}).collect(java.util.stream.Collectors.joining(","));}
    private void notice(ProxiedPlayer player,String key,boolean success,boolean end,Object...args){send(player,new Notice(success,end,Messages.t(key,args)).encode());}
    private void send(ProxiedPlayer player,String notice){
        Server server=player.getServer();if(server==null)return;
        try{
            List<Wire.Cape> visible=catalog.stream().map(c->CapeAccess.forPlayer(c,player::hasPermission)).toList();String selected;
            synchronized(assignments){selected=assignments.getProperty(player.getUniqueId().toString(),"");}
            String active=catalog.stream().anyMatch(c->c.id().equals(selected)&&allowed(player,c))?selected:"";
            // All cape models, including solid colours, use the shared pack.
            accessState.put(player.getUniqueId(),fingerprint(player));
            server.sendData(Wire.CHANNEL,new RuntimeVersion(player.getUniqueId(),getDescription().getVersion()).encode(secret));
            String routed=PackUrl.versioned(packRoutes.base(player.getUniqueId(),player,player.getAddress().getAddress()),packHash);
            server.sendData(Wire.CHANNEL,Wire.encode(new Wire.State(player.getUniqueId(),active,visible,routed,packHash,notice,packSessions.snapshot(player.getUniqueId(),player,server,packHash)),secret));
        }catch(IOException ex){getLogger().warning(Messages.t("log.sync-failed","error",Messages.error(ex)));}
    }
    private void saveSelection(UUID id,String cape)throws IOException{
        synchronized(assignments){Properties next=new Properties();next.putAll(assignments);if(cape.isEmpty())next.remove(id.toString());else next.setProperty(id.toString(),cape);
            Path temp=SafeFiles.temporary(folder,"assignments-");
            try{
                try(OutputStream out=Files.newOutputStream(temp)){next.store(out,"CapeBridge persistent cape selections");}
                CatalogStore.move(temp,SafeFiles.checked(folder,folder.resolve("assignments.properties")));assignments.clear();assignments.putAll(next);
            }finally{Files.deleteIfExists(temp);}
        }
    }
    private static void reply(CommandSender sender,String key,Object...args){String text=Messages.t(key,args);if(!text.isEmpty())for(String line:text.split("\\R",-1))sender.sendMessage(TextComponent.fromLegacyText(Messages.t("prefix")+line));}
    private final class Admin extends Command{
        Admin(){super("bcape",null);}
        @Override public void execute(CommandSender sender,String[] args){
            if(args.length==1&&args[0].equalsIgnoreCase("ver")){reply(sender,"version.proxy","version",getDescription().getVersion());return;}
            if(!sender.hasPermission("capebridge.admin")){reply(sender,"admin.no-permission");return;}
            if(args.length==0){reply(sender,"admin.usage");return;}
            if(args[0].equalsIgnoreCase("list")){reply(sender,"admin.list","capes",String.join(Messages.t("admin.list-separator"),catalog.stream().map(Wire.Cape::id).toList()));return;}
            if(!io.submit(()->{try{
                if(stopping||!sender.hasPermission("capebridge.admin"))return;
                if(sender instanceof ProxiedPlayer p&&getProxy().getPlayer(p.getUniqueId())!=p)return;
                switch(args[0].toLowerCase(Locale.ROOT)){
                    case "texts","messages"->{Messages.use(TextFiles.load(folder,"messages.yml"));reply(sender,"admin.texts-reloaded");}
                    case "reload","buildpack"->{Messages.use(TextFiles.load(folder,"messages.yml"));reloadData();reply(sender,"admin.reloaded","count",catalog.size());}
                    case "add","addblock"->{if(args.length!=3)throw new IOException("add <id> <file.png> / addblock <id> <MATERIAL>");store.add(args[1],args[2],args[0].equalsIgnoreCase("addblock"));reloadData();reply(sender,"admin.added","id",args[1]);}
                    case "set","clear"->{boolean clear=args[0].equalsIgnoreCase("clear");if(args.length!=(clear?2:3))throw new IOException("set <player|UUID> <id> / clear <player|UUID>");ProxiedPlayer p=getProxy().getPlayer(args[1]);UUID id=p==null?UUID.fromString(args[1]):p.getUniqueId();String cape=clear?"":args[2];if(!clear&&catalog.stream().noneMatch(c->c.id().equals(cape)))throw new IOException("Unknown cape");if(!clear&&p!=null&&!CapeAccess.allowed(cape,p::hasPermission))throw new IOException("Player needs "+CapeAccess.node(cape)+" or capebridge.cape.all first");saveSelection(id,cape);ProxiedPlayer online=getProxy().getPlayer(id);if(online!=null)notice(online,"notice.admin-updated",true,true);reply(sender,"admin.saved");}
                    default->throw new IOException("Unknown command; use bcape");
                }
            }catch(Exception ex){reply(sender,"admin.failed","error",Messages.error(ex));getLogger().warning(Messages.t("log.admin-failed","error",Messages.error(ex)));}}))reply(sender,"admin.busy");
        }
    }
}
