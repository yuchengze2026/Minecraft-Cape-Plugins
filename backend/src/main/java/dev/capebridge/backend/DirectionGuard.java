package dev.capebridge.backend;

/** Experimental: do not retract a predicted heading until actual body rotation reverses. */
final class DirectionGuard {
    private boolean initialized;private float raw,output;private int direction;
    private static float delta(float x){float d=x%720;if(d>=360)d-=720;if(d< -360)d+=720;return d;}
    float sample(float rawPhase,float target){
        if(!Float.isFinite(rawPhase)||!Float.isFinite(target))throw new IllegalArgumentException("non-finite heading");
        if(!initialized){initialized=true;raw=rawPhase;output=target;return output;}
        float movement=delta(rawPhase-raw);raw=rawPhase;
        if(Math.abs(movement)>.5f)direction=movement>0?1:-1;
        float change=delta(target-output);
        if(direction==0||change*direction>=0)output+=change;
        return output;
    }
    void reset(){initialized=false;raw=output=0;direction=0;}
}
