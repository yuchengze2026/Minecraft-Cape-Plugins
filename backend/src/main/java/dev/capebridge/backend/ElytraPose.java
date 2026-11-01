package dev.capebridge.backend;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Mojang ElytraModel angles; native model axes reflected to display Y-up coordinates. */
final class ElytraPose {
    float pitch=(float)Math.toRadians(15),roll=(float)Math.toRadians(15),yaw;
    private int flyingTicks;
    void step(boolean gliding,boolean crouch,double vx,double vy,double vz,int ticks) {
        float targetPitch=(float)Math.toRadians(crouch&&!gliding?40:15);
        float targetRoll=(float)Math.toRadians(crouch&&!gliding?45:15);
        float targetYaw=(float)Math.toRadians(crouch&&!gliding?5:0);
        if(gliding) {
            flyingTicks=Math.min(100,flyingTicks+ticks);
            double length=Math.sqrt(vx*vx+vy*vy+vz*vz);
            float spread=vy<0&&length>1e-8?1-(float)Math.pow(-vy/length,1.5):1;
            targetPitch=(float)Math.toRadians(15+5*spread);targetRoll=(float)Math.toRadians(15+75*spread);
        } else flyingTicks=0;
        float blend=1-(float)Math.pow(.9,ticks);
        pitch+=(targetPitch-pitch)*blend;roll+=(targetRoll-roll)*blend;yaw+=(targetYaw-yaw)*blend;
    }
    Transformation[] poses(float heading,float lookPitch,float ridingHeight,boolean crouch,boolean gliding) {
        float progress=gliding?Math.min(1,flyingTicks*flyingTicks/100f):0;
        Quaternionf body=new Quaternionf().rotationY((float)-Math.toRadians(heading))
                .rotateX((float)Math.toRadians(90+lookPitch)*progress);
        Transformation[] poses=new Transformation[2];
        for(int i=0;i<2;i++) {
            float side=i==0?1:-1;
            // PlayerRenderer scales the entire living model by 15/16. Its crouch
            // render offset is world-space; ElytraModel adds another 3 model pixels.
            Vector3f root=new Vector3f(side*.3125f,1.501f-(crouch&&!gliding?.1875f:0),-.125f)
                    .mul(.9375f).rotate(body).sub(0,ridingHeight+(crouch&&!gliding?.125f:0),0);
            Quaternionf rotation=new Quaternionf(body).rotateZ(side*roll).rotateY(-side*yaw).rotateX(pitch);
            // ItemDisplayRenderer adds Y=180 after the display transform. Cancel it
            // so the inward-extending native wing boxes do not become outward boards.
            poses[i]=new Transformation(root,rotation,new Vector3f(.9375f),new Quaternionf(0,1,0,0));
        }
        return poses;
    }
}
