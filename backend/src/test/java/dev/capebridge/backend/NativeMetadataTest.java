package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeMetadataTest {
 public record Data(int id,Object value){}
 public static class Watcher {int drains;public List<Data> getNonDefaultValues(){return List.of(new Data(7,(byte)127));}public List<Data> packDirty(){drains++;throw new AssertionError("Dirty metadata must not be drained");}}
 public static class Handle {final Watcher data=new Watcher();public Watcher getEntityData(){return data;}}
 public static class Craft {final Handle h=new Handle();public Handle getHandle(){return h;}}
 @Test void initialSnapshotDoesNotConsumeServerDirtyState(){Craft c=new Craft();assertEquals((byte)127,NativeMetadata.read(c).get(7));assertEquals(0,c.h.data.drains);}
}
