package dev.capebridge.proxy;
import dev.capebridge.common.Wire;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class WardrobeAccessTest {
 private Wire.Cape cape(boolean visible){return new Wire.Cape("secret","ANIMATED","PAPER",10001,"secret","capebridge.cape.secret",true,visible);}
 @Test void publicCapesAreVisibleWithoutGrantingWearPermission(){var c=CapeAccess.forPlayer(cape(true),Set.<String>of()::contains);assertTrue(c.wardrobeVisible());assertFalse(c.available());}
 @Test void hiddenCapesRequireTheAllPermissionEvenForIndividualOwnersAndAdmins(){
  for(var permissions:java.util.List.of(Set.<String>of(),Set.of("capebridge.cape.secret"),Set.of("capebridge.admin"))){var c=CapeAccess.forPlayer(cape(false),permissions::contains);assertFalse(c.wardrobeVisible());assertEquals(permissions.contains("capebridge.cape.secret"),c.available());}
 }
 @Test void allPermissionRevealsHiddenCapesAndRevocationHidesThemAgain(){var c=cape(false);assertTrue(CapeAccess.forPlayer(c,Set.of("capebridge.cape.all")::contains).wardrobeVisible());assertFalse(CapeAccess.forPlayer(c,Set.of("capebridge.cape.secret")::contains).wardrobeVisible());}
}
