package com.openai.sf2farmer;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class CaptureService extends Service {
    public static final String ACTION_START="com.openai.sf2farmer.START";
    public static final String ACTION_STOP="com.openai.sf2farmer.STOP";
    public static final String EXTRA_RESULT_CODE="resultCode";
    public static final String EXTRA_RESULT_DATA="resultData";
    private static final int NOTIF_ID=4042;
    private static final String CH="sf2bot";

    private HandlerThread thread;
    private Handler handler;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private VisionEngine engine;
    private long lastProcessed=0,lastFrameReceived=0;
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!BotState.running)return;
        if(SystemClock.uptimeMillis()-lastFrameReceived>15000){
            stopBot();return; // A new screen-capture consent is required; never act on stale frames.
        }
        handler.postDelayed(this,3000);
    }};

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        thread=new HandlerThread("SF2Vision", android.os.Process.THREAD_PRIORITY_DISPLAY); thread.start();
        handler=new Handler(thread.getLooper());
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        if(ACTION_STOP.equals(action)){ BotState.running=false;handler.post(this::stopBot);return START_NOT_STICKY; }
        if(ACTION_START.equals(action)){
            int code=intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data;
            if(Build.VERSION.SDK_INT>=33) data=intent.getParcelableExtra(EXTRA_RESULT_DATA,Intent.class);
            else data=intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if(code==Activity.RESULT_OK && data!=null){
                startForeground(NOTIF_ID,notification("Starte Aufnahme"));
                handler.post(()->{try{startProjection(code,data);}catch(RuntimeException e){stopBot();}});
            }else stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startProjection(int code,Intent data){
        stopProjectionOnly();
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection=m.getMediaProjection(code,data);
        final MediaProjection session=projection;
        projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){
            if(projection==session)stopBot();
        }},handler);

        DisplayMetrics dm=new DisplayMetrics();
        WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=30){
            android.graphics.Rect b=wm.getMaximumWindowMetrics().getBounds(); dm.widthPixels=b.width();dm.heightPixels=b.height();dm.densityDpi=getResources().getDisplayMetrics().densityDpi;
        }else{
            wm.getDefaultDisplay().getRealMetrics(dm);
        }
        int w=Math.max(dm.widthPixels,dm.heightPixels), h=Math.min(dm.widthPixels,dm.heightPixels);
        reader=ImageReader.newInstance(w,h, PixelFormat.RGBA_8888,2);
        engine=new VisionEngine(this,w,h,handler);
        reader.setOnImageAvailableListener(r->{
            Image image=null;
            try{
                image=r.acquireLatestImage(); if(image==null)return;
                long now=SystemClock.uptimeMillis();lastFrameReceived=now;
                if(now-lastProcessed>=105){ lastProcessed=now; engine.onFrame(image); updateNotificationThrottled(); }
            }catch(Throwable t){ BotState.status="Vision-Fehler: "+t.getClass().getSimpleName(); }
            finally{ if(image!=null)image.close(); }
        },handler);
        display=projection.createVirtualDisplay("SF2Vision",w,h,dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
        BotState.running=true;lastFrameReceived=SystemClock.uptimeMillis();handler.postDelayed(watchdog,3000);
        BotState.status="Vision aktiv – öffne SF2";
        startForeground(NOTIF_ID,notification(BotState.status));
        launchSf2();
    }

    private long lastNotif=0;
    private void updateNotificationThrottled(){
        long n=SystemClock.uptimeMillis(); if(n-lastNotif<1800)return;lastNotif=n;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID,notification(BotState.status));
    }

    private void launchSf2(){
        handler.postDelayed(()->{
            if(!BotState.running)return;
            Intent i=getPackageManager().getLaunchIntentForPackage("com.nekki.shadowfight2.paid");
            if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}else BotState.status="SF2 Special Edition nicht gefunden";
        },700);
    }

    private void stopBot(){
        BotState.running=false;BotState.status="Gestoppt";stopProjectionOnly();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    private void stopProjectionOnly(){
        handler.removeCallbacks(watchdog);
        if(engine!=null){engine.close();engine=null;}
        if(display!=null){display.release();display=null;}
        if(reader!=null){reader.close();reader=null;}
        if(projection!=null){MediaProjection old=projection;projection=null;try{old.stop();}catch(Exception ignored){}}
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CH,"SF2 Vision Bot",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Status und Stop-Schalter des Survival Bots");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    private Notification notification(String text){
        Intent stop=new Intent(this,CaptureService.class).setAction(ACTION_STOP);
        PendingIntent pi=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CH):new Notification.Builder(this);
        return b.setContentTitle("SF2 Vision Farmer").setContentText(text).setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"STOP",pi).build()).build();
    }

    @Override public void onDestroy(){
        BotState.running=false;
        handler.post(()->{stopProjectionOnly();thread.quitSafely();});
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

