package dev.capebridge.backend;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;

/** Isolated 0.6.4 heading candidate, used only in explicit A/B experiments. */
final class LegacyTorsoHeading {
    private static final class State {int tick=Integer.MIN_VALUE;float body;Location previous;int swinging;}
    private final Map<UUID,State> states=new HashMap<>();
    void swing(Player p){states.computeIfAbsent(p.getUniqueId(),id->new State()).swinging=6;}
    void forget(UUID id){states.remove(id);}
    float sample(Player p){
        State s=states.computeIfAbsent(p.getUniqueId(),id->new State());int tick=p.getTicksLived();
        if(s.tick==tick)return s.body;Location at=p.getLocation();
        if(s.previous==null||at.getWorld()!=s.previous.getWorld()||at.distanceSquared(s.previous)>256)s.body=CapePose.wrap(at.getYaw());
        else{
            int elapsed=Math.max(1,Math.min(4,tick-s.tick));double dx=(at.getX()-s.previous.getX())/elapsed,dz=(at.getZ()-s.previous.getZ())/elapsed;
            for(int i=0;i<elapsed;i++){s.body=TorsoHeading.step(s.body,at.getYaw(),dx,dz,s.swinging>0);if(s.swinging>0)s.swinging--;}
        }
        s.tick=tick;s.previous=at;return s.body;
    }
}
