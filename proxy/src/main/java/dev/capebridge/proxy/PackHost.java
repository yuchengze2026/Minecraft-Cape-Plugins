package dev.capebridge.proxy;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
final class PackHost implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor pool;
    private final DownloadLimit limit;
    private final AtomicReference<byte[]> bytes=new AtomicReference<>();
    PackHost(String bind,int port)throws IOException{this(bind,port,600);}
    PackHost(String bind,int port,int perMinute)throws IOException {
        HttpLimits.defaults();
        limit=new DownloadLimit(Math.max(10,Math.min(10000,perMinute)),2048);
        pool=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"CapeBridge-Pack");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        server=HttpServer.create(new InetSocketAddress(bind,port),16);server.setExecutor(pool);
        server.createContext("/",this::serve);server.start();
    }
    private void serve(HttpExchange exchange)throws IOException {
        try {
            var headers=exchange.getResponseHeaders();
            headers.set("Connection","close");headers.set("Cache-Control","no-store");
            headers.set("X-Content-Type-Options","nosniff");headers.set("Content-Security-Policy","default-src 'none'");
            // Only the TCP peer counts; Forwarded/X-Forwarded-For is untrusted.
            if(!limit.allow(exchange.getRemoteAddress().getAddress(),System.nanoTime())){
                headers.set("Retry-After","60");exchange.sendResponseHeaders(429,-1);return;
            }
            URI uri=exchange.getRequestURI();
            if(uri.toASCIIString().length()>4096){exchange.sendResponseHeaders(414,-1);return;}
            if(!"/capes.zip".equals(uri.getRawPath())){exchange.sendResponseHeaders(404,-1);return;}
            String method=exchange.getRequestMethod();
            if(!method.equals("GET")&&!method.equals("HEAD")){
                headers.set("Allow","GET, HEAD");exchange.sendResponseHeaders(405,-1);return;
            }
            String length=exchange.getRequestHeaders().getFirst("Content-Length");
            if(exchange.getRequestHeaders().containsKey("Transfer-Encoding")||(length!=null&&!length.equals("0"))){exchange.sendResponseHeaders(400,-1);return;}
            byte[] data=bytes.get();if(data==null){exchange.sendResponseHeaders(503,-1);return;}
            headers.set("Content-Type","application/zip");headers.set("Content-Disposition","attachment; filename=\"capes.zip\"");
            headers.set("Content-Length",Integer.toString(data.length));
            exchange.sendResponseHeaders(200,method.equals("HEAD")?-1:data.length);
            if(method.equals("GET"))exchange.getResponseBody().write(data);
        }finally{exchange.close();}
    }
    void publish(byte[] pack){bytes.set(pack.clone());}
    int port(){return server.getAddress().getPort();}
    int queued(){return pool.getQueue().size();}
    @Override public void close(){server.stop(0);pool.shutdownNow();}
}
