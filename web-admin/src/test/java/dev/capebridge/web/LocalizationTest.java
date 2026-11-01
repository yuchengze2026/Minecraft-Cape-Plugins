package dev.capebridge.web;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalizationTest {
 @TempDir Path root;
 @Test void publicTranslationsExcludePrivateServerAndCommandValuesAndEscapeHtml()throws Exception{
  Files.writeString(root.resolve("messages.yml"),"web:\n  language: '\"<script>alert(1)</script>'\ncommand:\n  code: 'PRIVATE SENTINEL'\n");WebMessages.reload(root);
  assertTrue(WebMessages.publicTexts().keySet().stream().allMatch(k->k.startsWith("web.")));assertFalse(WebMessages.publicTexts().values().contains("PRIVATE SENTINEL"));
  assertEquals("<html lang=\"&quot;&lt;script&gt;alert(1)&lt;/script&gt;\">",WebMessages.html("<html lang=\"{{web.language}}\">"));
 }
 @Test void rejectedReloadPreservesPreviousLanguage()throws Exception{
  Files.writeString(root.resolve("messages.yml"),"web:\n  language: fr\n");WebMessages.reload(root);Files.writeString(root.resolve("messages.yml"),"web:\n  language: 123\n");assertThrows(Exception.class,()->WebMessages.reload(root));assertEquals("fr",WebMessages.raw("web.language"));
 }
 @Test void existingSecurityAndUploadRegressionSuite()throws Exception{SelfTest.main(new String[]{root.toString()});}
 @AfterEach void reset()throws Exception{WebMessages.reload(Files.createDirectory(root.resolve("reset")));}
}
