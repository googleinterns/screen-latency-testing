package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.example.android.camera2.slowmo.VideoProcessor.FrameData;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Handles every interaction of app with the server. */
public class ServerHandler {
  private class Connection {
    Socket socket;
    BufferedReader inputReader;
    PrintWriter outputWriter;
  }

  private class HostData {
    List<FrameData> framesMetaData;
    long recordingStartTime;
    long videoFps;
    long hostSyncTimestamp;

    public HostData(List<FrameData> ocrTexts, long recordingStartTime, long videoFps,
        Long syncOffset) {
      this.framesMetaData = ocrTexts;
      this.recordingStartTime = recordingStartTime;
      this.videoFps = videoFps;
      this.hostSyncTimestamp = syncOffset;
    }
  }

  private class ServerAction {
    int code;
    String message;
    ServerAction(int code, String message){
      this.code = code;
      this.message = message;
    }
  }

  private int serverSocketPort;
  private CompletableFuture<Connection> connection;
  private CompletableFuture<Long> hostSyncTimestamp;
  private final int ACTION_CAPTURE_STARTED = 1;
  private final int ACTION_CAPTURE_RESULTS = 2;

  public ServerHandler(Integer serverSocketPort) {
    this.serverSocketPort = serverSocketPort;
  }

  /**
   * Creates a socket for communication with the server. Sets reader and writer for server
   * connection.
   */
  @RequiresApi(api = VERSION_CODES.N)
  public void startConnection() {
    connection =
        CompletableFuture.supplyAsync(
            () -> {
              Connection conn = new Connection();
              String SERVER_IP = "127.0.0.1";
              try {
                conn.socket = new Socket(SERVER_IP, serverSocketPort);
                conn.inputReader =
                    new BufferedReader(new InputStreamReader(conn.socket.getInputStream()));
                conn.outputWriter = new PrintWriter(conn.socket.getOutputStream());
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
              Log.d(ContentValues.TAG, "Connection established");
              return conn;
            });
  }

  @RequiresApi(api = VERSION_CODES.N)
  public void sendKeySimulationSignal() {
    hostSyncTimestamp =
        connection.thenApply(
            conn -> {
              synchronized (conn) {
                ServerAction captureStarted = new ServerAction(ACTION_CAPTURE_STARTED, "Started Capture");
                String json = new GsonBuilder().create().toJson(captureStarted);
                long ts = System.currentTimeMillis();
                conn.outputWriter.write(json);
                conn.outputWriter.flush();
                return ts;
              }
            });
  }

  @RequiresApi(api = VERSION_CODES.N)
  public void sendOcrResults(
      List<FrameData> framesMetaData, long recordingStartMillis, int fpsRecording) {
    connection.thenAccept(
        conn -> {
          synchronized (conn) {
            try {
              HostData hostData = new HostData(framesMetaData, recordingStartMillis, fpsRecording, hostSyncTimestamp.get());
              ServerAction receiveResults = new ServerAction(ACTION_CAPTURE_RESULTS, "Sending results");
              conn.outputWriter.write(new GsonBuilder().create().toJson(receiveResults));
              conn.outputWriter.flush();

              conn.outputWriter.write(new GsonBuilder().create().toJson(hostData));
              conn.outputWriter.flush();
            } catch (ExecutionException | InterruptedException e) {
              e.printStackTrace();
            }
          }
        });
  }
}
