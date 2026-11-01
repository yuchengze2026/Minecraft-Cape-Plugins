package dev.capebridge.backend;

/** Experimental tick-domain velocity filter; the output stays anchored to the latest target. */
final class HeadingSmoother {
    private boolean initialized,engaged;private float raw,output,velocity;
    private static float delta(float x){float d=x%720;if(d>=360)d-=720;if(d< -360)d+=720;return d;}
    float sample(float rawPhase,float target,boolean fast){
        if(!Float.isFinite(rawPhase)||!Float.isFinite(target))throw new IllegalArgumentException("non-finite heading");
        if(!initialized){initialized=true;raw=rawPhase;output=target;return target;}
        float move=delta(rawPhase-raw);raw=rawPhase;
        if(!engaged&&!fast){output=target;velocity=move;return target;}
        if(!engaged){engaged=true;velocity=move;output=target;return output;}
        velocity+=.35f*(move-velocity);
        float predicted=output+velocity,error=delta(target-predicted);
        output=predicted+Math.max(-20,Math.min(20,error*.35f));
        if(!fast&&Math.abs(move)<1&&Math.abs(delta(target-output))<2&&Math.abs(velocity)<2){engaged=false;output=target;velocity=0;}
        return output;
    }
    void reset(){initialized=false;engaged=false;raw=output=velocity=0;}
}
