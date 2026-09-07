package com.openai.sf2farmer;

public final class ScythePolicy {
    public enum Action { WAIT, APPROACH, RETREAT, STRIKE, DOUBLE_STRIKE }
    private ScythePolicy(){}
    public static Action choose(float player,float enemy,boolean down,int variant,int turn){
        if(down || !Float.isFinite(player) || !Float.isFinite(enemy))return Action.WAIT;
        float d=Math.abs(enemy-player);
        float near=.19f+Math.max(0,Math.min(2,variant))*.02f;
        float far=near+.09f;
        if(d<near){
            boolean wall=(enemy>player && player<.13f)||(enemy<player && player>.87f);
            return wall?Action.STRIKE:Action.RETREAT;
        }
        if(d>far)return Action.APPROACH;
        return turn%3==0?Action.DOUBLE_STRIKE:Action.STRIKE;
    }
}
