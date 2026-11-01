package dev.capebridge.backend;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapePoseTest {
    @Test void shoulderDoesNotDriftWithHeadingOrSwing() {
        for (boolean block : new boolean[]{true,false}) for (int yaw = -360; yaw <= 360; yaw += 15) for (int pitch = 0; pitch <= 75; pitch += 5) {
            Transformation pose = CapePose.pose(block, yaw, (float)Math.toRadians(pitch),1.42f,1.35f,.167f);
            Vector3f shoulder = block ? new Vector3f(.5f,1,.5f) : new Vector3f(0,.5f,0);
            shoulder.mul(pose.getScale()).rotate(pose.getLeftRotation()).add(pose.getTranslation());
            Vector3f expected = new Vector3f(0,.07f,-.167f).rotateY((float)-Math.toRadians(yaw));
            assertTrue(shoulder.distance(expected)<0.00001f, "Top edge must remain anchored");
            assertEquals(1f,pose.getLeftRotation().lengthSquared(),.00001f);
            assertTrue(pose.getScale().x>0&&pose.getScale().y>0&&pose.getScale().z>0);
        }
    }
    @Test void crouchingKeepsTopEdgeAtShoulder() {
        Transformation pose = CapePose.pose(true,0,.4f,1.19f,1.125f,.167f);
        Vector3f top = new Vector3f(.5f,1,.5f).mul(pose.getScale()).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        assertEquals(1.19f,top.y+1.125f,.00001f);
    }
    @Test void headingWrapUsesShortestTurn() { assertEquals(2,CapePose.wrap(-358));assertEquals(-2,CapePose.wrap(358));assertEquals(0,CapePose.wrap(720)); }
    @Test void topFrontSurfaceStaysJustBehindTheBack() {
        for(boolean block:new boolean[]{true,false}) for(int yaw=-180;yaw<180;yaw+=15) {
            Transformation pose=CapePose.pose(block,yaw,(float)Math.toRadians(2),1.42f,1.35f,.167f);
            Vector3f point=block?new Vector3f(.5f,1,1):new Vector3f(0,.5f,.025f);
            point.mul(pose.getScale()).rotate(pose.getLeftRotation()).add(pose.getTranslation()).rotateY((float)Math.toRadians(yaw));
            float gap=-point.z-.140625f;
            assertTrue(gap>=.001f&&gap<=.003f,"Attachment edge must clear the outer skin layer without floating");
        }
    }
    @Test void turningInterpolationKeepsAttachmentWithinTheCloseBackRange() {
        for(boolean block:new boolean[]{true,false}) for(int yaw=-180;yaw<=180;yaw+=15) {
            Transformation from=CapePose.pose(block,yaw,.035f,1.42f,1.35f,.167f);
            Transformation to=CapePose.pose(block,yaw+22,.035f,1.42f,1.35f,.18f);
            for(int i=0;i<=20;i++) {
                float t=i/20f;
                Vector3f translation=new Vector3f(from.getTranslation()).lerp(to.getTranslation(),t);
                Quaternionf rotation=new Quaternionf(from.getLeftRotation()).slerp(to.getLeftRotation(),t);
                Vector3f a=block?new Vector3f(.5f,1,.5f):new Vector3f(0,.5f,0);
                a.mul(from.getScale()).rotate(rotation).add(translation);
                double distance=Math.hypot(a.x,a.z);
                assertTrue(distance>=.163&&distance<=.181,"Interpolation must not produce the previous large turn offset");
            }
        }
    }
}
