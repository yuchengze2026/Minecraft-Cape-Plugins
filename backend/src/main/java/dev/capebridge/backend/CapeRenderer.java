package dev.capebridge.backend;

import dev.capebridge.common.*;
import dev.capebridge.backend.pose.NativeAngles;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import java.util.*;

final class CapeRenderer implements AutoCloseable {
    private final CapeBackend plugin;
    private final ClientAttachment attachment;
    private final NativeCapeMask nativeMask;
    private final BodyHeading bodyHeading;
    private final TorsoHeading torsoHeading;
    private final TorsoHeading selfHeading;
    private final LegacyTorsoHeading legacyHeading=new LegacyTorsoHeading();
    private final boolean experimentLegacy=Boolean.getBoolean("capebridge.experiment.legacy064");
    private final boolean experimentServerBody=Boolean.getBoolean("capebridge.experiment.serverBody");
    private final boolean experimentSmooth=Boolean.getBoolean("capebridge.experiment.smoothRotation");
    private final boolean experimentVelocity=Boolean.getBoolean("capebridge.experiment.velocitySmoothing");
    private final boolean experimentGuard=Boolean.getBoolean("capebridge.experiment.directionGuard");
    private final boolean experimentBoundedGuard=Boolean.parseBoolean(System.getProperty("capebridge.experiment.boundedGuard","true"));
    private final boolean experimentSelfBillboard=Boolean.getBoolean("capebridge.experiment.selfBillboard");
    private final boolean experimentSelfBillboardLead=Boolean.getBoolean("capebridge.experiment.selfBillboardLead");
    private final boolean experimentViewer;
    private final int experimentCadence;
    private final boolean tolerateGap;
    private final boolean experimentLead;
    private final boolean transitionSmoothing;
    private final NativeAngles serverAngles;
    private final dev.capebridge.backend.pose.ScaleReader scaleReader;
    private final NamespacedKey marker;
    private final boolean modernMount;
    private final Map<UUID,Wire.Cape> selected=new HashMap<>();
    private final Map<UUID,Render> live=new HashMap<>(),trials=new HashMap<>();
    private BukkitTask equipmentTask;
    private final RenderClock clock;
    private int tick,period;
    private long samples,total,max;
    private static final class Render {
        final Wire.Cape cape;
        final boolean preview;
        final Display[] parts=new Display[1];
        final Transformation[] sent=new Transformation[1];
        final Transformation[] sentObserver=new Transformation[1];
        final int[] sentDuration=new int[1];
        final CapeCloth cloth;
        final HeadingLead lead,batchLead,observerLead;
        final CadenceBlend blend=new CadenceBlend();
        final HeadingSmoother smoother=new HeadingSmoother();
        final DirectionGuard guard=new DirectionGuard();
        final BoundedDirectionGuard boundedGuard=new BoundedDirectionGuard();
        final ContinuousTurnLead continuousLead=new ContinuousTurnLead(60);
        final BendSmoother bend=new BendSmoother();
        ContinuousTurnLead modernLead;
        Location previous;
        float yaw,rawYaw,observerYaw,selfPhase,playerScale=1,lift=6;boolean selfPhaseInitialized;
        int anchorTick,expires,lastFlushTick=Integer.MIN_VALUE/2;
        int profile=-1;
        boolean crouched,batching;
        Render(Wire.Cape cape,boolean preview,Player owner,int cadence,boolean grace){
            this.cape=cape;this.preview=preview;
            boolean mainGrace=grace&&!Boolean.getBoolean("capebridge.experiment.batchGraceOnly");
            float cap=Float.parseFloat(System.getProperty("capebridge.experiment.selfLeadCap","60"));
            float normalCap=cap<0?80f:Math.min(140,cap),batchCap=cap<0?140f:Math.min(140,cap);
            lead=new HeadingLead(!Boolean.getBoolean("capebridge.experiment.headingLead"),1f,normalCap,mainGrace,Integer.getInteger("capebridge.experiment.leadSamples",3),Integer.getInteger("capebridge.experiment.leadRamp",18));
            batchLead=cadence>1?new HeadingLead(true,cadence,batchCap,grace):new HeadingLead(true);
            observerLead=new HeadingLead(true,-2f,150f,mainGrace);
            previous=owner.getLocation();yaw=previous.getYaw();cloth=new CapeCloth();
        }
        int count(){return 1;}
    }
    CapeRenderer(CapeBackend plugin){
        this.plugin=plugin;experimentLead=plugin.getConfig().getBoolean("render.turn-prediction",true)||Boolean.getBoolean("capebridge.experiment.headingLead")||Boolean.getBoolean("capebridge.experiment.softLead");
        experimentCadence=Math.max(1,Math.min(3,Integer.getInteger("capebridge.experiment.cadence",1)));
        transitionSmoothing=plugin.getConfig().getBoolean("render.transition-smoothing",true);
        experimentViewer=experimentLead&&(plugin.getConfig().getBoolean("render.observer-correction",true)||Boolean.getBoolean("capebridge.experiment.viewerHeading"));
        tolerateGap=(experimentCadence>1&&plugin.getConfig().getBoolean("render.tolerate-turn-gaps",true))||Boolean.getBoolean("capebridge.experiment.turnGrace");
        torsoHeading=new TorsoHeading(plugin);selfHeading=new TorsoHeading(plugin,true);attachment=new ClientAttachment(plugin,this::clientWorldReset);nativeMask=new NativeCapeMask(plugin);bodyHeading=new BodyHeading(plugin);serverAngles=new NativeAngles(plugin.getServer().getBukkitVersion());
        scaleReader=new dev.capebridge.backend.pose.ScaleReader(plugin.getServer().getBukkitVersion());
        marker=new NamespacedKey(plugin,"cape");period=1;
        modernMount=com.github.retrooper.packetevents.PacketEvents.getAPI().getServerManager().getVersion()
                .isNewerThanOrEquals(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_20_2);
        clock=new RenderClock(plugin,period,this::frame);
        for(Player p:plugin.getServer().getOnlinePlayers())join(p);
    }
    void permissions(UUID id,List<Wire.Cape> catalog){Render trial=trials.get(id);if(trial!=null&&catalog.stream().noneMatch(c->c.id().equals(trial.cape.id())&&c.available()))cancelPreview(id);}
    void select(UUID id,Wire.Cape cape){
        Wire.Cape old=selected.get(id);
        if(cape==null)selected.remove(id);else selected.put(id,cape);
        if(!Objects.equals(old,cape)){remove(live,id);cancelPreview(id);}
        Player p=plugin.getServer().getPlayer(id);if(p!=null)equipment(p);wake();refresh(id);
    }
    void preview(Player p,Wire.Cape cape){
        cancelPreview(p.getUniqueId());Render r=new Render(cape,true,p,experimentCadence,tolerateGap);
        r.expires=tick+20*Math.max(5,Math.min(60,plugin.getConfig().getInt("menu.preview-seconds",15)));
        trials.put(p.getUniqueId(),r);wake();refresh(p.getUniqueId());
    }
    void cancelPreview(UUID id){remove(trials,id);refresh(id);}
    boolean previewing(UUID id){return trials.containsKey(id);}
    void forget(UUID id){
        selected.remove(id);
        torsoHeading.forget(id);
        selfHeading.forget(id);
        legacyHeading.forget(id);
        // A disconnect already removes the player client-side; do not send restoration packets to a closed connection.
        for(Map<UUID,Render> map:List.of(live,trials)){Render r=map.remove(id);if(r!=null)dispose(r);}
        attachment.quit(id);nativeMask.forget(id);
    }
    void cleanup(Chunk c){for(Entity e:c.getEntities())if(e.getPersistentDataContainer().has(marker,PersistentDataType.BYTE)&&!attachment.isTracked(e.getEntityId()))e.remove();}
    private boolean wearing(Player p){ItemStack item=p.getInventory().getChestplate();return item!=null&&item.getType()==Material.ELYTRA;}
    private boolean visible(Player p){return p.isOnline()&&plugin.packReady(p.getUniqueId())&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&!p.isInvisible()
            &&!p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)&&!p.isSleeping()&&!p.isSwimming()&&!wearing(p);}
    private void maintenance(){
        if(equipmentTask!=null)return;
        equipmentTask=plugin.getServer().getScheduler().runTaskTimer(plugin,()->{
            if(plugin.getServer().getOnlinePlayers().isEmpty()){equipmentTask.cancel();equipmentTask=null;return;}
            for(Player p:plugin.getServer().getOnlinePlayers())syncEquipment(p);
        },1,5);
    }
    void equipment(Player p){maintenance();syncEquipment(p);}
    void swing(Player p){torsoHeading.swing(p);selfHeading.swing(p);legacyHeading.swing(p);}
    private void clientWorldReset(UUID id){
        // SR can rebuild the local player without a Bukkit world-change event.
        // Recreate its cape from the new local torso origin, never the old pose.
        selfHeading.forget(id);torsoHeading.forget(id);remove(live,id);cancelPreview(id);wake();
    }
    Map<String,Object> diagnostic(Player owner){
        List<Map<String,Object>> displays=new ArrayList<>();
        for(Render r:new Render[]{live.get(owner.getUniqueId()),trials.get(owner.getUniqueId())})if(r!=null)for(int i=0;i<r.parts.length;i++){
            Display part=r.parts[i];if(part==null||r.sent[i]==null)continue;
            Transformation t=r.sent[i];var origin=CapePivot.origin(r.profile);
            List<List<Double>> edge=List.of(List.of(-.3125,-origin.y(),origin.z()),List.of(.3125,-origin.y(),origin.z()));
            displays.add(Map.of("uuid",part.getUniqueId().toString(),"entityId",part.getEntityId(),"cape",r.cape.id(),"profile",r.profile,
                "preview",r.preview,"interpolationTicks",r.sentDuration[i],"geometryRole","DISPLAY_BEFORE_ITEM","innerTopEdge",edge,
                "target",Map.of("translation",List.of(t.getTranslation().x,t.getTranslation().y,t.getTranslation().z),
                    "scale",List.of(t.getScale().x,t.getScale().y,t.getScale().z),
                    "left",List.of(t.getLeftRotation().x,t.getLeftRotation().y,t.getLeftRotation().z,t.getLeftRotation().w),
                    "right",List.of(t.getRightRotation().x,t.getRightRotation().y,t.getRightRotation().z,t.getRightRotation().w))));
        }
        Map<String,Object> result=new LinkedHashMap<>(Map.of("owner",owner.getUniqueId().toString(),"entityTick",owner.getTicksLived(),"rendererTick",tick,"displays",List.copyOf(displays)));
        Render active=live.get(owner.getUniqueId());if(active!=null){result.put("rawYaw",active.rawYaw);result.put("renderYaw",active.yaw);}
        return result;
    }
    void join(Player p){nativeMask.join(p);maintenance();torsoHeading.sample(p);selfHeading.sample(p);clock.wake();}
    private void syncEquipment(Player p){
        refreshNative(p);
        if(wearing(p)){remove(live,p.getUniqueId());Render trial=trials.get(p.getUniqueId());if(trial!=null)dispose(trial);}
        refreshNative(p);
    }
    private void wake(){if(!selected.isEmpty()||!trials.isEmpty())clock.wake();}
    private void dispose(Render r){for(int i=0;i<r.parts.length;i++)if(r.parts[i]!=null){attachment.unregister(r.parts[i]);r.parts[i].remove();r.parts[i]=null;r.sent[i]=null;r.sentObserver[i]=null;}}
    private void remove(Map<UUID,Render> map,UUID id){Render r=map.remove(id);if(r!=null){dispose(r);Player p=plugin.getServer().getPlayer(id);if(p!=null)refreshNative(p);}}
    private float setting(String key,float fallback){return(float)plugin.getConfig().getDouble("render."+key,fallback);}
    private float riding(Player p){return PassengerAnchor.height(p.getHeight(),modernMount);}
    private Location anchor(Player p){Location l=p.getLocation().add(0,riding(p),0);l.setYaw(0);l.setPitch(0);return l;}
    private Transformation[] poses(Player p,Render r){
        return poses(p,r,r.yaw);
    }
    private Transformation[] poses(Player p,Render r,float yaw){
        float lift=r.lift;
        float scale=r.playerScale;
        if(scale!=1){
            Transformation base=experimentLegacy||experimentServerBody?CapePose.hinged(yaw,lift,r.crouched,0,back(p)):CapePose.smoothHinged(yaw,lift,r.crouched,0,back(p));
            return new Transformation[]{CapePose.scaled(CapePose.pivoted(base,r.profile),scale,riding(p))};
        }
        Transformation pose=experimentLegacy||experimentServerBody?CapePose.hinged(yaw,lift,r.crouched,riding(p),back(p)):CapePose.smoothHinged(yaw,lift,r.crouched,riding(p),back(p));
        return new Transformation[]{CapePose.pivoted(pose,r.profile)};
    }
    private int profile(Player p,boolean crouched){ItemStack chest=p.getInventory().getChestplate();boolean armor=chest!=null&&chest.getType().name().endsWith("CHESTPLATE");return CapePivot.profile(armor?2:(nativeMask.original(p)&2)!=0?1:0,crouched);}
    private ItemStack model(Render r){ItemStack item=new ItemStack(Material.PAPER);ItemMeta meta=Objects.requireNonNull(item.getItemMeta());meta.setCustomModelData(CapeMesh.smoothModel(r.cape.model(),r.profile));item.setItemMeta(meta);return item;}
    private float back(Player p){
        ItemStack chest=p.getInventory().getChestplate();boolean armor=chest!=null&&chest.getType().name().endsWith("CHESTPLATE");
        return CapePose.backSurface((nativeMask.original(p)&2)!=0,armor)+CapeFitting.clearance(setting("back-clearance",.008f));
    }

    private void configure(Player p,Display e,Transformation pose){
        e.setVisibleByDefault(false);e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setSilent(true);
        e.setDisplayWidth(0);e.setDisplayHeight(0);e.setShadowRadius(0);e.setShadowStrength(0);
        e.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
        e.setInterpolationDuration(0);e.setInterpolationDelay(0);
        e.setViewRange(Math.max(8,Math.min(128,plugin.getConfig().getInt("render.view-distance-blocks",32)))/64f);
        e.setTransformation(pose);attachment.register(p,e);
    }
    private void spawn(Player p,Render r){
        Transformation[] poses=poses(p,r);
        for(int i=0;i<r.count();i++){
            final int part=i;
            r.parts[i]=p.getWorld().spawn(anchor(p),ItemDisplay.class,e->{
                e.setItemStack(model(r));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);configure(p,e,poses[part]);
            });
        }
        refreshNative(p);
        for(Player v:plugin.getServer().getOnlinePlayers())visibility(v,p,r);
    }
    private boolean shown(Player viewer,Player owner,Render r){return viewer.getWorld()==owner.getWorld()&&viewer.canSee(owner)
            &&CapeFitting.visibleTo(viewer.equals(owner),r.preview)
            &&(r.preview||!viewer.equals(owner)||!trials.containsKey(owner.getUniqueId()))
            &&OptionalPack.visible(plugin.packReady(owner.getUniqueId()),plugin.packReady(viewer.getUniqueId()));}
    private void visibility(Player viewer,Player owner,Render r){
        boolean show=shown(viewer,owner,r);
        for(Display part:r.parts)if(part!=null){attachment.allow(viewer,part.getEntityId(),show);
            if(show&&!viewer.canSee(part))viewer.showEntity(plugin,part);else if(!show&&viewer.canSee(part))viewer.hideEntity(plugin,part);}
    }
    private boolean rendered(Render r){return r!=null&&r.parts[0]!=null&&r.parts[0].isValid();}
    private void refreshNative(Player owner){
        Render saved=live.get(owner.getUniqueId()),trial=trials.get(owner.getUniqueId());
        // Loading the pack alone must not hide vanilla/SR capes. Mask per viewer
        // only while a real equipped or preview display replaces the native cape.
        for(Player viewer:plugin.getServer().getOnlinePlayers())nativeMask.set(viewer,owner,
            visible(owner)&&((rendered(saved)&&shown(viewer,owner,saved))
                ||(rendered(trial)&&shown(viewer,owner,trial))));
    }
    void refresh(UUID id){Player p=plugin.getServer().getPlayer(id);if(p==null)return;
        for(Render r:new Render[]{live.get(id),trials.get(id)})if(r!=null)for(Player v:plugin.getServer().getOnlinePlayers())visibility(v,p,r);
        refreshNative(p);
    }
    void refreshViewer(Player viewer){
        for(Map<UUID,Render> map:List.of(live,trials))for(var e:map.entrySet()){
            Player p=plugin.getServer().getPlayer(e.getKey());if(p!=null){visibility(viewer,p,e.getValue());refreshNative(p);}}
        nativeMask.join(viewer);for(Player owner:plugin.getServer().getOnlinePlayers())refreshNative(owner);equipment(viewer);
    }
    private void update(Player p,Render r){
        if(r.modernLead==null)r.modernLead=new ContinuousTurnLead(Math.max(60,Math.min(140,setting("modern-max-lead",90))),Math.max(1,Math.min(2,setting("modern-prediction-ticks",1.25f))));
        float currentScale=(float)scaleReader.effectiveValue(p);boolean scaleChanged=Math.abs(currentScale-r.playerScale)>.00001f;r.playerScale=currentScale;
        Location at=p.getLocation();
        boolean discontinuity=at.getWorld()!=r.previous.getWorld()||at.distanceSquared(r.previous)>256;
        if(discontinuity){r.previous=at.clone();r.yaw=at.getYaw();r.lead.reset();r.batchLead.reset();r.observerLead.reset();r.blend.reset();r.smoother.reset();r.guard.reset();r.boundedGuard.reset();r.continuousLead.reset();r.modernLead.reset();r.bend.reset();r.batching=false;r.selfPhaseInitialized=false;}
        float speed=(float)(Math.hypot(at.getX()-r.previous.getX(),at.getZ()-r.previous.getZ())/period);
        float vertical=(float)((at.getY()-r.previous.getY())/period);
        boolean postureChanged=r.crouched!=(p.getPose()==Pose.SNEAKING);
        boolean crouched=p.getPose()==Pose.SNEAKING;
        r.crouched=crouched;
        int profile=profile(p,crouched);boolean profileChanged=r.profile!=profile;r.profile=profile;
        // Follow client torso rules; head yaw is only the native body constraint.
        if(experimentServerBody){var body=serverAngles.body(p);r.yaw=body.available()?body.value().floatValue():legacyHeading.sample(p);}
        else r.yaw=experimentLegacy?legacyHeading.sample(p):torsoHeading.sample(p);
        if(experimentViewer)r.observerYaw=r.observerLead.sample(r.yaw);
        if(!experimentLegacy&&!experimentServerBody)r.yaw=selfHeading.sample(p);
        r.rawYaw=r.yaw;
        if(experimentSelfBillboard){
            float offset=0;
            if(experimentSelfBillboardLead){float predicted=r.lead.sample(r.rawYaw);offset=(predicted-r.rawYaw)%720;if(offset>=360)offset-=720;if(offset< -360)offset+=720;}
            float desired=CapePose.wrap(180+r.rawYaw-at.getYaw()-offset);
            if(!r.selfPhaseInitialized){r.selfPhase=desired;r.selfPhaseInitialized=true;}
            else r.selfPhase=(r.selfPhase+CapePose.wrap(desired-CapePose.wrap(r.selfPhase)))%720;
            r.yaw=r.selfPhase;
        }else if(experimentLead&&!experimentServerBody&&!experimentLegacy)r.yaw=transitionSmoothing?(attachment.modernTurnTiming(p)?r.modernLead:r.continuousLead).sample(r.yaw):r.lead.sample(r.yaw);
        if(!experimentSelfBillboard&&experimentCadence>1&&experimentLead&&!experimentServerBody&&!experimentLegacy){
            float future=r.batchLead.sample(r.rawYaw);
            r.yaw=r.blend.sample(r.rawYaw,r.yaw,future,r.batchLead.settled()||(r.batching&&r.batchLead.consistent()));
        }
        if(!experimentSelfBillboard&&experimentVelocity&&!experimentServerBody&&!experimentLegacy)r.yaw=r.smoother.sample(r.rawYaw,r.yaw,r.lead.active());
        if(!experimentSelfBillboard&&experimentGuard&&!experimentServerBody&&!experimentLegacy)r.yaw=r.guard.sample(r.rawYaw,r.yaw);
        if(!experimentSelfBillboard&&experimentBoundedGuard&&!experimentServerBody&&!experimentLegacy)r.yaw=r.boundedGuard.sample(r.rawYaw,r.yaw);
        r.cloth.step(speed,vertical,0,false,p.isFlying(),period,setting("max-bend-angle",65),0,setting("motion-scale",1));r.cloth.side=0;
        float desiredLift=CapeFitting.lift(r.cloth.angle,r.crouched);
        r.lift=attachment.smoothBend(p)?r.bend.sample(desiredLift,Math.max(1,Math.min(6,setting("modern-max-bend-step",2.5f)))):desiredLift;
        boolean valid=true;for(int i=0;i<r.count();i++)if(r.parts[i]==null||!r.parts[i].isValid()||r.parts[i].getWorld()!=p.getWorld()){valid=false;break;}
        if(!valid){dispose(r);spawn(p,r);}
        else if(profileChanged)for(Display part:r.parts){((ItemDisplay)part).setItemStack(model(r));attachment.item(part);}
        boolean move=tick-r.anchorTick>=4||r.parts[0].getLocation().distanceSquared(anchor(p))>9;
        Transformation[] poses=poses(p,r);
        Transformation[] observerPoses=experimentViewer?poses(p,r,r.observerYaw):poses;
        boolean eligible=!experimentSelfBillboard&&experimentCadence>1&&r.batchLead.consistent()&&!postureChanged&&!profileChanged&&!discontinuity&&!torsoHeading.snap(p);
        boolean batching=eligible&&(r.batching||(r.batchLead.settled()&&r.blend.settled()));r.batching=batching;
        boolean flushDue=!batching||tick-r.lastFlushTick>=experimentCadence;
        for(int i=0;i<r.count();i++){
            if(move)r.parts[i].teleport(anchor(p));
            int duration=PoseInterpolation.duration(r.sent[i],poses[i],scaleChanged||postureChanged||profileChanged||discontinuity||(!experimentSelfBillboard&&!experimentLegacy&&!experimentServerBody&&torsoHeading.snap(p)));
            int observerDuration=experimentViewer?PoseInterpolation.duration(r.sentObserver[i],observerPoses[i],scaleChanged||postureChanged||profileChanged||discontinuity||torsoHeading.snap(p)):duration;
            if(experimentSmooth&&duration==1&&r.lead.active())duration=2;
            if(batching&&duration==1)duration=experimentCadence;
            if(batching&&observerDuration==1)observerDuration=experimentCadence;
            if((flushDue||duration==0||observerDuration==0)&&(!poses[i].equals(r.sent[i])||!observerPoses[i].equals(r.sentObserver[i]))){
                attachment.flush(p,r.parts[i],poses[i],duration,observerPoses[i],observerDuration);r.sent[i]=poses[i];r.sentObserver[i]=observerPoses[i];r.sentDuration[i]=duration;r.lastFlushTick=tick;
            }
        }
        if(move)r.anchorTick=tick;r.previous=at;
        if(tick%20<period)refresh(p.getUniqueId());
    }
    private void frame(){
        long start=System.nanoTime();tick+=period;
        // Loading a pack must not discard the body-turn history from joining.
        for(Player player:plugin.getServer().getOnlinePlayers()){torsoHeading.sample(player);selfHeading.sample(player);}
        for(UUID id:new ArrayList<>(trials.keySet()))if(trials.get(id).expires<=tick)cancelPreview(id);
        for(var e:selected.entrySet()){
            Player p=plugin.getServer().getPlayer(e.getKey());if(p==null||!visible(p)){remove(live,e.getKey());continue;}
            update(p,live.computeIfAbsent(e.getKey(),id->new Render(e.getValue(),false,p,experimentCadence,tolerateGap)));
        }
        for(var e:trials.entrySet()){
            Player p=plugin.getServer().getPlayer(e.getKey());if(p!=null&&visible(p))update(p,e.getValue());else dispose(e.getValue());
        }
        long elapsed=System.nanoTime()-start;samples++;total+=elapsed;max=Math.max(max,elapsed);
        if(plugin.getServer().getOnlinePlayers().isEmpty()){clock.sleep();}
    }
    private int entities(){int n=0;for(Map<UUID,Render> m:List.of(live,trials))for(Render r:m.values())for(Display e:r.parts)if(e!=null&&e.isValid())n++;return n;}
    String stats(){return Messages.t("stats.format","selected",selected.size(),"entities",entities(),"previews",trials.size(),"phase",clock.phase(),"packets",attachment.immediatePackets(),"period",period,"active",clock.active(),"samples",samples,"average",samples==0?0:total/samples/1000,"maximum",max/1000,"mounts",attachment.mounts(),"suppressed",attachment.blockedMoves());}
    String inspect(Player p){Render r=live.get(p.getUniqueId());String none=Messages.t("diagnostic.none");return Messages.t("inspect.format","head",p.getLocation().getYaw(),"body",torsoHeading.sample(p),"render",r==null?none:r.yaw,"server-body",bodyHeading.sample(p),"side",r==null?0:r.cloth.side,"crouched",r!=null&&r.crouched,"bend",r==null?0:r.cloth.angle,"thickness",CapeMesh.THICKNESS,"back",back(p),"mount-height",riding(p),"mount-profile",modernMount?"1.20.2+":"1.20.1","viewer-protocol",attachment.protocol(p),"pivot",r==null?none:r.profile,"native-state",nativeMask.inspect(p));}
    void reset(){samples=0;total=0;max=0;}
    @Override public void close(){
        clock.close();if(equipmentTask!=null)equipmentTask.cancel();
        for(Map<UUID,Render> m:List.of(live,trials))for(Render r:m.values())dispose(r);
        torsoHeading.close();selfHeading.close();nativeMask.close();attachment.close();
    }
}
