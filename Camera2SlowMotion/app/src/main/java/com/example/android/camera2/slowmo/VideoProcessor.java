package com.example.android.camera2.slowmo;

import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.example.android.camera2.slowmo.fragments.CameraFragment;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import java.util.ArrayList;
import java.util.List;

public class VideoProcessor {

  private static final Integer SAFE_FRAMES = 10;
  private MediaMetadataRetriever mediaMetadataRetriever;
  private Integer totalFrames = 0;
  private List<Bitmap> frameList;
  private TextRecognizer recognizer;
  private ArrayList<Long> videoFrameTimestamp = new ArrayList<>();

  public Long getRecordStartTime() {
    return recordStartTime;
  }

  public ArrayList<Long> getVideoFrameTimestamp() {
    return videoFrameTimestamp;
  }

  private Long recordStartTime = CameraFragment.Companion.getRecordingStartMillis();
  private Long frameDuration = 1000L / CameraFragment.Companion.getFpsRecording();
  private Integer framesProcessed = 0;
  private ArrayList<String> resultsOCR = new ArrayList<>();

  public ArrayList<String> getResultsOCR() {
    return resultsOCR;
  }

  @RequiresApi(api = VERSION_CODES.P)
  public void setVideoReader(Context applicationContext, Uri fileUri) {
    recognizer = TextRecognition.getClient();
    mediaMetadataRetriever = new MediaMetadataRetriever();
    mediaMetadataRetriever.setDataSource(applicationContext, fileUri);
    totalFrames = Integer.valueOf(mediaMetadataRetriever.extractMetadata(METADATA_KEY_VIDEO_FRAME_COUNT));

    // TODO: Add on demand frame read opposed to read all frames.
    // TODO: Number of frames that can be loaded together seems to be capped by application. Behaviour occur only on some specific chosen resolutions.
    frameList = mediaMetadataRetriever.getFramesAtIndex(0, Math.max(0, totalFrames - SAFE_FRAMES));
  }

  private void setNextFrameTimeStamp() {
    videoFrameTimestamp.add(recordStartTime + (framesProcessed * frameDuration));
  }

  public void doOcrOnVideo(LagCalculator lagCalculator) {
    AnalyseVideo ocrOnVideoTask = new AnalyseVideo();
    ocrOnVideoTask.execute(lagCalculator);
  }

  private class AnalyseVideo extends AsyncTask<LagCalculator, Void, Void> {
    @Override
    protected Void doInBackground(LagCalculator... lagCalculators) {
      for (int i = 0; i < frameList.size(); i++) {
        InputImage imageHolder = InputImage.fromBitmap(frameList.get(i), 0);
        final int finalI = i;
        Task<Text> result =
            recognizer
                .process(imageHolder)
                .addOnSuccessListener(
                    new OnSuccessListener<Text>() {
                      @Override
                      public void onSuccess(Text visionText) {
                        resultsOCR.add(visionText.getText());
                        Log.d(
                            ContentValues.TAG,
                            "Text detected at index:" + finalI + " " + visionText.getText());
                        setNextFrameTimeStamp();
                        framesProcessed++;
                        if (framesProcessed == frameList.size()) {
                          lagCalculators[0].calculateLag();
                        }
                      }
                    })
                .addOnFailureListener(
                    new OnFailureListener() {
                      @Override
                      public void onFailure(@NonNull Exception e) {
                        Log.d(ContentValues.TAG, "Analyse failed with: " + e.getMessage());
                      }
                    });
      }
      return null;
    }
  }
}
