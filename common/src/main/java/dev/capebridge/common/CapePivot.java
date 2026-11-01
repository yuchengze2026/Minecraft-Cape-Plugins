package dev.capebridge.common;

/** Bounded item-model origins shared by the pack and every supported client renderer. */
public final class CapePivot {
 public record Origin(double y,double z){}
 private static final Origin[] ORIGINS=new Origin[6];
 static {
  for(int armor=0;armor<3;armor++)for(int crouch=0;crouch<2;crouch++){
   boolean sneaking=crouch!=0;double pitch=sneaking?.5:0;
   double min=sneaking?18:6,max=80,middle=pitch+Math.toRadians((min+max)/2),half=Math.toRadians((max-min)/2);
   double radius=(back(armor)*Math.cos(pitch)+.003)/Math.cos(half);
   ORIGINS[armor*2+crouch]=new Origin(radius*Math.sin(middle),radius*Math.cos(middle));
  }
 }
 private CapePivot(){}
 public static int profile(int armor,boolean crouched){if(armor<0||armor>2)throw new IllegalArgumentException("Invalid back profile");return armor*2+(crouched?1:0);}
 public static Origin origin(int profile){if(profile<0||profile>=ORIGINS.length)throw new IllegalArgumentException("Invalid cape pivot");return ORIGINS[profile];}
 public static double back(int armor){if(armor<0||armor>2)throw new IllegalArgumentException("Invalid back profile");return (armor==2?3:armor==1?2.25:2)/16*.9375;}
}
