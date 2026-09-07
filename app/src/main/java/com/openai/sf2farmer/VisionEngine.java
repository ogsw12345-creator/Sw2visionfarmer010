package com.openai.sf2farmer;

import android.media.Image;

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
    private long pendingCounterAt=0;
    private long lastLaterTapAt=0;
    private int combo=0;
    private float playerX=-1, enemyX=-1, lastEnemyX=-1;
    private long lastFrameAt=0;
    private int lostFrames=0;

    // Calibrated from user's clips: fight occupies the center-lower region.
    private static final float ROI_L=.245f, ROI_R=.785f, ROI_T=.31f, ROI_B=.865f;
    private static final int GW=112, GH=58;

    public VisionEngine(int w,int h){ input=new GestureController(w,h); }
    public void resize(int w,int h){ input.resize(w,h); }

    public void onFrame(Image image){
        if(!BotState.running || image==null || !input.available()) return;
        long now=System.currentTimeMillis();
        int w=image.getWidth(), h=image.getHeight(); input.resize(w,h);
        Frame f=sample(image,w,h);
        boolean fight=fightActive(f);

        // May's exhaustion/shop popup appears after roughly five Survival fights.
        // Detect the orange "SPÄTER" button next to the green "HÄNDLER" button
        // and dismiss it before the generic no-fight/menu handling can mis-tap.
        if(!fight && tiredShopPopup(f)){
            if(now-lastLaterTapAt>1800 && now>=nextActionAt){
                input.tapNormalized(.422f,.760f);
                lastLaterTapAt=now;
                nextActionAt=now+1400;
                noFightSince=now;
                BotState.status="Erschöpft-Popup → SPÄTER";
            }
            return;
        }

        if(fight){
            lastFightSeen=now; noFightSince=0;
            Fighters fs=detectFighters(f);
            if(fs!=null){
                lostFrames=0;
                track(fs);
                decide(fs,now,w,h);
            } else {
                lostFrames++;
                // When silhouettes merge during close combat, a safe low kick is better than random mashing.
                if(lostFrames>=2 && now>=nextActionAt){
                    input.lowKick(); nextActionAt=now+330;
                    BotState.status="Nahkampf/verdeckt → Low Kick";
                }
            }
        } else {
            if(noFightSince==0) noFightSince=now;
            handleNoFight(now,w,h,f);
        }
    }

    private void decide(Fighters fs,long now,int w,int h){
        if(now<nextActionAt) return;
        if(pendingCounterAt>0){
            if(now>=pendingCounterAt){ input.doublePunch(); pendingCounterAt=0; nextActionAt=now+390; BotState.status="Block → Konter"; }
            return;
        }

        float dist=Math.abs(enemyX-playerX);
        int dir=enemyX>playerX?1:-1;
        float enemyVel=0;
        if(lastEnemyX>=0 && lastFrameAt>0){
            long dt=Math.max(1,now-lastFrameAt);
            enemyVel=(enemyX-lastEnemyX)*1000f/dt;
        }
        float closing = dir>0 ? -enemyVel : enemyVel;
        lastEnemyX=enemyX; lastFrameAt=now;

        boolean pDown=fs.playerDown;
        boolean eDown=fs.enemyDown;
        if(pDown){ nextActionAt=now+430; BotState.status="Shadow am Boden → warten"; return; }
        if(eDown){
            if(dist>.18f) input.walk(dir,120); else input.lowKick();
            nextActionAt=now+380; BotState.status="Gegner am Boden → Druck"; return;
        }

        float far=.315f, mid=.205f, danger=.125f;
        if(BotState.profile==1){ far=.33f; mid=.22f; danger=.14f; }
        if(BotState.profile==2){ far=.30f; mid=.19f; danger=.115f; }

        if(dist>far){
            input.dash(dir); nextActionAt=now+280; BotState.status="Distanz groß → Dash"; return;
        }
        if(dist>mid){
            input.forwardPunch(dir); nextActionAt=now+360; BotState.status="Mittlere Distanz → Vorwärtsschlag"; return;
        }

        // Fast approach at close distance is treated as an incoming attack. SF2 auto-blocks while neutral.
        float closingThreshold = BotState.profile==2 ? .18f : .12f;
        if(dist<.18f && closing>closingThreshold){
            pendingCounterAt=now+205;
            nextActionAt=now+190;
            BotState.status="Angriff erkannt → neutral/block";
            return;
        }

        if(dist<danger){
            if((combo++%3)==0) input.backPunch(dir); else input.lowKick();
            nextActionAt=now+360; BotState.status="Sehr nah → Sweep/Back Attack"; return;
        }

        int c=combo++%4;
        if(c==0 || c==3) input.doublePunch();
        else if(c==1) input.punchKick();
        else input.lowKick();
        nextActionAt=now+(BotState.profile==2?300:365);
        BotState.status="Nahdistanz → kurze Combo";
    }

    private void handleNoFight(long now,int w,int h,Frame f){
        if(!BotState.autoAdvance) return;
        long quiet=now-noFightSince;
        if(quiet<2800 || now<nextActionAt) return;

        // Round cards in the uploaded video last ~2 seconds and need no touch.
        if(now-lastFightSeen<5500){ nextActionAt=now+900; BotState.status="Rundenübergang → warten"; return; }

        // Experimental end-of-run handling. First tries a common centered Continue/OK region.
        if(quiet<8500){
            input.tapNormalized(.50f,.82f); nextActionAt=now+1600; BotState.status="Kein Kampf → Weiter/OK versuchen"; return;
        }
        // If we landed back on the map, Survival node and Fight button positions match the user's clip.
        if(mapLike(f)){
            input.tapNormalized(.43f,.56f); nextActionAt=now+1300;
            new Thread(() -> { try{Thread.sleep(950);}catch(Exception ignored){} input.tapNormalized(.80f,.82f); }).start();
            BotState.status="Karte erkannt → Überleben starten";
        } else {
            input.tapNormalized(.50f,.78f); nextActionAt=now+1800;
        }
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
        // Both health bars shrink with damage. The old threshold (total/40)
        // stopped recognizing the fight once little red health remained, so
        // the bot stopped attacking at exactly the worst moment. A small but
        // still spatially constrained red HUD signal is sufficient here.
        return red>Math.max(6,total/170);
    }

    private boolean mapLike(Frame f){
        // Beige map/menu is much less green than the bamboo arena.
        long r=0,g=0,b=0,n=0;
        for(int y=(int)(f.h*.18);y<(int)(f.h*.72);y+=16)
            for(int x=(int)(f.w*.15);x<(int)(f.w*.85);x+=16){ int c=f.rgb(x,y); r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;n++; }
        if(n==0)return false;
        float rr=r/(float)n,gg=g/(float)n,bb=b/(float)n;
        return rr>95 && gg>75 && Math.abs(rr-gg)<55 && gg-bb<45;
    }

    private boolean tiredShopPopup(Frame f){
        // The popup is resolution-independent in landscape: orange button on
        // the left and bright green button on the right at about 76% height.
        float orange=coloredRatio(f,.350f,.493f,.727f,.793f,true);
        float green=coloredRatio(f,.505f,.650f,.727f,.793f,false);

        // A light parchment centre prevents similarly coloured arena/menu
        // elements from triggering this special-case tap.
        int light=0,total=0;
        for(int y=(int)(f.h*.22f);y<(int)(f.h*.66f);y+=12){
            for(int x=(int)(f.w*.36f);x<(int)(f.w*.66f);x+=12){
                int c=f.rgb(x,y), r=(c>>16)&255, g=(c>>8)&255, b=c&255;
                if(r>145 && g>120 && b>80 && r>=g && g>b) light++;
                total++;
            }
        }
        return orange>.055f && green>.055f && total>0 && light>total*.42f;
    }

    private float coloredRatio(Frame f,float l,float r,float t,float b,boolean orange){
        int hit=0,total=0;
        for(int y=(int)(f.h*t);y<(int)(f.h*b);y+=4){
            for(int x=(int)(f.w*l);x<(int)(f.w*r);x+=4){
                int c=f.rgb(x,y), rr=(c>>16)&255, gg=(c>>8)&255, bb=c&255;
                if(orange){
                    if(rr>135 && rr>gg*1.20f && gg>45 && gg<155 && bb<95) hit++;
                }else{
                    if(gg>120 && gg>rr*1.08f && gg>bb*1.55f && rr>55) hit++;
                }
                total++;
            }
        }
        return total==0?0:hit/(float)total;
    }

    private void track(Fighters fs){
        if(playerX<0){ playerX=fs.a.x; enemyX=fs.b.x; if(playerX>enemyX){float t=playerX;playerX=enemyX;enemyX=t;} }
        else {
            float cost1=Math.abs(fs.a.x-playerX)+Math.abs(fs.b.x-enemyX);
            float cost2=Math.abs(fs.b.x-playerX)+Math.abs(fs.a.x-enemyX);
            Component p,e;
            if(cost1<=cost2){p=fs.a;e=fs.b;}else{p=fs.b;e=fs.a;}
            playerX=.62f*playerX+.38f*p.x; enemyX=.62f*enemyX+.38f*e.x;
            fs.playerDown=p.down; fs.enemyDown=e.down;
        }
    }

    private Fighters detectFighters(Frame f){
        boolean[][] m=new boolean[GH][GW];
        int x0=(int)(f.w*ROI_L), x1=(int)(f.w*ROI_R), y0=(int)(f.h*ROI_T), y1=(int)(f.h*ROI_B);
        for(int gy=0;gy<GH;gy++){
            int py=y0+(int)((gy+.5f)*(y1-y0)/GH);
            for(int gx=0;gx<GW;gx++){
                int px=x0+(int)((gx+.5f)*(x1-x0)/GW);
                int c=f.rgb(px,py); int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                int max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
                int lum=(r*54+g*183+b*19)>>8;
                m[gy][gx]=lum<48 && max-min<48;
            }
        }
        boolean[][] seen=new boolean[GH][GW]; List<Component> cc=new ArrayList<>();
        int[] dx={-1,0,1,-1,1,-1,0,1}, dy={-1,-1,-1,0,0,1,1,1};
        for(int sy=0;sy<GH;sy++) for(int sx=0;sx<GW;sx++){
            if(!m[sy][sx]||seen[sy][sx])continue;
            ArrayDeque<Integer> q=new ArrayDeque<>(); q.add(sy*GW+sx);seen[sy][sx]=true;
            int n=0,minx=sx,maxx=sx,miny=sy,maxy=sy,sumx=0,sumy=0;
            while(!q.isEmpty()){
                int v=q.removeFirst(), yy=v/GW,xx=v%GW;n++;sumx+=xx;sumy+=yy;
                if(xx<minx)minx=xx;if(xx>maxx)maxx=xx;if(yy<miny)miny=yy;if(yy>maxy)maxy=yy;
                for(int k=0;k<8;k++){int nx=xx+dx[k],ny=yy+dy[k];if(nx>=0&&nx<GW&&ny>=0&&ny<GH&&m[ny][nx]&&!seen[ny][nx]){seen[ny][nx]=true;q.add(ny*GW+nx);}}
            }
            int bw=maxx-minx+1,bh=maxy-miny+1; float density=n/(float)(bw*bh);
            if(n>=18 && bw>=4 && bh>=6 && bw<=35 && bh<=42 && density>.18f){
                float gx=sumx/(float)n, gy=sumy/(float)n;
                float nx=ROI_L+(gx/GW)*(ROI_R-ROI_L);
                float ny=ROI_T+(gy/GH)*(ROI_B-ROI_T);
                boolean down=(bh<13 && ny>.69f) || (bw>bh*1.45f && ny>.70f);
                cc.add(new Component(nx,ny,n,bw,bh,down));
            }
        }
        cc.sort(Comparator.comparingInt((Component c)->c.area).reversed());
        // Static background blobs are often very far right; prefer plausible moving-fighter region and separation.
        for(int i=0;i<Math.min(6,cc.size());i++) for(int j=i+1;j<Math.min(7,cc.size());j++){
            Component a=cc.get(i),b=cc.get(j);
            if(Math.abs(a.x-b.x)>.055f && a.y>.45f && b.y>.45f) return new Fighters(a,b);
        }
        return null;
    }

    private Frame sample(Image image,int w,int h){
        Image.Plane p=image.getPlanes()[0];
        return new Frame(w,h,p.getBuffer(),p.getRowStride(),p.getPixelStride());
    }

    static class Component { float x,y; int area,bw,bh; boolean down; Component(float x,float y,int a,int bw,int bh,boolean d){this.x=x;this.y=y;area=a;this.bw=bw;this.bh=bh;down=d;} }
    static class Fighters { Component a,b; boolean playerDown,enemyDown; Fighters(Component a,Component b){this.a=a;this.b=b;playerDown=a.down;enemyDown=b.down;} }
    static class Frame {
        final int w,h,rowStride,pixelStride; final ByteBuffer buf;
        Frame(int w,int h,ByteBuffer b,int rs,int ps){this.w=w;this.h=h;buf=b;rowStride=rs;pixelStride=ps;}
        int rgb(int x,int y){
            if(x<0||y<0||x>=w||y>=h)return 0;
            int pos=y*rowStride+x*pixelStride; if(pos+2>=buf.limit())return 0;
            int r=buf.get(pos)&255,g=buf.get(pos+1)&255,b=buf.get(pos+2)&255;
            return (r<<16)|(g<<8)|b;
        }
    }
}
