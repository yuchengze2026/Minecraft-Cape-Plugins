package dev.capebridge.backend;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.util.*;

/** Uses the actual server torso field. No head yaw or movement reconstruction. */
final class BodyHeading {
    record Access(Method handle,Field body,String source){
        float read(Object player){
            if(body==null)return Float.NaN;
            try{float value=body.getFloat(handle.invoke(player));return Float.isFinite(value)?CapePose.wrap(value):Float.NaN;}
            catch(ReflectiveOperationException ex){return Float.NaN;}
        }
    }
    private final JavaPlugin plugin;
    private final Map<Class<?>,Access> readers=new HashMap<>();
    private final Set<Class<?>> warned=new HashSet<>();
    BodyHeading(JavaPlugin plugin){this.plugin=plugin;}
    static Access resolve(Class<?> craft,boolean legacy1201){
        try{
            Method handle=craft.getMethod("getHandle");
            for(String name:legacy1201?new String[]{"yBodyRot","aV"}:new String[]{"yBodyRot"}){
                try{Field body=handle.getReturnType().getField(name);if(body.getType()==float.class)return new Access(handle,body,name);}
                catch(NoSuchFieldException ignored){}
            }
        }catch(ReflectiveOperationException ignored){}
        return new Access(null,null,"unavailable");
    }
    private Access access(Player p){return readers.computeIfAbsent(p.getClass(),c->resolve(c,plugin.getServer().getBukkitVersion().startsWith("1.20.1-")));}
    float sample(Player p){
        float yaw=access(p).read(p);
        if(!Float.isFinite(yaw)&&warned.add(p.getClass()))plugin.getLogger().warning(Messages.t("log.body-unavailable"));
        return yaw;
    }
    String source(Player p){return access(p).source;}
}
