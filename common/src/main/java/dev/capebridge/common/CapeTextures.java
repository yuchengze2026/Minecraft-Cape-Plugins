package dev.capebridge.common;

import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** One canonical atlas converter shared by uploads and resource-pack generation. */
public final class CapeTextures {
    private CapeTextures() {}
    public static BufferedImage read(byte[] bytes,boolean animated)throws IOException{
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Invalid PNG");
            ImageReader reader=readers.next();
            try{
                if(!reader.getFormatName().equalsIgnoreCase("png"))throw new IOException("PNG required");
                reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||(long)w*h>8_388_608)throw new IOException("cape.image.dimensions");
                if(animated){
                    if(w<64||w>512||w%64!=0||h%(w/2)!=0||h/(w/2)<2||h/(w/2)>64)
                        throw new IOException("cape.animation.dimensions");
                    return reader.read(0);
                }
                boolean atlas=w>=64&&w<=512&&w%64==0&&h==w/2;
                boolean front=(long)w*8==(long)h*5;
                if(!atlas&&!front)throw new IOException("cape.image.dimensions");
                BufferedImage source=reader.read(0);
                return atlas?source:front(source);
            }finally{reader.dispose();}
        }
    }
    public static BufferedImage front(BufferedImage source){
        int width=64;
        while(width<512&&width/64*10<source.getWidth())width*=2;
        int unit=width/64,fw=unit*10,fh=unit*16;
        BufferedImage face=new BufferedImage(fw,fh,BufferedImage.TYPE_INT_ARGB);
        var g=face.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source,0,0,fw,fh,null);g.dispose();
        BufferedImage atlas=new BufferedImage(width,width/2,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<fh;y++)for(int x=0;x<fw;x++){
            int color=face.getRGB(x,y);atlas.setRGB(unit+x,unit+y,color);atlas.setRGB(12*unit+x,unit+y,color);
        }
        for(int y=0;y<fh;y++)for(int x=0;x<unit;x++){
            atlas.setRGB(x,unit+y,face.getRGB(0,y));atlas.setRGB(11*unit+x,unit+y,face.getRGB(fw-1,y));
        }
        for(int y=0;y<unit;y++)for(int x=0;x<fw;x++){
            atlas.setRGB(unit+x,y,face.getRGB(x,0));atlas.setRGB(11*unit+x,y,face.getRGB(x,fh-1));
        }
        return atlas;
    }
    public static String animation(int width,int ticks){
        if(ticks<1||ticks>200)throw new IllegalArgumentException("cape.animation.timing");
        return "{\"animation\":{\"width\":"+width+",\"height\":"+(width/2)+",\"frametime\":"+ticks+",\"interpolate\":false}}";
    }
    public static String animation(int width,int height,int ticks,List<Integer> times){
        String ordinary=animation(width,ticks);
        if(times==null||times.isEmpty())return ordinary;
        if(width<64||width>512||width%64!=0||height%(width/2)!=0||times.size()!=height/(width/2)||times.size()<2||times.size()>64)
            throw new IllegalArgumentException("cape.animation.timing");
        StringJoiner frames=new StringJoiner(",");
        for(int i=0;i<times.size();i++){
            Integer time=times.get(i);if(time==null||time<1||time>200)throw new IllegalArgumentException("cape.animation.timing");
            frames.add("{\"index\":"+i+",\"time\":"+time+"}");
        }
        return ordinary.substring(0,ordinary.length()-2)+",\"frames\":["+frames+"]}}";
    }
}
