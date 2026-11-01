package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WireTest {
    private final String secret = "a-test-secret-with-at-least-32-characters";
    private Wire.State state() { return new Wire.State(UUID.randomUUID(), "black", List.of(new Wire.Cape("black", "BLOCK", "BLACK_CONCRETE", 0)), "", ""); }
    @Test void roundTrip() throws Exception { Wire.State s = state(); assertEquals(s, Wire.decode(Wire.encode(s, secret), secret)); }
    @Test void wardrobeVisibilityIsAuthenticatedIndependentlyFromWearPermission()throws Exception{var cape=new Wire.Cape("hidden","ANIMATED","PAPER",10001).wardrobe(false);var s=new Wire.State(UUID.randomUUID(),"hidden",List.of(cape),"","");var c=Wire.decode(Wire.encode(s,secret),secret).capes().get(0);assertFalse(c.wardrobeVisible());assertTrue(c.available());assertFalse(c.access(false).wardrobeVisible());}
    @Test void localizedNoticeSurvivesAuthenticatedWireAndRemainsBounded()throws Exception{Wire.State base=state();String notice=new Notice(true,true,"新文案😀".repeat(300)).encode();Wire.State s=new Wire.State(base.player(),base.selected(),base.capes(),"","",notice);Notice decoded=Notice.decode(Wire.decode(Wire.encode(s,secret),secret).notice());assertTrue(decoded.success()&&decoded.endPreview());assertTrue(notice.length()<=1024);}
    @Test void tamperingRejected() throws Exception { byte[] bytes = Wire.encode(state(), secret); bytes[14] ^= 1; assertThrows(IOException.class, () -> Wire.decode(bytes, secret)); }
    @Test void wrongSecretRejected() throws Exception { byte[] bytes = Wire.encode(state(), secret); assertThrows(IOException.class, () -> Wire.decode(bytes, "another-test-secret-with-at-least-32-characters")); }
    @Test void expiredRejected() throws Exception { byte[] bytes = Wire.encode(state(), secret); Clock future = Clock.fixed(Instant.now().plusSeconds(31), ZoneOffset.UTC); assertThrows(IOException.class, () -> Wire.decode(bytes, secret, future)); }
    @Test void truncatedRejected() { for (int len : new int[]{0, 1, 32, 50000}) assertThrows(IOException.class, () -> Wire.decode(new byte[len], secret)); }
    @Test void catalogBounded() { List<Wire.Cape> capes = Collections.nCopies(129, new Wire.Cape("x", "BLOCK", "STONE", 0)); assertThrows(IOException.class, () -> Wire.encode(new Wire.State(UUID.randomUUID(), "x", capes, "", ""), secret)); }
    @Test void duplicateIdsRejected() throws Exception { Wire.Cape c = new Wire.Cape("black", "BLOCK", "BLACK_CONCRETE", 0); byte[] b = Wire.encode(new Wire.State(UUID.randomUUID(), "black", List.of(c,c), "", ""), secret); assertThrows(IOException.class, () -> Wire.decode(b, secret)); }
    @Test void traversalRejected() { assertThrows(IllegalArgumentException.class, () -> new Wire.Cape("../x", "BLOCK", "STONE", 0)); }
    @Test void selectionRoundTripAndClear() throws Exception {UUID id=UUID.randomUUID();assertEquals(new Wire.Selection(id,"black"),Wire.selection(Wire.select(id,"black",secret),secret));assertEquals("",Wire.selection(Wire.select(id,"",secret),secret).cape());}
    @Test void selectionRejectsForgeryAndStatePacket() throws Exception {byte[] b=Wire.select(UUID.randomUUID(),"black",secret);b[20]^=1;assertThrows(IOException.class,()->Wire.selection(b,secret));assertThrows(IOException.class,()->Wire.selection(Wire.encode(state(),secret),secret));}
    @Test void titlesAndPermissionsRoundTrip() throws Exception {Wire.Cape c=new Wire.Cape("vip","ITEM","PAPER",10001,"星河 · 极光","capebridge.vip",false);Wire.State s=new Wire.State(UUID.randomUUID(),"vip",List.of(c),"https://example.org/capes.zip","a".repeat(40),"已保存");assertEquals(s,Wire.decode(Wire.encode(s,secret),secret));}
}
