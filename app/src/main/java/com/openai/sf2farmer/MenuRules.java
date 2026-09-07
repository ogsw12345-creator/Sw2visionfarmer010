package com.openai.sf2farmer;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public final class MenuRules {
    public static final class Label {
        public final String text; public final float x,y;
        public Label(String text,float x,float y){this.text=normalize(text);this.x=x;this.y=y;}
    }
    public static String normalize(String s){
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").trim().replaceAll(" +"," ");
    }
    public static boolean has(String text,String word){return (" "+text+" ").contains(" "+word+" ");}
    public static boolean blocked(List<Label> lines){
        for(Label l:lines)for(String s:new String[]{"buy","purchase","kaufen","kauf","not enough energy","nicht genug energie","refill","shop","store","connection","verbindung"})
            if(has(l.text,s))return true;
        return false;
    }
    public static int outcome(List<Label> lines){
        boolean win=false,lose=false;
        for(Label l:lines){
            if(l.y>.65f)continue;
            win|=l.text.equals("victory")||l.text.equals("you win")||l.text.equals("sieg")||l.text.equals("gewonnen");
            lose|=l.text.equals("defeat")||l.text.equals("you lose")||l.text.equals("niederlage")||l.text.equals("verloren");
        }
        return win==lose?0:win?1:-1;
    }
    /** Only the two user-confirmed buttons, within their screenshot regions. */
    public static Label simpleTarget(List<Label> lines){
        if(blocked(lines))return null;
        for(Label l:lines){
            if(l.text.equals("ok") && l.x>.40f && l.x<.60f && l.y>.78f && l.y<.94f)return l;
            if((l.text.equals("kampft")||l.text.equals("fight")) &&
                l.x>.73f && l.x<.91f && l.y>.73f && l.y<.92f)return l;
        }
        return null;
    }

    public static Label target(List<Label> lines,boolean survivalSelected){
        if(blocked(lines))return null;
        boolean result=outcome(lines)!=0;
        for(Label l:lines)if(has(l.text,"reward")||has(l.text,"belohnung"))result=true;
        if(result)for(Label l:lines)if(l.y>.45f && (l.text.equals("continue")||l.text.equals("weiter")||l.text.equals("ok")||l.text.equals("okay")))return l;
        if(survivalSelected)for(Label l:lines)if(l.y>.4f && (l.text.equals("fight")||l.text.equals("kampfen")))return l;
        for(Label l:lines)if(l.text.equals("survival")||l.text.equals("uberleben"))return l;
        return null;
    }
}
