package dev.capebridge.backend;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TextFilesTest {
 @TempDir Path root;
 @Test void oldGuiValuesArePreservedAndEveryNewFieldGetsADefault()throws Exception{
  Files.writeString(root.resolve("gui.yml"),"title: '&#AABBCC测试 {page}'\nconfirm:\n  lore: []\n");var gui=TextFiles.load(root,"gui.yml");
  assertEquals("§x§a§a§b§b§c§c测试 2",gui.text("title","page",2));assertTrue(gui.lines("confirm.lore").isEmpty());assertFalse(gui.text("entry.name","cape","测试","name-color","&f").isBlank());assertTrue(Files.exists(root.resolve("gui.yml.before-text-config")));
 }
 @Test void badYamlAndInvalidTypesAreRejectedWithoutReplacingSnapshot()throws Exception{
  var old=TextFiles.load(root,"messages.yml");Messages.use(old);Files.writeString(root.resolve("messages.yml"),"cape:\n  saving: false\n");assertThrows(Exception.class,()->Messages.use(TextFiles.load(root,"messages.yml")));assertEquals(old.text("cape.saving"),Messages.t("cape.saving"));
  Files.writeString(root.resolve("messages.yml"),"cape: [\n");assertThrows(Exception.class,()->TextFiles.load(root,"messages.yml"));
 }
 @AfterEach void reset(){Messages.use(TextFiles.defaults("messages.yml"));}
}
