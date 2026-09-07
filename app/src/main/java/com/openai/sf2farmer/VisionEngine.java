package com.openai.sf2farmer;

import android.media.Image;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VisionEngine {
    private final GestureController input;
    private long nextActionAt=0;
    private long lastFightSeen=0;
    private long noFightSince=0;
    private final Handler handler;
    private final MenuReader menus=new MenuReader();
    private long generation=0,lastOcr=0,boutStarted=0,survivalUntil=0,lastMenuTap=0;
    private boolean inFight=false,resultRecorded=false,closed=false;
    private int hudFrames=0,stableFighters=0,rangeVariant=1,menuMatches=0,menuRetries=0;
    private String menuKey="",lastTapped="";
    private float lastMenuX=0,lastMenuY=0;
    private int[] previousPixels;
    private int combo=0;
    private float playerX=-1, enemyX=-1, lastEnemyX=-1;
    private long lastFrameAt=0;
    private int lostFrames=0;

    // Calibrated from user's clips: fight occupies the center-lower region.
    private static final float ROI_L=.08f, ROI_R=.92f, ROI_T=.32f, ROI_B=.79f;
    private static final int GW=112, GH=58;

    public VisionEngine(Context context,int w,int h,Handler handler){
        input=new GestureController(w,h);this.handler=handler;
    }
    public void close(){closed=true;generation++;menus.close();}
    private void resetTracking(){
        playerX=enemyX=lastEnemyX=-1;lastFrameAt=0;lostFrames=0;stableFighters=0;
        previousPixels=null;combo=0;
    }
    public void resize(int w,int h){ input.resize(w,h); }

    public void onFrame(Image image){
        if(closed || !BotState.running || image==null)return;
        BotAccessibilityService service=BotAccessibilityService.instance;
        if(service==null || !service.gameVisible()){
            generation++;stableFighters=0;hudFrames=0;noFightSince=0;
            BotState.status="Pausiert – SF2 muss sichtbar sein";return;
        }
        long now=SystemClock.uptimeMillis();
        int w=image.getWidth(),h=image.getHeight();input.resize(w,h);
        Frame f=sample(image,w,h);
        boolean fight=fightActive(f);
        if(fight){
            generation++;lastFightSeen=now;noFightSince=0;hudFrames++;
            menuMatches=0;menuKey="";survivalUntil=0;
            if(hudFrames<3)return;
            inFight=true;
            lastTapped="";menuRetries=0;
            if(now>=nextActionAt && input.available()){
                input.punch();nextActionAt=now+180;
                BotState.status="Einfachmodus: Schlagen";
            }
        }else{
            hudFrames=0;
            if(noFightSince==0)noFightSince=now;
            if(now-noFightSince<900)return;
            if(inFight){inFight=false;resetTracking();}
            handleNoFight(now,f);
        }
    }

    private void handleNoFight(long now,Frame f){
        if(now-lastOcr<1100 || !menus.available())return;
        lastOcr=now;
        final long token=generation,requestedAt=now;
        menus.read(f.bitmap(),r->handler.post(r),labels->{
            long current=SystemClock.uptimeMillis();
            BotAccessibilityService svc=BotAccessibilityService.instance;
            if(closed || !BotState.running || generation!=token || inFight || svc==null ||
                !svc.gameVisible() || current-requestedAt>1800)return;
            int result=MenuRules.outcome(labels);
            // Require the same outcome on consecutive reads; never infer defeat from missing frames.
            MenuRules.Label target=MenuRules.simpleTarget(labels);
            String key=result+":"+(target==null?"none":target.text);
            float tx=target==null?0:target.x,ty=target==null?0:target.y;
            if(key.equals(menuKey) && Math.abs(tx-lastMenuX)<.025f && Math.abs(ty-lastMenuY)<.025f)menuMatches++;
            else {menuKey=key;menuMatches=1;lastMenuX=tx;lastMenuY=ty;}
            if(menuMatches<2)return;
            if(!BotState.autoAdvance)return;
            if(target==null){
                BotState.status=current-noFightSince>20000?"Menü unbekannt – bitte Screenshot; keine Blindklicks":"Warte auf eindeutiges Menü";
                return;
            }
            if(current<nextActionAt || !input.available())return;
            // A stuck button may be retried twice, never tapped indefinitely.
            if(key.equals(lastTapped)){
                if(current-lastMenuTap<7000)return;
                if(menuRetries>=2){BotState.status="Menü hängt – bitte prüfen";return;}
                menuRetries++;
            }else menuRetries=0;
            input.tapNormalized(target.x,target.y);
            lastTapped=key;lastMenuTap=current;nextActionAt=current+2000;menuMatches=0;
            BotState.status="Menü: "+target.text;
        });
    }

    private boolean fightActive(Frame f){
        int red=0,total=0;
        int y0=(int)(f.h*.075), y1=(int)(f.h*.18);
        int x0=(int)(f.w*.24), x1=(int)(f.w*.76);
        for(int y=y0;y<y1;y+=6) for(int x=x0;x<x1;x+=6){
            int c=f.rgb(x,y); int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            if(r>125 && r>g*1.45 && r>b*1.45 && g<125) red++;
            total++;
        }
        return red>Math.max(18,total/40);
    }

    private Frame sample(Image image,int w,int h){
        Image.Plane p=image.getPlanes()[0];
        return new Frame(w,h,p.getBuffer(),p.getRowStride(),p.getPixelStride());
    }

    static class Frame {
        final int w,h,rowStride,pixelStride; final ByteBuffer buf;
        Frame(int w,int h,ByteBuffer b,int rs,int ps){this.w=w;this.h=h;buf=b;rowStride=rs;pixelStride=ps;}
        Bitmap bitmap(){
            // Own the copy: MediaProjection Image is closed after onFrame returns.
            int width=Math.min(w,1280),height=Math.max(1,h*width/w);
            int[] pixels=new int[width*height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=0xff000000|rgb(x*w/width,y*h/height);
            return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
        }
        int rgb(int x,int y){
            if(x<0||y<0||x>=w||y>=h)return 0;
            int pos=y*rowStride+x*pixelStride; if(pos+2>=buf.limit())return 0;
            int r=buf.get(pos)&255,g=buf.get(pos+1)&255,b=buf.get(pos+2)&255;
            return (r<<16)|(g<<8)|b;
        }
    }
}

