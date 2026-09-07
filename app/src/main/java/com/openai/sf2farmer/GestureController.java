package com.openai.sf2farmer;

public class GestureController {
    // Punch button measured from the uploaded 2340x1080 clips.
    private static final float PUNCH_X = 0.866f;
    private static final float PUNCH_Y = 0.710f;

    private int width, height;
    public GestureController(int w, int h) { width=w; height=h; }
    public void resize(int w, int h) { width=w; height=h; }

    private float x(float n) { return n*width; }
    private float y(float n) { return n*height; }
    private BotAccessibilityService svc() { return BotAccessibilityService.instance; }

    public boolean available() { return svc()!=null; }
    public void punch() { BotAccessibilityService s=svc(); if(s!=null)s.tap(x(PUNCH_X),y(PUNCH_Y),55); }

    public void tapNormalized(float nx,float ny) {
        BotAccessibilityService s=svc(); if(s!=null)s.tap(x(nx),y(ny),55);
    }
}
