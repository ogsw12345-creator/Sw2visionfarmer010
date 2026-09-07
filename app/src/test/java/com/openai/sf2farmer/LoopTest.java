package com.openai.sf2farmer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;

public class LoopTest {
    static class Recorder extends GestureController {
        String action="";
        Recorder(){super(1536,709);}
        @Override public boolean available(){return true;}
        @Override public void punch(){action="punch";}
        @Override public void tapNormalized(float x,float y){
            if(Math.abs(x-.819f)<.001f && Math.abs(y-.828f)<.001f) action="fight";
            else if(x==.50f && y==.860f) action="ok";
            else if(x==.422f && y==.760f) action="later";
            else fail("Unexpected touch: "+x+","+y);
        }
    }
    VisionEngine.Frame frame(String name,int w,int h)throws Exception{
        // Android's compile API omits desktop classes; the host test JVM has ImageIO.
        Object original=Class.forName("javax.imageio.ImageIO").getMethod("read",java.io.InputStream.class)
            .invoke(null,getClass().getResourceAsStream("/"+name+".jpg"));
        Class<?> imageClass=Class.forName("java.awt.image.BufferedImage");
        int ow=(Integer)imageClass.getMethod("getWidth").invoke(original);
        int oh=(Integer)imageClass.getMethod("getHeight").invoke(original);
        java.lang.reflect.Method rgb=imageClass.getMethod("getRGB",int.class,int.class);
        ByteBuffer bytes=ByteBuffer.allocate(w*h*4);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=(Integer)rgb.invoke(original,x*ow/w,y*oh/h);
            bytes.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)255);
        }
        return new VisionEngine.Frame(w,h,bytes,w*4,4);
    }
    @Test public void screenshotLoopAndRetries()throws Exception{
        BotState.running=true;BotState.autoAdvance=true;
        for(int[] size:new int[][]{{1536,709},{2340,1080},{1170,540}}){
            Recorder r=new Recorder();VisionEngine engine=new VisionEngine(r);
            long now=10000;
            for(String name:new String[]{"fight","fight","ok","later","fight","ok","fight"}){
                r.action="";engine.processFrame(frame(name,size[0],size[1]),now);
                assertEquals(name,r.action);now+=2000;
            }
            // No HP/red pixels: keep punching, including across round transitions.
            VisionEngine.Frame empty=new VisionEngine.Frame(1536,709,ByteBuffer.allocate(1536*709*4),1536*4,4);
            for(int i=0;i<20;i++){
                r.action="";engine.processFrame(empty,now);assertEquals("punch",r.action);now+=200;
            }
            r.action="";BotState.running=false;engine.processFrame(empty,now);
            assertEquals("",r.action);BotState.running=true;
        }
    }
}
