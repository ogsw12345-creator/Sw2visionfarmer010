package com.openai.sf2farmer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public final class MenuReader {
    public interface Callback {void ready(List<MenuRules.Label> labels);}
    private final TextRecognizer reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private volatile boolean busy=false,closed=false;
    public boolean available(){return !busy && !closed;}
    public void read(Bitmap bitmap,Executor executor,Callback callback){
        if(!available()){bitmap.recycle();return;}
        busy=true;
        reader.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(Runnable::run, task->{
            List<MenuRules.Label> labels=new ArrayList<>();
            if(task.isSuccessful())for(Text.TextBlock block:task.getResult().getTextBlocks())for(Text.Line line:block.getLines()){
                Rect r=line.getBoundingBox();
                if(r!=null)labels.add(new MenuRules.Label(line.getText(),r.exactCenterX()/bitmap.getWidth(),r.exactCenterY()/bitmap.getHeight()));
            }
            bitmap.recycle();busy=false;
            if(!closed)executor.execute(()->{if(!closed)callback.ready(labels);});
        });
    }
    public void close(){closed=true;reader.close();}
}
