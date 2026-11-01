package dev.capebridge.common;

/** Stable action flags are independent of administrator-editable wording and colors. */
public record Notice(boolean success,boolean endPreview,String text) {
 private static final String PREFIX="@cbn1:";
 public String encode(){String tag=PREFIX+(success?"1":"0")+(endPreview?"1":"0")+":";int limit=1024-tag.length();String value=text;if(value.length()>limit){int end=limit;if(Character.isHighSurrogate(value.charAt(end-1)))end--;value=value.substring(0,end);}return tag+value;}
 public static Notice decode(String value){
  if(value.startsWith(PREFIX)&&value.length()>=9&&value.charAt(8)==':'&&(value.charAt(6)=='0'||value.charAt(6)=='1')&&(value.charAt(7)=='0'||value.charAt(7)=='1'))return new Notice(value.charAt(6)=='1',value.charAt(7)=='1',value.substring(9));
  return new Notice(false,false,value);
 }
}
