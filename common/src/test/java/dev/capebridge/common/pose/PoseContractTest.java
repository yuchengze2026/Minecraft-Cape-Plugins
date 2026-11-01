package dev.capebridge.common.pose;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.capebridge.common.pose.Observation.*;

class PoseContractTest {
    @Test void absentDataCannotBecomeZeroOrIdentity() {
        for(BodyPart part:BodyPart.values()) {
            var pose=PartPose.unavailable(part);
            assertEquals(Availability.NOT_TRANSMITTED,pose.worldMatrix().availability());
            assertNull(pose.worldMatrix().value());assertNull(pose.worldRotation().yaw().value());
            assertNull(pose.worldPosition().value());
        }
        assertThrows(IllegalArgumentException.class,()->new Observation<>(0d,Source.NONE,Availability.NOT_TRANSMITTED,"missing"));
        assertThrows(IllegalArgumentException.class,()->of(Double.NaN,Source.SERVER_FIELD,"body.yaw"));
    }
    @Test void matrixAndScaleRejectInvalidGeometryAndProtectCollections() {
        assertThrows(IllegalArgumentException.class,()->new PoseGeometry.Vec3(0,Double.POSITIVE_INFINITY,0));
        assertThrows(IllegalArgumentException.class,()->new PoseGeometry.Matrix4(List.of(1d)));
        var list=new ArrayList<>(Collections.nCopies(16,0d));var matrix=new PoseGeometry.Matrix4(list);list.set(0,99d);
        assertEquals(0d,matrix.columns().get(0));
        assertThrows(IllegalArgumentException.class,()->new ScaleState(ScaleState.Support.SUPPORTED,
            of(0d,Source.SERVER_API,"base"),of(1d,Source.SERVER_API,"effective"),of(List.of(),Source.SERVER_API,"modifiers")));
    }
    @Test void legacyUnitScaleIsADefaultNotAnAttributeRead() {
        var scale=ScaleState.unsupported();assertEquals(ScaleState.Support.UNSUPPORTED,scale.support());
        assertEquals(Source.VERSION_DEFAULT,scale.effective().source());assertEquals(1d,scale.effective().value());
        assertFalse(scale.base().available());
    }
    @Test void logicalPartsAreIndependentAndNotInferredFromHead() {
        assertEquals(6,BodyPart.values().length);
        assertEquals("ROOT",BodyPart.BODY.parent());
        assertEquals("BODY",BodyPart.LEFT_ARM.parent());
        assertThrows(IllegalArgumentException.class,()->new PartPose(BodyPart.HEAD,"ROOT",null,null,null,null,null,null,null,null,null,null));
    }
}
