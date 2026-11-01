package dev.capebridge.web;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GifImporterTest {
 java.awt.image.BufferedImage png(GifImporter.Result r)throws Exception{return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(r.image())));}
 int pixel(java.awt.image.BufferedImage image,int frame,int x,int y){return image.getRGB(1+x,frame*32+1+y);}
 @Test void partialFramesRetainTheirBackgroundAndRestorePreviousImage()throws Exception{
  var r=GifImporter.convert(GifFixtures.make(false,"restoreToPrevious",false),64,"auto","contain");var image=png(r);
  assertEquals(List.of(2,4,1),r.frameTimes());assertEquals(350,r.durationMs());assertFalse(r.adapted());assertEquals(3,r.frames());
  assertEquals(0xff0000ff,pixel(image,1,3,4));assertEquals(0xffff0000,pixel(image,1,4,5));assertEquals(0xffff0000,pixel(image,2,3,4));assertEquals(0xff00ff00,pixel(image,2,9,15));
  assertDoesNotThrow(()->ImageRules.canonicalPng(Base64.getDecoder().decode(r.image()),true));
 }
 @Test void transparentBackgroundIsClearedLocallyAfterDisposalTwo()throws Exception{
  var r=GifImporter.convert(GifFixtures.make(true,"restoreToBackgroundColor",false),64,"front","contain");var image=png(r);
  assertEquals(0,pixel(image,0,9,15)>>>24);assertEquals(0xff0000ff,pixel(image,1,3,4));assertEquals(0,pixel(image,2,3,4)>>>24);assertEquals(0xffff0000,pixel(image,2,0,0));assertEquals(0xff00ff00,pixel(image,2,9,15));
 }
 @Test void interlacedRowsDecodeInTheirOriginalOrder()throws Exception{
  var image=png(GifImporter.convert(GifFixtures.make(false,"doNotDispose",true),64,"front","contain"));
  for(int y=0;y<16;y++)assertEquals(y%2==0?0xffff0000:0xff00ff00,pixel(image,0,0,y));
 }
 @Test void fastOrLongAnimationsKeepTheirCycleLengthWhileSamplingAtMost64Frames()throws Exception{
  var fast=GifImporter.schedule(Collections.nCopies(100,10));assertTrue(fast.size()<=20);assertEquals(20,fast.stream().mapToInt(GifImporter.Selection::ticks).sum());
  var large=GifImporter.schedule(Collections.nCopies(512,100));assertTrue(large.size()<=64);assertEquals(1024,large.stream().mapToInt(GifImporter.Selection::ticks).sum());
  for(var selection:large)assertTrue(selection.ticks()>=1&&selection.ticks()<=200);
  assertThrows(IOException.class,()->GifImporter.schedule(List.of(500000,500000)));
 }
 @Test void malformedAndOversizedInputsFailBeforeImport()throws Exception{
  assertThrows(IOException.class,()->GifImporter.convert(new byte[12],64,"front","contain"));
  assertThrows(IOException.class,()->GifImporter.convert(new byte[GifImporter.MAX_BYTES+1],64,"front","contain"));
  byte[] huge=GifFixtures.make(false,"doNotDispose",false);Arrays.fill(huge,6,10,(byte)255);assertThrows(IOException.class,()->GifImporter.convert(huge,64,"front","contain"));
  assertThrows(IOException.class,()->GifImporter.convert(GifFixtures.make(false,"doNotDispose",false),65,"front","contain"));
 }
}
