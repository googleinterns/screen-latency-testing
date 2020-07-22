package com.example.android.camera2.slowmo;

import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.example.android.camera2.slowmo.fragments.CameraFragment;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private Integer totalFrames = 0;
  private Integer framesProcessed = 0;
  private final Integer SAFE_FRAMES = 10;
  private final Integer FILE_PICKER_REQUEST_CODE = 10;
  private String tag = "Rokus Logs:";
  private Integer fps;
  private Long sync_offset;

  private TextView filePath;
  private TextView analyseResultField;
  private Button filePickerBtn;
  private Button analyzeBtn;
  private Intent fileIntent;
  private MediaMetadataRetriever mediaMetadataRetriever;
  private Uri fileUri;
  private InputImage imageHolder;
  private TextRecognizer recognizer;
  private ArrayList<String> resultsOCR = new ArrayList<String>();
  private ArrayList<String> serverCache = new ArrayList<String>();
  private ArrayList<Long> deltaServer = new ArrayList<>();
  private List<Bitmap> frameList = new ArrayList<>();
  private ArrayList<Long> deltaOCR = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    filePath = findViewById(R.id.selectedFilePath);
    filePickerBtn = findViewById(R.id.fileSelectButton);
    analyzeBtn = findViewById(R.id.analyze);
    analyseResultField = findViewById(R.id.analyseResultView);

    analyseResultField.setMovementMethod(new ScrollingMovementMethod());
    analyseResultField.append("\nPort bound:" + CameraActivity.Companion.getLaptopPort() +"\n");
    recognizer = TextRecognition.getClient();
    mediaMetadataRetriever = new MediaMetadataRetriever();
    Bundle extras = getIntent().getExtras();

    try {
      filePath.setText(extras.getString("file URI"));
      fps = Integer.valueOf(extras.getString("video fps"));
    } catch (Exception e) {
      Log.d("Rokus logs:", "Integration failed, no file URI received from capture session");
    }

    filePickerBtn.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            fileIntent.setType("*/*");
            startActivityForResult(fileIntent, FILE_PICKER_REQUEST_CODE);
          }
        });

    analyzeBtn.setOnClickListener(
        new View.OnClickListener() {
          @RequiresApi(api = Build.VERSION_CODES.P)
          @Override
          public void onClick(View view) {
            try {
              analyze();
            } catch (Exception e) {
              Log.d("Rokus Logs:", "Analyse call failed with: " + e.getMessage());
            }
          }
        });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
      String path = data.getData().toString();
      fileUri = data.getData();
      filePath.setText(path);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.P)
  private void analyze() {
    fileUri = Uri.parse(filePath.getText().toString());
    mediaMetadataRetriever.setDataSource(getApplicationContext(), fileUri);
    totalFrames =
        Integer.valueOf(mediaMetadataRetriever.extractMetadata(METADATA_KEY_VIDEO_FRAME_COUNT));
    frameList = mediaMetadataRetriever.getFramesAtIndex(0, Math.max(0, totalFrames - SAFE_FRAMES));

    AsyncTaskServer asyncTaskServer = new AsyncTaskServer();
    asyncTaskServer.execute();

    AsyncTaskOCR asyncTaskOCR = new AsyncTaskOCR();
    asyncTaskOCR.execute();
  }

  private class AsyncTaskServer extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... strings) {
      if (CameraActivity.Companion.getLaptopPort() == 0) {
        Log.d(tag, "No server to read input from. Check if server communication is working.");
        return null;
      }
      try {
        Log.d(tag, "Sending request to send cache");
        CameraActivity.output.write("send cache" + "*");
        CameraActivity.output.flush();
        Log.d(tag, "Sent request to send cache");
        String message = "";

        while (message != null) {
          message = CameraActivity.input.readLine();
          if (message == null) {
            break;
          }
          serverCache.add(message);
          Log.d("Rokus Logs:", "Cache:" + message);
        }

      } catch (IOException e) {
        Log.d("Rokus logs:", "Cache read failed");
      }

      return null;
    }

    @Override
    protected void onPostExecute(String s) {
      super.onPostExecute(s);
      try {
        parseServer();
      } catch (Exception e) {
        Log.d(tag, "Parsing server cache failed");
        Log.d("Rokus Logs:", String.valueOf(e.getCause()));
        Log.d("Rokus Logs:", e.getMessage());
        Log.d("Rokus Logs:", String.valueOf(e.getStackTrace()));
      }
    }
  }

  private class AsyncTaskOCR extends AsyncTask<String, String, String> {
    @Override
    protected String doInBackground(String... strings) {
      for (int i = 0; i < frameList.size(); i++) {
        imageHolder = InputImage.fromBitmap(frameList.get(i), 0);
        final int finalI = i;
        Task<Text> result =
            recognizer
                .process(imageHolder)
                .addOnSuccessListener(
                    new OnSuccessListener<Text>() {
                      @Override
                      public void onSuccess(Text visionText) {
                        resultsOCR.add(visionText.getText());
                        Log.d(
                            "Rokus logs",
                            "Text detected at index:" + finalI + " " + visionText.getText());
                        framesProcessed++;
                        if (framesProcessed == frameList.size()) {
                          analyseResultField.append("OCR on all frames done\n");
                          parseOCR();
                          analyseResultField.append("OCR frames parsed\n");
                          showResults();
                        }
                      }
                    })
                .addOnFailureListener(
                    new OnFailureListener() {
                      @Override
                      public void onFailure(@NonNull Exception e) {
                        Log.d("Rokus Logs:", "Analyse failed with: " + e.getMessage());
                      }
                    });
      }
      return null;
    }
  }

  private void parseOCR() {
    try {
      Long recordStartTime = CameraFragment.Companion.getRecordingStartMillis();
      Long offset = 1000L / fps;
      for (int i = 0; i < resultsOCR.size(); i++) {
        deltaOCR.add(recordStartTime + (i * offset));
      }
    } catch (Exception e) {
      Log.d("Rokus Logs:", "Parsing OCR failed with: " + e.getMessage());
    }
  }

  // Parse timestamps received from server.
  private void parseServer() {
    for (int i = 0; i < serverCache.size(); i++) {
      deltaServer.add(Long.valueOf(serverCache.get(i)));
      Log.d(tag, "ParseServer:" + deltaServer.get(deltaServer.size() - 1));
    }
  }

  private void showResults() {
    if (deltaServer.size() < 2 || deltaOCR.size() < 2) {
      Log.d(tag, "No results to show");
      return;
    }
    Log.d(
        "Show Results",
        "Video start timestamp:" + CameraFragment.Companion.getRecordingStartTime());
    String serverSequence = "m";
    sync_offset = deltaServer.get(0) - CameraFragment.Companion.getRecordingStartMillis();
    Log.d("Show Results", "Sync offset:" + sync_offset);
    for (int j = 1; j < deltaServer.size(); j++) {
      for (int i = 0; i < resultsOCR.size(); i++) {
        if (serverSequence.equals(resultsOCR.get(i))) {
          analyseResultField.append(
              "Lag:" + (deltaOCR.get(i) - deltaServer.get(j) + sync_offset)
                  + " Server sequence:" + serverSequence + "\n");
          break;
        }
      }
      serverSequence += "m";
    }
  }
}
