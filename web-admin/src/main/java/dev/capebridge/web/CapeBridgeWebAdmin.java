package dev.capebridge.web;

import net.md_5.bungee.api.*;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.plugin.*;
import net.md_5.bungee.config.*;
import java.nio.file.*;

public final class CapeBridgeWebAdmin extends Plugin {
 private WebServer web;
 @Override public void onEnable(){
  try{
   Path data=SafeFiles.root(getDataFolder().toPath());WebMessages.reload(data);Path config=SafeFiles.inside(data,"config.yml");
   if(!Files.exists(config))try(var in=getResourceAsStream("config.yml")){Files.copy(in,config);}
   Configuration settings=ConfigurationProvider.getProvider(YamlConfiguration.class).load(config.toFile());
   Plugin cape=getProxy().getPluginManager().getPlugin("CapeBridgeProxy");if(cape==null)throw new IllegalStateException(WebMessages.raw("server.text-001"));
   CapeService service=new CapeService(cape,data,settings.getInt("backup-limit",20));
   web=new WebServer(settings.getString("bind","127.0.0.1"),settings.getInt("port",27780),settings.getString("public-base-url",""),service,failure->getLogger().log(java.util.logging.Level.WARNING,WebMessages.raw("server.text-002"),failure));
   getProxy().getPluginManager().registerCommand(this,new Command("capeweb",null){
    @Override public void execute(CommandSender sender,String[] args){
     if(!sender.hasPermission("capebridge.admin")){WebMessages.send(sender,"command.no-permission");return;}
     if(args.length>0&&(args[0].equalsIgnoreCase("texts")||args[0].equalsIgnoreCase("messages"))){try{WebMessages.reload(data);WebMessages.send(sender,"command.reloaded");}catch(Exception failure){WebMessages.send(sender,"command.failed","error",failure.getMessage());}return;}
     String url=web.url(),code=web.issueCode();
     WebMessages.sendAction(sender,"command.url",ClickEvent.Action.OPEN_URL,url,"command.open-hover","url",url);
     WebMessages.sendAction(sender,"command.code",ClickEvent.Action.COPY_TO_CLIPBOARD,code,"command.copy-hover","code",code);
    }
   });
   getLogger().info(WebMessages.raw("log.ready","url",web.url()));
  }catch(Exception failure){if(web!=null)web.close();throw new IllegalStateException(WebMessages.raw("server.text-007"),failure);}
 }
 @Override public void onDisable(){if(web!=null)web.close();}
}
