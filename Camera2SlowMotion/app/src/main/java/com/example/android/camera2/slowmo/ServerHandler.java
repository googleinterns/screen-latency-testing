package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.os.AsyncTask;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;

/** Handles every interaction of app with the server. */
public class ServerHandler {

  private Integer serverSocketPort;
  private long serverStartTimestamp;
  private Socket socket;
  private BufferedReader inputReader;
  private PrintWriter outputWriter;
  private long hostSyncTimestamp;

  public ServerHandler(Integer serverSocketPort) {
    this.serverSocketPort = serverSocketPort;
  }

  public long getHostSyncTimestamp() {
    return hostSyncTimestamp;
  }

  public Integer getServerSocketPort() {
    return serverSocketPort;
  }

  public void setServerSocketPort(Integer serverSocketPort) {
    this.serverSocketPort = serverSocketPort;
  }

  public long getServerStartTimestamp() {
    return serverStartTimestamp;
  }

  public void startConnection() {
    ConnectionThread connectionThread = new ConnectionThread();
    connectionThread.execute();
  }

  public void sendKeySimulationSignal() {
    hostSyncTimestamp = System.currentTimeMillis();
    outputWriter.write("started capture*");
    outputWriter.flush();
  }

  @NotNull
  public long getSyncOffset() {
    return serverStartTimestamp - hostSyncTimestamp;
  }

  /** Creates a socket for communication with the server. Sets reader and writer for server
   * connection. */
  private class ConnectionThread extends AsyncTask<Void, Void, Void> {

    @Override
    protected Void doInBackground(Void... voids) {
      try {
        String SERVER_IP = "127.0.0.1";
        socket = new Socket(SERVER_IP, serverSocketPort);
        outputWriter = new PrintWriter(socket.getOutputStream());
        inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Log.d(ContentValues.TAG, "Connection established");
      } catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }
  }

  public ArrayList<Long> downloadServerTimeStamps() {
    if (serverSocketPort == null || !socket.isConnected()) {
      Log.d(ContentValues.TAG, "Can't find server to download timestamps. No port bound or connection is dead.");
      return null;
    }
    DownloadServerLogs downloadServerLogsTask = new DownloadServerLogs();
    try {
      return downloadServerLogsTask.execute().get();
    } catch (ExecutionException | InterruptedException e) {
      e.printStackTrace();
    }
    return null;
  }

  /** Requests the server to send the timestamps of key-presses. */
  private class DownloadServerLogs extends AsyncTask<Void, Void, ArrayList<Long>> {
    @Override
    protected ArrayList<Long> doInBackground(Void... values) {
      if (serverSocketPort == null) {
        Log.d(
            ContentValues.TAG,
            "No server to read input from. Check if server communication is working.");
      }
      ArrayList<Long> serverTimestamps = new ArrayList<>();
      try {
        Log.d(ContentValues.TAG, "Sending request to download timestamps");
        outputWriter.write("send timestamps" + "*");
        outputWriter.flush();
        Log.d(ContentValues.TAG, "Request sent to server. Waiting to receive timestamps..");
        String message = inputReader.readLine();
        serverStartTimestamp = Long.parseLong(message);

        while (message != null) {
          serverTimestamps.add(Long.valueOf(message));
          Log.d(ContentValues.TAG, "Server TimeStamp:" + message);
          message = inputReader.readLine();
        }
        Log.d(ContentValues.TAG, "All timestamps from server received.");
      } catch (IOException e) {
        Log.d(ContentValues.TAG, "Server TimeStamp read failed");
      }
      return serverTimestamps;
    }
  }
}
