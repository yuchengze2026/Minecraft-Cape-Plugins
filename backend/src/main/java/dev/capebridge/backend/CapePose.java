package dev.capebridge.backend;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.capebridge.common.CapeMesh;
import dev.capebridge.common.CapePivot;

/** Explicit quaternion and fixed scale; avoids per-frame matrix SVD axis/sign changes. */
final class CapePose {
    static final float PLAYER_SCALE=.9375f;
    static float backSurface(boolean jacket,boolean armor){return (armor?3:jacket?2.25f:2)/16*PLAYER_SCALE;}
    private CapePose() {}
    static Transformation hinged(float yaw,float lift,boolean crouched,float ridingHeight,float back){
        Quaternionf body=new Quaternionf().rotationY((float)-Math.toRadians(yaw)).rotateX(crouched?.5f:0);
        Vector3f pivot=new Vector3f(0,0,-back).rotate(body)
                .add(0,1.501f*PLAYER_SCALE-(crouched?.125f+.2f*PLAYER_SCALE:0)-ridingHeight,0);
        // Interpolate heading and fore/aft lift independently. Slerping the combined
        // yaw/pitch quaternion introduces unwanted sideways roll between tick endpoints.
        Quaternionf heading=new Quaternionf().rotationY((float)-Math.toRadians(yaw));
        Quaternionf bend=new Quaternionf().rotationX((crouched?.5f:0)+(float)Math.toRadians(lift)).mul(new Quaternionf(0,1,0,0));
        return new Transformation(pivot,heading,new Vector3f(1),bend);
    }
    static float wrap(float value) { float result = value % 360; if (result >= 180) result -= 360; if (result < -180) result += 360; return result; }
    /** Split the directed yaw across both display quaternions. Each interpolates less
     * than a half-turn even when a merged tick contains up to a full body turn.
     * The phase is modulo 720, not 360: wrapping either half at 180 would reverse it. */
    static Transformation smoothHinged(float phase,float lift,boolean crouched,float ridingHeight,float back){
        phase%=720;
        Transformation pose=hinged(wrap(phase),lift,crouched,ridingHeight,back);
        Quaternionf half=new Quaternionf().rotationY((float)-Math.toRadians(phase*.5f));
        return new Transformation(pose.getTranslation(),half,pose.getScale(),new Quaternionf(half).mul(pose.getRightRotation()));
    }
    static Transformation pivoted(Transformation pose,int profile){
        var origin=CapePivot.origin(profile);
        // ItemDisplayRenderer applies Y=180 before the display transformation.
        Vector3f compensation=new Vector3f(0,(float)origin.y(),(float)-origin.z()).rotate(pose.getRightRotation()).rotate(pose.getLeftRotation());
        return new Transformation(new Vector3f(pose.getTranslation()).add(compensation),new Quaternionf(pose.getLeftRotation()),new Vector3f(pose.getScale()),new Quaternionf(pose.getRightRotation()));
    }
    static Transformation scaled(Transformation pose,float playerScale,float ridingHeight){
        return new Transformation(new Vector3f(pose.getTranslation()).mul(playerScale).add(0,-ridingHeight,0),
            new Quaternionf(pose.getLeftRotation()),new Vector3f(pose.getScale()).mul(playerScale),new Quaternionf(pose.getRightRotation()));
    }
    /** The native torso frame is applied before, and independently of, cape motion. */
    static Transformation attached(boolean block,float yaw,float side,float lift,boolean crouched,float ridingHeight,float back){
        Quaternionf heading=new Quaternionf().rotationY((float)-Math.toRadians(yaw));
        Quaternionf body=new Quaternionf(heading).rotateX(crouched?.5f:0);
        // HumanoidModel body.y=3.2 px while crouching; PlayerRenderer also lowers by .125.
        Vector3f pivot=new Vector3f(0,0,-back).rotate(body)
                .add(0,1.501f*PLAYER_SCALE-(crouched?.125f+.2f*PLAYER_SCALE:0)-ridingHeight,0);
        Quaternionf rotation=new Quaternionf(body).rotateZ((float)Math.toRadians(side)).rotateX((float)Math.toRadians(lift));
        Vector3f translation=(block?new Vector3f(-CapeMesh.WIDTH/2,-1,-CapeMesh.THICKNESS):new Vector3f(0,-.5f,-CapeMesh.THICKNESS/2)).rotate(rotation).add(pivot);
        return new Transformation(translation,rotation,block?new Vector3f(CapeMesh.WIDTH,1,CapeMesh.THICKNESS):new Vector3f(1),block?new Quaternionf():new Quaternionf(0,1,0,0));
    }
    static Transformation rigid(boolean block,float yaw,float side,float pitch,float shoulder,float ridingHeight,float offset){
        Quaternionf heading=new Quaternionf().rotationY((float)-Math.toRadians(yaw));
        Quaternionf rotation=new Quaternionf(heading).rotateZ((float)Math.toRadians(side)).rotateX((float)Math.toRadians(pitch));
        Vector3f pivot=new Vector3f(0,shoulder-ridingHeight,-offset).rotate(heading);
        // The hinge is the inner top edge on the back, not the slab centre.
        Vector3f translation=(block?new Vector3f(-CapeMesh.WIDTH/2,-1,-CapeMesh.THICKNESS):new Vector3f(0,-.5f,-CapeMesh.THICKNESS/2)).rotate(rotation).add(pivot);
        return new Transformation(translation,rotation,block?new Vector3f(CapeMesh.WIDTH,1,CapeMesh.THICKNESS):new Vector3f(1),block?new Quaternionf():new Quaternionf(0,1,0,0));
    }
    static Transformation pose(boolean block, float yawDegrees, float pitchRadians, float shoulder, float ridingHeight) {
        return pose(block, yawDegrees, pitchRadians, shoulder, ridingHeight, .155f);
    }
    static Transformation pose(boolean block, float yawDegrees, float pitchRadians, float shoulder, float ridingHeight, float backOffset) {
        Quaternionf heading = new Quaternionf().rotationY((float) -Math.toRadians(yawDegrees));
        Quaternionf cloth = new Quaternionf(heading).rotateX(pitchRadians);
        // The body back is 0.125 blocks from its centre; cloth half-thickness is 0.025.
        // Keep only 0.005 blocks of clearance at the attachment edge.
        Vector3f pivot = new Vector3f(0, shoulder - ridingHeight, -backOffset).rotate(heading);
        Vector3f translation = (block ? new Vector3f(-0.3125f, -1f, -0.025f) : new Vector3f(0, -0.5f, 0)).rotate(cloth).add(pivot);
        return new Transformation(translation, cloth, block ? new Vector3f(0.625f, 1, 0.05f) : new Vector3f(1), new Quaternionf());
    }
}
