package com.openai.sf2farmer;

import android.content.Context;
import android.content.SharedPreferences;

/** Small bandit over three range bands. Only explicit OCR outcomes train it.
 * This is an experimental win/time proxy, NOT a measurement of gold/hour. */
public final class RangeLearner {
    private final SharedPreferences prefs;
    private final int[] counts=new int[3];
    private final float[] means=new float[3];
    private int arm=1;
    public RangeLearner(Context context){
        prefs=context.getSharedPreferences("scythe_learning_v1",Context.MODE_PRIVATE);
        for(int i=0;i<3;i++){counts[i]=prefs.getInt("n"+i,0);means[i]=prefs.getFloat("q"+i,0);}
    }
    public int choose(){
        int total=counts[0]+counts[1]+counts[2];
        if(total<6){arm=new int[]{1,0,2}[total%3];return arm;}
        double best=-Double.MAX_VALUE;
        for(int i=0;i<3;i++){
            double score=means[i]+.35*Math.sqrt(Math.log(total+1)/(counts[i]+1));
            if(score>best){best=score;arm=i;}
        }
        return arm;
    }
    public void outcome(boolean win,long durationMs){
        float reward=(win?1f:-1f)-Math.min(.5f,durationMs/240000f);
        counts[arm]++;means[arm]+=(reward-means[arm])/counts[arm];
        prefs.edit().putInt("n"+arm,counts[arm]).putFloat("q"+arm,means[arm])
            .putInt(win?"wins":"losses",prefs.getInt(win?"wins":"losses",0)+1).apply();
    }
    public String summary(){return "Erkannt: "+prefs.getInt("wins",0)+" Siege / "+prefs.getInt("losses",0)+" Niederlagen";}
}
