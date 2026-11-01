package dev.capebridge.backend;

/** Keeps slow turns unmodified and introduces fast-turn compensation continuously. */
final class ContinuousTurnLead {
    private final float limit;
    private final float predictionTicks;
    private final HeadingLead original;
    private boolean initialized;
    private float previous,lead,blend;
    private boolean engaged;
    private boolean transition;
    private int direction,consistent,mediumSamples;

    ContinuousTurnLead(float limit){this(limit,1);}
    ContinuousTurnLead(float limit,float predictionTicks){this.limit=limit;this.predictionTicks=predictionTicks;original=new HeadingLead(true,predictionTicks,limit,false);}

    float sample(float phase){
        if(!Float.isFinite(phase))throw new IllegalArgumentException("non-finite heading");
        float reference=original.sample(phase);
        if(!initialized){initialized=true;previous=phase;return phase;}
        float movement=delta(phase-previous);previous=phase;
        int sign=Math.abs(movement)>.5f?(movement>0?1:-1):0;
        if(sign==0){consistent=0;direction=0;}
        else if(sign!=direction){direction=sign;consistent=1;}
        else consistent=Math.min(3,consistent+1);
        float referenceLead=reference-phase,speed=Math.abs(movement);
        mediumSamples=speed>=12&&speed<=25?mediumSamples+1:0;
        // Outside the transition band, retain the original fast-turn algorithm,
        // including its response to isolated packet gaps and fast acceleration.
        if(speed>30||speed<=.5f||lead*movement<0){
            transition=false;lead=referenceLead;blend=original.active()?1:0;return reference;
        }
        if(!transition){
            if(mediumSamples>=3){
                transition=true;lead=referenceLead;blend=original.active()?1:0;return reference;
            }
            boolean wouldFreeze=lead*movement>0&&Math.abs(referenceLead)<Math.abs(lead)
                &&(movement+referenceLead-lead)*movement<=0;
            if(wouldFreeze){transition=true;engaged=false;blend=0;}
            else{lead=referenceLead;return reference;}
        }
        // Separate enter/exit speeds prevent repeated activation around 15 degrees.
        // Slow turns that never enter the fast range stay exactly unmodified.
        if(Math.abs(movement)<=12||consistent<3)engaged=false;
        else if(Math.abs(movement)>=15.5f)engaged=true;
        blend+=Math.max(-.25f,Math.min(.25f,(engaged?1:0)-blend));
        float target=Math.max(-limit,Math.min(limit,movement*predictionTicks))*smooth(blend);
        float change=target-lead;
        // Releasing more prediction than the body advances would make the old
        // direction guard freeze the cape. Release gradually while moving; retain
        // the original release speed at rest and during an actual reversal.
        boolean releasing=lead*movement>0&&Math.abs(target)<Math.abs(lead);
        float release=releasing?Math.max(.25f,Math.abs(movement)*.5f):18;
        float rate=change*lead<0?release:18;
        lead+=Math.max(-rate,Math.min(rate,change));
        if(Math.abs(lead)<.0001f&&target==0)lead=0;
        return phase+lead;
    }

    static float delta(float value){float d=value%720;if(d>=360)d-=720;if(d< -360)d+=720;return d;}
    private static float smooth(float value){float x=Math.max(0,Math.min(1,value));return x*x*(3-2*x);}
    void reset(){original.reset();initialized=engaged=transition=false;previous=lead=blend=0;direction=consistent=mediumSamples=0;}
}
