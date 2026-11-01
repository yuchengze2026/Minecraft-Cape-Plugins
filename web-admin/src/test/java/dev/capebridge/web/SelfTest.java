package dev.capebridge.web;

import com.google.gson.*;
import net.md_5.bungee.config.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import com.sun.net.httpserver.HttpServer;

public final class SelfTest {
 private static int passed,skipped;
 private static final Gson JSON=new Gson();
 public static final class FakeStore {
  final Path root;final ConfigurationProvider yaml=ConfigurationProvider.getProvider(YamlConfiguration.class);
  FakeStore(Path root){this.root=root;}
  public synchronized void addAnimated(String id,String file,int ticks)throws IOException{addAnimated(id,file,ticks,List.of());}
  public synchronized void addAnimated(String id,String file,int ticks,List<Integer> times)throws IOException{
   add(id,file,false);Configuration c=yaml.load(root.resolve("capes.yml").toFile());c.set("capes."+id+".type","ANIMATED");c.set("capes."+id+".animation.frame-time",ticks);c.set("capes."+id+".animation.frame-times",times.isEmpty()?null:times);yaml.save(c,root.resolve("capes.yml").toFile());
  }
  public synchronized void add(String id,String file,boolean block) throws IOException {
   Configuration c=yaml.load(root.resolve("capes.yml").toFile());int next=10001;
   for(String key:c.getSection("capes").getKeys())next=Math.max(next,c.getInt("capes."+key+".model",0)+1);
   if(c.getInt("capes."+id+".model",0)==0)c.set("capes."+id+".model",next);
   c.set("capes."+id+".type","IMAGE");c.set("capes."+id+".enabled",true);c.set("capes."+id+".permission","capebridge.cape."+id);yaml.save(c,root.resolve("capes.yml").toFile());
  }
 }
 public static final class FakeOwner {
  final Path root;boolean failNext;int reloads;
  FakeOwner(Path root){this.root=root;}
  public synchronized void refreshCatalog() throws IOException {if(failNext){failNext=false;throw new IOException("synthetic catalog failure");}}
  public synchronized void reload() throws IOException {
   if(failNext){failNext=false;throw new IOException("synthetic build failure");}reloads++;
   try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(root.resolve("resource-pack.zip")))){
    zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write("{\"pack\":{\"pack_format\":15,\"description\":\"test\"}}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    try(var files=Files.list(root.resolve("capes"))){for(Path p:files.filter(f->f.toString().endsWith(".png")).toList()){zip.putNextEntry(new ZipEntry("assets/test/"+p.getFileName()));Files.copy(p,zip);zip.closeEntry();}}
   }
  }
 }
 public static final class LiveStore {
  final FakeStore delegate;boolean failNext;
  LiveStore(Path root){delegate=new FakeStore(root);}
  public synchronized void add(String id,String file,boolean block) throws IOException {delegate.add(id,file,block);}
  public synchronized List<String> load() throws IOException {
   if(failNext){failNext=false;throw new IOException("synthetic live build failure");}
   return List.copyOf(delegate.yaml.load(delegate.root.resolve("capes.yml").toFile()).getSection("capes").getKeys());
  }
 }
 public static final class LiveBuilder {
  public static String build(Path root,Collection<?> catalog) throws Exception {
   new FakeOwner(root).reload();return SafeFiles.hex(Files.readAllBytes(root.resolve("resource-pack.zip")),"SHA-1");
  }
 }
 public static final class LiveUrls {public static String versioned(String base,String hash){return base+"?capebridge="+hash;}}
 public static final class LiveHost implements AutoCloseable {
  final HttpServer server;volatile byte[] bytes=new byte[]{80,75,3,4};int publications;
  LiveHost() throws IOException {
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
   server.createContext("/capes.zip",x->{byte[] data=bytes;try{x.sendResponseHeaders(200,data.length);x.getResponseBody().write(data);}finally{x.close();}});server.start();
  }
  public void publish(byte[] data){bytes=data.clone();publications++;}
  public void close(){server.stop(0);}
 }
 public static final class LiveOwner {
  boolean stopping;LiveHost host;String secret="",packUrl="",packHash="";List<?> catalog=List.of();
  LiveOwner(LiveHost host){this.host=host;}
  public void reloadData(){throw new AssertionError("full reload would rebind the occupied port");}
  public void send(ProxiedPlayer player,String message){}
 }
 private static void publisherChecks(Path run,byte[] black,byte[] white) throws Exception {
  Path root=Files.createDirectory(run.resolve("live-proxy"));Files.createDirectory(root.resolve("capes"));
  Files.writeString(root.resolve("capes.yml"),"capes: {}\n");
  String settings="sync-secret: LIVE_TEST_ONLY_0123456789abcdef0123456789abcdef\nresource-pack:\n  embedded-host: true\n  public-url: http://127.0.0.1/capes.zip\n";
  Files.writeString(root.resolve("config.yml"),settings);
  try(LiveHost host=new LiveHost()){
   LiveOwner owner=new LiveOwner(host);LiveStore store=new LiveStore(root);
   CapePublisher publisher=new CapePublisher(owner,store,root,LiveBuilder.class,LiveUrls.class,List::of);
   CapeService service=new CapeService(owner,root,store,LiveStore.class.getDeclaredMethod("add",String.class,String.class,boolean.class),publisher::refresh,run.resolve("LiveWebAdmin"),2);
   int port=host.server.getAddress().getPort();
   rejects("resource listener is already bound before refresh",BindException.class,()->{try(ServerSocket competing=new ServerSocket()){competing.bind(new InetSocketAddress("127.0.0.1",port));}});
   service.install("live","实时披风",black,false);
   check("live refresh reuses occupied listener without invoking full reload",owner.host==host&&host.server.getAddress().getPort()==port&&host.publications==1);
   check("live refresh commits catalog hash and versioned URL",owner.catalog.contains("live")&&owner.packHash.equals(SafeFiles.hex(host.bytes,"SHA-1"))&&owner.packUrl.endsWith(owner.packHash));
   HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
   URI uri=URI.create("http://127.0.0.1:"+port+"/capes.zip");
   var errors=new java.util.concurrent.CopyOnWriteArrayList<Throwable>();var downloads=new java.util.concurrent.atomic.AtomicInteger();var running=new java.util.concurrent.atomic.AtomicBoolean(true);
   Thread reader=new Thread(()->{while(running.get())try{
    var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    if(response.statusCode()!=200||response.body().length<4||response.body()[0]!=80||response.body()[1]!=75)throw new IOException("invalid pack during refresh");downloads.incrementAndGet();
   }catch(Throwable failure){errors.add(failure);break;}});
   reader.start();try{for(int i=0;i<12;i++)service.install("live","实时披风"+i,i%2==0?white:black,true);}
   finally{running.set(false);reader.join(5000);}
   check("continuous downloads survive twelve successive replacements",!reader.isAlive()&&errors.isEmpty()&&downloads.get()>0&&host.publications==13);
   byte[] original=Files.readAllBytes(root.resolve("capes/live.png")),catalog=Files.readAllBytes(root.resolve("capes.yml")),pack=host.bytes.clone();
   store.failNext=true;rejects("live build failure is reported",IOException.class,()->service.install("live","失败替换",white,true));
   check("live rollback restores PNG and catalog without replacing listener",Arrays.equals(original,Files.readAllBytes(root.resolve("capes/live.png")))&&Arrays.equals(catalog,Files.readAllBytes(root.resolve("capes.yml")))&&owner.host==host);
   check("rollback republishes matching resource pack bytes",Arrays.equals(pack,host.bytes));
   var downloaded=client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
   check("original download endpoint remains usable after rollback",downloaded.statusCode()==200&&Arrays.equals(downloaded.body(),host.bytes));
   owner.host=null;
   rejects("missing embedded host fails instead of opening a second listener",IOException.class,publisher::refresh);
   Files.writeString(root.resolve("config.yml"),settings.replace("embedded-host: true","embedded-host: false"));publisher.refresh();
   check("external static host mode still rebuilds catalog without a listener",owner.catalog.contains("live"));
   owner.stopping=true;rejects("refresh refused while plugin shuts down",IOException.class,publisher::refresh);
  }
 }
 static byte[] png(int width,int height,Color color) throws IOException {
  BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(color);g.fillRect(0,0,width,height);g.dispose();var bytes=new ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);return bytes.toByteArray();
 }
 private static void check(String label,boolean condition){if(!condition)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
 interface Action {void run() throws Exception;}
 private static void rejects(String label,Class<? extends Throwable> type,Action action) throws Exception {
  try{action.run();}catch(Throwable failure){if(type.isInstance(failure)){check(label,true);return;}throw failure;}throw new AssertionError(label+" was accepted");
 }
 private static HttpResponse<String> request(HttpClient client,String base,String path,String method,String body,String csrf,String origin,String host) throws Exception {
  var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10));
  if(origin!=null)builder.header("Origin",origin);if(csrf!=null)builder.header("X-Cape-CSRF",csrf);if(host!=null)builder.header("Host",host);
  if(body!=null)builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));else builder.method(method,HttpRequest.BodyPublishers.noBody());
  return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
 }
 public static void main(String[] args) throws Exception {
  Path parent=Path.of(args[0]).toAbsolutePath();Files.createDirectories(parent);Path run=Files.createDirectory(parent.resolve("run-"+UUID.randomUUID()));Path cape=Files.createDirectory(run.resolve("CapeBridgeProxy"));Files.createDirectory(cape.resolve("capes"));
  Files.writeString(cape.resolve("capes.yml"),"capes: {}\n",StandardCharsets.UTF_8);Files.writeString(cape.resolve("config.yml"),"sync-secret: TEST_PRIVATE_SECRET_MUST_NOT_BE_EXPOSED\nresource-pack:\n  embedded-host: true\n  public-url: http://127.0.0.1/capes.zip\n",StandardCharsets.UTF_8);
  FakeOwner owner=new FakeOwner(cape);FakeStore store=new FakeStore(cape);CapeService service=new CapeService(owner,cape,store,FakeStore.class.getDeclaredMethod("add",String.class,String.class,boolean.class),FakeOwner.class.getDeclaredMethod("reload"),run.resolve("WebAdmin"),2);
  byte[] black=png(64,32,Color.BLACK),white=png(64,32,Color.WHITE);
  publisherChecks(run,black,white);
  check("opaque black cape is accepted instead of being mistaken for empty",ImageRules.canonicalPng(black).length>0);
  rejects("transparent-only atlas rejected",IOException.class,()->ImageRules.canonicalPng(png(64,32,new Color(0,0,0,0))));
  rejects("wrong aspect rejected by server",IOException.class,()->ImageRules.canonicalPng(png(64,64,Color.RED)));
  rejects("JPEG or arbitrary bytes not accepted as PNG",IOException.class,()->ImageRules.canonicalPng("fake png".getBytes(StandardCharsets.UTF_8)));
  byte[] hugeHeader=Arrays.copyOf(black,black.length);java.nio.ByteBuffer.wrap(hugeHeader).putInt(16,100000);
  rejects("oversized PNG header rejected before pixel allocation",IOException.class,()->ImageRules.canonicalPng(hugeHeader));
  rejects("path traversal id rejected",IllegalArgumentException.class,()->service.install("../escape","bad",black,false));
  rejects("catalog injection id rejected",IllegalArgumentException.class,()->service.install("a.enabled","bad",black,false));
  rejects("Windows reserved device name rejected",IllegalArgumentException.class,()->service.install("con","bad",black,false));
  rejects("invisible directional control in name rejected",IllegalArgumentException.class,()->service.install("hidden","cape\u202e",black,false));
  rejects("menu name respects existing CapeBridge twenty-character limit",IllegalArgumentException.class,()->service.install("long","x".repeat(21),black,false));
  var installed=service.install("night","夜色披风",black,false);check("new cape automatically receives stable model and permission",installed.model()==10001&&installed.permission().equals("capebridge.cape.night"));
  check("resource ZIP built by reload callback",Files.isRegularFile(cape.resolve("resource-pack.zip"))&&owner.reloads==1);
  try(ZipFile zip=new ZipFile(cape.resolve("resource-pack.zip").toFile())){check("new PNG is included in generated resource pack",zip.getEntry("assets/test/night.png")!=null);}
  byte[] original=Files.readAllBytes(cape.resolve("capes/night.png")),catalog=Files.readAllBytes(cape.resolve("capes.yml"));
  rejects("unconfirmed overwrite rejected",CapeService.Conflict.class,()->service.install("night","替换",white,false));
  check("rejected overwrite leaves PNG untouched",Arrays.equals(original,Files.readAllBytes(cape.resolve("capes/night.png"))));
  owner.failNext=true;rejects("failed resource build triggers rollback",IOException.class,()->service.install("night","不应留下",white,true));
  check("rollback restores exact original PNG and catalog",Arrays.equals(original,Files.readAllBytes(cape.resolve("capes/night.png")))&&Arrays.equals(catalog,Files.readAllBytes(cape.resolve("capes.yml"))));
  var updated=service.install("night","新夜色",white,true);check("approved replacement preserves model ID",updated.model()==installed.model());
  check("approved replacement updates name",service.list().get(0).name().equals("新夜色"));
  try(var stream=Files.list(run.resolve("WebAdmin/backups"))){check("backup retention stays bounded",stream.count()<=2);}
  Path external=run.resolve("external.png");Files.write(external,black);Path link=cape.resolve("capes/linked.png");
  try{Files.createSymbolicLink(link,external);rejects("symbolic link cannot read another file",IOException.class,()->service.image("linked"));Files.delete(link);}
  catch(UnsupportedOperationException|FileSystemException unavailable){skipped++;System.out.println("SKIP symbolic link creation unavailable");}
  List<Throwable> internal=new ArrayList<>();
  try(WebServer web=new WebServer("127.0.0.1",0,"",service,internal::add)){
   String base=web.url().replaceFirst("/+$","");var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();HttpClient anonymous=HttpClient.newHttpClient();
   var home=request(anonymous,base,"/","GET",null,null,null,null);check("embedded management page served",home.statusCode()==200&&home.body().contains("Cape Studio"));
   var animationScript=request(anonymous,base,"/animation.js","GET",null,null,null,null);
   check("grid converter is served and referenced before the application",animationScript.statusCode()==200&&animationScript.body().contains("CapeAnimation")&&home.body().indexOf("/animation.js")<home.body().indexOf("/app.js"));
   check("strict CSP and no-referrer headers",home.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'")&&home.headers().firstValue("Referrer-Policy").orElse("").equals("no-referrer"));
   check("unauthenticated image access denied",request(anonymous,base,"/api/texture?id=night","GET",null,null,null,null).statusCode()==401);
   check("GIF conversion requires authentication",request(anonymous,base,"/api/gif","POST","{}",null,base,null).statusCode()==401);
   check("wardrobe visibility requires authentication",request(anonymous,base,"/api/wardrobe","POST","{}",null,base,null).statusCode()==401);
   check("configuration files never served",request(anonymous,base,"/config.yml","GET",null,null,null,null).statusCode()!=200);
   check("DNS rebinding host denied",request(anonymous,base,"/","GET",null,null,null,"evil.invalid").statusCode()==403);
   var publicTexts=request(anonymous,base,"/api/texts","GET",null,null,null,null);var published=JsonParser.parseString(publicTexts.body()).getAsJsonObject();
   check("public translation endpoint contains only frontend keys",publicTexts.statusCode()==200&&!published.isEmpty()&&published.keySet().stream().allMatch(k->k.startsWith("web.")));
   check("public translation endpoint rejects foreign host",request(anonymous,base,"/api/texts","GET",null,null,null,"evil.invalid").statusCode()==403);
   check("translated HTML has no unresolved template markers",!home.body().contains("{{web."));
   String code=web.issueCode();var login=request(client,base,"/api/login","POST",JSON.toJson(Map.of("code",code)),null,base,null);check("one-time administrator login succeeds",login.statusCode()==200);
   String csrf=JsonParser.parseString(login.body()).getAsJsonObject().get("csrf").getAsString();String cookie=login.headers().firstValue("Set-Cookie").orElse("");check("session cookie is HttpOnly and SameSite Strict",cookie.contains("HttpOnly")&&cookie.contains("SameSite=Strict"));
   String hidden=JSON.toJson(Map.of("id","night","visible",false));byte[] packBeforeVisibility=Files.readAllBytes(cape.resolve("resource-pack.zip"));
   check("visibility rejects missing CSRF",request(client,base,"/api/wardrobe","POST",hidden,null,base,null).statusCode()==403);
   check("visibility rejects foreign origin",request(client,base,"/api/wardrobe","POST",hidden,csrf,"https://evil.invalid",null).statusCode()==403);
   check("visibility requires a boolean",request(client,base,"/api/wardrobe","POST","{\"id\":\"night\",\"visible\":\"false\"}",csrf,base,null).statusCode()==400);
   check("visibility cannot create missing capes",request(client,base,"/api/wardrobe","POST","{\"id\":\"missing\",\"visible\":false}",csrf,base,null).statusCode()==400);
   check("authenticated visibility update saves hidden state",request(client,base,"/api/wardrobe","POST",hidden,csrf,base,null).statusCode()==200&&!service.list().get(0).wardrobeVisible());
   check("visibility leaves the existing pack unchanged",Arrays.equals(packBeforeVisibility,Files.readAllBytes(cape.resolve("resource-pack.zip"))));
   check("authenticated visibility update can restore public state",request(client,base,"/api/wardrobe","POST",hidden.replace("false","true"),csrf,base,null).statusCode()==200&&service.list().get(0).wardrobeVisible());
   String gifBody=JSON.toJson(Map.of("image",Base64.getEncoder().encodeToString(GifFixtures.make(false,"restoreToPrevious",false)),"resolution","64","layout","auto","fit","contain"));
   check("GIF conversion rejects missing CSRF",request(client,base,"/api/gif","POST",gifBody,null,base,null).statusCode()==403);
   var gifReply=request(client,base,"/api/gif","POST",gifBody,csrf,base,null);var converted=JsonParser.parseString(gifReply.body()).getAsJsonObject();
   check("GIF HTTP route returns composed frames and source timing",gifReply.statusCode()==200&&converted.get("frames").getAsInt()==3&&converted.getAsJsonArray("frameTimes").get(1).getAsInt()==4);
   String timed=JSON.toJson(Map.of("id","timedgif","name","GIF测试","image",converted.get("image").getAsString(),"frameTicks","2","frameTimes","2,4,1"));
   check("timed PNG installation accepts GIF frame timings",request(client,base,"/api/animation","POST",timed,csrf,base,null).statusCode()==200);
   check("GIF frame timings remain available when reopening the library",service.list().stream().anyMatch(c->c.id().equals("timedgif")&&c.frameTimes().equals(List.of(2,4,1))));
   check("invalid GIF frame timings are rejected",request(client,base,"/api/animation","POST",timed.replace("2,4,1","2,0,1"),csrf,base,null).statusCode()==400);
   service.delete("timedgif");
   check("login code replay rejected",request(anonymous,base,"/api/login","POST",JSON.toJson(Map.of("code",code)),null,base,null).statusCode()==401);
   check("read-only inventory accessible after login",request(client,base,"/api/capes","GET",null,null,null,null).body().contains("night"));
   String status=request(client,base,"/api/status","GET",null,null,null,null).body();check("status never exposes sync secret or configuration",!status.contains("TEST_PRIVATE_SECRET")&&!status.contains("sync-secret"));
   String upload=JSON.toJson(Map.of("id","mint","name","薄荷披风","image",Base64.getEncoder().encodeToString(black),"replace",false));
   check("missing CSRF denies mutation",request(client,base,"/api/install","POST",upload,null,base,null).statusCode()==403);
   check("foreign origin denies mutation even with cookie",request(client,base,"/api/install","POST",upload,csrf,"https://evil.invalid",null).statusCode()==403);
   check("JSON nesting and unknown fields rejected",request(client,base,"/api/install","POST","{\"id\":{\"deep\":[]}}",csrf,base,null).statusCode()==400);
   check("authenticated upload performs configuration and reload",request(client,base,"/api/install","POST",upload,csrf,base,null).statusCode()==200&&service.list().stream().anyMatch(c->c.id().equals("mint")));
   check("API keeps overwrite opt-in",request(client,base,"/api/install","POST",upload,csrf,base,null).statusCode()==409);
   check("encoded file traversal rejected",request(client,base,"/api/texture?id=%2e%2e%2fconfig.yml","GET",null,null,null,null).statusCode()==400);
   check("wrong method not treated as upload",request(client,base,"/api/install","GET",null,null,null,null).statusCode()==405);
   check("logout invalidates session",request(client,base,"/api/logout","POST","{}",csrf,base,null).statusCode()==200&&request(client,base,"/api/capes","GET",null,null,null,null).statusCode()==401);
   check("normal HTTP tests do not produce internal exceptions",internal.isEmpty());
  }
  Files.writeString(parent.resolve("latest.json"),JSON.toJson(Map.of("passed",passed,"skipped",skipped,"success",true,"run",run.toString())),StandardCharsets.UTF_8);
  System.out.println("RESULT passed="+passed+" skipped="+skipped);
 }
}
