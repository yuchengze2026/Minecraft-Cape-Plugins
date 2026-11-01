package dev.capebridge.web;
import dev.capebridge.common.TextCatalog;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
final class WebMessages {
 private static volatile TextCatalog current=TextFiles.defaults("messages.yml");
 static void reload(Path folder)throws IOException{current=TextFiles.load(folder,"messages.yml");}
 static String raw(String key,Object...args){return current.raw(key,args);}
 static void send(CommandSender sender,String key,Object...args){for(String line:current.lines(key,args))sender.sendMessage(TextComponent.fromLegacyText(line));}
 static BaseComponent[] clickable(String line,ClickEvent.Action action,String value,String hover){
  BaseComponent[] parts=TextComponent.fromLegacyText(line);
  for(BaseComponent part:parts){part.setClickEvent(new ClickEvent(action,value));part.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new Text(TextComponent.fromLegacyText(hover))));}
  return parts;
 }
 static void sendAction(CommandSender sender,String key,ClickEvent.Action action,String value,String hoverKey,Object...args){
  for(String line:current.lines(key,args))sender.sendMessage(clickable(line,action,value,current.text(hoverKey)));
 }
 static Map<String,String> publicTexts(){Map<String,String> out=new LinkedHashMap<>();TextCatalog snapshot=current;for(String key:snapshot.values().keySet())if(key.startsWith("web."))out.put(key,snapshot.raw(key));return out;}
 static String html(String source){Map<String,String> texts=publicTexts();Matcher m=Pattern.compile("\\{\\{(web\\.[a-zA-Z0-9_.-]+)}}").matcher(source);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(escape(texts.getOrDefault(m.group(1),""))));m.appendTail(out);return out.toString();}
 private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
