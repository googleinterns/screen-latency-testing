package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.os.AsyncTask;
import android.util.Log;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Calculates lag of each key-press on the server using the key-press timestamps from server and
 * character seen timestamp from recorded video.
 */
public class LagCalculator {

  private static class LagCalculatorPrams {
    ArrayList<Long> serverTimestamps, videoFrameTimestamps;
    ArrayList<String> resultsOcr;
    long syncOffset;

    public LagCalculatorPrams(
        ArrayList<Long> serverTimestamps,
        ArrayList<Long> videoFrameTimestamps,
        ArrayList<String> resultsOcr,
        long syncOffset) {
      this.serverTimestamps = serverTimestamps;
      this.videoFrameTimestamps = videoFrameTimestamps;
      this.resultsOcr = resultsOcr;
      this.syncOffset = syncOffset;
    }
  }

  public ArrayList<Long> calculateLag(
      ArrayList<Long> serverTimestamps,
      ArrayList<Long> videoFrameTimestamp,
      ArrayList<String> resultsOcr,
      long syncOffset) {
    LagCalculatorThread lagCalculatorThread = new LagCalculatorThread();
    try {
      return lagCalculatorThread
          .execute(
              new LagCalculatorPrams(serverTimestamps, videoFrameTimestamp, resultsOcr, syncOffset))
          .get();
    } catch (ExecutionException | InterruptedException e) {
      e.printStackTrace();
      return null;
    }
  }

  private class LagCalculatorThread extends AsyncTask<LagCalculatorPrams, Void, ArrayList<Long>> {
    @Override
    protected ArrayList<Long> doInBackground(LagCalculatorPrams... params) {
      ArrayList<Long> serverTimestamps = params[0].serverTimestamps;
      ArrayList<Long> videoFrameTimestamp = params[0].videoFrameTimestamps;
      ArrayList<String> resultsOCR = params[0].resultsOcr;
      ArrayList<Long> lagResults = new ArrayList<Long>();
      long serverHostSyncOffset = params[0].syncOffset;

      if (serverTimestamps.size() < 2 || videoFrameTimestamp.size() < 2) {
        Log.d(ContentValues.TAG, "No results to show. Insufficient server-host data.");
        return null;
      }
      // TODO: Server sequence is hardcoded. Add functionality to receive serverSequence.
      String serverSequence = "m";
      Log.d(ContentValues.TAG, "Server-Host Sync offset:" + serverHostSyncOffset);

      // Finds the video frame with the running serverSequence text.
      for (int j = 1; j < serverTimestamps.size(); j++) {
        for (int i = 0; i < resultsOCR.size(); i++) {
          if (resultsOCR.get(i).startsWith(serverSequence)) {
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
}
