package dev.capebridge.proxy;

import dev.capebridge.common.PackUrl;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PackRoutesTest {
    private static final String PUBLIC="https://cape.example/capes.zip",LOCAL="http://192.168.1.2:26780/capes.zip";
    PackRoutes routes(){var r=new PackRoutes();r.configure(PUBLIC,true,LOCAL,List.of("127.0.0.0/8","192.168.0.0/16","::1/128","fc00::/7"));return r;}
    @Test void numericIpv4AndIpv6UseConfiguredNetworks() throws Exception {
        var r=routes();
        for(String ip:List.of("127.0.0.1","192.168.33.7","::1","fd12::1"))assertEquals(LOCAL,r.base(UUID.randomUUID(),new Object(),InetAddress.getByName(ip)));
        for(String ip:List.of("8.8.8.8","192.169.1.1","2001:4860::1"))assertEquals(PUBLIC,r.base(UUID.randomUUID(),new Object(),InetAddress.getByName(ip)));
    }
    @Test void routeStaysStableAcrossServerTransfersAndConfigurationReload() throws Exception {
        var r=routes();var player=UUID.randomUUID();Object tcp=new Object();
        assertEquals(LOCAL,r.base(player,tcp,InetAddress.getLoopbackAddress()));
        r.configure(PUBLIC,true,"http://192.168.2.2/capes.zip",List.of("192.168.0.0/16"));
        assertEquals("http://192.168.2.2/capes.zip",r.base(player,tcp,InetAddress.getByName("8.8.8.8")));
        Object reconnect=new Object();assertEquals(PUBLIC,r.base(player,reconnect,InetAddress.getByName("8.8.8.8")));
        r.disconnect(player,tcp);assertEquals(PUBLIC,r.base(player,reconnect,InetAddress.getLoopbackAddress()));
    }
    @Test void invalidConfigurationKeepsPreviousPolicyAndNeverResolvesHostnames() throws Exception {
        var r=routes();
        for(String cidr:List.of("localhost/8","10.0.0.1/33","10.999.0.0/8","fe80::1%eth0/64","::/129"))
            assertThrows(IllegalArgumentException.class,()->r.configure(PUBLIC,true,LOCAL,List.of(cidr)));
        assertEquals(LOCAL,r.base(UUID.randomUUID(),new Object(),InetAddress.getByName("192.168.1.10")));
        for(String url:List.of("file:///x","http://user:password@host/file","https://host/file#x","http://"))
            assertThrows(IllegalArgumentException.class,()->r.configure(url,true,LOCAL,List.of("0.0.0.0/0")));
    }
    @Test void disabledOrUnknownAddressUsesPublicAndPackIdentityDoesNotChange() throws Exception {
        var r=routes();assertEquals(PUBLIC,r.base(UUID.randomUUID(),new Object(),null));
        r.configure(PUBLIC,false,"",List.of());assertEquals(PUBLIC,r.base(UUID.randomUUID(),new Object(),InetAddress.getLoopbackAddress()));
        String hash="a".repeat(40);
        assertTrue(PackUrl.versioned(PUBLIC,hash).contains(hash));assertTrue(PackUrl.versioned(LOCAL,hash).contains(hash));
    }
}
