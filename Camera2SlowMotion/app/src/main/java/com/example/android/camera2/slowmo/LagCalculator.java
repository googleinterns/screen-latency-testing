package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.util.Log;
import java.util.ArrayList;

public class LagCalculator {
  private VideoProcessor videoData;
  private ServerHandler serverData;
  public ArrayList<Long> lagResult = new ArrayList<Long>();

  public LagCalculator(VideoProcessor videoData, ServerHandler serverData) {
    this.videoData = videoData;
    this.serverData = serverData;
  }

  public ArrayList<Long> calculateLag() {
    if (serverData.getServerTimeStamps().size() < 2 || videoData.getResultsOCR().size() < 2) {
      Log.d(ContentValues.TAG, "No results to show. Insufficient server-host data.");
      return null;
    }
    Log.d(ContentValues.TAG, "Video start timestamp:" + videoData.getRecordStartTime());
    // TODO: Server sequence is hardcoded. Add functionality to receive serverSequence.
    String serverSequence = "m";
    long serverHostSyncOffset =
        serverData.getServerStartTimeStamp() - serverData.getHostSyncTimeStamp();
    Log.d(ContentValues.TAG, "Server-Host Sync offset:" + serverHostSyncOffset);

    ArrayList<Long> serverTimestamp = serverData.getServerTimeStamps();
    ArrayList<Long> videoFrameTimestamp = videoData.getVideoFrameTimestamp();
    ArrayList<String> resultsOCR = videoData.getResultsOCR();

    for (int j = 1; j < serverTimestamp.size(); j++) {
      for (int i = 0; i < resultsOCR.size(); i++) {
        if (serverSequence.equals(resultsOCR.get(i))) {
          long lag = videoFrameTimestamp.get(i) - serverTimestamp.get(j) + serverHostSyncOffset;
          lagResult.add(lag);
          break;
        }
      }
      serverSequence += "m";
    }
    return lagResult;
  }
}
