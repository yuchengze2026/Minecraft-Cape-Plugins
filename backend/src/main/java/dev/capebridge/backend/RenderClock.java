package dev.capebridge.backend;

import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Reads the torso after entity ticking on Paper; retains a declared Spigot fallback. */
final class RenderClock implements AutoCloseable {
    private final Listener listener=new Listener(){};
    private BukkitTask fallback;
    private boolean active;
    private int elapsed;
    private String phase="tick-end";
    @SuppressWarnings("unchecked")
    RenderClock(JavaPlugin plugin,int period,Runnable frame){
        Runnable step=()->{if(active&&++elapsed>=period){elapsed=0;frame.run();}};
        try{
            Class<? extends Event> event=(Class<? extends Event>)Class.forName("com.destroystokyo.paper.event.server.ServerTickEndEvent");
            plugin.getServer().getPluginManager().registerEvent(event,listener,EventPriority.MONITOR,(l,e)->step.run(),plugin);
        }catch(ClassNotFoundException unavailable){
            phase="scheduler-fallback";
            fallback=plugin.getServer().getScheduler().runTaskTimer(plugin,step,1,1);
            plugin.getLogger().warning(Messages.t("log.clock-fallback"));
        }
    }
    void wake(){active=true;}
    void sleep(){active=false;elapsed=0;}
    boolean active(){return active;}
    String phase(){return phase;}
    @Override public void close(){active=false;HandlerList.unregisterAll(listener);if(fallback!=null)fallback.cancel();}
}
