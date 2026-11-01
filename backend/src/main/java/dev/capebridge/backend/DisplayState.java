package dev.capebridge.backend;
import com.github.retrooper.packetevents.protocol.entity.data.*;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import java.util.*;
/** Resolve native slots once on an invisible display; no per-tick NMS access. */
final class DisplayState {
 private final List<EntityData<?>> base;
 private final int delay,duration,translation,scale,left,right,item,billboard;
 private ItemStack ownItem,currentItem;
 private long itemRevision;
 private double playerHeight;
 private volatile List<EntityData<?>> frame;
 private volatile List<EntityData<?>> observerFrame;
 private DisplayState(List<EntityData<?>> base,int delay,int duration,int translation,int scale,int left,int right,int item,int billboard,ItemStack own){
  this.base=List.copyOf(base);this.delay=delay;this.duration=duration;this.translation=translation;this.scale=scale;this.left=left;this.right=right;this.item=item;this.billboard=billboard;ownItem=own;
 }
 static DisplayState capture(ItemDisplay display,ItemStack own,double height,boolean selfBillboard){
  Transformation pose=display.getTransformation();
  org.bukkit.entity.Display.Billboard original=display.getBillboard();
  try{
   display.setInterpolationDelay(41);display.setInterpolationDuration(17);
   display.setTransformation(new Transformation(pose.getTranslation(),new org.joml.Quaternionf().rotationY(.123f),new org.joml.Vector3f(2.1f,2.2f,2.3f),new org.joml.Quaternionf().rotationX(.456f)));
   var snapshot=NativeMetadata.read(display);int billboard=-1;
   if(selfBillboard){
    display.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);var vertical=NativeMetadata.read(display);
    billboard=billboardIndex(snapshot,vertical);
   }
   List<EntityData<?>> data=new ArrayList<>();
   for(var e:snapshot.entrySet())data.add(convert(e.getKey(),e.getValue(),display));
   int delay=index(data,d->Objects.equals(d.getValue(),41)),duration=index(data,d->Objects.equals(d.getValue(),17));
   List<Integer> vectors=data.stream().filter(d->d.getType()==EntityDataTypes.VECTOR3F).map(EntityData::getIndex).sorted().toList();
   List<Integer> rotations=data.stream().filter(d->d.getType()==EntityDataTypes.QUATERNION).map(EntityData::getIndex).sorted().toList();
   if(vectors.size()!=2||rotations.size()!=2)throw new IllegalStateException("Unsupported native display metadata schema");
   DisplayState state=new DisplayState(data,delay,duration,vectors.get(0),vectors.get(1),rotations.get(0),rotations.get(1),index(data,d->d.getType()==EntityDataTypes.ITEMSTACK),billboard,own);
   state.currentItem=SpigotConversionUtil.fromBukkitItemStack(display.getItemStack());state.update(pose,0,height);return state;
  }finally{display.setBillboard(original);display.setInterpolationDelay(0);display.setInterpolationDuration(0);display.setTransformation(pose);}
 }
 void item(org.bukkit.inventory.ItemStack paper,ItemStack own){currentItem=SpigotConversionUtil.fromBukkitItemStack(paper);ownItem=own;itemRevision++;}
 long itemRevision(){return itemRevision;}
 private static EntityData<?> convert(int index,Object value,ItemDisplay display){
  if(value instanceof Byte b)return new EntityData<>(index,EntityDataTypes.BYTE,b);
  if(value instanceof Integer n)return new EntityData<>(index,EntityDataTypes.INT,n);
  if(value instanceof Float f)return new EntityData<>(index,EntityDataTypes.FLOAT,f);
  if(value instanceof Boolean b)return new EntityData<>(index,EntityDataTypes.BOOLEAN,b);
  if(value instanceof org.joml.Vector3f v)return new EntityData<>(index,EntityDataTypes.VECTOR3F,new Vector3f(v.x,v.y,v.z));
  if(value instanceof org.joml.Quaternionf q)return new EntityData<>(index,EntityDataTypes.QUATERNION,new Quaternion4f(q.x,q.y,q.z,q.w));
  if(value!=null&&value.getClass().getSimpleName().equals("ItemStack"))return new EntityData<>(index,EntityDataTypes.ITEMSTACK,SpigotConversionUtil.fromBukkitItemStack(display.getItemStack()));
  throw new IllegalStateException("Unexpected native display metadata type at "+index+": "+(value==null?"null":value.getClass().getName()));
 }
 private static int index(List<EntityData<?>> data,java.util.function.Predicate<EntityData<?>> match){
  var ids=data.stream().filter(match).map(EntityData::getIndex).toList();if(ids.size()!=1)throw new IllegalStateException("Cannot resolve native display metadata slot: "+ids);return ids.get(0);
 }
 static int billboardIndex(Map<Integer,Object> before,Map<Integer,Object> vertical){
  var changed=vertical.entrySet().stream().filter(e->!Objects.equals(before.get(e.getKey()),e.getValue())&&e.getValue() instanceof Byte).map(Map.Entry::getKey).toList();
  if(changed.size()!=1)throw new IllegalStateException("Cannot resolve display billboard metadata");return changed.get(0);
 }
 void update(Transformation pose,int ticks,double height){
  update(pose,ticks,pose,ticks,height);
 }
 void update(Transformation pose,int ticks,Transformation observerPose,int observerTicks,double height){
  playerHeight=height;
  frame=poseFrame(pose,ticks);observerFrame=poseFrame(observerPose,observerTicks);
 }
 private List<EntityData<?>> poseFrame(Transformation pose,int ticks){
  var p=pose.getTranslation();var s=pose.getScale();var l=pose.getLeftRotation();var r=pose.getRightRotation();
  return List.of(new EntityData<>(delay,EntityDataTypes.INT,0),new EntityData<>(duration,EntityDataTypes.INT,ticks),
   new EntityData<>(translation,EntityDataTypes.VECTOR3F,new Vector3f(p.x,p.y,p.z)),new EntityData<>(scale,EntityDataTypes.VECTOR3F,new Vector3f(s.x,s.y,s.z)),
   new EntityData<>(left,EntityDataTypes.QUATERNION,new Quaternion4f(l.x,l.y,l.z,l.w)),new EntityData<>(right,EntityDataTypes.QUATERNION,new Quaternion4f(r.x,r.y,r.z,r.w)));
 }
 List<EntityData<?>> frame(boolean serverModern,boolean viewerModern){
  return frame(true,serverModern,viewerModern);
 }
 List<EntityData<?>> frame(boolean owner,boolean serverModern,boolean viewerModern){
  List<EntityData<?>> selected=owner?frame:observerFrame;
  float offset=PassengerAnchor.viewerOffset(playerHeight,serverModern,viewerModern);if(offset==0)return selected;
  List<EntityData<?>> adjusted=new ArrayList<>(selected.size());
  for(EntityData<?> d:selected){if(d.getIndex()==translation){Vector3f p=(Vector3f)d.getValue();adjusted.add(new EntityData<>(translation,EntityDataTypes.VECTOR3F,new Vector3f(p.x,p.y+offset,p.z)));}else adjusted.add(d);}
  return List.copyOf(adjusted);
 }
 List<EntityData<?>> full(boolean owner,boolean serverModern,boolean viewerModern){
  Map<Integer,EntityData<?>> all=new TreeMap<>();for(EntityData<?> d:base)all.put(d.getIndex(),d);for(EntityData<?> d:frame(owner,serverModern,viewerModern))all.put(d.getIndex(),d);
  if(billboard>=0)all.put(billboard,new EntityData<>(billboard,EntityDataTypes.BYTE,(byte)(owner?1:0)));
  all.put(item,new EntityData<>(item,EntityDataTypes.ITEMSTACK,owner?ownItem:currentItem));return List.copyOf(all.values());
 }
 List<EntityData<?>> initial(boolean owner,boolean serverModern,boolean viewerModern){
  List<EntityData<?>> all=new ArrayList<>(full(owner,serverModern,viewerModern));
  // A newly mounted client has no previous passenger pose to interpolate from.
  for(int i=0;i<all.size();i++)if(all.get(i).getIndex()==duration)all.set(i,new EntityData<>(duration,EntityDataTypes.INT,0));
  return List.copyOf(all);
 }
}
