package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Calculates lag of each key-press on the server using the key-press timestamps from server and
 * character seen timestamp from recorded video.
 */
public class LagCalculator {

  public ArrayList<Long> calculateLag(
      @Nullable CompletableFuture<ArrayList<Long>> serverTimestamps,
      @Nullable CompletableFuture<ArrayList<Long>> videoFramesTimestamp,
      @Nullable CompletableFuture<ArrayList<String>> resultsOcr,
      @Nullable CompletableFuture<Long> serverHostSyncOffset)
      throws ExecutionException, InterruptedException {

    ArrayList<Long> lagResults = new ArrayList<>();
    if (serverTimestamps.get().size() < 2 || videoFramesTimestamp.get().size() < 2) {
      Log.d(ContentValues.TAG, "No results to show. Insufficient server-host data.");
      return null;
    }
    // TODO: Server sequence is hardcoded. Add functionality to receive serverSequence.
    String serverSequence = "m";
    Log.d(ContentValues.TAG, "Server-Host Sync offset:" + serverHostSyncOffset.get());

    // Finds the video frame with the running serverSequence text.
    for (int j = 1; j < serverTimestamps.get().size(); j++) {
      for (int i = 0; i < resultsOcr.get().size(); i++) {
        if (resultsOcr.get().get(i).startsWith(serverSequence)) {
          long lag = videoFramesTimestamp.get().get(i) - serverTimestamps.get().get(j) + serverHostSyncOffset.get();
          lagResults.add(lag);
          Log.d("Lag:", String.valueOf(lag));
          break;
        }
      }
      serverSequence += "m";
    }
    return lagResults;
  }
}
