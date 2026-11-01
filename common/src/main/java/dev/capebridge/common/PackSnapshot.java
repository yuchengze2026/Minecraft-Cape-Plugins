package dev.capebridge.common;
import java.util.UUID;
public record PackSnapshot(UUID session,UUID attempt,long revision,PackStatus status,boolean offer,boolean identified) {
 public static final UUID ZERO=new UUID(0,0);
 public static final PackSnapshot EMPTY=new PackSnapshot(ZERO,ZERO,0,PackStatus.UNKNOWN,false,false);
 public PackSnapshot {
  if(session==null||attempt==null||status==null||revision<0||(offer&&status!=PackStatus.OFFERING))throw new IllegalArgumentException("Invalid pack snapshot");
  if(session.equals(ZERO)!=attempt.equals(ZERO)||(session.equals(ZERO)&&(revision!=0||status!=PackStatus.UNKNOWN||offer||identified)))throw new IllegalArgumentException("Invalid empty pack snapshot");
 }
 public boolean present(){return !session.equals(ZERO);}
}
