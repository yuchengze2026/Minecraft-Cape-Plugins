package dev.capebridge.proxy;
import java.util.Map;
/** JDK HTTP limits are JVM-wide, read at first initialization. Explicit JVM flags win. */
final class HttpLimits {
    static void defaults(){
        Map<String,String> values=Map.of(
            "jdk.httpserver.maxConnections","128",
            "sun.net.httpserver.maxReqHeaders","64",
            "sun.net.httpserver.maxReqHeaderSize","16384",
            "sun.net.httpserver.maxReqTime","10",
            "sun.net.httpserver.maxRspTime","60",
            "sun.net.httpserver.idleInterval","5",
            "sun.net.httpserver.maxIdleConnections","0",
            "sun.net.httpserver.drainAmount","0");
        values.forEach((key,value)->System.getProperties().putIfAbsent(key,value));
    }
}
