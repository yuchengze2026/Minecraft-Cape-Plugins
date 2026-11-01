package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CapeAtlasTest {
 @Test void capeUsesAllSixOfficialCubeRegions(){
  var f=CapeAtlas.cape(-1);assertEquals(Set.of("north","south","west","east","up","down"),f.keySet());
  assertArrayEquals(new int[]{1,1,11,17},f.get("north"));assertArrayEquals(new int[]{12,1,22,17},f.get("south"));
  assertArrayEquals(new int[]{0,1,1,17},f.get("west"));assertArrayEquals(new int[]{11,1,12,17},f.get("east"));
  assertArrayEquals(new int[]{1,0,11,1},f.get("up"));assertArrayEquals(new int[]{11,0,21,1},f.get("down"));
 }
 @Test void segmentsReadSeparateFrontAndBackBandsWithoutInternalCaps(){
  for(int i=0;i<4;i++){
   var f=CapeAtlas.cape(i);assertArrayEquals(new int[]{1,1+i*4,11,5+i*4},f.get("north"));
   assertArrayEquals(new int[]{12,1+i*4,22,5+i*4},f.get("south"));
   assertEquals(i==0,f.containsKey("up"));assertEquals(i==3,f.containsKey("down"));
  }
 }
 @Test void wingsUseOfficialOffset22AndMirrorSharedTexture(){
  var left=CapeAtlas.wing(false);var right=CapeAtlas.wing(true);
  // Exported from official ModelPart.Cube; directions are display-space after Y/Z reflection.
  assertArrayEquals(new int[]{36,2,46,22},left.get("north"));assertArrayEquals(new int[]{24,2,34,22},left.get("south"));
  assertArrayEquals(new int[]{46,2,36,22},right.get("north"));
  assertArrayEquals(new int[]{36,2,34,22},right.get("west"));assertArrayEquals(new int[]{24,2,22,22},right.get("east"));
  assertArrayEquals(new int[]{34,2,44,0},left.get("down"));assertArrayEquals(new int[]{44,2,34,0},right.get("down"));
  assertNotEquals(CapeMesh.wingModel(10001,0),CapeMesh.wingModel(10001,1));
  assertTrue(CapeMesh.wingModel(10001,0)>CapeMesh.model(10001,3));
 }
}
