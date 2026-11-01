package dev.capebridge.web;

import dev.capebridge.common.CapeTextures;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.Node;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** GIF is composed once on import; the game receives only a normal PNG animation. */
final class GifImporter {
 record Selection(int index,int ticks){}
 record Result(String image,int frames,List<Integer> frameTimes,int sourceFrames,long sourceDurationMs,long durationMs,boolean adapted){}
 record Frame(int x,int y,int w,int h,int delay,boolean transparent,int transparentIndex,String disposal){}
 static final int MAX_BYTES=10*1024*1024;
 private static final class Invalid extends IOException {Invalid(String message){super(message);}}
 private static Invalid bad(String key){return new Invalid(WebMessages.raw(key));}
 private static Node child(Node node,String name){if(node!=null)for(Node n=node.getFirstChild();n!=null;n=n.getNextSibling())if(n.getNodeName().equals(name))return n;return null;}
 private static String attr(Node n,String name,String fallback){Node a=n==null?null:n.getAttributes().getNamedItem(name);return a==null?fallback:a.getNodeValue();}
 private static int number(Node n,String name,int fallback){return Integer.parseInt(attr(n,name,Integer.toString(fallback)));}
 static List<Selection> schedule(List<Integer> delays)throws IOException{
  if(delays.size()<2||delays.size()>512)throw bad("server.gif.limit");
  long total=0;for(int delay:delays){if(delay<1)throw bad("server.gif.invalid");total+=delay;}
  long ticks=Math.max(2,Math.round(total/50.0));if(ticks>12800)throw bad("server.gif.duration");
  int slots=(int)Math.min(64,ticks),index=0;long end=delays.get(0);List<Selection> result=new ArrayList<>();
  for(int slot=0;slot<slots;slot++){
   double time=(slot+.5)*total/slots;while(index<delays.size()-1&&time>=end)end+=delays.get(++index);
   int duration=(int)(Math.round((slot+1d)*ticks/slots)-Math.round((double)slot*ticks/slots));
   Selection last=result.isEmpty()?null:result.get(result.size()-1);
   if(last!=null&&last.index()==index&&last.ticks()+duration<=200)result.set(result.size()-1,new Selection(index,last.ticks()+duration));
   else result.add(new Selection(index,duration));
  }
  if(result.size()==1){Selection only=result.remove(0);int first=only.ticks()/2;result.add(new Selection(only.index(),first));result.add(new Selection(only.index(),only.ticks()-first));}
  return List.copyOf(result);
 }
 static Result convert(byte[] bytes,int width,String layout,String fit)throws IOException{
  if(bytes.length>MAX_BYTES)throw bad("server.gif.limit");
  if(bytes.length<13||!(new String(bytes,0,6,java.nio.charset.StandardCharsets.US_ASCII).equals("GIF87a")||new String(bytes,0,6,java.nio.charset.StandardCharsets.US_ASCII).equals("GIF89a")))throw bad("server.gif.invalid");
  if(!List.of(64,128,256,512).contains(width)||!List.of("auto","atlas","front").contains(layout)||!List.of("contain","cover").contains(fit))throw bad("server.gif.options");
  try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReadersByFormatName("gif");if(!readers.hasNext())throw bad("server.gif.invalid");ImageReader reader=readers.next();
   try{
    reader.setInput(input,false,false);Node stream=reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"),screen=child(stream,"LogicalScreenDescriptor"),palette=child(stream,"GlobalColorTable");
    int sw=number(screen,"logicalScreenWidth",0),sh=number(screen,"logicalScreenHeight",0);
    if(sw<1||sh<1||(long)sw*sh>8388608)throw bad("server.gif.limit");
    int count=reader.getNumImages(true);if(count<2||count>512)throw bad("server.gif.limit");
    List<Frame> frames=new ArrayList<>();List<Integer> delays=new ArrayList<>();long pixels=0,sourceDuration=0;
    for(int i=0;i<count;i++){
     Node root=reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0"),descriptor=child(root,"ImageDescriptor"),control=child(root,"GraphicControlExtension");
     int x=number(descriptor,"imageLeftPosition",0),y=number(descriptor,"imageTopPosition",0),w=number(descriptor,"imageWidth",0),h=number(descriptor,"imageHeight",0),delay=number(control,"delayTime",0);
     if(x<0||y<0||w<1||h<1||(long)x+w>sw||(long)y+h>sh)throw bad("server.gif.invalid");
     pixels+=(long)w*h;if(pixels>67108864)throw bad("server.gif.limit");
     int ms=delay==0?100:Math.multiplyExact(delay,10);delays.add(ms);sourceDuration+=ms;
     frames.add(new Frame(x,y,w,h,ms,attr(control,"transparentColorFlag","FALSE").equalsIgnoreCase("TRUE"),number(control,"transparentColorIndex",-1),attr(control,"disposalMethod","none")));
    }
    List<Selection> selected=schedule(delays);int bgIndex=number(palette,"backgroundColorIndex",-1),background=0;
    if(palette!=null)for(Node c=palette.getFirstChild();c!=null;c=c.getNextSibling())if(number(c,"index",-2)==bgIndex)background=0xff000000|number(c,"red",0)<<16|number(c,"green",0)<<8|number(c,"blue",0);
    int initial=frames.get(0).transparent()?0:background;
    BufferedImage canvas=new BufferedImage(sw,sh,BufferedImage.TYPE_INT_ARGB);fill(canvas,0,0,sw,sh,initial);
    BufferedImage strip=new BufferedImage(width,width/2*selected.size(),BufferedImage.TYPE_INT_ARGB);Graphics2D output=strip.createGraphics();int next=0;
    try{
     for(int i=0;i<count&&next<selected.size();i++){
      Frame f=frames.get(i);BufferedImage before=null;
      if(f.disposal().equals("restoreToPrevious")){before=new BufferedImage(sw,sh,BufferedImage.TYPE_INT_ARGB);Graphics2D g=before.createGraphics();g.setComposite(AlphaComposite.Src);g.drawImage(canvas,0,0,null);g.dispose();}
      BufferedImage patch=reader.read(i);if(patch.getWidth()!=f.w()||patch.getHeight()!=f.h())throw bad("server.gif.invalid");
      Graphics2D g=canvas.createGraphics();g.setComposite(AlphaComposite.SrcOver);g.drawImage(patch,f.x(),f.y(),null);g.dispose();
      if(selected.get(next).index()==i){BufferedImage atlas=atlas(canvas,width,layout,fit);while(next<selected.size()&&selected.get(next).index()==i){output.drawImage(atlas,0,next*width/2,null);next++;}}
      if(f.disposal().equals("restoreToBackgroundColor"))fill(canvas,f.x(),f.y(),f.w(),f.h(),f.transparent()&&f.transparentIndex()==bgIndex?0:initial);
      else if(f.disposal().equals("restoreToPrevious"))canvas=before;
     }
    }finally{output.dispose();}
    if(next!=selected.size())throw bad("server.gif.invalid");
    ByteArrayOutputStream encoded=new ByteArrayOutputStream();if(!ImageIO.write(strip,"png",encoded))throw bad("server.gif.invalid");byte[] png=encoded.toByteArray();if(png.length>ImageRules.MAX_BYTES)throw bad("server.gif.output-limit");
    List<Integer> times=selected.stream().map(Selection::ticks).toList();long duration=times.stream().mapToLong(t->t*50L).sum();boolean adapted=selected.size()!=count;
    for(int i=0;i<selected.size()&&!adapted;i++)adapted=selected.get(i).index()!=i||selected.get(i).ticks()*50!=delays.get(i);
    return new Result(Base64.getEncoder().encodeToString(png),times.size(),times,count,sourceDuration,duration,adapted);
   }finally{reader.dispose();}
  }catch(Invalid e){throw e;}catch(IOException|RuntimeException e){throw new IOException(WebMessages.raw("server.gif.invalid"),e);}
 }
 private static void fill(BufferedImage image,int x,int y,int w,int h,int color){Graphics2D g=image.createGraphics();g.setComposite(AlphaComposite.Src);g.setColor(new Color(color,true));g.fillRect(x,y,w,h);g.dispose();}
 private static BufferedImage atlas(BufferedImage source,int width,String layout,String fit){
  boolean atlas=layout.equals("atlas")||layout.equals("auto")&&Math.abs((double)source.getWidth()/source.getHeight()-2)<.01;
  BufferedImage target=new BufferedImage(atlas?width:width/64*10,atlas?width/2:width/64*16,BufferedImage.TYPE_INT_ARGB);Graphics2D g=target.createGraphics();
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
  if(atlas)g.drawImage(source,0,0,target.getWidth(),target.getHeight(),null);
  else {double scale=fit.equals("cover")?Math.max((double)target.getWidth()/source.getWidth(),(double)target.getHeight()/source.getHeight()):Math.min((double)target.getWidth()/source.getWidth(),(double)target.getHeight()/source.getHeight());AffineTransform transform=AffineTransform.getTranslateInstance((target.getWidth()-source.getWidth()*scale)/2,(target.getHeight()-source.getHeight()*scale)/2);transform.scale(scale,scale);g.drawImage(source,transform,null);}
  g.dispose();return atlas?target:CapeTextures.front(target);
 }
}
