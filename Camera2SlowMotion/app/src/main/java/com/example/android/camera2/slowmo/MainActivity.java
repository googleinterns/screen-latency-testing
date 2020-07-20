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
  private ArrayList<Integer> deltaServer = new ArrayList<>();
  private List<Bitmap> frameList = new ArrayList<>();
  private ArrayList<Integer> deltaOCR = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    filePath = findViewById(R.id.selectedFilePath);
    filePickerBtn = findViewById(R.id.fileSelectButton);
    analyzeBtn = findViewById(R.id.analyze);
    analyseResultField = findViewById(R.id.analyseResultView);

    analyseResultField.setMovementMethod(new ScrollingMovementMethod());
    analyseResultField.append("\nPort bound:" + CameraActivity.Companion.getLaptopPort());
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
      if(CameraActivity.Companion.getLaptopPort()==0)
      {
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
          if (message != null) serverCache.add(message);
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
        Log.d(tag, String.valueOf(e.getCause()));
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
                        if(framesProcessed == frameList.size()){
                          parseOCR();
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
      String last = resultsOCR.get(0);
      Integer recordStartTime =
          convertStringTimeToInt(CameraFragment.Companion.getRecordingStartTime());
      deltaOCR.add(recordStartTime);
      Integer offset = 1000 / fps;
      for (int i = 1; i < resultsOCR.size(); i++) {
        if (!last.equals(resultsOCR.get(i))) {
          last = resultsOCR.get(i);
          deltaOCR.add(recordStartTime + (i * offset));
          Log.d(tag, "In parseOCR:" + i + "  " + (recordStartTime + (i * offset)));
        }
      }
    } catch (Exception e) {
      Log.d("Rokus Logs:", "Parsing OCR failed with: " + e.getMessage());
    }
  }

  // Parse timestamps received from server. Last 12 characters of time is important here.
  private void parseServer() {
    Integer length = serverCache.get(0).length() - 12;
    for (int i = 0; i < serverCache.size(); i++) {
      String tmp = serverCache.get(i).substring(length);
      deltaServer.add(convertStringTimeToInt(tmp));
      Log.d(tag, "ParseServer:" + deltaServer.get(deltaServer.size() - 1));
    }
  }

  // Converts time in String format hh:mm:ss.MsMsMs (Eg: 08:47:56.637) to Integer milliseconds
  private Integer convertStringTimeToInt(String currentTime) {
    Integer result = 0;
    result += (Integer.parseInt(currentTime.substring(0, 2))) * 3600000;
    result += (Integer.parseInt(currentTime.substring(3, 5))) * 60000;
    result += (Integer.parseInt(currentTime.substring(6, 8))) * 1000;
    result += (Integer.parseInt(currentTime.substring(9, 12)));
    return result;
  }
}
