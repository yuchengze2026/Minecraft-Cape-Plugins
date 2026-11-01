package dev.capebridge.backend;

/** Bounded, opt-in experiment: extrapolate only after consistent, fast body motion. */
final class HeadingLead {
    private boolean initialized,graceUsed;private float previous,lead,target;private int direction,count;
    private final boolean soft,grace;private final float predictionTicks,maxLead;
    private final int activationSamples;
    private final float ramp;
    HeadingLead(){this(false);}
    HeadingLead(boolean soft){this(soft,1f,80f);}
    HeadingLead(boolean soft,float predictionTicks,float maxLead){this(soft,predictionTicks,maxLead,false);}
    HeadingLead(boolean soft,float predictionTicks,float maxLead,boolean grace){this(soft,predictionTicks,maxLead,grace,3,18);}
    HeadingLead(boolean soft,float predictionTicks,float maxLead,boolean grace,int activationSamples,float ramp){this.soft=soft;this.predictionTicks=predictionTicks;this.maxLead=maxLead;this.grace=grace;this.activationSamples=Math.max(1,Math.min(3,activationSamples));this.ramp=Math.max(1,Math.min(60,ramp));}
    float sample(float phase){
        if(!Float.isFinite(phase))throw new IllegalArgumentException("non-finite heading");
        if(!initialized){initialized=true;previous=phase;return phase;}
        float delta=(phase-previous)%720f;if(delta>=360)delta-=720;if(delta< -360)delta+=720;previous=phase;
        int sign=delta>15?1:delta< -15?-1:0;
        if(grace&&sign==0&&Math.abs(delta)<1&&count>=3&&!graceUsed){graceUsed=true;return phase+lead;}
        graceUsed=false;
        if(sign==0){direction=0;count=0;}
        else if(sign!=direction){direction=sign;count=1;}else count=Math.min(3,count+1);
        target=count>=activationSamples?Math.max(-maxLead,Math.min(maxLead,delta*predictionTicks)):0;
        lead=soft?lead+Math.max(-ramp,Math.min(ramp,target-lead)):target;
        return phase+lead;
    }
    void reset(){initialized=false;graceUsed=false;direction=0;count=0;lead=target=0;}
    boolean active(){return count>=3||Math.abs(lead)>.001f;}
    boolean consistent(){return count>=3;}
    boolean settled(){return consistent()&&Math.abs(target-lead)<5;}
}
