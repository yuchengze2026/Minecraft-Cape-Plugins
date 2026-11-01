package dev.capebridge.backend;
import dev.capebridge.common.TextCatalog;
import java.util.*;
final class Messages {
 private static volatile TextCatalog current=TextFiles.defaults("messages.yml");
 private static final List<Map.Entry<String,String>> ERRORS=List.of(Map.entry("Cannot preserve the previous render configuration","errors.cannot-preserve-the-previous-render-configuration"),Map.entry("Cannot resolve native display metadata slot: ","errors.cannot-resolve-native-display-metadata-slot"),Map.entry("Cannot preserve optional pack configuration","errors.cannot-preserve-optional-pack-configuration"),Map.entry("Unexpected native display metadata type at ","errors.unexpected-native-display-metadata-type-at"),Map.entry("Unsupported native display metadata schema","errors.unsupported-native-display-metadata-schema"),Map.entry("Cannot resolve native player skin accessor","errors.cannot-resolve-native-player-skin-accessor"),Map.entry("Cannot preserve fitting configuration","errors.cannot-preserve-fitting-configuration"),Map.entry("Cannot capture native entity metadata","errors.cannot-capture-native-entity-metadata"),Map.entry("Native player skin accessor not found","errors.native-player-skin-accessor-not-found"),Map.entry("Cannot identify cape pack status","errors.cannot-identify-cape-pack-status"),Map.entry("Cannot access native skin state","errors.cannot-access-native-skin-state"),Map.entry("Cannot update native skin state","errors.cannot-update-native-skin-state"),Map.entry("Cannot offer optional cape pack","errors.cannot-offer-optional-cape-pack"),Map.entry("Cannot read native skin state","errors.cannot-read-native-skin-state"),Map.entry("Cape attachment closed","errors.cape-attachment-closed"));
 static void use(TextCatalog next){current=next;}
 static String t(String key,Object...args){return current.text(key,args);}
 static String raw(String key,Object...args){return current.raw(key,args);}
 static void send(org.bukkit.command.CommandSender sender,String key,Object...args){for(String line:current.lines(key,args)){sender.sendMessage(line);}}
 static String error(Throwable error){String value=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();for(var e:ERRORS)if(value.startsWith(e.getKey()))return t(e.getValue(),"detail",value.substring(e.getKey().length()));return t("errors.unexpected","detail",value);}
}
