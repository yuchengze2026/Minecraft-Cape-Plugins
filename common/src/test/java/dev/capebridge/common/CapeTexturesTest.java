package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class CapeTexturesTest {
 @Test void perFrameTimingIsExplicitAndValidatedAgainstTheActualStrip(){
  String value=CapeTextures.animation(64,96,2,java.util.List.of(2,4,1));assertTrue(value.contains("\"frames\":[{\"index\":0,\"time\":2},{\"index\":1,\"time\":4},{\"index\":2,\"time\":1}]"));
  assertEquals(CapeTextures.animation(64,2),CapeTextures.animation(64,96,2,java.util.List.of()));
  assertThrows(IllegalArgumentException.class,()->CapeTextures.animation(64,96,2,java.util.List.of(2,4)));
  assertThrows(IllegalArgumentException.class,()->CapeTextures.animation(64,96,2,java.util.List.of(0,4,1)));
 }
 byte[] png(BufferedImage image)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
 @Test void frontArtworkMapsBothFacesAndAllFourEdgesWithoutCropping()throws Exception{
  var image=new BufferedImage(10,16,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<16;y++)for(int x=0;x<10;x++)image.setRGB(x,y,0xff000000|x<<16|y);
  var atlas=CapeTextures.read(png(image),false);assertEquals(64,atlas.getWidth());
  for(int y=0;y<16;y++)for(int x=0;x<10;x++){
   assertEquals(image.getRGB(x,y),atlas.getRGB(1+x,1+y));assertEquals(image.getRGB(x,y),atlas.getRGB(12+x,1+y));
  }
  assertEquals(image.getRGB(0,7),atlas.getRGB(0,8));assertEquals(image.getRGB(9,7),atlas.getRGB(11,8));
  assertEquals(image.getRGB(5,0),atlas.getRGB(6,0));assertEquals(image.getRGB(5,15),atlas.getRGB(16,0));
 }
 @Test void nativeAtlasIsPreservedAndAnimationFramesRemainDistinct()throws Exception{
  var strip=new BufferedImage(64,96,BufferedImage.TYPE_INT_ARGB);strip.setRGB(1,1,0xffff0000);strip.setRGB(1,33,0xff00ff00);strip.setRGB(1,65,0xff0000ff);
  var actual=CapeTextures.read(png(strip),true);assertEquals(96,actual.getHeight());assertEquals(0xff00ff00,actual.getRGB(1,33));
  assertThrows(IOException.class,()->CapeTextures.read(png(strip),false));
  assertThrows(IOException.class,()->CapeTextures.read(png(new BufferedImage(64,32,2)),true));
  String metadata=CapeTextures.animation(64,4);assertTrue(metadata.contains("\"width\":64,\"height\":32,\"frametime\":4"));
 }
 @Test void malformedRatiosAndUnboundedFrameCountsAreRejected()throws Exception{
  for(int height:new int[]{31,33,32*65})assertThrows(IOException.class,()->CapeTextures.read(png(new BufferedImage(64,height,2)),true));
  assertThrows(IOException.class,()->CapeTextures.read(png(new BufferedImage(41,64,2)),false));
  assertThrows(IllegalArgumentException.class,()->CapeTextures.animation(64,0));
 }
}
