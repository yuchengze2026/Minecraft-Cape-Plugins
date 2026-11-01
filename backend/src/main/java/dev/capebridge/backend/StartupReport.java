package dev.capebridge.backend;
import java.io.*;
import java.nio.file.*;
import java.util.regex.Matcher;
/** Preserve the first enable failure without publishing the synchronization secret. */
final class StartupReport {
    static String render(String stage,String server,String java,String dependency,Throwable error,String secret){
        var buffer=new StringWriter();error.printStackTrace(new PrintWriter(buffer));
        String report=Messages.raw("report.format","stage",stage,"server",server,"java",java,"dependency",dependency,"trace",buffer);
        if(secret!=null&&!secret.isEmpty())report=report.replace(secret,Messages.raw("report.redacted"));
        return report.replaceAll("(?im)(sync-secret\\s*:\\s*)[^\\r\\n]*","$1"+Matcher.quoteReplacement(Messages.raw("report.redacted")));
    }
    static void save(Path folder,String report)throws IOException{
        Files.createDirectories(folder);Path temp=Files.createTempFile(folder,"startup-",".tmp");
        try{Files.writeString(temp,report);Files.move(temp,folder.resolve("startup-error.txt"),StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
}
