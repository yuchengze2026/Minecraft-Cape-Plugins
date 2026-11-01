package dev.capebridge.proxy;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
/** Validated names plus containment and no-follow checks under the plugin data root. */
final class SafeFiles {
    static Path checked(Path root,Path path)throws IOException{
        Path base=root.toAbsolutePath().normalize(),file=path.toAbsolutePath().normalize();
        if(!file.startsWith(base)||file.equals(base))throw new IOException("Path outside plugin data directory");
        Path cursor=base;
        for(Path part:base.relativize(file)){
            cursor=cursor.resolve(part);
            if(Files.isSymbolicLink(cursor))throw new IOException("Symbolic links are not permitted");
            if(Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)&&!cursor.toRealPath().startsWith(base.toRealPath()))throw new IOException("Link escapes plugin data directory");
        }
        return file;
    }
    static byte[] readImage(Path path)throws IOException{
        var attr=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attr.isRegularFile()||attr.isSymbolicLink())throw new IOException("PNG must be a regular file, not a link");
        if(attr.size()>1024*1024)throw new IOException("PNG must be <= 1 MiB");
        try(var channel=Files.newByteChannel(path,java.util.Set.<OpenOption>of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS));var in=java.nio.channels.Channels.newInputStream(channel)){
            byte[] data=in.readNBytes(1024*1024+1);
            if(data.length>1024*1024)throw new IOException("PNG must be <= 1 MiB");return data;
        }
    }
    static Path temporary(Path root,String prefix)throws IOException{return Files.createTempFile(root,prefix,".tmp");}
}
