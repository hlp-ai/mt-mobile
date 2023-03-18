package com.yimt.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.mlkit.vision.common.InputImage;


/**
 * Abstract base class for vision frame processors. Subclasses need to implement {@link
 * #onSuccess(Object)} to define what they want to with the detection results and
 * {@link #detectInImage(InputImage)} to specify the detector object.
 *
 * @param <T> The type of the detected feature.
 */
public abstract class VisionProcessorBase<T> implements VisionImageProcessor {

    protected static final String MANUAL_TESTING_LOG = "LogTagForTest";
    private static final String TAG = "VisionProcessorBase";

    private final ScopedExecutor executor;

    // Whether this processor is already shut down
    private boolean isShutdown;

    protected VisionProcessorBase(Context context) {
        executor = new ScopedExecutor(TaskExecutors.MAIN_THREAD);
    }

    @Override
    public void processBitmap(Bitmap bitmap) {
        requestDetectInImage(InputImage.fromBitmap(bitmap, 0));
    }

    private Task<T> requestDetectInImage(final InputImage image) {
        return setUpListener(detectInImage(image));
    }

    private Task<T> setUpListener(
            Task<T> task) {
        return task.addOnSuccessListener(
                        executor,
                        results -> {
                            VisionProcessorBase.this.onSuccess(results);
                        })
                .addOnFailureListener(
                        executor,
                        e -> {
                            String error = "Failed to process. Error: " + e.getLocalizedMessage();
                            Log.d(TAG, error);
                            e.printStackTrace();
                            VisionProcessorBase.this.onFailure(e);
                        });
    }

    @Override
    public void stop() {
        executor.shutdown();
        isShutdown = true;
    }

    protected abstract Task<T> detectInImage(InputImage image);

    protected abstract void onSuccess(@NonNull T results);

    protected abstract void onFailure(@NonNull Exception e);

}
