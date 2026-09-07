package com.openai.sf2farmer;

import android.media.Image;
import java.nio.ByteBuffer;

/** Punch-only Survival farmer. No kick, movement, combo or range logic. */
public class VisionEngine {
    private final GestureController input;
    private long nextActionAt=0, lastFightSeen=0, noFightSince=0, lastLaterTapAt=0;

    public VisionEngine(int w,int h){ input=new GestureController(w,h); }
    public void resize(int w,int h){ input.resize(w,h); }

    public void onFrame(Image image){
        if(!BotState.running || image==null || !input.available()) return;
        long now=System.currentTimeMillis();
        int w=image.getWidth(), h=image.getHeight();
        input.resize(w,h);
        Frame f=sample(image,w,h);
        boolean fight=fightActive(f);

        // May's exhaustion popup: always choose SPÄTER, never HÄNDLER.
        if(tiredShopPopup(f)){
            if(!BotState.autoAdvance) return;
            if(now-lastLaterTapAt>1800 && now>=nextActionAt){
                input.tapNormalized(.422f,.760f);
                lastLaterTapAt=now; nextActionAt=now+1400; noFightSince=now;
                BotState.status="Erschöpft-Popup → SPÄTER";
            }
            return;
        }

        // Recognised buttons take priority over health-bar colour heuristics.
        // Result OK is at 86% height, not the old 78/82% guesses.
        if(lightButton(f,.50f,.860f)){
            menuTap(now,.50f,.860f,"Ergebnis → OK");
            return;
        }
        if(lightButton(f,.82f,.830f)){
            menuTap(now,.82f,.830f,"Überleben → KÄMPFT!");
            return;
        }

        if(fight){
            lastFightSeen=now; noFightSince=0;
            if(now>=nextActionAt){
                input.punch();
                nextActionAt=now+145;
                BotState.status="Kampf → nur SCHLAGEN";
            }
            return;
        }

        if(noFightSince==0) noFightSince=now;
        handleNoFight(now,f);
    }

    private void handleNoFight(long now,Frame f){
        BotState.status="Übergang → warte auf Kampf oder Menübutton";
    }

    private void menuTap(long now,float x,float y,String status){
        if(!BotState.autoAdvance || now<nextActionAt) return;
        input.tapNormalized(x,y);
        nextActionAt=now+1400;
        noFightSince=now;
        BotState.status=status;
    }

    private boolean lightButton(Frame f,float cx,float cy){
        // Require both pale button wings, dark lettering and darker surroundings.
        return paleRatio(f,cx-.050f,cx-.025f,cy-.013f,cy+.013f)>.70f
            && paleRatio(f,cx+.025f,cx+.050f,cy-.013f,cy+.013f)>.70f
            && paleRatio(f,cx-.016f,cx+.016f,cy-.012f,cy+.012f)<.85f
            && paleRatio(f,cx-.04f,cx+.04f,cy-.070f,cy-.050f)<.35f
            && paleRatio(f,cx-.04f,cx+.04f,cy+.050f,cy+.070f)<.35f;
    }

    private float paleRatio(Frame f,float l,float r,float t,float b){
        int hits=0,total=0;
        for(int y=(int)(f.h*t);y<(int)(f.h*b);y+=2)
            for(int x=(int)(f.w*l);x<(int)(f.w*r);x+=2){
                int c=f.rgb(x,y),rr=(c>>16)&255,gg=(c>>8)&255,bb=c&255;
                if(rr>180 && gg>155 && bb>105 && rr>=gg && gg>=bb) hits++;
                total++;
            }
        return total==0?0:hits/(float)total;
    }

    private boolean fightActive(Frame f){
        int red=0,total=0;
        int y0=(int)(f.h*.075f), y1=(int)(f.h*.18f);
        int x0=(int)(f.w*.24f), x1=(int)(f.w*.76f);
        for(int y=y0;y<y1;y+=6) for(int x=x0;x<x1;x+=6){
            int c=f.rgb(x,y), r=(c>>16)&255, g=(c>>8)&255, b=c&255;
            if(r>125 && r>g*1.45f && r>b*1.45f && g<125) red++;
            total++;
        }
        return red>Math.max(6,total/170); // still active with a tiny HP sliver
    }

    private boolean mapLike(Frame f){
        long r=0,g=0,b=0,n=0;
        for(int y=(int)(f.h*.18f);y<(int)(f.h*.72f);y+=16)
            for(int x=(int)(f.w*.15f);x<(int)(f.w*.85f);x+=16){
                int c=f.rgb(x,y); r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;n++;
            }
        if(n==0)return false;
        float rr=r/(float)n,gg=g/(float)n,bb=b/(float)n;
        return rr>95 && gg>75 && Math.abs(rr-gg)<55 && gg-bb<45;
    }

    private boolean tiredShopPopup(Frame f){
        float orange=coloredRatio(f,.350f,.493f,.727f,.793f,true);
        float green=coloredRatio(f,.505f,.650f,.727f,.793f,false);
        int light=0,total=0;
        for(int y=(int)(f.h*.22f);y<(int)(f.h*.66f);y+=12)
            for(int x=(int)(f.w*.36f);x<(int)(f.w*.66f);x+=12){
                int c=f.rgb(x,y), r=(c>>16)&255, g=(c>>8)&255, b=c&255;
                if(r>145 && g>120 && b>80 && r>=g && g>b) light++;
                total++;
            }
        return orange>.055f && green>.055f && total>0 && light>total*.42f;
    }

    private float coloredRatio(Frame f,float l,float r,float t,float b,boolean orange){
        int hit=0,total=0;
        for(int y=(int)(f.h*t);y<(int)(f.h*b);y+=4)
            for(int x=(int)(f.w*l);x<(int)(f.w*r);x+=4){
                int c=f.rgb(x,y), rr=(c>>16)&255, gg=(c>>8)&255, bb=c&255;
                if(orange){ if(rr>135 && rr>gg*1.20f && gg>45 && gg<155 && bb<95) hit++; }
                else if(gg>120 && gg>rr*1.08f && gg>bb*1.55f && rr>55) hit++;
                total++;
            }
        return total==0?0:hit/(float)total;
    }

    private Frame sample(Image image,int w,int h){
        Image.Plane p=image.getPlanes()[0];
        return new Frame(w,h,p.getBuffer(),p.getRowStride(),p.getPixelStride());
    }

    static class Frame {
        final int w,h,rowStride,pixelStride; final ByteBuffer buf;
        Frame(int w,int h,ByteBuffer b,int rs,int ps){this.w=w;this.h=h;buf=b;rowStride=rs;pixelStride=ps;}
        int rgb(int x,int y){
            if(x<0||y<0||x>=w||y>=h)return 0;
            int pos=y*rowStride+x*pixelStride;if(pos+2>=buf.limit())return 0;
            int r=buf.get(pos)&255,g=buf.get(pos+1)&255,b=buf.get(pos+2)&255;
            return (r<<16)|(g<<8)|b;
        }
    }
}
