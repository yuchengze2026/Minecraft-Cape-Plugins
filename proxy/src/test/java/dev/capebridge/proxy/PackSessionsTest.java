package dev.capebridge.proxy;
import dev.capebridge.common.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PackSessionsTest {
 final UUID id=UUID.randomUUID();final Object player=new Object(),a=new Object(),b=new Object();final String hash="a".repeat(40);
 Wire.PackReport report(PackSnapshot s,PackStatus status){return new Wire.PackReport(id,s.session(),s.attempt(),hash,status,false);}
 @Test void loadedPackSurvivesTransfersAndUnchangedReload(){
  PackSessions ledger=new PackSessions();PackSnapshot first=ledger.snapshot(id,player,a,hash);assertTrue(first.offer());
  assertTrue(ledger.report(id,player,a,report(first,PackStatus.PENDING)));
  assertTrue(ledger.report(id,player,a,report(first,PackStatus.LOADED)));
  for(Object backend:List.of(b,a,b)){var next=ledger.snapshot(id,player,backend,hash);assertFalse(next.offer());assertEquals(PackStatus.LOADED,next.status());assertEquals(first.attempt(),next.attempt());}
  var changed=ledger.snapshot(id,player,b,"b".repeat(40));assertTrue(changed.offer());assertNotEquals(first.attempt(),changed.attempt());
 }
 @Test void refusalSurvivesSwitchUntilManualRetryOrReconnect(){
  PackSessions ledger=new PackSessions();var first=ledger.snapshot(id,player,a,hash);
  ledger.report(id,player,a,report(first,PackStatus.PENDING));ledger.report(id,player,a,report(first,PackStatus.DECLINED));
  assertFalse(ledger.snapshot(id,player,b,hash).offer());
  assertTrue(ledger.retry(id,player,new Wire.PackRetry(id,first.session(),first.attempt(),hash)));
  var retry=ledger.snapshot(id,player,b,hash);assertTrue(retry.offer());assertNotEquals(first.attempt(),retry.attempt());
  ledger.disconnect(id,player);assertEquals(0,ledger.size());assertNotEquals(first.session(),ledger.snapshot(id,player,a,hash).session());
 }
 @Test void pendingDownloadCanFinishOnDestinationButOldAttemptsAndOtherConnectionsCannotReport(){
  PackSessions ledger=new PackSessions();var first=ledger.snapshot(id,player,a,hash);
  assertFalse(ledger.report(id,player,b,report(first,PackStatus.PENDING)));
  assertTrue(ledger.report(id,player,a,report(first,PackStatus.PENDING)));
  assertFalse(ledger.snapshot(id,player,b,hash).offer());
  assertFalse(ledger.report(id,new Object(),b,report(first,PackStatus.LOADED)));
  assertTrue(ledger.report(id,player,b,report(first,PackStatus.LOADED)));
  assertTrue(ledger.report(id,player,b,report(first,PackStatus.FAILED)));
  assertFalse(ledger.snapshot(id,player,a,hash).offer());
  assertEquals(PackStatus.FAILED,ledger.snapshot(id,player,a,hash).status());
  ledger.retry(id,player,new Wire.PackRetry(id,first.session(),first.attempt(),hash));
  assertFalse(ledger.report(id,player,b,report(first,PackStatus.LOADED)));
 }
 @Test void downloadAndReloadFailuresNeverReofferDuringTransfers(){
  for(PackStatus outcome:List.of(PackStatus.FAILED,PackStatus.DECLINED,PackStatus.DISCARDED)){
   var ledger=new PackSessions();var first=ledger.snapshot(id,player,a,hash);
   assertTrue(ledger.report(id,player,a,report(first,PackStatus.PENDING)));
   assertTrue(ledger.report(id,player,a,report(first,outcome)));
   for(Object server:List.of(b,a,b,a)){
    var next=ledger.snapshot(id,player,server,hash);assertFalse(next.offer());assertEquals(outcome,next.status());assertEquals(first.attempt(),next.attempt());
   }
  }
 }
}
