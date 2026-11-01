package dev.capebridge.backend;

/** Experimental turn filter: retain at most a small prediction after motion stops. */
final class BoundedDirectionGuard {
    private boolean initialized;
    private float raw, output;
    private int direction,stillSamples;
    private static float delta(float value){float d=value%720; if(d>=360)d-=720; if(d< -360)d+=720; return d;}
    float sample(float phase,float target){
        if(!initialized){initialized=true;raw=phase;output=target%720;return output;}
        float movement=delta(phase-raw);raw=phase;
        if(Math.abs(movement)>.5f){direction=movement>0?1:-1;stillSamples=0;}
        else stillSamples=Math.min(2,stillSamples+1);
        float change=delta(target-output);
        if(direction==0||change*direction>=0)output+=change;
        // One unchanged sample also occurs during uninterrupted turns when a
        // movement packet arrives after this tick. Confirm rest before release.
        if(stillSamples>=2){
            float ahead=delta(output-phase);
            if(Math.abs(ahead)>12) output+=Math.copySign(Math.min(3,Math.abs(ahead)-12),-ahead);
        }
        // Keep the same two-turn phase as the split display quaternions. An
        // unbounded float eventually quantizes fine turns after repeated laps.
        // All comparisons above already use directed modulo-720 differences.
        output%=720;
        return output;
    }
    void reset(){initialized=false;raw=output=0;direction=stillSamples=0;}
}
