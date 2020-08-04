package com.example.android.camera2.slowmo;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.example.android.camera2.slowmo.fragments.CameraFragment;

/** This class handles the lifecycle of lag calculation. */
public class AnalyserActivity extends AppCompatActivity {
  private final Integer FILE_PICKER_REQUEST_CODE = 10;

  private TextView filePath;
  private TextView analyseResultField;
  private Button filePickerBtn;
  private Button analyzeBtn;
  private Intent fileIntent;
  private Uri fileUri;

  private VideoProcessor videoProcessor;
  private LagCalculator lagCalculator;
  private ResultPublisher resultPublisher;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_analyser);

    filePath = findViewById(R.id.selectedFilePath);
    filePickerBtn = findViewById(R.id.fileSelectButton);
    analyzeBtn = findViewById(R.id.analyze);
    analyseResultField = findViewById(R.id.analyseResultView);

    analyseResultField.setMovementMethod(new ScrollingMovementMethod());

    videoProcessor = new VideoProcessor();

    lagCalculator = new LagCalculator(videoProcessor, CameraActivity.serverHandler);

    resultPublisher = new ResultPublisher();

    analyseResultField.append(
        "\nPort bound:" + CameraActivity.serverHandler.getServerSocketPort() + "\n");

    CameraFragment.Companion.getFilePath();
    filePath.setText(CameraFragment.Companion.getFilePath());

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
              Log.d(ContentValues.TAG, "Analyse call failed with: " + e.getMessage());
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

  /** Triggers subsequent lifecycle events of lag calculations. */
  @RequiresApi(api = Build.VERSION_CODES.P)
  private void analyze() {
    fileUri = Uri.parse(filePath.getText().toString());
    videoProcessor.setVideoReader(getApplicationContext(), fileUri);

    CameraActivity.serverHandler.downloadServerTimeStamps();

    videoProcessor.doOcrOnVideo(lagCalculator);

    resultPublisher.execute(lagCalculator);
  }

  /** A background threads which listens for the results of LagCalculator and shows the results
   * when available to TextView.*/
  private class ResultPublisher extends AsyncTask<LagCalculator, Void, Void> {
    private Integer resultPublishedCount = 0;

    @Override
    protected Void doInBackground(LagCalculator... lagCalculators) {
      while (true) {
        if (resultPublishedCount < lagCalculators[0].lagResult.size()) {
          analyseResultField.append(
              "Char = "
                  + (resultPublishedCount + 1)
                  + "\tLag = "
                  + lagCalculators[0].lagResult.get(resultPublishedCount)
                  + "ms\n");
          resultPublishedCount++;
        }
      }
    }
  }
}
