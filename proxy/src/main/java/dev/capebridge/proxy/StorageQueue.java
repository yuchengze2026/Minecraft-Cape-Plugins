package dev.capebridge.proxy;
import java.util.concurrent.*;
/** Disk/catalog work cannot accumulate without bound or fall back onto a network thread. */
final class StorageQueue implements AutoCloseable {
    private final ThreadPoolExecutor worker;
    StorageQueue(){this(128);}
    StorageQueue(int capacity){worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{Thread t=new Thread(r,"CapeBridge-Storage");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());}
    boolean submit(Runnable task){try{worker.execute(task);return true;}catch(RejectedExecutionException ex){return false;}}
    int queued(){return worker.getQueue().size();}
    public void close(){worker.shutdownNow();}
}
