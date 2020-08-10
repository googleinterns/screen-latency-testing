package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.util.Log;
import com.example.android.camera2.slowmo.ServerHandler.ServerData;
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
      CompletableFuture<ServerData> serverData,
      CompletableFuture<ArrayList<Long>> videoFramesTimestamp,
      CompletableFuture<ArrayList<String>> resultsOcr,
      CompletableFuture<Long> serverHostSyncOffset)
      throws ExecutionException, InterruptedException {

    ArrayList<Long> lagResults = new ArrayList<>();
    if (serverData.get().timestamps.size() < 2 || videoFramesTimestamp.get().size() < 2) {
      Log.d(ContentValues.TAG, "No results to show. Insufficient server-host data.");
      return null;
    }

    Log.d(ContentValues.TAG, "Server-Host Sync offset:" + serverHostSyncOffset.get());

    // Finds the video frame with the running serverSequence text.
    for (int j = 1; j < serverData.get().timestamps.size(); j++) {
      for (int i = 0; i < resultsOcr.get().size(); i++) {
        if (resultsOcr.get().get(i).startsWith(serverData.get().serverSequence.get(j))) {
          long lag = videoFramesTimestamp.get().get(i) - serverData.get().timestamps.get(j) + serverHostSyncOffset.get();
          lagResults.add(lag);
          Log.d("Lag:", String.valueOf(lag));
          break;
        }
      }
    }
    return lagResults;
  }
}
