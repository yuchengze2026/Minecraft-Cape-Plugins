package dev.capebridge.proxy;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Bounded fixed-window counters; no address comes from an HTTP header. */
final class DownloadLimit {
    private record Window(long start,int count){}
    private final int requests,capacity;
    private final Map<InetAddress,Window> clients=new HashMap<>();
    private static final long MINUTE=TimeUnit.MINUTES.toNanos(1);
    DownloadLimit(int requests,int capacity){this.requests=requests;this.capacity=capacity;}
    synchronized boolean allow(InetAddress address,long now){
        Window old=clients.get(address);
        if(old!=null&&now-old.start<MINUTE){
            if(old.count>=requests)return false;
            clients.put(address,new Window(old.start,old.count+1));return true;
        }
        if(old==null&&clients.size()>=capacity){
            clients.values().removeIf(w->now-w.start>=MINUTE);
            if(clients.size()>=capacity)return false;
        }
        clients.put(address,new Window(now,1));return true;
    }
    synchronized int size(){return clients.size();}
}
