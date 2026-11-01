package dev.capebridge.proxy;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class CapeAccessTest {
 @Test void noDefaultCapes(){assertFalse(CapeAccess.allowed("black",Set.<String>of()::contains));}
 @Test void individualGrantOnlyUnlocksThatStyle(){var p=Set.of("capebridge.cape.black");assertTrue(CapeAccess.allowed("black",p::contains));assertFalse(CapeAccess.allowed("white",p::contains));}
 @Test void allUnlocksExistingAndFutureStyles(){var p=Set.of("capebridge.cape.all");assertTrue(CapeAccess.allowed("white",p::contains));assertTrue(CapeAccess.allowed("future_style",p::contains));}
 @Test void menuAndAdminPermissionsDoNotGrantCapes(){assertFalse(CapeAccess.allowed("black",Set.of("capebridge.use","capebridge.admin")::contains));}
}
