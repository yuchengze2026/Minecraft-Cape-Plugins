package dev.capebridge.common;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TextCatalogTest {
 @Test void overridesFallbackAndDisableAreExplicit(){var t=new TextCatalog(Map.of("a","default","b","fallback","c","show"),Map.of("a","custom","c",""));assertEquals("custom",t.text("a"));assertEquals("fallback",t.text("b"));assertTrue(t.lines("c").isEmpty());}
 @Test void placeholdersAreLiteralSinglePassData(){var t=new TextCatalog(Map.of("prefix","[P] ","m","{prefix}{name}: {value}"),Map.of());assertEquals("[P] $1\\: {name}",t.text("m","name","$1\\","value","{name}"));}
 @Test void listsAndMultilineRetainIntentionalBlankLines(){var t=new TextCatalog(Map.of("m",List.of("&aA","","B {n}")),Map.of());assertEquals(List.of("§aA","","B 2"),t.lines("m","n",2));}
 @Test void legacyAndHexColorsWork(){assertEquals("§aHi §x§1§2§a§b§c§dRGB",TextCatalog.color("&aHi &#12AbCdRGB"));}
 @Test void namedPrefixReferenceIsConfigurable(){var t=new TextCatalog(Map.of("admin-prefix","[A] ","m","{admin-prefix}{value}"),Map.of());assertEquals("[A] hi",t.text("m","value","hi"));}
 @Test void malformedValuesAreRejected(){assertThrows(IllegalArgumentException.class,()->new TextCatalog(Map.of("m","ok"),Map.of("m",true)));}
 @Test void actionFlagsSurviveCompletelyDifferentText(){Notice n=Notice.decode(new Notice(true,true,"&cSomething different").encode());assertTrue(n.success());assertTrue(n.endPreview());assertEquals("&cSomething different",n.text());}
 @Test void noticesAreBoundedWithoutSplittingSurrogates(){String s="😀".repeat(1000);String encoded=new Notice(true,false,s).encode();assertTrue(encoded.length()<=1024);String value=Notice.decode(encoded).text();assertFalse(Character.isHighSurrogate(value.charAt(value.length()-1)));}
}
