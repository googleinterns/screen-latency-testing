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

/** Handles every interaction of app with the server. */
public class ServerHandler {

  private Integer serverSocketPort;
  private final String SERVER_IP = "127.0.0.1";
  private ArrayList<Long> serverTimestamps = new ArrayList<>();
  private long serverStartTimestamp;
  private ConnectionThread connectionThread;
  private Socket socket;
  private BufferedReader inputReader;
  private PrintWriter outputWriter;
  private long hostSyncTimestamp;

  public long getHostSyncTimestamp() {
    return hostSyncTimestamp;
  }

  public Integer getServerSocketPort() {
    return serverSocketPort;
  }

  public void setServerSocketPort(Integer serverSocketPort) {
    this.serverSocketPort = serverSocketPort;
  }

  public ArrayList<Long> getServerTimestamps() {
    return serverTimestamps;
  }

  public long getServerStartTimestamp() {
    return serverStartTimestamp;
  }

  public void startConnection() {
    connectionThread = new ConnectionThread();
    connectionThread.execute();
  }

  public void sendKeySimulationSignal() {
    hostSyncTimestamp = System.currentTimeMillis();
    outputWriter.write("started capture*");
    outputWriter.flush();
  }

  /** Creates a socket for communication with the server. Sets reader and writer for server
   * connection. */
  private class ConnectionThread extends AsyncTask<Void, Void, Void> {

    @Override
    protected Void doInBackground(Void... voids) {
      try {
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

  public void downloadServerTimeStamps() {
    if (serverSocketPort == null) {
      Log.d(ContentValues.TAG, "Can't find server to download timestamps. No port bound.");
      return;
    }
    DownloadServerLogs downloadServerLogsTask = new DownloadServerLogs();
    downloadServerLogsTask.execute();
  }

  /** Requests the server to send the timestamps of key-presses. */
  private class DownloadServerLogs extends AsyncTask<Void, Void, Void> {
    @Override
    protected Void doInBackground(Void... values) {
      if (serverSocketPort == null) {
        Log.d(
            ContentValues.TAG,
            "No server to read input from. Check if server communication is working.");
      }
      try {
        Log.d(ContentValues.TAG, "Sending request to download timestamps");
        outputWriter.write("send timestamps" + "*");
        outputWriter.flush();
        Log.d(ContentValues.TAG, "Request sent to server. Waiting to receive timestamps..");
        String message = inputReader.readLine();
        serverStartTimestamp = Long.valueOf(message);

        while (message != null) {
          serverTimestamps.add(Long.valueOf(message));
          Log.d(ContentValues.TAG, "Server TimeStamp:" + message);
          message = inputReader.readLine();
        }
        Log.d(ContentValues.TAG, "All timestamps from server received.");
      } catch (IOException e) {
        Log.d(ContentValues.TAG, "Server TimeStamp read failed");
      }
      return null;
    }
  }
}
