package dev.capebridge.proxy;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class StorageQueueTest{
 @Test void overloadIsRejectedRatherThanBlockingTheCallerOrGrowingMemory()throws Exception{
  var started=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var queue=new StorageQueue(2)){
   assertTrue(queue.submit(()->{started.countDown();try{release.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}}));
   assertTrue(started.await(2,TimeUnit.SECONDS));assertTrue(queue.submit(()->{}));assertTrue(queue.submit(()->{}));assertFalse(queue.submit(()->fail("Must not run on network thread")));assertEquals(2,queue.queued());
   release.countDown();
  }finally{release.countDown();}
 }
 @Test void workCannotBeQueuedAfterShutdown(){var queue=new StorageQueue(2);queue.close();assertFalse(queue.submit(()->fail("Must not execute")));}
}
