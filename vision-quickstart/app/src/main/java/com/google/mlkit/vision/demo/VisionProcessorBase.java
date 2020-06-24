/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build.VERSION_CODES;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.preference.PreferenceUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Abstract base class for vision frame processors. Subclasses need to implement {@link
 * #onSuccess(Object, GraphicOverlay)} to define what they want to with the detection results and
 * {@link #detectInImage(InputImage)} to specify the detector object.
 *
 * @param <T> The type of the detected feature.
 */
public abstract class VisionProcessorBase<T> implements VisionImageProcessor {

    protected static final String MANUAL_TESTING_LOG = "LogTagForTest";
    private static final String TAG = "VisionProcessorBase";

    private final ActivityManager activityManager;
    private final Timer fpsTimer = new Timer();
    private final ScopedExecutor executor;

    // Whether this processor is already shut down
    private boolean isShutdown;

    // Used to calculate latency, running in the same thread, no sync needed.
    private int numRuns = 0;
    private long totalRunMs = 0;
    private long maxRunMs = 0;
    private long minRunMs = Long.MAX_VALUE;

    // Frame count that have been processed so far in an one second interval to calculate FPS.
    private int frameProcessedInOneSecondInterval = 0;
    private int framesPerSecond = 0;

    // To keep the latest images and its metadata.
    @GuardedBy("this")
    private ByteBuffer latestImage;
    @GuardedBy("this")
    private FrameMetadata latestImageMetaData;
    // To keep the images and metadata in process.
    @GuardedBy("this")
    private ByteBuffer processingImage;
    @GuardedBy("this")
    private FrameMetadata processingMetaData;
    private ArrayList<Pair<Bitmap, Long>> bufferImageList = new ArrayList<>();
    private Integer depth = 30;

    protected VisionProcessorBase(Context context) {
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        executor = new ScopedExecutor(TaskExecutors.MAIN_THREAD);
        fpsTimer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        framesPerSecond = frameProcessedInOneSecondInterval;
                        frameProcessedInOneSecondInterval = 0;
                    }
                },
                /* delay= */ 0,
                /* period= */ 1000);
    }

    // -----------------Code for processing single still image----------------------------------------
    @Override
    public void processBitmap(Bitmap bitmap, final GraphicOverlay graphicOverlay) {
        requestDetectInImage(
                InputImage.fromBitmap(bitmap, 0),
                graphicOverlay,
                /* originalCameraImage= */ null,
                /* shouldShowFps= */ false);
    }

    // -----------------Code for processing live preview frame from Camera1 API-----------------------
    @Override
    public synchronized void processByteBuffer(
            ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay graphicOverlay) {
        latestImage = data;
        latestImageMetaData = frameMetadata;
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(graphicOverlay);
        }
    }

    private synchronized void processLatestImage(final GraphicOverlay graphicOverlay) {
        processingImage = latestImage;
        processingMetaData = latestImageMetaData;
        latestImage = null;
        latestImageMetaData = null;
        if (processingImage != null && processingMetaData != null && !isShutdown) {
            processImage(processingImage, processingMetaData, graphicOverlay);
        }
    }

    private void processImage(
            ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay graphicOverlay) {
        // If live viewport is on (that is the underneath surface view takes care of the camera preview
        // drawing), skip the unnecessary bitmap creation that used for the manual preview drawing.
        Bitmap bitmap =
                PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.getContext())
                        ? null
                        : BitmapUtils.getBitmap(data, frameMetadata);

        requestDetectInImage(
                InputImage.fromByteBuffer(
                        data,
                        frameMetadata.getWidth(),
                        frameMetadata.getHeight(),
                        frameMetadata.getRotation(),
                        InputImage.IMAGE_FORMAT_NV21),
                graphicOverlay,
                bitmap,
                /* shouldShowFps= */ true)
                .addOnSuccessListener(executor, results -> processLatestImage(graphicOverlay));
    }
    @RequiresApi(api = VERSION_CODES.KITKAT)
    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
    // -----------------Code for processing live preview frame from CameraX API-----------------------
    @Override
    @RequiresApi(VERSION_CODES.KITKAT)
    @ExperimentalGetImage
    public void processImageProxy(ImageProxy image, GraphicOverlay graphicOverlay) {
        if (isShutdown) {
            image.close();
            return;
        }
        if(bufferImageList.size()>depth)
            bufferImageList.clear();        // O(n) operation

        bufferImageList.add(new Pair(toBitmap(image.getImage()), System.currentTimeMillis()));

        Bitmap bitmap = null;
        if (!PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.getContext())) {
            //bitmap = BitmapUtils.getBitmap(image);
            bitmap = bufferImageList.get(0).first;
        }

        Log.d("Rokus logs", "Current buffer size:" + bufferImageList.size());
        graphicOverlay.clear();
        if (bitmap != null) {
            try {
                graphicOverlay.add(new CameraImageGraphic(graphicOverlay, bitmap));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        graphicOverlay.postInvalidate();
        image.close();                                  // Camera Image preview suppressed
//        requestDetectInImage(
//                InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees()),
//                graphicOverlay,
//                /* originalCameraImage= */ bitmap,
//                /* shouldShowFps= */ true)
//                // When the image is from CameraX analysis use case, must call image.close() on received
//                // images when finished using them. Otherwise, new images may not be received or the camera
//                // may stall.
//                .addOnCompleteListener(results -> image.close());
    }

    // -----------------Common processing logic-------------------------------------------------------
    private Task<T> requestDetectInImage(
            final InputImage image,
            final GraphicOverlay graphicOverlay,
            @Nullable final Bitmap originalCameraImage,
            boolean shouldShowFps) {
        final long startMs = SystemClock.elapsedRealtime();
        return detectInImage(image)
                .addOnSuccessListener(
                        executor,
                        results -> {
                            long currentLatencyMs = SystemClock.elapsedRealtime() - startMs;
                            numRuns++;
                            frameProcessedInOneSecondInterval++;
                            totalRunMs += currentLatencyMs;
                            maxRunMs = Math.max(currentLatencyMs, maxRunMs);
                            minRunMs = Math.min(currentLatencyMs, minRunMs);

                            // Only log inference info once per second. When frameProcessedInOneSecondInterval is
                            // equal to 1, it means this is the first frame processed during the current second.
                            if (frameProcessedInOneSecondInterval == 1) {
                                Log.d(TAG, "Max latency is: " + maxRunMs);
                                Log.d(TAG, "Min latency is: " + minRunMs);
                                Log.d(TAG, "Num of Runs: " + numRuns + ", Avg latency is: " + totalRunMs / numRuns);
                                MemoryInfo mi = new MemoryInfo();
                                activityManager.getMemoryInfo(mi);
                                long availableMegs = mi.availMem / 0x100000L;
                                Log.d(TAG, "Memory available in system: " + availableMegs + " MB");
                            }

                            graphicOverlay.clear();
                            if (originalCameraImage != null) {
                                graphicOverlay.add(new CameraImageGraphic(graphicOverlay, originalCameraImage));
                            }
                            VisionProcessorBase.this.onSuccess(results, graphicOverlay);
                            graphicOverlay.add(
                                    new InferenceInfoGraphic(
                                            graphicOverlay, currentLatencyMs, shouldShowFps ? framesPerSecond : null));
                            graphicOverlay.postInvalidate();
                        })
                .addOnFailureListener(
                        executor,
                        e -> {
                            graphicOverlay.clear();
                            graphicOverlay.postInvalidate();
                            String error = "Failed to process. Error: " + e.getLocalizedMessage();
                            Toast.makeText(
                                    graphicOverlay.getContext(),
                                    error + "\nCause: " + e.getCause(),
                                    Toast.LENGTH_SHORT)
                                    .show();
                            Log.d(TAG, error);
                            e.printStackTrace();
                            VisionProcessorBase.this.onFailure(e);
                        });
    }

    @Override
    public void stop() {
        executor.shutdown();
        isShutdown = true;
        numRuns = 0;
        totalRunMs = 0;
        fpsTimer.cancel();
    }

    protected abstract Task<T> detectInImage(InputImage image);

    protected abstract void onSuccess(@NonNull T results, @NonNull GraphicOverlay graphicOverlay);

    protected abstract void onFailure(@NonNull Exception e);
}
