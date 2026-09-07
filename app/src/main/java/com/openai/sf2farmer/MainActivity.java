package com.openai.sf2farmer;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE=7301;
    private TextView status;
    private Spinner spinner;
    private CheckBox autoAdvance;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);setContentView(R.layout.activity_main);
        status=findViewById(R.id.status);spinner=findViewById(R.id.profileSpinner);autoAdvance=findViewById(R.id.autoAdvance);
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Einfachmodus: nur Schlagen + KÄMPFT / OK"});
        spinner.setAdapter(a);
        findViewById(R.id.accessibilityBtn).setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.startBtn).setOnClickListener(v->startBot());
        findViewById(R.id.stopBtn).setOnClickListener(v->{Intent i=new Intent(this,CaptureService.class).setAction(CaptureService.ACTION_STOP);startService(i);status.setText("Bot gestoppt");});
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},990);
    }

    @Override protected void onResume(){super.onResume();status.setText(accessibilityEnabled()?"Bereit – Einfachmodus: nur Schlagen":"Bitte zuerst Bedienungshilfe aktivieren");}

    private void startBot(){
        if(!accessibilityEnabled()){Toast.makeText(this,"Bitte zuerst die Bedienungshilfe für SF2 Vision Farmer aktivieren.",Toast.LENGTH_LONG).show();startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
        BotState.profile=spinner.getSelectedItemPosition();BotState.autoAdvance=autoAdvance.isChecked();
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(),REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=REQ_CAPTURE)return;
        if(result!=RESULT_OK||data==null){status.setText("Bildschirmaufnahme nicht erlaubt");return;}
        Intent i=new Intent(this,CaptureService.class).setAction(CaptureService.ACTION_START);
        i.putExtra(CaptureService.EXTRA_RESULT_CODE,result);i.putExtra(CaptureService.EXTRA_RESULT_DATA,data);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        status.setText("Bot startet …");
    }

    private boolean accessibilityEnabled(){
        String expected=getPackageName()+"/"+BotAccessibilityService.class.getName();
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null)return false;
        TextUtils.SimpleStringSplitter s=new TextUtils.SimpleStringSplitter(':');s.setString(enabled);
        while(s.hasNext()) if(s.next().equalsIgnoreCase(expected))return true;
        return BotAccessibilityService.instance!=null;
    }
}

