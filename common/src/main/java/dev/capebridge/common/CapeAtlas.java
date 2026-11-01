package dev.capebridge.common;

import java.util.*;

/** Mojang 64x32 atlas: cape box(10,16,1) at(0,0), mirrored elytra box(10,20,2) at(22,0). */
public final class CapeAtlas {
    private CapeAtlas() {}
    public static Map<String,int[]> cape(int part) {
        int v0=part<0?1:1+part*4,v1=part<0?17:v0+4;
        Map<String,int[]> f=new LinkedHashMap<>();
        f.put("north",new int[]{1,v0,11,v1});f.put("south",new int[]{12,v0,22,v1});
        f.put("west",new int[]{0,v0,1,v1});f.put("east",new int[]{11,v0,12,v1});
        if(part<0||part==0)f.put("up",new int[]{1,0,11,1});
        if(part<0||part==CapeMesh.PARTS-1)f.put("down",new int[]{11,0,21,1});
        return f;
    }
    public static Map<String,int[]> wing(boolean mirror) {
        Map<String,int[]> f=new LinkedHashMap<>();
        // Native Y-down/Z-back cube is reflected into display Y-up/Z-forward.
        // Thus native SOUTH is display NORTH; preserve each native vertex's UV.
        f.put("north",new int[]{36,2,46,22});f.put("south",new int[]{24,2,34,22});
        f.put("west",new int[]{22,2,24,22});f.put("east",new int[]{34,2,36,22});
        f.put("up",new int[]{24,0,34,2});f.put("down",new int[]{34,2,44,0});
        if(mirror){
            int[] west=f.get("west");f.put("west",f.get("east"));f.put("east",west);
            for(String face:f.keySet()){
                int[] uv=f.get(face);int u=uv[0];uv[0]=uv[2];uv[2]=u;
            }
        }
        return f;
    }
}
