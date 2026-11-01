package dev.capebridge.backend;

/** One rigid cape: velocity-driven rearward lift, monotonic settling, no idle oscillation. */
final class CapeCloth {
    float angle, side;
    private final float[] horizontal=new float[8],height=new float[8],estimates=new float[7];
    private int cursor;
    static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    void step(float speed,float vertical,float turnRate,boolean crouch,boolean flying,int ticks,
              float maxBend,float maxSide,float motionScale){
        speed=clamp(speed,0,2.8f);
        // Overlapping two-tick velocities remove regular packet batching. Their median
        // also rejects an early/late packet at the edge of a fixed averaging window.
        for(int t=0;t<Math.max(1,Math.min(horizontal.length,ticks));t++){
            horizontal[cursor]=speed;height[cursor]=vertical;cursor=(cursor+1)%horizontal.length;
        }
        for(int i=0;i<estimates.length;i++)estimates[i]=(horizontal[(cursor+7-i)%8]+horizontal[(cursor+6-i+8)%8])*.5f;
        java.util.Arrays.sort(estimates);speed=clamp(estimates[estimates.length/2],0,.7f);
        float meanVertical=0;for(int i=0;i<4;i++)meanVertical+=height[(cursor+7-i)%8]*.25f;
        if(speed<.005f)speed=0;
        float rise=Math.abs(meanVertical)<.01f?0:Math.abs(meanVertical);
        float target=clamp((Math.min(55,speed*130)+Math.min(15,rise*20)
                +(flying&&speed>.02f?10:0))*clamp(motionScale,0,2)+(crouch?18:0),0,clamp(maxBend,18,80));
        // 90% less than the previous -.004 response and .15 degree maximum.
        float sideTarget=turnRate==0?0:clamp(-turnRate*.0004f,-clamp(maxSide,0,2),clamp(maxSide,0,2));
        for(int t=0;t<ticks;t++){
            angle+=(target-angle)*.35f;
            side+=(sideTarget-side)*.35f;
        }
        if(Math.abs(angle-target)<.001f)angle=target;
        if(Math.abs(side-sideTarget)<.00001f)side=sideTarget;
    }
}
