package com.yimt.ocr;

import android.graphics.Bitmap;

/**
 * An interface to process the images with different vision detectors and custom image models.
 */
public interface VisionImageProcessor {

    /**
     * Processes a bitmap image.
     */
    void processBitmap(Bitmap bitmap);

    /**
     * Stops the underlying machine learning model and release resources.
     */
    void stop();
}
