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

/**
 * Loads recorded video file into media-reader, attach timestamps to individual video frames and
 * requests Ocr on each frame. Upon Ocr processing of all frames it requests LagCalculator class to
 * further compute the lag results.
 */
public class VideoProcessor {

  private static final int SAFE_FRAMES = 10;
  private MediaMetadataRetriever mediaMetadataRetriever;
  private int totalFrames = 0;
  private List<Bitmap> frameList;
  private TextRecognizer recognizer;
  private ArrayList<Long> videoFrameTimestamp = new ArrayList<>();

  public long getRecordStartTime() {
    return recordStartTime;
  }

  public ArrayList<Long> getVideoFrameTimestamp() {
    return videoFrameTimestamp;
  }

  private long recordStartTime = CameraFragment.Companion.getRecordingStartMillis();
  private long frameDuration = 1000L / CameraFragment.Companion.getFpsRecording();
  private long framesProcessed = 0;
  private ArrayList<String> resultsOCR = new ArrayList<>();

  public ArrayList<String> getResultsOCR() {
    return resultsOCR;
  }

  /** Sets media-reader and loads available video frames. SAFE_FRAMES trims potential corrupted
   * frames from the end.*/
  @RequiresApi(api = VERSION_CODES.P)
  public void createVideoReader(Context applicationContext, Uri fileUri) {
    recognizer = TextRecognition.getClient();
    mediaMetadataRetriever = new MediaMetadataRetriever();
    mediaMetadataRetriever.setDataSource(applicationContext, fileUri);
    totalFrames = Integer.valueOf(mediaMetadataRetriever.extractMetadata(METADATA_KEY_VIDEO_FRAME_COUNT));

    // TODO: Add on demand frame read opposed to read all frames.
    // TODO: Number of frames that can be loaded together seems to be capped by application. Behaviour occur only on some specific chosen resolutions.
    frameList = mediaMetadataRetriever.getFramesAtIndex(0, Math.max(0, totalFrames - SAFE_FRAMES));
  }

  /** Convenience method used to assign frame timestamps to individual frames based on video
   * recording start time and the fps of video. */
  private void setNextFrameTimeStamp() {
    videoFrameTimestamp.add(recordStartTime + (framesProcessed * frameDuration));
  }

  public void doOcr(LagCalculator lagCalculator) {
    AnalyseVideo ocrOnVideoTask = new AnalyseVideo();
    ocrOnVideoTask.execute(lagCalculator);
  }

  /** Iterates on video frames issuing an Ocr request. Requests video-frame timestamp assignment.
   * Calls LagCalculator upon Ocr completion of all video frame.*/
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
                    visionText -> {
                      resultsOCR.add(visionText.getText());
                      Log.d(
                          ContentValues.TAG,
                          "Text detected at index:" + finalI + " " + visionText.getText());
                      setNextFrameTimeStamp();
                      framesProcessed++;
                      if (framesProcessed == frameList.size()) {
                        lagCalculators[0].calculateLag();
                      }
                    })
                .addOnFailureListener(
                    e -> Log.d(ContentValues.TAG, "Analyse failed with: " + e.getMessage()));
      }
      return null;
    }
  }
}
