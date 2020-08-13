package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.graphics.Point;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.google.gson.GsonBuilder;
import com.google.mlkit.vision.text.Text;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.ArrayList;
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
    ArrayList<FrameData> framesMetaData;
    long recordingStartTime;
    long videoFps;
    Long hostSyncTimestamp;

    public HostData(ArrayList<FrameData> ocrTexts, long recordingStartTime, long videoFps,
        Long syncOffset) {
      this.framesMetaData = ocrTexts;
      this.recordingStartTime = recordingStartTime;
      this.videoFps = videoFps;
      this.hostSyncTimestamp = syncOffset;
    }
  }

  private class FrameData{
    ArrayList<String> line;
    ArrayList<Point[]> cornerPoints;

    public FrameData() {
      line = new ArrayList<>();
      cornerPoints = new ArrayList<Point[]>();
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

  private Integer serverSocketPort;
  private CompletableFuture<Connection> connection;
  private CompletableFuture<Long> hostSyncTimestamp;

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
                ServerAction captureStarted = new ServerAction(1, "Started Capture");
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
      ArrayList<Text> resultsOcr, long recordingStartMillis, int fpsRecording) {
    connection.thenApply(
        conn -> {
          synchronized (conn) {
            ArrayList<FrameData> framesMetaData = parseTextObjects(resultsOcr);
            try {
              HostData hostData = new HostData(framesMetaData, recordingStartMillis, fpsRecording, hostSyncTimestamp.get());
              ServerAction receiveResults = new ServerAction(2, "Sending results");
              conn.outputWriter.write(new GsonBuilder().create().toJson(receiveResults));
              conn.outputWriter.flush();

              conn.outputWriter.write(new GsonBuilder().create().toJson(hostData));
              conn.outputWriter.flush();
            } catch (ExecutionException | InterruptedException e) {
              e.printStackTrace();
            }
          }
          return null;
        });
  }

  private ArrayList<FrameData> parseTextObjects(ArrayList<Text> resultsOcr) {
    ArrayList<FrameData> framesMetaData = new ArrayList<>();
    for(Text frameText : resultsOcr){
      FrameData frameData = new FrameData();
      for(Text.TextBlock block : frameText.getTextBlocks()){
        for(Text.Line line : block.getLines()){
          frameData.cornerPoints.add(line.getCornerPoints());
          frameData.line.add(line.getText());
        }
      }
      framesMetaData.add(frameData);
    }
    return framesMetaData;
  }
}
