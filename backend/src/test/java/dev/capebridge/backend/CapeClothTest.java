package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class CapeClothTest {
    private void step(CapeCloth c,float speed,float vertical,float turn,boolean fly,int ticks){c.step(speed,vertical,turn,false,fly,ticks,65,.015f,1);}
    @Test void runningRaisesOneRigidCapeAndStoppingComesToRest(){
        CapeCloth c=new CapeCloth();step(c,.28f,0,0,false,100);
        assertEquals(36.4f,c.angle,.001f);
        float previous=c.angle;
        for(int i=0;i<100;i++){step(c,0,0,0,false,1);assertTrue(c.angle<=previous);previous=c.angle;}
        assertEquals(0,c.angle);assertEquals(0,c.side);
        for(int i=0;i<400;i++){step(c,0,0,0,false,1);assertEquals(0,c.angle);assertEquals(0,c.side);}
    }
    @Test void stationaryNetworkNoiseDoesNotCreateIdleFlutter(){
        CapeCloth c=new CapeCloth();
        for(int i=0;i<1000;i++){step(c,.004f,i%2==0?.009f:-.009f,0,false,1);assertEquals(0,c.angle);}
    }
    @Test void flyingLiftsFurtherBackAndHoverSettles(){
        CapeCloth run=new CapeCloth(),fly=new CapeCloth();step(run,.28f,0,0,false,100);step(fly,.28f,0,0,true,100);
        assertEquals(run.angle+10,fly.angle,.001f);
        step(fly,.7f,.5f,0,true,100);assertEquals(65,fly.angle);
        step(fly,0,0,0,true,100);assertEquals(0,fly.angle);
    }
    @Test void sideResponseAndLimitAreReducedNinetyPercent(){
        CapeCloth c=new CapeCloth();step(c,.28f,0,10,false,100);assertEquals(-.004f,c.side,.00001f);
        step(c,.28f,0,180,false,100);assertEquals(-.015f,c.side,.00001f);
        step(c,.28f,0,-180,false,100);assertEquals(.015f,c.side,.00001f);
        step(c,0,0,0,false,100);assertEquals(0,c.side);
    }
    @Test void largeDirectionChangesAndTickPeriodsStayFiniteAndBounded(){
        CapeCloth c=new CapeCloth();
        for(int i=0;i<3000;i++){
            step(c,i%2==0?.7f:0,i%2==0?2:-2,i%2==0?180:-180,i%3==0,1+i%4);
            assertTrue(Float.isFinite(c.angle)&&c.angle>=0&&c.angle<=65);
            assertTrue(Float.isFinite(c.side)&&Math.abs(c.side)<=.01501f);
        }
    }
    @Test void fullPlateTopEdgeRemainsAnchoredWhileRunningTurningAndCrouching(){
        for(boolean block:new boolean[]{true,false})for(int yaw=-180;yaw<=180;yaw+=15)for(int pitch=0;pitch<=65;pitch+=5)for(float shoulder:new float[]{1.42f,1.19f}){
            Transformation p=CapePose.rigid(block,yaw,.015f,pitch,shoulder,1.35f,.152f);
            Vector3f top=(block?new Vector3f(.5f,1,1):new Vector3f(0,.5f,dev.capebridge.common.CapeMesh.THICKNESS/2)).mul(p.getScale()).rotate(p.getLeftRotation()).add(p.getTranslation());
            Vector3f expected=new Vector3f(0,shoulder-1.35f,-.152f).rotateY((float)-Math.toRadians(yaw));
            assertTrue(top.distance(expected)<.00001);
        }
    }
    @Test void onePixelCapeKeepsItsInnerEdgeFlushInEveryRearwardPose(){
        assertEquals(1/16f,dev.capebridge.common.CapeMesh.THICKNESS);
        for(boolean block:new boolean[]{true,false})for(boolean armor:new boolean[]{false,true})for(boolean jacket:new boolean[]{false,true})for(int pitch=0;pitch<=65;pitch+=5){
            float surface=CapePose.backSurface(jacket,armor),offset=surface+.00075f;
            Transformation p=CapePose.rigid(block,0,0,pitch,1.4071875f,1.35f,offset);
            Vector3f top=(block?new Vector3f(.5f,1,1):new Vector3f(0,.5f,1/32f)).mul(p.getScale()).rotate(p.getLeftRotation()).add(p.getTranslation());
            assertEquals(.00075f,-top.z-surface,.000001f);
            assertEquals(1.4071875f,top.y+1.35f,.000001f);
        }
    }
}
