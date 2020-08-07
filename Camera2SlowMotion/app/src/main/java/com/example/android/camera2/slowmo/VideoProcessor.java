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
import androidx.annotation.RequiresApi;
import com.example.android.camera2.slowmo.fragments.CameraFragment;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Loads recorded video file into media-reader, attach timestamps to individual video frames and
 * requests Ocr on each frame. Upon Ocr processing of all frames it requests LagCalculator class to
 * further compute the lag results.
 */
public class VideoProcessor {

  private static final int FRAME_CHUNK_READ_SIZE = 100;
  private List<Bitmap> frameList = new ArrayList<>();
  private ArrayList<Long> videoFramesTimestamp;

  public ArrayList<Long> getVideoFramesTimestamp() {
    return videoFramesTimestamp;
  }

  /**
   * Sets media-reader and loads available video frames. SAFE_FRAMES trims potential corrupted
   * frames from the end.
   */
  @RequiresApi(api = VERSION_CODES.P)
  public boolean createVideoReader(Context applicationContext, Uri fileUri) {
    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
    mediaMetadataRetriever.setDataSource(applicationContext, fileUri);
    int totalFrames = Integer
        .parseInt(mediaMetadataRetriever.extractMetadata(METADATA_KEY_VIDEO_FRAME_COUNT));

    // TODO: Add on demand frame read opposed to read all frames.
    for (int i = 0; i < totalFrames; i += FRAME_CHUNK_READ_SIZE) {
      try {
        frameList.addAll(
            mediaMetadataRetriever.getFramesAtIndex(
                i, Math.min(totalFrames - i, FRAME_CHUNK_READ_SIZE)));
      } catch (IllegalStateException e) {
        Log.d(ContentValues.TAG, "Video chunk read unsuccessful with: " + e.getMessage());
        return false;
      }
    }
    videoFramesTimestamp = setFramesTimestamp();
    return true;
  }

  /**
   * Convenience method used to assign frame timestamps to individual frames based on video
   * recording start time and the fps of video.
   */
  private ArrayList<Long> setFramesTimestamp() {
    ArrayList<Long> timestamps = new ArrayList<Long>();
    long recordStartTime = CameraFragment.Companion.getRecordingStartMillis();
    long frameDuration = 1000L / CameraFragment.Companion.getFpsRecording();
    for (int i = 0; i < frameList.size(); i++) {
      timestamps.add(recordStartTime + (i * frameDuration));
    }
    return timestamps;
  }

  public List<String> doOcr() {
    AnalyseVideo ocrOnVideoTask = new AnalyseVideo();
    try {
      return ocrOnVideoTask.execute().get();
    } catch (ExecutionException | InterruptedException e) {
      e.printStackTrace();
      return null;
    }
  }

  //TODO: Replace AsyncTask with something more robust

  /**
   * Iterates on video frames issuing an Ocr request. Requests video-frame timestamp assignment.
   * Calls LagCalculator upon Ocr completion of all video frame.
   */
  private class AnalyseVideo extends AsyncTask<Void, Void, List<String>> {

    @Override
    protected List<String> doInBackground(Void... values) {
      TextRecognizer recognizer = TextRecognition.getClient();
      List<String> resultsOCR = Arrays.asList(new String[frameList.size()]);
      Collection<Task<Text>> ocrTasks = new ArrayList<>();
      for (int i = 0; i < frameList.size(); i++) {
        InputImage imageHolder = InputImage.fromBitmap(frameList.get(i), 0);
        final int imageIndex = i;
        ocrTasks.add(
            recognizer
                .process(imageHolder)
                .addOnSuccessListener(
                    visionText -> {
                      resultsOCR.set(imageIndex, visionText.getText());
                      Log.d(
                          ContentValues.TAG,
                          "Text detected at index:" + imageIndex + " " + visionText.getText());
                    })
                .addOnFailureListener(
                    e -> Log.d(ContentValues.TAG, "Analyse failed with: " + e.getMessage())));
      }
      Task finalTask = Tasks.whenAllComplete(ocrTasks);
      try {
        Tasks.await(finalTask);
        return resultsOCR;
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
        return null;
      }
    }
  }
}
