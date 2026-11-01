package dev.capebridge.proxy;

import java.net.*;
import java.util.*;

/** Uses Bungee's player address, never backend peer addresses or HTTP forwarding headers. */
final class PackRoutes {
    private record Network(byte[] address,int bits) {
        static Network parse(String value) {
            String[] pair=value.split("/",-1);
            if(pair.length!=2)throw new IllegalArgumentException("CIDR requires an explicit prefix");
            byte[] bytes=literal(pair[0]).getAddress();int bits;
            try{bits=Integer.parseInt(pair[1]);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid CIDR prefix",e);}
            if(bits<0||bits>bytes.length*8)throw new IllegalArgumentException("CIDR prefix out of range");
            return new Network(bytes,bits);
        }
        boolean contains(InetAddress value) {
            byte[] other=value.getAddress();if(address.length!=other.length)return false;
            for(int i=0;i<bits;i++)if(((address[i/8]^other[i/8])&(1<<(7-i%8)))!=0)return false;
            return true;
        }
    }
    private record Policy(String publicUrl,boolean enabled,String localUrl,List<Network> networks) {}
    private record Session(Object owner,boolean local) {}
    private volatile Policy policy=new Policy("",false,"",List.of());
    private final Map<UUID,Session> sessions=new HashMap<>();
    void configure(String publicUrl,boolean enabled,String localUrl,List<String> cidrs) {
        checkUrl(publicUrl,true);checkUrl(localUrl,!enabled);
        List<Network> networks=cidrs.stream().map(Network::parse).toList();
        if(enabled&&networks.isEmpty())throw new IllegalArgumentException("Local routing requires at least one network");
        policy=new Policy(publicUrl,enabled,localUrl,networks);
    }
    synchronized String base(UUID player,Object connection,InetAddress address) {
        Policy p=policy;
        Session existing=sessions.get(player);
        if(existing==null||existing.owner!=connection){
            existing=new Session(connection,address!=null&&p.networks.stream().anyMatch(n->n.contains(address)));
            sessions.put(player,existing);
        }
        return existing.local&&p.enabled?p.localUrl:p.publicUrl;
    }
    synchronized void disconnect(UUID player,Object connection){Session s=sessions.get(player);if(s!=null&&s.owner==connection)sessions.remove(player);}
    synchronized void clear(){sessions.clear();}
    static void checkUrl(String url,boolean blankAllowed) {
        if(url.isBlank()&&blankAllowed)return;
        try {
            URI uri=new URI(url);
            if(url.length()>2048||!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))
                ||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Invalid resource-pack URL");
        }catch(URISyntaxException e){throw new IllegalArgumentException("Invalid resource-pack URL",e);}
    }
    private static InetAddress literal(String text) {
        // Restrict to numeric literals before using InetAddress, so configuration never performs a DNS lookup.
        if(!text.contains(":")) {
            String[] p=text.split("\\.",-1);if(p.length!=4)throw new IllegalArgumentException("CIDR must use numeric IP");
            byte[] bytes=new byte[4];
            for(int i=0;i<4;i++){
                if(!p[i].matches("[0-9]{1,3}"))throw new IllegalArgumentException("Invalid IPv4");
                int n=Integer.parseInt(p[i]);if(n>255)throw new IllegalArgumentException("Invalid IPv4");bytes[i]=(byte)n;
            }
            try{return InetAddress.getByAddress(bytes);}catch(UnknownHostException impossible){throw new AssertionError(impossible);}
        }
        if(!text.matches("[0-9a-fA-F:]+"))throw new IllegalArgumentException("Invalid numeric IPv6");
        try{return InetAddress.getByName(text);}catch(UnknownHostException e){throw new IllegalArgumentException("Invalid IPv6",e);}
    }
}
