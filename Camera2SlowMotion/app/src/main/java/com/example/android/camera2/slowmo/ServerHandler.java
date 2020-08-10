package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Handles every interaction of app with the server. */
public class ServerHandler {
  private class Connection {
    Socket socket;
    BufferedReader inputReader;
    PrintWriter outputWriter;
  }

  public class ServerData{
    ArrayList<Long> timestamps;
    ArrayList<String> serverSequence;
    ServerData(){
      this.timestamps = new ArrayList<>();
      this.serverSequence = new ArrayList<>();
    }
  }

  private Integer serverSocketPort;
  private CompletableFuture<Long> serverStartTimestamp;
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
                long ts = System.currentTimeMillis();
                conn.outputWriter.write("started capture*");
                conn.outputWriter.flush();
                return ts;
              }
            });
  }

  @RequiresApi(api = VERSION_CODES.N)
  public CompletableFuture<Long> getSyncOffset() {
    return CompletableFuture.allOf(serverStartTimestamp, hostSyncTimestamp)
        .thenApply(
            unused -> {
              try {
                return serverStartTimestamp.get() - hostSyncTimestamp.get();
              } catch (InterruptedException | ExecutionException e) {
                throw new CompletionException(e);
              }
            });
  }

  @RequiresApi(api = VERSION_CODES.N)
  public CompletableFuture<ServerData> downloadServerTimeStampsAndSequence() {
    CompletableFuture<ServerData> serverTimestampAndSequence =
        connection.thenApply(
            conn -> {
              synchronized (conn) {
                ServerData serverData = new ServerData();
                try {
                  Log.d(ContentValues.TAG, "Sending request to download timestamps");
                  conn.outputWriter.write("send timestamps" + "*");
                  conn.outputWriter.flush();
                  Log.d(
                      ContentValues.TAG, "Request sent to server. Waiting to receive timestamps..");
                  String message = conn.inputReader.readLine();

                  while (message != null) {
                    serverData.timestamps.add(Long.valueOf(message));
                    Log.d(ContentValues.TAG, "Server TimeStamp:" + message);

                    message = conn.inputReader.readLine();
                    serverData.serverSequence.add(message);
                    Log.d(ContentValues.TAG, "Server Sequence:" + message);

                    message = conn.inputReader.readLine();
                  }
                  Log.d(ContentValues.TAG, "All timestamps from server received.");
                } catch (IOException e) {
                  Log.d(ContentValues.TAG, "Server TimeStamp read failed");
                }
                return serverData;
              }
            });
    serverStartTimestamp = serverTimestampAndSequence.thenApply(data -> data.timestamps.get(0));
    return serverTimestampAndSequence;
  }
}
