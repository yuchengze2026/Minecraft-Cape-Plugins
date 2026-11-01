package dev.capebridge.proxy;
import dev.capebridge.common.*;
import java.util.*;
/** Small in-memory ledger. Identity comparisons bind it to this TCP player and backend connection. */
final class PackSessions {
 private static final class Entry {
  final Object owner;final UUID session=UUID.randomUUID();UUID attempt=UUID.randomUUID();String hash="";long revision;PackStatus status=PackStatus.UNKNOWN;Object grant;boolean identified;
  Entry(Object owner){this.owner=owner;}
  void reset(String hash){this.hash=hash;attempt=UUID.randomUUID();status=PackStatus.UNKNOWN;grant=null;identified=false;revision++;}
 }
 private final Map<UUID,Entry> entries=new HashMap<>();
 synchronized PackSnapshot snapshot(UUID player,Object owner,Object server,String hash){
  Entry e=entries.get(player);if(e==null||e.owner!=owner){e=new Entry(owner);entries.put(player,e);}
  if(!Objects.equals(e.hash,hash))e.reset(hash);
  if(hash.isEmpty())return new PackSnapshot(e.session,e.attempt,e.revision,PackStatus.UNKNOWN,false,false);
  if(e.status==PackStatus.UNKNOWN||(e.status==PackStatus.OFFERING&&e.grant!=server)){
   if(e.status==PackStatus.OFFERING)e.reset(hash);
   e.status=PackStatus.OFFERING;e.grant=server;e.revision++;
  }
  return new PackSnapshot(e.session,e.attempt,e.revision,e.status,e.status==PackStatus.OFFERING&&e.grant==server,e.identified);
 }
 synchronized boolean report(UUID player,Object owner,Object server,Wire.PackReport report){
  Entry e=entries.get(player);
  if(e==null||!player.equals(report.player())||e.owner!=owner||!matches(e,report.session(),report.attempt(),report.hash())||!report.status().canFollow(e.status))return false;
  if(e.status==PackStatus.OFFERING){if(e.grant!=server||report.status()!=PackStatus.PENDING)return false;e.identified=report.identified();}
  e.status=report.status();e.revision++;return true;
 }
 synchronized boolean retry(UUID player,Object owner,Wire.PackRetry retry){
  Entry e=entries.get(player);if(e==null||!player.equals(retry.player())||e.owner!=owner||!matches(e,retry.session(),retry.attempt(),retry.hash()))return false;
  e.reset(e.hash);return true;
 }
 private static boolean matches(Entry e,UUID session,UUID attempt,String hash){return e.session.equals(session)&&e.attempt.equals(attempt)&&e.hash.equals(hash)&&!hash.isEmpty();}
 synchronized void disconnect(UUID player,Object owner){Entry e=entries.get(player);if(e!=null&&e.owner==owner)entries.remove(player);}
 synchronized void clear(){entries.clear();}
 synchronized int size(){return entries.size();}
}
