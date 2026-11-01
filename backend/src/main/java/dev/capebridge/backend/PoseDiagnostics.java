package dev.capebridge.backend;

import com.google.gson.GsonBuilder;
import dev.capebridge.backend.pose.ServerPoseSampler;
import dev.capebridge.backend.pose.NativeReferenceModel;
import dev.capebridge.common.pose.PlayerPoseSnapshot;
import dev.capebridge.common.pose.PlayerPoseSource;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit opt-in diagnostics; never feeds server estimates into the cape renderer. */
final class PoseDiagnostics implements AutoCloseable, Listener {
    private final JavaPlugin plugin;
    private final ServerPoseSampler sampler;
    private final Set<UUID> watched=new HashSet<>();
    private final Map<UUID,ArrayDeque<PlayerPoseSnapshot>> traces=new HashMap<>();
    private final Map<UUID,ArrayDeque<Map<String,Object>>> capeTraces=new HashMap<>();
    private final Map<UUID,Integer> jumps=new HashMap<>(),impulses=new HashMap<>();
    private final RenderClock clock;
    private final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"CapeBridge-pose-export");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Path output;
    private String jumpEvents="unavailable";
    private volatile boolean closed;
    PoseDiagnostics(JavaPlugin plugin) {
        this.plugin=plugin; sampler=new ServerPoseSampler(plugin.getServer().getBukkitVersion());
        output=plugin.getDataFolder().toPath().resolve("pose-diagnostics");
        clock=new RenderClock(plugin,1,this::tick);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        plugin.getServer().getServicesManager().register(PlayerPoseSource.class,sampler,plugin,org.bukkit.plugin.ServicePriority.Normal);
        registerJump();
    }
    @SuppressWarnings("unchecked") private void registerJump() {
        for(String name:List.of("com.destroystokyo.paper.event.player.PlayerJumpEvent","io.papermc.paper.event.player.PlayerJumpEvent"))try {
            Class<? extends Event> type=Class.forName(name).asSubclass(Event.class);
            var getter=type.getMethod("getPlayer");
            plugin.getServer().getPluginManager().registerEvent(type,this,EventPriority.MONITOR,(l,e)->{
                try {Player p=(Player)getter.invoke(e);if(watched.contains(p.getUniqueId()))jumps.put(p.getUniqueId(),p.getTicksLived());}
                catch(ReflectiveOperationException ex){throw new EventException(ex);}
            },plugin,true);
            jumpEvents=name;return;
        }catch(ClassNotFoundException|NoSuchMethodException ignored){}
    }
    boolean watch(Player player) {
        UUID id=player.getUniqueId();
        if(!watched.contains(id)&&watched.size()>=8)return false;
        if(watched.add(id)){sampler.forget(id);traces.put(id,new ArrayDeque<>());capeTraces.put(id,new ArrayDeque<>());}
        clock.wake();return true;
    }
    void unwatch(UUID id) {watched.remove(id);traces.remove(id);capeTraces.remove(id);sampler.forget(id);jumps.remove(id);impulses.remove(id);if(watched.isEmpty())clock.sleep();}
    private boolean recent(Map<UUID,Integer> events,Player p) {
        Integer tick=events.get(p.getUniqueId());
        if(tick==null)return false;
        int age=p.getTicksLived()-tick;if(age<0||age>2){events.remove(p.getUniqueId());return false;}return true;
    }
    private PlayerPoseSnapshot sample(Player p,String phase){return sampler.sample(p,phase,recent(jumps,p),recent(impulses,p));}
    private void tick() {
        for(UUID id:List.copyOf(watched)) {
            Player p=plugin.getServer().getPlayer(id);
            if(p==null){unwatch(id);continue;}
            try {
                var frame=sample(p,clock.phase());var trace=traces.get(id);
                if(trace.isEmpty()||trace.getLast().sequence()!=frame.sequence()){
                    trace.addLast(frame);if(trace.size()>256)trace.removeFirst();
                    var cape=capeTraces.get(id);cape.addLast(Map.of("poseSequence",frame.sequence(),"sampledAtMillis",frame.sampledAtEpochMillis(),
                        "render",((CapeBackend)plugin).renderDiagnostic(p)));if(cape.size()>256)cape.removeFirst();
                }
            } catch(RuntimeException failure) {unwatch(id);plugin.getLogger().warning(Messages.t("pose.sample-failed","player",p.getName(),"error",failure.getClass().getSimpleName()));}
        }
    }
    String export(Player p) {
        PlayerPoseSnapshot frame=sample(p,"command");
        var frames=new ArrayList<>(traces.getOrDefault(p.getUniqueId(),new ArrayDeque<>()));
        if(frames.isEmpty()||frames.get(frames.size()-1).sequence()!=frame.sequence())frames.add(frame);
        // Capture all live state on the server thread. Serialize/write only immutable copies off-thread.
        var report=Map.of("schema",2,"jumpEventSource",jumpEvents,"clientRenderFrameAvailable",false,"latest",frame,"frames",List.copyOf(frames),
            "capeFrames",List.copyOf(capeTraces.getOrDefault(p.getUniqueId(),new ArrayDeque<>())));
        String name=p.getUniqueId()+".json";
        enqueue(report,name);return name;
    }
    String reference(Player player,String version)throws java.io.IOException {
        NativeReferenceModel model=NativeReferenceModel.load(version);
        var snapshot=sample(player,"reference-command");
        Double scale=snapshot.scale().effective().available()?snapshot.scale().effective().value():null;
        List<Map<String,Object>> examples=new ArrayList<>();
        for(boolean slim:List.of(false,true))for(boolean crouch:List.of(false,true)){
            var input=new NativeReferenceModel.Input(slim,false,0,0,40,0,0,crouch,false,false,0,0,1);
            var parts=model.evaluate(input);var edge=model.bodyBackTop(parts,slim,crouch);
            var scaled=new ArrayList<org.joml.Vector3f>();if(scale!=null)for(var point:edge)scaled.add(new org.joml.Vector3f(point).mul(scale.floatValue()));
            Map<String,Object> example=new LinkedHashMap<>();example.put("input",input);example.put("parts",parts);
            Map<String,float[]> matrices=new LinkedHashMap<>();parts.forEach((name,part)->matrices.put(name,part.matrix()));example.put("matrices",matrices);
            example.put("bodyBackTopModelRoot",edge);example.put("bodyBackTopScaledModelRoot",scale==null?null:scaled);examples.add(example);
        }
        var report=Map.of("schema",1,"requestedReferenceVersion",version,"backendVersion",plugin.getServer().getBukkitVersion(),
            "referenceOnly",true,"clientFrameAvailable",false,"inputSource","STATIC_REFERENCE_EXAMPLES",
            "liveScaleState",snapshot.scale(),"examples",examples);
        String name="reference-"+player.getUniqueId()+"-"+version+".json";enqueue(report,name);return name;
    }
    private void enqueue(Object report,String name){
        writer.execute(()->{
            try {
                Files.createDirectories(output);
                Path temp=Files.createTempFile(output,"pose-",".tmp");
                try {
                    Files.writeString(temp,new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report));
                    try{Files.move(temp,output.resolve(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(AtomicMoveNotSupportedException e){Files.move(temp,output.resolve(name),StandardCopyOption.REPLACE_EXISTING);}
                } finally {Files.deleteIfExists(temp);}
            }catch(Exception e){if(!closed)plugin.getLogger().warning(Messages.t("pose.export-failed","error",e.getClass().getSimpleName()));}
        });
    }
    @EventHandler public void quit(PlayerQuitEvent e){unwatch(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){reset(e.getPlayer());}
    @EventHandler public void respawn(PlayerRespawnEvent e){reset(e.getPlayer());}
    @EventHandler public void world(PlayerChangedWorldEvent e){reset(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void velocity(PlayerVelocityEvent e){if(watched.contains(e.getPlayer().getUniqueId()))impulses.put(e.getPlayer().getUniqueId(),e.getPlayer().getTicksLived());}
    private void reset(Player p){sampler.reset(p.getUniqueId());jumps.remove(p.getUniqueId());impulses.remove(p.getUniqueId());}
    @Override public void close(){closed=true;clock.close();HandlerList.unregisterAll(this);plugin.getServer().getServicesManager().unregister(sampler);for(UUID id:List.copyOf(watched))unwatch(id);writer.shutdown();}
}
