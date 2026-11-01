package dev.capebridge.proxy;
import dev.capebridge.common.TextCatalog;
import net.md_5.bungee.config.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
final class TextFiles {
 private static final ConfigurationProvider YAML=ConfigurationProvider.getProvider(YamlConfiguration.class);
 private static Configuration resource(String name)throws IOException{try(var in=TextFiles.class.getClassLoader().getResourceAsStream(name)){if(in==null)throw new IOException("Missing bundled text file: "+name);return YAML.load(new InputStreamReader(in,StandardCharsets.UTF_8));}}
 private static void flatten(Configuration c,String prefix,Map<String,Object> out){for(String key:c.getKeys()){Object value=c.get(key);if(value instanceof Configuration section)flatten(section,prefix+key+".",out);else out.put(prefix+key,value==null?"":value);}}
 private static Map<String,Object> flat(Configuration c){Map<String,Object> out=new LinkedHashMap<>();flatten(c,"",out);return out;}
 static TextCatalog defaults(String name){try{return new TextCatalog(flat(resource(name)),Map.of());}catch(IOException e){throw new IllegalStateException("Invalid bundled texts",e);}}
 static TextCatalog load(Path folder,String name)throws IOException{
  Files.createDirectories(folder);Path path=SafeFiles.checked(folder,folder.resolve(name));boolean exists=Files.exists(path);if(exists&&Files.size(path)>1_048_576)throw new IOException("Text configuration too large");
  Configuration defaults=resource(name),custom=new Configuration();if(exists)try(var input=Files.newBufferedReader(path,StandardCharsets.UTF_8)){custom=YAML.load(input);}
  boolean changed=false;
  Map<String,Object> base=flat(defaults),user=flat(custom);TextCatalog result=new TextCatalog(base,user);
  for(var e:base.entrySet())if(!user.containsKey(e.getKey())){custom.set(e.getKey(),e.getValue());changed=true;}
  if(changed||!exists){if(exists){Path backup=SafeFiles.checked(folder,folder.resolve(name+".before-text-config"));if(!Files.exists(backup))Files.copy(path,backup);}Path temporary=SafeFiles.temporary(folder,"texts-");try{try(var output=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){YAML.save(custom,output);}CatalogStore.move(temporary,path);}finally{Files.deleteIfExists(temporary);}}
  return result;
 }
}
