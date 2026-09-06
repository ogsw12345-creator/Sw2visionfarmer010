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
    private final RangeLearner learner;
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
        input=new GestureController(w,h);this.handler=handler;learner=new RangeLearner(context);
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
            if(!inFight){
                resetTracking();inFight=true;boutStarted=now;resultRecorded=false;
                rangeVariant=BotState.profile==0?learner.choose():BotState.profile==1?2:0;
                lastTapped="";menuRetries=0;
            }
            Fighters fs=detectFighters(f);
            if(fs==null || !track(fs)){
                lostFrames++;stableFighters=0;
                BotState.status="Kämpfer unklar – Eingaben pausiert";
                return;
            }
            lostFrames=0;
            if(++stableFighters<3)return;
            if(now>=nextActionAt && input.available())decide(fs,now);
        }else{
            hudFrames=0;
            if(noFightSince==0)noFightSince=now;
            if(now-noFightSince<900)return;
            if(inFight){inFight=false;resetTracking();}
            handleNoFight(now,f);
        }
    }

    private void decide(Fighters fs,long now){
        int dir=enemyX>playerX?1:-1;
        ScythePolicy.Action action=ScythePolicy.choose(playerX,enemyX,fs.playerDown,rangeVariant,combo++);
        switch(action){
            case APPROACH: input.walk(dir,100);nextActionAt=now+260;break;
            case RETREAT: input.walk(-dir,150);nextActionAt=now+300;break;
            case STRIKE: input.punch();nextActionAt=now+650;break;
            case DOUBLE_STRIKE: input.doublePunch();nextActionAt=now+750;break;
            default: nextActionAt=now+300;
        }
        BotState.status="Sense: "+action+" | "+learner.summary();
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
            MenuRules.Label target=MenuRules.target(labels,current<survivalUntil);
            String key=result+":"+(target==null?"none":target.text);
            float tx=target==null?0:target.x,ty=target==null?0:target.y;
            if(key.equals(menuKey) && Math.abs(tx-lastMenuX)<.025f && Math.abs(ty-lastMenuY)<.025f)menuMatches++;
            else {menuKey=key;menuMatches=1;lastMenuX=tx;lastMenuY=ty;}
            if(menuMatches<2)return;
            if(result!=0 && boutStarted>0 && !resultRecorded){
                if(BotState.profile==0)learner.outcome(result>0,current-boutStarted);
                resultRecorded=true;
            }
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
            if(target.text.equals("survival")||target.text.equals("uberleben"))survivalUntil=current+20000;
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

    private boolean track(Fighters fs){
        Component p,e;
        if(playerX<0){
            // SF2 starts Shadow on the left. Reset this association at each bout.
            p=fs.a.x<fs.b.x?fs.a:fs.b;e=p==fs.a?fs.b:fs.a;
        }else{
            float c1=Math.abs(fs.a.x-playerX)+Math.abs(fs.b.x-enemyX);
            float c2=Math.abs(fs.b.x-playerX)+Math.abs(fs.a.x-enemyX);
            if(Math.abs(c1-c2)<.025f)return false;
            p=c1<c2?fs.a:fs.b;e=p==fs.a?fs.b:fs.a;
            if(Math.abs(p.x-playerX)>.18f || Math.abs(e.x-enemyX)>.18f)return false;
        }
        playerX=playerX<0?p.x:.35f*playerX+.65f*p.x;
        enemyX=enemyX<0?e.x:.35f*enemyX+.65f*e.x;
        fs.playerDown=p.down;fs.enemyDown=e.down;
        return true;
    }

    private Fighters detectFighters(Frame f){
        boolean[][] m=new boolean[GH][GW];
        int[] pixels=new int[GW*GH];
        int x0=(int)(f.w*ROI_L), x1=(int)(f.w*ROI_R), y0=(int)(f.h*ROI_T), y1=(int)(f.h*ROI_B);
        for(int gy=0;gy<GH;gy++){
            int py=y0+(int)((gy+.5f)*(y1-y0)/GH);
            for(int gx=0;gx<GW;gx++){
                int px=x0+(int)((gx+.5f)*(x1-x0)/GW);
                int c=f.rgb(px,py);pixels[gy*GW+gx]=c; int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
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
            int motion=0,n=0,minx=sx,maxx=sx,miny=sy,maxy=sy,sumx=0,sumy=0;
            while(!q.isEmpty()){
                int v=q.removeFirst(), yy=v/GW,xx=v%GW;n++;sumx+=xx;sumy+=yy;
                if(previousPixels!=null){
                    int a=pixels[v],b=previousPixels[v];
                    int delta=Math.abs((a&255)-(b&255))+Math.abs(((a>>8)&255)-((b>>8)&255))+Math.abs(((a>>16)&255)-((b>>16)&255));
                    if(delta>55)motion++;
                }
                if(xx<minx)minx=xx;if(xx>maxx)maxx=xx;if(yy<miny)miny=yy;if(yy>maxy)maxy=yy;
                for(int k=0;k<8;k++){int nx=xx+dx[k],ny=yy+dy[k];if(nx>=0&&nx<GW&&ny>=0&&ny<GH&&m[ny][nx]&&!seen[ny][nx]){seen[ny][nx]=true;q.add(ny*GW+nx);}}
            }
            int bw=maxx-minx+1,bh=maxy-miny+1; float density=n/(float)(bw*bh);
            if(n>=18 && bw>=4 && bh>=6 && bw<=30 && bh<=56 && density>.18f){
                float gx=sumx/(float)n, gy=sumy/(float)n;
                float nx=ROI_L+(gx/GW)*(ROI_R-ROI_L);
                float ny=ROI_T+(gy/GH)*(ROI_B-ROI_T);
                boolean down=(bh<13 && ny>.69f) || (bw>bh*1.45f && ny>.70f);
                Component component=new Component(nx,ny,n,bw,bh,down);component.motion=motion;cc.add(component);
            }
        }
        previousPixels=pixels;
        cc.sort(Comparator.comparingDouble((Component c)->c.motion*5+c.area).reversed());
        // Static background blobs are often very far right; prefer plausible moving-fighter region and separation.
        for(int i=0;i<Math.min(6,cc.size());i++) for(int j=i+1;j<Math.min(7,cc.size());j++){
            Component a=cc.get(i),b=cc.get(j);
            if(Math.abs(a.x-b.x)>.065f && a.y>.43f && b.y>.43f &&
                (playerX>=0 || (a.motion>=2 && b.motion>=2))) return new Fighters(a,b);
        }
        return null;
    }

    private Frame sample(Image image,int w,int h){
        Image.Plane p=image.getPlanes()[0];
        return new Frame(w,h,p.getBuffer(),p.getRowStride(),p.getPixelStride());
    }

    static class Component { float x,y; int area,bw,bh,motion; boolean down; Component(float x,float y,int a,int bw,int bh,boolean d){this.x=x;this.y=y;area=a;this.bw=bw;this.bh=bh;down=d;} }
    static class Fighters { Component a,b; boolean playerDown,enemyDown; Fighters(Component a,Component b){this.a=a;this.b=b;playerDown=a.down;enemyDown=b.down;} }
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

