package dev.capebridge.backend;
import dev.capebridge.common.TextCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
final class TextFiles {
 private static YamlConfiguration resource(String name)throws Exception{var c=new YamlConfiguration();try(var in=TextFiles.class.getClassLoader().getResourceAsStream(name)){if(in==null)throw new IOException("Missing bundled text file: "+name);c.load(new InputStreamReader(in,StandardCharsets.UTF_8));}return c;}
 private static Map<String,Object> flat(YamlConfiguration c){Map<String,Object> map=new LinkedHashMap<>();for(String key:c.getKeys(true))if(!c.isConfigurationSection(key))map.put(key,c.get(key)==null?"":c.get(key));return map;}
 static TextCatalog defaults(String name){try{return new TextCatalog(flat(resource(name)),Map.of());}catch(Exception e){throw new IllegalStateException("Invalid bundled texts",e);}}
 static TextCatalog load(Path folder,String name)throws Exception{
  Files.createDirectories(folder);Path path=folder.resolve(name);if(Files.isSymbolicLink(path))throw new IOException("Text configuration cannot be a symbolic link");
  var defaults=resource(name);var custom=new YamlConfiguration();boolean exists=Files.exists(path);if(exists){if(Files.size(path)>1_048_576)throw new IOException("Text configuration too large");custom.load(path.toFile());}
  Map<String,Object> base=flat(defaults),user=flat(custom);TextCatalog result=new TextCatalog(base,user);
  boolean changed=false;for(var e:base.entrySet())if(!user.containsKey(e.getKey())){custom.set(e.getKey(),e.getValue());changed=true;}
  if(changed||!exists){if(exists){Path backup=folder.resolve(name+".before-text-config");if(Files.isSymbolicLink(backup))throw new IOException("Invalid text backup path");if(!Files.exists(backup))Files.copy(path,backup);}custom.save(path.toFile());}
  return result;
 }
}
