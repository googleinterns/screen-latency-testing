package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates lag of each key-press on the server using the key-press timestamps from server and
 * character seen timestamp from recorded video.
 */
public class LagCalculator {

  public ArrayList<Long> calculateLag(
      ArrayList<Long> serverTimestamps,
      ArrayList<Long> videoFrameTimestamp,
      List<String> resultsOcr,
      long serverHostSyncOffset) {

    ArrayList<Long> lagResults = new ArrayList<>();
    if (serverTimestamps.size() < 2 || videoFrameTimestamp.size() < 2) {
      Log.d(ContentValues.TAG, "No results to show. Insufficient server-host data.");
      return null;
    }
    // TODO: Server sequence is hardcoded. Add functionality to receive serverSequence.
    String serverSequence = "m";
    Log.d(ContentValues.TAG, "Server-Host Sync offset:" + serverHostSyncOffset);

    // Finds the video frame with the running serverSequence text.
    for (int j = 1; j < serverTimestamps.size(); j++) {
      for (int i = 0; i < resultsOcr.size(); i++) {
        if (resultsOcr.get(i).startsWith(serverSequence)) {
          long lag = videoFrameTimestamp.get(i) - serverTimestamps.get(j) + serverHostSyncOffset;
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
