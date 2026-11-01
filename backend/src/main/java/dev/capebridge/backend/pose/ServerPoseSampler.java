package dev.capebridge.backend.pose;

import org.bukkit.entity.Player;
import dev.capebridge.common.pose.*;
import dev.capebridge.common.pose.PoseGeometry.*;
import java.util.*;
import static dev.capebridge.common.pose.Observation.*;

/** Main-thread adapter. No dependency on CapeRenderer, TorsoHeading or prediction. */
public final class ServerPoseSampler implements PlayerPoseSource {
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final MotionTracker motion = new MotionTracker();
        long sequence; volatile PlayerPoseSnapshot latest; boolean reset = true;
    }
    private final Map<UUID, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private final NativeAngles angles;
    private final ScaleReader scales;
    private final String version;
    public ServerPoseSampler(String version) { this.version=version; angles=new NativeAngles(version); scales=new ScaleReader(version); }
    public void forget(UUID player) { sessions.remove(player); }
    public void reset(UUID player) { Session s=sessions.get(player); if(s!=null) s.reset=true; }
    @Override public Optional<PlayerPoseSnapshot> latest(UUID player) { Session s=sessions.get(player); return Optional.ofNullable(s==null?null:s.latest); }
    public PlayerPoseSnapshot sample(Player p, String phase, boolean jump, boolean impulse) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) throw new IllegalStateException("Pose sampling requires server thread");
        Session s=sessions.computeIfAbsent(p.getUniqueId(), k->new Session());
        var location=p.getLocation(); int tick=p.getTicksLived();
        boolean reset=s.reset || s.latest!=null && (!s.latest.world().equals(p.getWorld().getUID()) || tick<s.latest.entityTick());
        if (!reset && s.latest!=null && tick==s.latest.entityTick()) return s.latest;
        var velocity=p.getVelocity();
        PlayerPoseSnapshot.RootState root=new PlayerPoseSnapshot.RootState(
            of(new Vec3(location.getX(),location.getY(),location.getZ()),Source.SERVER_API,"entity.feet.world"),
            of(new Vec3(velocity.getX(),velocity.getY(),velocity.getZ()),Source.SERVER_API,"entity.velocity.blocks_per_tick"),
            of((double)location.getYaw(),Source.SERVER_API,"entity.yaw"), of((double)location.getPitch(),Source.SERVER_API,"entity.pitch"),
            of(p.getWidth(),Source.SERVER_API,"entity.collision_width"),of(p.getHeight(),Source.SERVER_API,"entity.collision_height"),
            p.getPose().name(),p.getMainHand().name());
        Map<BodyPart,PartPose> parts=new EnumMap<>(BodyPart.class);
        for(BodyPart part:BodyPart.values())parts.put(part,PartPose.unavailable(part));
        Observation<Vec3> anchor=missing(Availability.NOT_TRANSMITTED,"client.body.back_top.not_transmitted");
        Observation<Double> previous=reset||s.latest==null ? missing(Availability.NOT_SAMPLED,"server.body.previous_reset") : s.latest.body().serverYaw();
        var body=new PlayerPoseSnapshot.BodyState(angles.body(p),previous,angles.head(p),anchor,anchor,anchor);
        var motion=s.motion.sample(new MotionTracker.Input(tick,location.getY(),p.isOnGround(),p.isFlying(),p.isGliding(),p.isSwimming(),p.isInsideVehicle(),jump,impulse),reset);
        s.latest=new PlayerPoseSnapshot(p.getUniqueId(),s.id,p.getWorld().getUID(),++s.sequence,tick,System.currentTimeMillis(),version,phase,
            root,parts,body,scales.read(p),motion,false);
        s.reset=false;
        return s.latest;
    }
}
