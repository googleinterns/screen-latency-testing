package com.example.android.camera2.slowmo;

import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Loads recorded video file into media-reader, attach timestamps to individual video frames and
 * requests Ocr on each frame. Upon Ocr processing of all frames it requests LagCalculator class to
 * further compute the lag results.
 */
public class VideoProcessor {

  private static final int FRAME_CHUNK_READ_SIZE = 100;
  private CompletableFuture<List<Bitmap>> frameList;

  /* Creates media-reader and loads available video frames. */
  @RequiresApi(api = VERSION_CODES.P)
  public void createVideoReader(Context applicationContext, Uri fileUri) {
    frameList =
        CompletableFuture.supplyAsync(
            () -> {
              MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
              mediaMetadataRetriever.setDataSource(applicationContext, fileUri);
              List<Bitmap> frames = new ArrayList<>();
              int totalFrames =
                  Integer.parseInt(
                      mediaMetadataRetriever.extractMetadata(METADATA_KEY_VIDEO_FRAME_COUNT));

              // TODO: Add on demand frame read opposed to read all frames.
              for (int i = 0; i < totalFrames; i += FRAME_CHUNK_READ_SIZE) {
                try {
                  frames.addAll(
                      mediaMetadataRetriever.getFramesAtIndex(
                          i, Math.min(totalFrames - i, FRAME_CHUNK_READ_SIZE)));
                } catch (IllegalStateException e) {
                  Log.d(ContentValues.TAG, "Video chunk read unsuccessful with: " + e.getMessage());
                }
              }
              return frames;
            });
  }

  @RequiresApi(api = VERSION_CODES.N)
  public CompletableFuture<ArrayList<Text>> doOcr() {
    CompletableFuture<ArrayList<Text>> resultsOCR =
        frameList.thenApply(
            frames -> {
              synchronized (frames) {
                TextRecognizer recognizer = TextRecognition.getClient();
                Collection<Task<Text>> ocrTasks = new ArrayList<>();
                ArrayList<Text> ocrTexts = new ArrayList<>();
                for (int i = 0; i < frames.size(); i++) {
                  InputImage imageHolder = InputImage.fromBitmap(frames.get(i), 0);
                  final int imageIndex = i;
                  ocrTasks.add(
                      recognizer
                          .process(imageHolder)
                          .addOnSuccessListener(
                              visionText -> {
                                ocrTexts.add(visionText);
                                Log.d(
                                    ContentValues.TAG,
                                    "Text detected at index:"
                                        + imageIndex
                                        + " "
                                        + visionText.getText());
                              })
                          .addOnFailureListener(
                              e ->
                                  Log.d(
                                      ContentValues.TAG,
                                      "Analyse failed with: " + e.getMessage())));
                }
                Task finalTask = Tasks.whenAllComplete(ocrTasks);
                try {
                  Tasks.await(finalTask);
                  return ocrTexts;
                } catch (InterruptedException | ExecutionException e) {
                  e.printStackTrace();
                  return null;
                }
              }
            });
    return resultsOCR;
  }
}
