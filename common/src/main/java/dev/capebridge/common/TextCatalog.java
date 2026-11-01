package dev.capebridge.common;

import java.util.*;
import java.util.regex.*;

/** Immutable text snapshot. Placeholders are literal data and never evaluated as code. */
public final class TextCatalog {
 private static final Pattern TOKEN=Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");
 private static final Pattern HEX=Pattern.compile("&#([0-9a-fA-F]{6})");
 private final Map<String,Object> values;
 public TextCatalog(Map<String,Object> defaults,Map<String,Object> custom){
  Map<String,Object> result=new LinkedHashMap<>();defaults.forEach((k,v)->result.put(k,checked(k,v)));custom.forEach((k,v)->result.put(k,checked(k,v)));values=Collections.unmodifiableMap(result);
 }
 private static Object checked(String key,Object value){
  if(value instanceof String s)return s;
  if(value instanceof List<?> list&&list.stream().allMatch(String.class::isInstance))return List.copyOf(list);
  throw new IllegalArgumentException("Text value must be a string or string list: "+key);
 }
 public Map<String,Object> values(){return values;}
 public String raw(String key,Object...args){
  Object value=values.get(key);if(value==null)throw new IllegalArgumentException("Unknown text key: "+key);
  Map<String,String> replacements=parameters(args);
  String template=asString(value);Matcher refs=TOKEN.matcher(template);
  while(refs.find()){String reference=refs.group(1);if(!replacements.containsKey(reference)&&values.containsKey(reference))replacements.put(reference,replace(asString(values.get(reference)),replacements));}
  return replace(template,replacements);
 }
 public String text(String key,Object...args){return color(raw(key,args));}
 public List<String> lines(String key,Object...args){String text=text(key,args);return text.isEmpty()?List.of():Arrays.asList(text.split("\\R",-1));}
 private static String asString(Object value){return value instanceof List<?> list?String.join("\n",list.stream().map(Object::toString).toList()):value.toString();}
 private static Map<String,String> parameters(Object[] args){if(args.length%2!=0)throw new IllegalArgumentException("Expected placeholder/value pairs");Map<String,String> out=new HashMap<>();for(int i=0;i<args.length;i+=2)out.put(args[i].toString(),String.valueOf(args[i+1]));return out;}
 private static String replace(String text,Map<String,String> values){Matcher m=TOKEN.matcher(text);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(values.getOrDefault(m.group(1),m.group())));m.appendTail(out);return out.toString();}
 public static String color(String input){
  Matcher m=HEX.matcher(input);StringBuffer out=new StringBuffer();while(m.find()){StringBuilder code=new StringBuilder("§x");for(char c:m.group(1).toCharArray())code.append('§').append(Character.toLowerCase(c));m.appendReplacement(out,Matcher.quoteReplacement(code.toString()));}m.appendTail(out);
  char[] chars=out.toString().toCharArray();for(int i=0;i+1<chars.length;i++)if(chars[i]=='&'&&"0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(chars[i+1])>=0){chars[i]='§';chars[i+1]=Character.toLowerCase(chars[i+1]);}return new String(chars);
 }
}
