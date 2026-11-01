package dev.capebridge.backend;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** New clients identify each pack; legacy 1.20.1 only reports the current offer. */
final class PackOffer {
    private static final Method SEND=method(Player.class,"setResourcePack",UUID.class,String.class,byte[].class,String.class,boolean.class);
    private static final Method ID=method(PlayerResourcePackStatusEvent.class,"getID");
    private PackOffer() {}
    private static Method method(Class<?> type,String name,Class<?>...args){try{return type.getMethod(name,args);}catch(NoSuchMethodException missing){return null;}}
    static UUID id(String token){return UUID.nameUUIDFromBytes(("capebridge:"+token).getBytes(StandardCharsets.UTF_8));}
    static boolean identified(){return SEND!=null;}
    static UUID eventId(PlayerResourcePackStatusEvent event){if(ID==null)return null;try{return (UUID)ID.invoke(event);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot identify cape pack status",failure);}}
    static boolean matches(PlayerResourcePackStatusEvent event,String token){
        if(ID==null)return true;
        try{return id(token).equals(ID.invoke(event));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot identify cape pack status",failure);}
    }
    static void send(Player player,String url,byte[] hash,String token){
        send(player,url,hash,id(token));
    }
    static void send(Player player,String url,byte[] hash,UUID id){
        String prompt=Messages.t("pack.prompt","player",player.getName());
        if(SEND==null){player.setResourcePack(url,hash,prompt,false);return;}
        try{SEND.invoke(player,id,url,hash,prompt,false);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot offer optional cape pack",failure);}
    }
}
