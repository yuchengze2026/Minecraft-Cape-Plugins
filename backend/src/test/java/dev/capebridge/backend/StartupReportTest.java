package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class StartupReportTest {
    @TempDir Path folder;
    @Test void recordsStageDependencyAndCauseWithoutTheSecret(){
        String secret="private-test-sync-key-do-not-publish";
        var error=new IllegalStateException("sync-secret: "+secret,new NoSuchFieldError("ENTITY_POSITION_SYNC"));
        String report=StartupReport.render("renderer","Purpur 1.20.4","21","5.2.0",error,secret);
        assertTrue(report.contains("ENTITY_POSITION_SYNC")&&report.contains("Purpur 1.20.4")&&report.contains("5.2.0")&&report.contains("renderer"));assertFalse(report.contains(secret));
    }
    @Test void unknownYamlSecretIsAlsoRedacted(){String report=StartupReport.render("config","test","21","5.4",new IllegalArgumentException("bad YAML\nsync-secret: raw-sensitive-content\nnext: field"),"");assertFalse(report.contains("raw-sensitive-content"));assertTrue(report.contains("next: field"));}
    @Test void colorLikeSequencesCannotAlterASecretBeforeRedaction(){String secret="sensitive&a&#AABBCC-key";String report=StartupReport.render("config","test","21","2.14",new IllegalArgumentException("problem "+secret),secret);assertFalse(report.contains("sensitive"));assertTrue(report.contains("[REDACTED]"));}
    @Test void writesDiagnosticAsASeparateFile()throws Exception{StartupReport.save(folder,"first failure");StartupReport.save(folder,"second failure");assertEquals("second failure",Files.readString(folder.resolve("startup-error.txt")));try(var files=Files.list(folder)){assertEquals(1,files.count());}}
}
