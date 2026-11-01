package dev.capebridge.backend;

import org.junit.jupiter.api.Test;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.capebridge.common.CapeMesh;
import static org.junit.jupiter.api.Assertions.*;

class BodyAttachmentTest {
    private Vector3f point(Transformation t,boolean block,float x,float y,float z){
        // Item coordinates are after the renderer's built-in Y flip is cancelled.
        return new Vector3f(x,y,z).mul(t.getScale()).rotate(t.getLeftRotation()).add(t.getTranslation());
    }
    @Test void allCapeCornersStayOutsideNativeTorsoPlaneIncludingFirstCrouchTick(){
        for(boolean block:new boolean[]{true,false})for(boolean crouched:new boolean[]{false,true})
        for(boolean armor:new boolean[]{false,true})for(boolean jacket:new boolean[]{false,true})
        for(int yaw=-180;yaw<=180;yaw+=30)for(int lift=0;lift<=65;lift+=5)for(float side:new float[]{-.015f,0,.015f}){
            float surface=CapePose.backSurface(jacket,armor),back=surface+.00075f,riding=crouched?1.125f:1.35f;
            Transformation t=CapePose.attached(block,yaw,side,lift,crouched,riding,back);
            float root=1.501f*.9375f-(crouched?.125f+.2f*.9375f:0)-riding;
            Quaternionf inverse=new Quaternionf().rotationY((float)-Math.toRadians(yaw)).rotateX(crouched?.5f:0).invert();
            for(float x:block?new float[]{0,1}:new float[]{-CapeMesh.WIDTH/2,CapeMesh.WIDTH/2})
            for(float y:block?new float[]{0,1}:new float[]{-.5f,.5f})
            for(float z:block?new float[]{0,1}:new float[]{-CapeMesh.THICKNESS/2,CapeMesh.THICKNESS/2}){
                Vector3f local=point(t,block,x,y,z).sub(0,root,0).rotate(inverse);
                assertTrue(local.z<=-surface-.0007f,"Cape crossed torso plane: "+local);
            }
            Vector3f hinge=point(t,block,block?.5f:0,block?1:.5f,block?1:CapeMesh.THICKNESS/2).sub(0,root,0).rotate(inverse);
            assertEquals(0,hinge.x,.000002);assertEquals(0,hinge.y,.000002);assertEquals(-back,hinge.z,.000002);
        }
    }
    @Test void repeatedShiftChangesOnlyBodyFrameNotMotionState(){
        CapeCloth moving=new CapeCloth(),reference=new CapeCloth();
        for(int i=0;i<400;i++){
            float speed=i<200?.28f:0;
            moving.step(speed,0,10,false,false,1,65,.015f,1);
            reference.step(speed,0,10,false,false,1,65,.015f,1);
            boolean crouched=i%2==0;
            Transformation t=CapePose.attached(true,35,moving.side,moving.angle,crouched,crouched?1.125f:1.35f,.13258594f);
            Quaternionf relative=new Quaternionf().rotationY((float)-Math.toRadians(35)).rotateX(crouched?.5f:0).invert().mul(t.getLeftRotation());
            Vector3f up=new Vector3f(0,1,0).rotate(relative);
            assertEquals(moving.angle,Math.toDegrees(Math.asin(up.z)),.0001);
            assertEquals(reference.angle,moving.angle);assertEquals(reference.side,moving.side);
        }
    }
}
