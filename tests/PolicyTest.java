package com.openai.sf2farmer;
import java.util.Arrays;
import java.util.List;
public class PolicyTest {
    static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    static MenuRules.Label label(String s,float y){return new MenuRules.Label(s,.5f,y);}
    public static void main(String[] args){
        for(int variant=0;variant<3;variant++){
            check(ScythePolicy.choose(.4f,.5f,false,variant,1)==ScythePolicy.Action.RETREAT,"Retreat right enemy");
            check(ScythePolicy.choose(.5f,.4f,false,variant,1)==ScythePolicy.Action.RETREAT,"Retreat left enemy");
            check(ScythePolicy.choose(.3f,.8f,false,variant,1)==ScythePolicy.Action.APPROACH,"Close excessive range");
            check(ScythePolicy.choose(.4f,.5f,true,variant,1)==ScythePolicy.Action.WAIT,"No attack while down");
            check(ScythePolicy.choose(.1f,.2f,false,variant,1)==ScythePolicy.Action.STRIKE,"Do not retreat into left wall");
            check(ScythePolicy.choose(.9f,.8f,false,variant,1)==ScythePolicy.Action.STRIKE,"Do not retreat into right wall");
        }
        check(ScythePolicy.choose(Float.NaN,.5f,false,1,1)==ScythePolicy.Action.WAIT,"Reject NaN");
        check(ScythePolicy.choose(.3f,.56f,false,1,1)==ScythePolicy.Action.STRIKE,"Strike inside range");
        List<MenuRules.Label> result=Arrays.asList(label("SIEG",.3f),label("Weiter",.8f));
        check(MenuRules.outcome(result)==1,"German victory");
        check(MenuRules.target(result,false).text.equals("weiter"),"Advance result");
        check(MenuRules.target(Arrays.asList(label("OK",.8f)),false)==null,"Never blind OK");
        check(MenuRules.target(Arrays.asList(label("Fight",.8f)),false)==null,"Fight requires survival selection");
        check(MenuRules.target(Arrays.asList(label("Kämpfen",.8f)),true)!=null,"German fight selection");
        check(MenuRules.target(Arrays.asList(label("Überleben",.5f)),false)!=null,"German survival");
        check(MenuRules.target(Arrays.asList(label("Purchase",.3f),label("Sieg",.4f),label("OK",.8f)),true)==null,"Reject purchase");
        check(MenuRules.outcome(Arrays.asList(label("Victory",.3f),label("Defeat",.4f)))==0,"Reject ambiguous outcome");
        check(MenuRules.outcome(Arrays.asList(label("Defeat",.3f)))==-1,"English defeat");
        check(MenuRules.target(Arrays.asList(label("Victory bonus",.3f),label("Continue",.8f)),false)==null,"No substring outcome");
        System.out.println("Policy and menu regression tests passed");
    }
}
