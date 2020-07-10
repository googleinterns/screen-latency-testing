package com.example.android.camera2.slowmo;

import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
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
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private Integer totalFrames = 0;
  private final Integer SAFE_FRAMES = 10;
  private final Integer FILE_PICKER_REQUEST_CODE = 10;

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

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    filePath = findViewById(R.id.selectedFilePath);
    filePickerBtn = findViewById(R.id.fileSelectButton);
    analyzeBtn = findViewById(R.id.analyze);
    analyseResultField = findViewById(R.id.analyseResultView);

    analyseResultField.setMovementMethod(new ScrollingMovementMethod());
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
}
