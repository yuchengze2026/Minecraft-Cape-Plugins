package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BodyHeadingTest {
 public static class NativeBody {public float yBodyRot=15,headYaw=100;}
 public static class Craft {public final NativeBody value=new NativeBody();public NativeBody getHandle(){return value;}}
 public static class LegacyBody {public float aV=40;}
 public static class LegacyCraft {public LegacyBody getHandle(){return new LegacyBody();}}
 public static class HeadOnly {public float headYaw=120;}
 public static class Unsupported {public HeadOnly getHandle(){return new HeadOnly();}}
 @Test void headChangesCannotAffectReadBody(){Craft c=new Craft();var read=BodyHeading.resolve(Craft.class,false);for(int head=-180;head<=180;head++){c.value.headYaw=head;assertEquals(15,read.read(c));}}
 @Test void changedBodyIsReadImmediatelyWithoutCacheOrSmoothing(){Craft c=new Craft();var read=BodyHeading.resolve(Craft.class,false);assertEquals(15,read.read(c));c.value.yBodyRot=-70;assertEquals(-70,read.read(c));}
 @Test void legacyFieldIsRestrictedToKnown1201Mapping(){assertEquals(40,BodyHeading.resolve(LegacyCraft.class,true).read(new LegacyCraft()));assertTrue(Float.isNaN(BodyHeading.resolve(LegacyCraft.class,false).read(new LegacyCraft())));}
 @Test void unsupportedBodyDoesNotFallBackToHead(){assertTrue(Float.isNaN(BodyHeading.resolve(Unsupported.class,false).read(new Unsupported())));}
 @Test void invalidBodyFailsClosed(){Craft c=new Craft();c.value.yBodyRot=Float.NaN;assertTrue(Float.isNaN(BodyHeading.resolve(Craft.class,false).read(c)));}
 @Test void zeroSideSettingDoesNotCreateAdditionalRollDuringTurn(){CapeCloth c=new CapeCloth();for(int i=0;i<100;i++){c.step(.25f,0,0,false,false,1,65,0,1);assertEquals(0,c.side);}}
}
