package dev.capebridge.web;

import com.google.gson.Gson;
import com.google.gson.stream.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class WebServer implements AutoCloseable {
 private record Session(String csrf,long expires){}
 private final HttpServer server;
 private final ThreadPoolExecutor pool;
 private final CapeService service;
 private final URI base;
 private final Consumer<Throwable> errors;
 private final Gson gson=new Gson();
 private final Map<String,Long> codes=new LinkedHashMap<>();
 private final Map<String,Session> sessions=new LinkedHashMap<>();
 private final Map<String,Deque<Long>> failures=new LinkedHashMap<>();
 private final Semaphore importing=new Semaphore(1);
 private volatile boolean closed;
 WebServer(String bind,int port,String publicBase,CapeService service,Consumer<Throwable> errors) throws IOException {
  this.service=service;this.errors=errors;InetAddress address=InetAddress.getByName(bind);
  if(publicBase.isBlank()&&!address.isLoopbackAddress())throw new IOException(WebMessages.raw("server.text-031"));
  server=HttpServer.create(new InetSocketAddress(address,port),16);
  int actualPort=server.getAddress().getPort();
  String host=address.getHostAddress();if(host.contains(":"))host="["+host+"]";
  String origin=publicBase.isBlank()?"http://"+host+":"+actualPort:publicBase.replaceFirst("/+$","");
  try{base=URI.create(origin);}catch(IllegalArgumentException bad){server.stop(0);throw new IOException(WebMessages.raw("server.text-032"));}
  if(base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!(base.getScheme().equals("http")||base.getScheme().equals("https"))||!(base.getPath().isEmpty()||base.getPath().equals("/"))){server.stop(0);throw new IOException(WebMessages.raw("server.text-033"));}
  pool=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"CapeWeb-HTTP");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  server.setExecutor(pool);server.createContext("/",this::handle);server.start();
 }
 String url(){return base.toString()+"/";}
 synchronized String issueCode(){
  long now=System.currentTimeMillis();codes.entrySet().removeIf(e->e.getValue()<now);
  while(codes.size()>=8)codes.remove(codes.keySet().iterator().next());String code=SafeFiles.nonce();codes.put(digest(code),now+180_000);return code;
 }
 private static String digest(String value){return SafeFiles.hex(value.getBytes(StandardCharsets.UTF_8),"SHA-256");}
 private synchronized Session session(HttpExchange x){
  String cookie=x.getRequestHeaders().getFirst("Cookie");if(cookie==null||cookie.length()>4096)return null;
  long now=System.currentTimeMillis();sessions.entrySet().removeIf(e->e.getValue().expires<now);
  for(String item:cookie.split(";")){String[] p=item.trim().split("=",2);if(p.length==2&&p[0].equals("CAPEWEB")){String token=p[1];if(token.length()==45&&token.startsWith("\"")&&token.endsWith("\""))token=token.substring(1,44);if(token.matches("[A-Za-z0-9_-]{43}"))return sessions.get(digest(token));}}
  return null;
 }
 private synchronized boolean limited(String ip){
  long now=System.currentTimeMillis();failures.values().forEach(q->q.removeIf(t->t<now-60_000));failures.entrySet().removeIf(e->e.getValue().isEmpty());
  return failures.getOrDefault(ip,new ArrayDeque<>()).size()>=5;
 }
 private synchronized void failed(String ip){if(failures.size()>1024)failures.remove(failures.keySet().iterator().next());failures.computeIfAbsent(ip,k->new ArrayDeque<>()).add(System.currentTimeMillis());}
 private synchronized Map<String,String> login(String code,HttpExchange x){
  String hash=digest(code);Long expiration=null;String found=null;
  for(var entry:codes.entrySet())if(MessageDigest.isEqual(entry.getKey().getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII))){found=entry.getKey();expiration=entry.getValue();break;}
  if(found!=null)codes.remove(found);
  if(expiration==null||expiration<System.currentTimeMillis())return null;
  String token=SafeFiles.nonce(),csrf=SafeFiles.nonce();while(sessions.size()>=32)sessions.remove(sessions.keySet().iterator().next());sessions.put(digest(token),new Session(csrf,System.currentTimeMillis()+60*60*1000));
  x.getResponseHeaders().add("Set-Cookie","CAPEWEB="+token+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=3600"+(base.getScheme().equals("https")?"; Secure":""));
  return Map.of("csrf",csrf,"message",WebMessages.raw("server.text-034"));
 }
 private void handle(HttpExchange x){
  try{
   headers(x);
   if(closed){json(x,503,Map.of("error",WebMessages.raw("server.text-035")));return;}
   if(!Objects.equals(base.getRawAuthority().toLowerCase(Locale.ROOT),Optional.ofNullable(x.getRequestHeaders().getFirst("Host")).orElse("").toLowerCase(Locale.ROOT))){json(x,403,Map.of("error",WebMessages.raw("server.text-036")));return;}
   String path=x.getRequestURI().getRawPath(),verb=x.getRequestMethod();
   if(Set.of("/","/app.js","/animation.js","/styles.css").contains(path)){
    if(!verb.equals("GET")){method(x,"GET");return;}String resource=path.equals("/")?"index.html":path.substring(1);
    try(InputStream input=getClass().getResourceAsStream("/web/"+resource)){
     if(input==null){json(x,404,Map.of("error",WebMessages.raw("server.text-037")));return;}
     byte[] content=input.readAllBytes();if(resource.equals("index.html"))content=WebMessages.html(new String(content,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);send(x,200,content,resource.endsWith("js")?"text/javascript; charset=utf-8":resource.endsWith("css")?"text/css; charset=utf-8":"text/html; charset=utf-8");return;
    }
   }
   if(path.equals("/api/texts")){if(!verb.equals("GET")){method(x,"GET");return;}json(x,200,WebMessages.publicTexts());return;}
   boolean post=verb.equals("POST");
   if(path.equals("/api/login")){
    if(!post){method(x,"POST");return;}if(!origin(x)){json(x,403,Map.of("error",WebMessages.raw("server.text-038")));return;}
    String ip=x.getRemoteAddress().getAddress().getHostAddress();if(limited(ip)){json(x,429,Map.of("error",WebMessages.raw("server.text-039")));return;}
    Map<String,Object> input=body(x,4096,Set.of("code"));Object code=input.get("code");
    Map<String,String> result=code instanceof String?login((String)code,x):null;
    if(result==null){failed(ip);json(x,401,Map.of("error",WebMessages.raw("server.text-040")));}else json(x,200,result);return;
   }
   Session session=session(x);if(session==null){json(x,401,Map.of("error",WebMessages.raw("server.text-041")));return;}
   if(post&&(!origin(x)||!secureEquals(session.csrf,x.getRequestHeaders().getFirst("X-Cape-CSRF")))){json(x,403,Map.of("error",WebMessages.raw("server.text-042")));return;}
   if(path.equals("/api/session")&&verb.equals("GET")){json(x,200,Map.of("csrf",session.csrf));return;}
   if(path.equals("/api/status")&&verb.equals("GET")){json(x,200,service.status());return;}
   if(path.equals("/api/capes")&&verb.equals("GET")){json(x,200,Map.of("capes",service.list()));return;}
   if(path.equals("/api/texture")&&verb.equals("GET")){
    String id=query(x.getRequestURI().getRawQuery(),"id");if(!SafeFiles.id(id)){json(x,400,Map.of("error",WebMessages.raw("server.text-011")));return;}
    byte[] image=service.image(id);x.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+id+".png\"");send(x,200,image,"application/octet-stream");return;
   }
   if(path.equals("/api/pack")&&verb.equals("GET")){
    Path pack=SafeFiles.inside(service.folder(),"resource-pack.zip");if(!Files.isRegularFile(pack)){json(x,404,Map.of("error",WebMessages.raw("server.text-016")));return;}
    x.getResponseHeaders().set("Content-Type","application/zip");x.getResponseHeaders().set("Content-Disposition","attachment; filename=\"capes.zip\"");x.sendResponseHeaders(200,Files.size(pack));try(InputStream in=Files.newInputStream(pack)){in.transferTo(x.getResponseBody());}return;
   }
   if(path.equals("/api/logout")&&post){
    synchronized(this){sessions.values().removeIf(s->s==session);}x.getResponseHeaders().add("Set-Cookie","CAPEWEB=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");json(x,200,Map.of("message",WebMessages.raw("server.text-043")));return;
   }
   if(path.equals("/api/wardrobe")){
    if(!post){method(x,"POST");return;}
    if(!importing.tryAcquire()){json(x,409,Map.of("error",WebMessages.raw("server.text-044")));return;}
    try{
     Map<String,Object> input=body(x,4096,Set.of("id","visible"));
     if(!(input.get("id") instanceof String id)||!(input.get("visible") instanceof Boolean visible))throw new BadRequest(WebMessages.raw("server.wardrobe.invalid"));
     try{json(x,200,service.wardrobe(id,visible));}catch(IllegalArgumentException invalid){json(x,400,Map.of("error",invalid.getMessage()));}
    }finally{importing.release();}return;
   }
   if(path.equals("/api/delete")){
    if(!post){method(x,"POST");return;}if(!importing.tryAcquire()){json(x,409,Map.of("error",WebMessages.raw("server.text-044")));return;}
    try{Map<String,Object> input=body(x,4096,Set.of("id"));if(!(input.get("id") instanceof String id))throw new BadRequest(WebMessages.raw("server.text-013"));
     try{json(x,200,service.delete(id));}catch(IllegalArgumentException invalid){json(x,400,Map.of("error",invalid.getMessage()));}
    }finally{importing.release();}return;
   }
   if(path.equals("/api/gif")){
    if(!post){method(x,"POST");return;}if(!importing.tryAcquire()){json(x,409,Map.of("error",WebMessages.raw("server.text-044")));return;}
    try{
     Map<String,Object> input=body(x,14_100_000,Set.of("image","resolution","layout","fit"));
     if(!(input.get("image") instanceof String data)||data.length()>14_000_000)throw new BadRequest(WebMessages.raw("server.gif.invalid"));
     try{
      byte[] bytes=Base64.getDecoder().decode(data);int width=Integer.parseInt(String.valueOf(input.getOrDefault("resolution","128")));
      json(x,200,GifImporter.convert(bytes,width,String.valueOf(input.getOrDefault("layout","auto")),String.valueOf(input.getOrDefault("fit","contain"))));
     }catch(IllegalArgumentException invalid){json(x,400,Map.of("error",WebMessages.raw("server.gif.options")));}
     catch(IOException invalid){json(x,422,Map.of("error",invalid.getMessage()));}
    }finally{importing.release();}return;
   }
   if(path.equals("/api/install")||path.equals("/api/animation")){
    if(!post){method(x,"POST");return;}if(!importing.tryAcquire()){json(x,409,Map.of("error",WebMessages.raw("server.text-044")));return;}
    try{
     Map<String,Object> input=body(x,1_450_000,Set.of("id","name","image","replace","frameTicks","frameTimes"));
     if(!(input.get("id") instanceof String id)||!(input.get("name") instanceof String name)||!(input.get("image") instanceof String data)||data.length()>1_400_000){json(x,400,Map.of("error",WebMessages.raw("server.text-045")));return;}
     byte[] bytes;try{bytes=Base64.getDecoder().decode(data);}catch(IllegalArgumentException invalid){json(x,400,Map.of("error",WebMessages.raw("server.text-046")));return;}
     try{int ticks=2;if(input.containsKey("frameTicks"))try{ticks=Integer.parseInt(String.valueOf(input.get("frameTicks")));}catch(NumberFormatException invalid){throw new IllegalArgumentException(WebMessages.raw("server.animation.timing"));}
      List<Integer> times=List.of();if(input.containsKey("frameTimes")){Object raw=input.get("frameTimes");if(!(raw instanceof String text)||!text.matches("[0-9]{1,3}(,[0-9]{1,3}){1,63}"))throw new IllegalArgumentException(WebMessages.raw("server.animation.timing"));try{times=Arrays.stream(text.split(",")).map(Integer::valueOf).toList();}catch(NumberFormatException bad){throw new IllegalArgumentException(WebMessages.raw("server.animation.timing"));}}
      json(x,200,service.install(id,name,bytes,Boolean.TRUE.equals(input.get("replace")),path.equals("/api/animation"),ticks,times));}
     catch(CapeService.Conflict conflict){json(x,409,Map.of("error",conflict.getMessage()));}
     catch(IllegalArgumentException invalid){json(x,400,Map.of("error",invalid.getMessage()));}
     catch(IOException invalid){errors.accept(invalid);json(x,422,Map.of("error",WebMessages.raw("server.text-047")+invalid.getMessage()));}
    }finally{importing.release();}return;
   }
   json(x,404,Map.of("error",WebMessages.raw("server.text-048")));
  }catch(TooLarge limit){quietJson(x,413,WebMessages.raw("server.text-049"));}
  catch(BadRequest invalid){quietJson(x,400,invalid.getMessage());}
  catch(Exception failure){errors.accept(failure);quietJson(x,500,WebMessages.raw("server.text-050"));}
  finally{x.close();}
 }
 private boolean origin(HttpExchange x){return base.toString().equals(x.getRequestHeaders().getFirst("Origin"));}
 private static boolean secureEquals(String a,String b){return b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
 private static String query(String raw,String key) throws BadRequest {
  if(raw==null||raw.length()>512)return "";String result="";int count=0;
  for(String field:raw.split("&")){if(++count>8)throw new BadRequest(WebMessages.raw("server.text-051"));String[] parts=field.split("=",2);try{if(URLDecoder.decode(parts[0],StandardCharsets.UTF_8).equals(key))result=parts.length==2?URLDecoder.decode(parts[1],StandardCharsets.UTF_8):"";}catch(IllegalArgumentException bad){throw new BadRequest(WebMessages.raw("server.text-052"));}}
  return result;
 }
 private static Map<String,Object> body(HttpExchange x,int max,Set<String> allowed) throws IOException {
  if(!Optional.ofNullable(x.getRequestHeaders().getFirst("Content-Type")).orElse("").split(";",2)[0].trim().equals("application/json"))throw new BadRequest(WebMessages.raw("server.text-053"));
  String length=x.getRequestHeaders().getFirst("Content-Length");if(length!=null)try{if(Long.parseLong(length)>max)throw new TooLarge();}catch(NumberFormatException invalid){throw new BadRequest(WebMessages.raw("server.text-054"));}
  byte[] bytes=x.getRequestBody().readNBytes(max+1);if(bytes.length>max)throw new TooLarge();
  Map<String,Object> fields=new HashMap<>();try(var reader=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8))){
   reader.setLenient(false);reader.beginObject();while(reader.hasNext()){
    String key=reader.nextName();if(!allowed.contains(key)||fields.containsKey(key))throw new BadRequest(WebMessages.raw("server.text-055"));
    JsonToken token=reader.peek();if(token==JsonToken.STRING)fields.put(key,reader.nextString());else if(token==JsonToken.BOOLEAN)fields.put(key,reader.nextBoolean());else throw new BadRequest(WebMessages.raw("server.text-056"));
   }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new BadRequest(WebMessages.raw("server.text-057"));
  }catch(BadRequest bad){throw bad;}catch(Exception invalid){throw new BadRequest(WebMessages.raw("server.text-058"));}return fields;
 }
 private static void headers(HttpExchange x){
  Headers h=x.getResponseHeaders();h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("Referrer-Policy","no-referrer");h.set("X-Frame-Options","DENY");
  h.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob: data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
 }
 private void json(HttpExchange x,int status,Object value) throws IOException {send(x,status,gson.toJson(value).getBytes(StandardCharsets.UTF_8),"application/json; charset=utf-8");}
 private void quietJson(HttpExchange x,int status,String message){try{json(x,status,Map.of("error",message));}catch(IOException ignored){}}
 private static void send(HttpExchange x,int status,byte[] body,String type) throws IOException {x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(status,body.length);x.getResponseBody().write(body);}
 private void method(HttpExchange x,String allow) throws IOException {x.getResponseHeaders().set("Allow",allow);json(x,405,Map.of("error",WebMessages.raw("server.text-059")));}
 @Override public void close(){closed=true;server.stop(0);pool.shutdownNow();synchronized(this){codes.clear();sessions.clear();failures.clear();}}
 private static final class BadRequest extends IOException {BadRequest(String message){super(message);}}
 private static final class TooLarge extends IOException {}
}
