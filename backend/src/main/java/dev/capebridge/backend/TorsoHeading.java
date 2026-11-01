package dev.capebridge.backend;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

/** Client torso constraints, driven only by main-thread movement accepted by the server. */
final class TorsoHeading implements AutoCloseable, Listener {
    private static final class State {int tick=Integer.MIN_VALUE;float body,phase;Location previous;int swinging;boolean snap;}
    private final Map<UUID,State> states=new HashMap<>();
    private final Map<UUID,TurnPath> turns=new HashMap<>();
    private final boolean localBootstrap;
    TorsoHeading(Plugin plugin){this(plugin,false);}
    TorsoHeading(Plugin plugin,boolean localBootstrap){this.localBootstrap=localBootstrap;plugin.getServer().getPluginManager().registerEvents(this,plugin);}
    static float localInitialBody(float head){return step(0,head,0,0,false);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event){
        if(event.getTo()==null)return;
        UUID id=event.getPlayer().getUniqueId();
        if(!states.containsKey(id))return;
        if(event.getFrom().getWorld()!=event.getTo().getWorld()||event.getFrom().distanceSquared(event.getTo())>256){turns.remove(id);return;}
        turns.computeIfAbsent(id,k->new TurnPath()).add(event.getFrom().getYaw(),event.getTo().getYaw());
    }
    boolean snap(Player player){State state=states.get(player.getUniqueId());return state!=null&&state.snap;}
    void swing(Player player){states.computeIfAbsent(player.getUniqueId(),id->new State()).swinging=6;}
    void forget(UUID id){states.remove(id);turns.remove(id);}
    float sample(Player player){
        State state=states.computeIfAbsent(player.getUniqueId(),id->new State());
        int now=player.getTicksLived();if(state.tick==now)return state.phase;
        Location at=player.getLocation();
        TurnPath path=turns.computeIfAbsent(player.getUniqueId(),k->new TurnPath());
        if(state.previous==null||now-state.tick>4||at.getWorld()!=state.previous.getWorld()||at.distanceSquared(state.previous)>256){
            boolean newClient=state.previous==null||at.getWorld()!=state.previous.getWorld();
            state.body=state.phase=localBootstrap&&newClient?localInitialBody(at.getYaw()):CapePose.wrap(at.getYaw());state.snap=true;path.clear();}
        else {
            int elapsed=Math.max(1,Math.min(4,now-state.tick));
            double dx=(at.getX()-state.previous.getX())/elapsed,dz=(at.getZ()-state.previous.getZ())/elapsed;
            float[] deltas=path.take(state.previous.getYaw(),at.getYaw());
            Turn turn=advancePath(state.body,state.previous.getYaw(),deltas,dx,dz,state.swinging>0,elapsed);
            state.body=turn.body();state.phase=(state.phase+turn.rotation())%720;state.snap=turn.travel()>=359.8f;
            state.swinging=Math.max(0,state.swinging-elapsed);
        }
        state.tick=now;state.previous=at;return state.phase;
    }
    record Turn(float body,float rotation,float travel){}
    static Turn advance(float body,float previousYaw,float delta,double dx,double dz,boolean swinging,int elapsed){
        return advancePath(body,previousYaw,new float[]{delta},dx,dz,swinging,elapsed);
    }
    static Turn advancePath(float body,float previousYaw,float[] deltas,double dx,double dz,boolean swinging,int elapsed){
        int steps=0;
        for(float delta:deltas)steps+=Math.max(1,(int)Math.ceil(Math.abs(delta)/45));
        float response=(float)(1-Math.pow(.7,(double)elapsed/Math.max(1,steps))),rotation=0,travel=0;
        for(float delta:deltas){
            int count=Math.max(1,(int)Math.ceil(Math.abs(delta)/45));
            for(int i=1;i<=count;i++){
                float next=step(body,previousYaw+delta*i/count,dx,dz,swinging,response);
                float change=CapePose.wrap(next-body);rotation+=change;travel+=Math.abs(change);body=next;
            }
            previousYaw=CapePose.wrap(previousYaw+delta);
        }
        // The rendered heading IS the current torso. There is no speed cap or rotation debt.
        return new Turn(body,rotation,travel);
    }
    static float step(float body,float entityYaw,double dx,double dz,boolean swinging){
        return step(body,entityYaw,dx,dz,swinging,.3f);
    }
    private static float step(float body,float entityYaw,double dx,double dz,boolean swinging,float response){
        float target=body;
        if((float)(dx*dx+dz*dz)>.0025000002f){
            float travel=(float)Math.atan2(dz,dx)*57.295776f-90;
            float difference=Math.abs(CapePose.wrap(entityYaw)-travel);
            target=difference>95&&difference<265?travel-180:travel;
        }
        if(swinging)target=entityYaw;
        body+=CapePose.wrap(target-body)*response;
        float relative=CapePose.wrap(entityYaw-body);
        if(Math.abs(relative)>50)body+=relative-Math.signum(relative)*50;
        return CapePose.wrap(body);
    }
    @Override public void close(){HandlerList.unregisterAll(this);states.clear();turns.clear();}
}
