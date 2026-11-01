package dev.capebridge.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Small, bounded, authenticated messages; no Java object deserialization. */
public final class Wire {
    public static final String CHANNEL = "capebridge:sync";
    public static final int VERSION = 7;
    public static final int MAX_CAPES = 128;
    public record Cape(String id, String kind, String material, int model, String title, String permission, boolean available, boolean wardrobeVisible) {
        public Cape(String id,String kind,String material,int model,String title,String permission,boolean available){this(id,kind,material,model,title,permission,available,true);}
        public Cape(String id, String kind, String material, int model) { this(id,kind,material,model,id,"",true); }
        public Cape {
            if (!id.matches("[a-z0-9_-]{1,32}") || !(kind.equals("BLOCK") || kind.equals("ITEM") || kind.equals("ANIMATED"))
                    || !material.matches("[A-Z_]{1,48}") || model < 0 || title.length()>20 || title.isBlank()
                    || !permission.matches("[a-zA-Z0-9_.-]{0,48}")) throw new IllegalArgumentException("Invalid cape");
        }
        public Cape access(boolean allowed) { return new Cape(id,kind,material,model,title,permission,allowed,wardrobeVisible); }
        public Cape wardrobe(boolean visible) { return new Cape(id,kind,material,model,title,permission,available,visible); }
    }
    public record State(UUID player, String selected, List<Cape> capes, String packUrl, String packSha1, String notice,PackSnapshot pack) {
        public State(UUID player,String selected,List<Cape> capes,String url,String hash,String notice){this(player,selected,capes,url,hash,notice,PackSnapshot.EMPTY);}
        public State(UUID player,String selected,List<Cape> capes,String url,String hash){this(player,selected,capes,url,hash,"");}
    }
    public record Selection(UUID player, String cape) {}
    public record PackReport(UUID player,UUID session,UUID attempt,String hash,PackStatus status,boolean identified) {}
    public record PackRetry(UUID player,UUID session,UUID attempt,String hash) {}
    private Wire() {}
    public static byte[] request() { return new byte[]{VERSION, 0}; }
    public static boolean isRequest(byte[] bytes) { return Arrays.equals(bytes, request()); }
    public static byte[] encode(State state, String secret) throws IOException {
        if (state.capes.size() > MAX_CAPES) throw new IOException("Too many capes");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(body);
        out.writeByte(VERSION); out.writeByte(1); out.writeLong(System.currentTimeMillis());
        out.writeLong(state.player.getMostSignificantBits()); out.writeLong(state.player.getLeastSignificantBits());
        out.writeUTF(state.selected); out.writeUTF(state.packUrl); out.writeUTF(state.packSha1); out.writeUTF(state.notice);
        uuid(out,state.pack.session());uuid(out,state.pack.attempt());out.writeLong(state.pack.revision());out.writeByte(state.pack.status().ordinal());out.writeBoolean(state.pack.offer());out.writeBoolean(state.pack.identified());
        out.writeInt(state.capes.size());
        for (Cape cape : state.capes) { out.writeUTF(cape.id); out.writeUTF(cape.kind); out.writeUTF(cape.material); out.writeInt(cape.model); out.writeUTF(cape.title);out.writeUTF(cape.permission);out.writeBoolean(cape.available);out.writeBoolean(cape.wardrobeVisible); }
        byte[] payload = body.toByteArray();
        if (payload.length > 30000) throw new IOException("Message exceeds limit");
        body.writeBytes(hmac(payload, secret)); return body.toByteArray();
    }
    public static State decode(byte[] message, String secret) throws IOException { return decode(message, secret, Clock.systemUTC()); }
    static State decode(byte[] message, String secret, Clock clock) throws IOException {
        if (message.length < 33 || message.length > 30032) throw new IOException("Invalid message length");
        byte[] body = Arrays.copyOf(message, message.length - 32);
        if (!MessageDigest.isEqual(hmac(body, secret), Arrays.copyOfRange(message, body.length, message.length))) throw new IOException("Signature mismatch");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        if (in.readUnsignedByte() != VERSION || in.readUnsignedByte()!=1) throw new IOException("Protocol version or message type");
        long timestamp = in.readLong();
        if (timestamp < clock.millis() - 30000 || timestamp > clock.millis() + 5000) throw new IOException("Expired message");
        UUID player = new UUID(in.readLong(), in.readLong());
        String selected = in.readUTF(), url = in.readUTF(), sha1 = in.readUTF(), notice=in.readUTF();
        if (!(selected.isEmpty() || selected.matches("[a-z0-9_-]{1,32}")) || url.length() > 2048 || notice.length()>1024 || !(sha1.isEmpty() || sha1.matches("[a-f0-9]{40}"))) throw new IOException("Invalid fields");
        PackSnapshot pack;
        try{pack=new PackSnapshot(uuid(in),uuid(in),in.readLong(),status(in.readUnsignedByte()),in.readBoolean(),in.readBoolean());}catch(IllegalArgumentException invalid){throw new IOException("Invalid pack snapshot",invalid);}
        int size = in.readInt(); if (size < 0 || size > MAX_CAPES) throw new IOException("Catalog limit");
        List<Cape> capes = new ArrayList<>(); Set<String> ids = new HashSet<>();
        try {
            for (int i = 0; i < size; i++) { Cape cape = new Cape(in.readUTF(), in.readUTF(), in.readUTF(), in.readInt(),in.readUTF(),in.readUTF(),in.readBoolean(),in.readBoolean()); if (!ids.add(cape.id)) throw new IOException("Duplicate cape"); capes.add(cape); }
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid cape", ex); }
        if (in.available() != 0) throw new IOException("Trailing data");
        return new State(player, selected, List.copyOf(capes), url, sha1,notice,pack);
    }
    public static byte[] select(UUID player,String cape,String secret)throws IOException {
        if(!(cape.isEmpty()||cape.matches("[a-z0-9_-]{1,32}")))throw new IOException("Invalid selection");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(VERSION);out.writeByte(2);out.writeLong(System.currentTimeMillis());out.writeLong(player.getMostSignificantBits());out.writeLong(player.getLeastSignificantBits());out.writeUTF(cape);
        bytes.writeBytes(hmac(bytes.toByteArray(),secret));return bytes.toByteArray();
    }
    public static Selection selection(byte[] message,String secret)throws IOException {
        if(message.length<60||message.length>160)throw new IOException("Invalid selection length");
        byte[] body=Arrays.copyOf(message,message.length-32);
        if(!MessageDigest.isEqual(hmac(body,secret),Arrays.copyOfRange(message,body.length,message.length)))throw new IOException("Selection signature mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(body));
        if(in.readUnsignedByte()!=VERSION||in.readUnsignedByte()!=2)throw new IOException("Selection protocol");
        long time=in.readLong(),now=System.currentTimeMillis();if(time<now-30000||time>now+5000)throw new IOException("Selection expired");
        UUID player=new UUID(in.readLong(),in.readLong());String cape=in.readUTF();
        if(in.available()!=0||!(cape.isEmpty()||cape.matches("[a-z0-9_-]{1,32}")))throw new IOException("Invalid selection");
        return new Selection(player,cape);
    }
    public static boolean isPackReport(byte[] bytes){return bytes.length>=2&&bytes[0]==VERSION&&bytes[1]==3;}
    public static boolean isPackRetry(byte[] bytes){return bytes.length>=2&&bytes[0]==VERSION&&bytes[1]==4;}
    public static byte[] packReport(PackReport report,String secret)throws IOException{
        validatePack(report.session,report.attempt,report.hash);if(!report.status.reportable())throw new IOException("Invalid pack report status");
        ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(body);header(out,3,report.player,report.session,report.attempt,report.hash);out.writeByte(report.status.ordinal());out.writeBoolean(report.identified);body.writeBytes(hmac(body.toByteArray(),secret));return body.toByteArray();
    }
    public static PackReport packReport(byte[] message,String secret)throws IOException{
        DataInputStream in=packInput(message,secret,3);UUID player=uuid(in),session=uuid(in),attempt=uuid(in);String hash=in.readUTF();PackStatus status=status(in.readUnsignedByte());boolean identified=in.readBoolean();validatePack(session,attempt,hash);if(in.available()!=0||!status.reportable())throw new IOException("Invalid pack report");return new PackReport(player,session,attempt,hash,status,identified);
    }
    public static byte[] packRetry(PackRetry retry,String secret)throws IOException{
        validatePack(retry.session,retry.attempt,retry.hash);ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(body);header(out,4,retry.player,retry.session,retry.attempt,retry.hash);body.writeBytes(hmac(body.toByteArray(),secret));return body.toByteArray();
    }
    public static PackRetry packRetry(byte[] message,String secret)throws IOException{
        DataInputStream in=packInput(message,secret,4);UUID player=uuid(in),session=uuid(in),attempt=uuid(in);String hash=in.readUTF();validatePack(session,attempt,hash);if(in.available()!=0)throw new IOException("Invalid pack retry");return new PackRetry(player,session,attempt,hash);
    }
    private static void header(DataOutputStream out,int type,UUID player,UUID session,UUID attempt,String hash)throws IOException{out.writeByte(VERSION);out.writeByte(type);out.writeLong(System.currentTimeMillis());uuid(out,player);uuid(out,session);uuid(out,attempt);out.writeUTF(hash);}
    private static DataInputStream packInput(byte[] message,String secret,int type)throws IOException{
        if(message.length<100||message.length>256)throw new IOException("Invalid pack message length");byte[] body=Arrays.copyOf(message,message.length-32);if(!MessageDigest.isEqual(hmac(body,secret),Arrays.copyOfRange(message,body.length,message.length)))throw new IOException("Pack signature mismatch");DataInputStream in=new DataInputStream(new ByteArrayInputStream(body));if(in.readUnsignedByte()!=VERSION||in.readUnsignedByte()!=type)throw new IOException("Pack message protocol");long time=in.readLong(),now=System.currentTimeMillis();if(time<now-30000||time>now+5000)throw new IOException("Pack message expired");return in;
    }
    private static void validatePack(UUID session,UUID attempt,String hash)throws IOException{if(session==null||attempt==null||session.equals(PackSnapshot.ZERO)||attempt.equals(PackSnapshot.ZERO)||!hash.matches("[a-f0-9]{40}"))throw new IOException("Invalid pack identity");}
    private static PackStatus status(int value)throws IOException{if(value>=PackStatus.values().length)throw new IOException("Invalid pack status");return PackStatus.values()[value];}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static byte[] hmac(byte[] bytes, String secret) throws IOException {
        if (secret.length() < 32) throw new IOException("sync-secret must contain at least 32 characters");
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return mac.doFinal(bytes); }
        catch (Exception ex) { throw new IOException(ex); }
    }
}
