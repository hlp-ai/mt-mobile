package com.yimt.ocr;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface;


/**
 * Processor for the text detector demo.
 */
public class TextRecognitionProcessor extends VisionProcessorBase<Text> {

    private static final String TAG = "TextRecProcessor";

    private final TextRecognizer textRecognizer;
    protected Handler mhandler;

    public TextRecognitionProcessor(
            Context context, TextRecognizerOptionsInterface textRecognizerOptions, Handler handle) {
        super(context);
        textRecognizer = TextRecognition.getClient(textRecognizerOptions);
        mhandler = handle;
    }

    @Override
    public void stop() {
        super.stop();
        textRecognizer.close();
    }

    @Override
    protected Task<Text> detectInImage(InputImage image) {
        Log.d(TAG, "Recognition task starts");
        return textRecognizer.process(image);
    }

    @Override
    protected void onSuccess(@NonNull Text text) {
        Log.d(TAG, "On-device Text detection successful");
        String tx = (String) text.getText();
        Bundle bundle = new Bundle();
        bundle.putString("ocr_text", tx);
        Message message = new Message();
        message.setData(bundle);
        message.what = 3;
        mhandler.sendMessage(message);
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.w(TAG, "Text detection failed." + e);
    }
}
