package dev.capebridge.backend;
import java.util.*;
/** Main-thread, applied movement events for one render interval. Never replay old turns. */
final class TurnPath {
 private final ArrayList<Float> pending=new ArrayList<>();
 private float start,end;
 private boolean overflow;
 void add(float from,float to){
  if(!Float.isFinite(from)||!Float.isFinite(to))return;
  if(!pending.isEmpty()&&Math.abs(CapePose.wrap(from-end))>.01f)clear();
  if(pending.isEmpty())start=from;
  end=to;
  if(pending.size()==64){overflow=true;return;}
  pending.add(CapePose.wrap(to-from));
 }
 float[] take(float from,float to){
  float[] result;
  // A teleport, rejected move, overflow or later modification invalidates this interval.
  // Repeated intermediate angles are never used to identify packet progress.
  if(overflow||pending.isEmpty()||Math.abs(CapePose.wrap(from-start))>.01f||Math.abs(CapePose.wrap(to-end))>.01f)
   result=new float[]{CapePose.wrap(to-from)};
  else {result=new float[pending.size()];for(int i=0;i<result.length;i++)result[i]=pending.get(i);}
  clear();return result;
 }
 void clear(){pending.clear();overflow=false;}
 int size(){return pending.size();}
}
