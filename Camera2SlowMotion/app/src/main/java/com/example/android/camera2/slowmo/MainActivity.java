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
  private final String TAG_AUTHOR = "Rokus Logs:";
  private Long syncOffset;
  private Long recordStartTime = CameraFragment.Companion.getRecordingStartMillis();
  private Long frameDuration = 1000L / CameraFragment.Companion.getFpsRecording();

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
  private ArrayList<Long> serverTimestamp = new ArrayList<>();
  private List<Bitmap> frameList = new ArrayList<>();
  private ArrayList<Long> videoFrameTimestamp = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    filePath = findViewById(R.id.selectedFilePath);
    filePickerBtn = findViewById(R.id.fileSelectButton);
    analyzeBtn = findViewById(R.id.analyze);
    analyseResultField = findViewById(R.id.analyseResultView);

    analyseResultField.setMovementMethod(new ScrollingMovementMethod());
    analyseResultField.append("\nPort bound:" + CameraActivity.Companion.getLaptopPort() + "\n");
    recognizer = TextRecognition.getClient();
    mediaMetadataRetriever = new MediaMetadataRetriever();
    if (CameraFragment.Companion.getFilePath() != null)
      filePath.setText(CameraFragment.Companion.getFilePath());
    else Log.d(TAG_AUTHOR, "No FilePath was set in capture session.");

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
              Log.d(TAG_AUTHOR, "Analyse call failed with: " + e.getMessage());
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
    // TODO: Add on demand frame read opposed to read all frames.
    frameList = mediaMetadataRetriever.getFramesAtIndex(0, Math.max(0, totalFrames - SAFE_FRAMES));

    DownloadServerLogs downloadServerLogsTask = new DownloadServerLogs();
    downloadServerLogsTask.execute();

    AnalyseVideo ocrOnVideoTask = new AnalyseVideo();
    ocrOnVideoTask.execute();
  }

  private class DownloadServerLogs extends AsyncTask<Void, Void, Void> {

    @Override
    protected Void doInBackground(Void... values) {
      if (CameraActivity.Companion.getLaptopPort() == 0) {
        Log.d(TAG_AUTHOR, "No server to read input from. Check if server communication is working.");
      }
      try {
        Log.d(TAG_AUTHOR, "Sending request to send TimeStamps");
        CameraActivity.output.write("send timestamps" + "*");
        CameraActivity.output.flush();
        Log.d(TAG_AUTHOR, "Sent request to send TimeStamps");
        String message = CameraActivity.input.readLine();

        while (message != null) {
          serverTimestamp.add(Long.valueOf(message));
          Log.d(TAG_AUTHOR, "Server TimeStamp:" + message);
          message = CameraActivity.input.readLine();
        }
      } catch (IOException e) {
        Log.d(TAG_AUTHOR, "Server TimeStamp read failed");
      }
      return null;
    }
  }

  private class AnalyseVideo extends AsyncTask<Void, Void, Void> {
    @Override
    protected Void doInBackground(Void... values) {
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
                        setFrameTimeStamp();
                        framesProcessed++;
                        if (framesProcessed == frameList.size()) {
                          analyseResultField.append("OCR on all frames done\n");
                          calculateLag();
                        }
                      }
                    })
                .addOnFailureListener(
                    new OnFailureListener() {
                      @Override
                      public void onFailure(@NonNull Exception e) {
                        Log.d(TAG_AUTHOR, "Analyse failed with: " + e.getMessage());
                      }
                    });
      }
      return null;
    }
  }

  private void setFrameTimeStamp() {
    try {
      videoFrameTimestamp.add(recordStartTime + (framesProcessed * frameDuration));
    } catch (Exception e) {
      videoFrameTimestamp.add(0L);
      Log.d(TAG_AUTHOR, "Unable to set timeStamp of frame= " + framesProcessed);
      Log.d(TAG_AUTHOR, e.getMessage());
    }
  }

  private void calculateLag() {
    if (serverTimestamp.size() < 2 || videoFrameTimestamp.size() < 2) {
      Log.d(TAG_AUTHOR, "No results to show");
      return;
    }
    Log.d(
        "Show Results",
        "Video start timestamp:" + recordStartTime);
    //TODO: Server sequence is hardcoded. Add functionality to receive serverSequence.
    String serverSequence = "m";
    syncOffset = serverTimestamp.get(0) - CameraFragment.Companion.getRecordingStartMillis();
    Log.d("Show Results", "Sync offset:" + syncOffset);
    for (int j = 1; j < serverTimestamp.size(); j++) {
      for (int i = 0; i < resultsOCR.size(); i++) {
        if (serverSequence.equals(resultsOCR.get(i))) {
          analyseResultField.append(
              "Lag:" + (videoFrameTimestamp.get(i) - serverTimestamp.get(j) + syncOffset)
                  + " Server sequence:" + serverSequence + "\n");
          break;
        }
      }
      serverSequence += "m";
    }
  }
}
