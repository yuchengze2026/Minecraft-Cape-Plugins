package dev.capebridge.backend;

/** Smoothly transitions between ordinary prediction and a longer prediction horizon. */
final class CadenceBlend {
    private float extra,total,targetExtra,targetTotal;
    private static float delta(float x){float d=x%720;if(d>=360)d-=720;if(d< -360)d+=720;return d;}
    float sample(float raw,float normal,float future,boolean ready){
        targetExtra=ready?delta(future-normal):0;
        if(!ready)extra=0;else extra+=Math.max(-9,Math.min(9,targetExtra-extra));
        targetTotal=delta(normal-raw)+extra;
        float rate=ready?18:36;
        total+=Math.max(-rate,Math.min(rate,targetTotal-total));
        return raw+total;
    }
    boolean settled(){return Math.abs(targetExtra-extra)<5&&Math.abs(targetTotal-total)<5;}
    void reset(){extra=total=targetExtra=targetTotal=0;}
}
