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
  private TextView filePath;
  private TextView analyseResultField;
  private Button analyzeBtn;
  private Uri fileUri;

  private VideoProcessor videoProcessor;
  private LagCalculator lagCalculator;
  private ResultPublisher resultPublisher;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_analyser);

    filePath = findViewById(R.id.selectedFilePath);
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
    fileUri = Uri.parse(CameraFragment.Companion.getFilePath());

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

  /** Triggers subsequent lifecycle events of lag calculations. */
  @RequiresApi(api = Build.VERSION_CODES.P)
  private void analyze() {
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
