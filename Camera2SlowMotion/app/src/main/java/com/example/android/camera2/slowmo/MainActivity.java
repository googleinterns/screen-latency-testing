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
  private final Integer SAFE_FRAMES = 10;
  private final Integer FILE_PICKER_REQUEST_CODE = 10;
  private String tag = "Rokus Logs:";

  private TextView filePath;
  private TextView analyseResultField;
  private Button filePickerBtn;
  private Button analyzeBtn;
  private Intent fileIntent;
  private MediaMetadataRetriever mediaMetadataRetriever;
  private Uri fileUri;
  private InputImage imageHolder;
  private TextRecognizer recognizer;
  private ArrayList<String> results = new ArrayList<String>();
  private ArrayList<String> serverCache = new ArrayList<String>();
  ArrayList<Integer> deltaServer = new ArrayList<>();

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
    List<Bitmap> frameList =
        mediaMetadataRetriever.getFramesAtIndex(0, Math.max(0, totalFrames - SAFE_FRAMES));

    AsyncTaskServer asyncTaskServer = new AsyncTaskServer();
    asyncTaskServer.execute();

    for (int i = 0; i < frameList.size(); i++) {
      // analysis will be done here (forking in a separate thread)
      imageHolder = InputImage.fromBitmap(frameList.get(i), 0);
      final int finalI = i;
      Task<Text> result =
          recognizer
              .process(imageHolder)
              .addOnSuccessListener(
                  new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text visionText) {
                      results.add(visionText.getText());
                      analyseResultField.append(
                          "\n\nText detected at index:" + finalI + " " + visionText.getText());
                      Log.d(
                          "Rokus logs",
                          "Text detected at index:" + finalI + " " + visionText.getText());
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
  }

  private class AsyncTaskServer extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... strings) {
      Log.d(tag, "Sending request to send cache");
      CameraActivity.output.write("send cache" + "*");
      CameraActivity.output.flush();
      Log.d(tag, "Sent request to send cache");
      try {
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
}
