package dev.capebridge.web;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.*;
import java.io.*;
public final class GifFixtures {
 private static final IndexColorModel COLORS=new IndexColorModel(2,4,new int[]{0x00000000,0xffff0000,0xff0000ff,0xff00ff00},0,true,0,java.awt.image.DataBuffer.TYPE_BYTE);
 static BufferedImage image(int w,int h,int color){var result=new BufferedImage(w,h,BufferedImage.TYPE_BYTE_INDEXED,COLORS);for(int y=0;y<h;y++)for(int x=0;x<w;x++)result.getRaster().setSample(x,y,0,color);return result;}
 private static IIOMetadataNode node(IIOMetadataNode root,String name){for(int i=0;i<root.getLength();i++)if(root.item(i).getNodeName().equals(name))return (IIOMetadataNode)root.item(i);var n=new IIOMetadataNode(name);root.appendChild(n);return n;}
 public static byte[] make(boolean transparent,String disposal,boolean interlace)throws Exception{
  var bytes=new ByteArrayOutputStream();var writer=ImageIO.getImageWritersByFormatName("gif").next();
  try(var output=new MemoryCacheImageOutputStream(bytes)){
   writer.setOutput(output);writer.prepareWriteSequence(null);
   for(int i=0;i<3;i++){
    BufferedImage image=i==0?image(10,16,transparent?0:1):i==1?image(2,2,0):image(1,1,3);
    if(i==0&&transparent)image.getRaster().setSample(0,0,0,1);
    if(i==0&&interlace)for(int y=0;y<16;y++)for(int x=0;x<10;x++)image.getRaster().setSample(x,y,0,y%2==0?1:3);
    if(i==1)image.getRaster().setSample(0,0,0,2);
    var meta=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),writer.getDefaultWriteParam());var tree=(IIOMetadataNode)meta.getAsTree(meta.getNativeMetadataFormatName());
    var table=node(tree,"LocalColorTable");table.setAttribute("sizeOfLocalColorTable","4");table.setAttribute("sortFlag","FALSE");while(table.hasChildNodes())table.removeChild(table.getFirstChild());
    for(int color=0;color<4;color++){var entry=new IIOMetadataNode("ColorTableEntry");entry.setAttribute("index",Integer.toString(color));entry.setAttribute("red",Integer.toString(COLORS.getRed(color)));entry.setAttribute("green",Integer.toString(COLORS.getGreen(color)));entry.setAttribute("blue",Integer.toString(COLORS.getBlue(color)));table.appendChild(entry);}
    var descriptor=node(tree,"ImageDescriptor");descriptor.setAttribute("imageLeftPosition",i==1?"3":i==2?"9":"0");descriptor.setAttribute("imageTopPosition",i==1?"4":i==2?"15":"0");descriptor.setAttribute("interlaceFlag",interlace&&i==0?"TRUE":"FALSE");
    var control=node(tree,"GraphicControlExtension");control.setAttribute("disposalMethod",i==1?disposal:"doNotDispose");control.setAttribute("userInputFlag","FALSE");control.setAttribute("transparentColorFlag",transparent||i==1?"TRUE":"FALSE");control.setAttribute("transparentColorIndex","0");control.setAttribute("delayTime",Integer.toString(i==0?10:i==1?20:5));
    meta.setFromTree(meta.getNativeMetadataFormatName(),tree);writer.writeToSequence(new IIOImage(image,null,meta),null);
   }
   writer.endWriteSequence();output.flush();
  }finally{writer.dispose();}
  return bytes.toByteArray();
 }
 public static void main(String[] args)throws Exception{java.nio.file.Files.write(java.nio.file.Path.of(args[0]),make(false,"restoreToPrevious",false));}
}
