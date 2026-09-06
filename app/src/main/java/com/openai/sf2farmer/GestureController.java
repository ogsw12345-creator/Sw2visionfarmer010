package com.openai.sf2farmer;

import java.util.ArrayList;
import java.util.List;

public class GestureController {
    // Aus den hochgeladenen 2340x1080-Clips gemessen.
    private static final float JOY_CX = 0.180f;
    private static final float JOY_CY = 0.768f;
    private static final float JOY_DX = 0.067f;
    private static final float JOY_DY = 0.108f;
    private static final float PUNCH_X = 0.866f;
    private static final float PUNCH_Y = 0.710f;
    private static final float KICK_X = 0.810f;
    private static final float KICK_Y = 0.835f;

    private int width, height;
    public GestureController(int w, int h) { width=w; height=h; }
    public void resize(int w, int h) { width=w; height=h; }

    private float x(float n) { return n*width; }
    private float y(float n) { return n*height; }
    private BotAccessibilityService svc() { return BotAccessibilityService.instance; }

    public boolean available() { return svc()!=null && svc().ready(); }
    public void neutral(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    public void walk(int dir, long ms) {
        BotAccessibilityService s=svc(); if(s==null)return;
        s.tap(x(JOY_CX + (dir>0?JOY_DX:-JOY_DX)), y(JOY_CY), ms);
    }

    public void dash(int dir) {
        BotAccessibilityService s=svc(); if(s==null)return;
        float px=x(JOY_CX + (dir>0?JOY_DX:-JOY_DX)), py=y(JOY_CY);
        s.multi(new BotAccessibilityService.Stroke(px,py,0,55),
                new BotAccessibilityService.Stroke(px,py,95,90));
    }

    public void punch() { BotAccessibilityService s=svc(); if(s!=null)s.tap(x(PUNCH_X),y(PUNCH_Y),55); }
    public void kick() { BotAccessibilityService s=svc(); if(s!=null)s.tap(x(KICK_X),y(KICK_Y),55); }

    public void doublePunch() {
        BotAccessibilityService s=svc(); if(s==null)return;
        List<BotAccessibilityService.Stroke> q=new ArrayList<>();
        q.add(new BotAccessibilityService.Stroke(x(PUNCH_X),y(PUNCH_Y),0,55));
        q.add(new BotAccessibilityService.Stroke(x(PUNCH_X),y(PUNCH_Y),135,55));
        s.sequence(q);
    }

    public void punchKick() {
        BotAccessibilityService s=svc(); if(s==null)return;
        List<BotAccessibilityService.Stroke> q=new ArrayList<>();
        q.add(new BotAccessibilityService.Stroke(x(PUNCH_X),y(PUNCH_Y),0,55));
        q.add(new BotAccessibilityService.Stroke(x(KICK_X),y(KICK_Y),145,55));
        s.sequence(q);
    }

    public void forwardPunch(int dir) {
        BotAccessibilityService s=svc(); if(s==null)return;
        float dx=x(JOY_CX + (dir>0?JOY_DX:-JOY_DX));
        s.multi(new BotAccessibilityService.Stroke(dx,y(JOY_CY),0,145),
                new BotAccessibilityService.Stroke(x(PUNCH_X),y(PUNCH_Y),18,75));
    }

    public void backPunch(int dirToEnemy) {
        BotAccessibilityService s=svc(); if(s==null)return;
        float dx=x(JOY_CX + (dirToEnemy>0?-JOY_DX:JOY_DX));
        s.multi(new BotAccessibilityService.Stroke(dx,y(JOY_CY),0,145),
                new BotAccessibilityService.Stroke(x(PUNCH_X),y(PUNCH_Y),20,75));
    }

    public void lowKick() {
        BotAccessibilityService s=svc(); if(s==null)return;
        s.multi(new BotAccessibilityService.Stroke(x(JOY_CX),y(JOY_CY+JOY_DY),0,130),
                new BotAccessibilityService.Stroke(x(KICK_X),y(KICK_Y),15,75));
    }

    public void tapNormalized(float nx,float ny) {
        BotAccessibilityService s=svc(); if(s!=null)s.tap(x(nx),y(ny),55);
    }
}

