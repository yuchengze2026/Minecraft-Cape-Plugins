package dev.capebridge.proxy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TextFilesTest {
 @TempDir Path root;
 @Test void utf8ValuesListsAndExplicitEmptySurviveDefaultUpgrade()throws Exception{
  Files.writeString(root.resolve("messages.yml"),"prefix: '[自定义] '\nadmin:\n  saved: ['&a保存 {name}', '', '第二行']\nnotice:\n  busy: ''\n");
  var texts=TextFiles.load(root,"messages.yml");
  assertEquals(List.of("§a保存 测试","","第二行"),texts.lines("admin.saved","name","测试"));assertTrue(texts.lines("notice.busy").isEmpty());assertFalse(texts.raw("admin.usage").isBlank());
  assertTrue(Files.exists(root.resolve("messages.yml.before-text-config")));assertEquals("[自定义] ",TextFiles.load(root,"messages.yml").raw("prefix"));
 }
 @Test void malformedConfigurationCannotReplaceCurrentSnapshot()throws Exception{
  var before=TextFiles.load(root,"messages.yml");Messages.use(before);Files.writeString(root.resolve("messages.yml"),"admin:\n  saved: true\n");
  assertThrows(IllegalArgumentException.class,()->Messages.use(TextFiles.load(root,"messages.yml")));assertEquals(before.text("admin.saved"),Messages.t("admin.saved"));
 }
 @AfterEach void reset(){Messages.use(TextFiles.defaults("messages.yml"));}
}
