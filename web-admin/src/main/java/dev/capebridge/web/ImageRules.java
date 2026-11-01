package dev.capebridge.web;

import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

final class ImageRules {
 static final int MAX_BYTES=1024*1024;
 private static final byte[] MAGIC={(byte)137,80,78,71,13,10,26,10};
 static byte[] canonicalPng(byte[] bytes) throws IOException {
  return canonicalPng(bytes,false);
 }
 static byte[] canonicalPng(byte[] bytes,boolean animated) throws IOException {
  if(bytes.length>MAX_BYTES||bytes.length<24||!Arrays.equals(MAGIC,Arrays.copyOf(bytes,8)))throw new IOException(WebMessages.raw("server.text-019"));
  try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException(WebMessages.raw("server.text-020"));
   ImageReader reader=readers.next();
   try{
    BufferedImage image;
    try{image=dev.capebridge.common.CapeTextures.read(bytes,animated);}
    catch(IOException invalid){throw new IOException(WebMessages.raw(animated?"server.animation.dimensions":"server.text-021"),invalid);}
    int width=image.getWidth(),height=image.getHeight();boolean visible=false;
    int scale=width/64;
    for(int offset=0;offset<height&&!visible;offset+=width/2)
     for(int y=scale;y<17*scale&&!visible;y++)for(int x=scale;x<22*scale;x++)if((image.getRGB(x,y+offset)>>>24)!=0){visible=true;break;}
    if(!visible)throw new IOException(WebMessages.raw("server.text-022"));
    var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"PNG",out))throw new IOException(WebMessages.raw("server.text-023"));
    byte[] result=out.toByteArray();if(result.length>MAX_BYTES)throw new IOException(WebMessages.raw("server.text-024"));return result;
   }finally{reader.dispose();}
  }catch(RuntimeException failure){throw new IOException(WebMessages.raw("server.text-025"),failure);}
 }
}
