/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.slowmo

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class CameraActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    internal lateinit var serverHandler: ServerHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        container = findViewById(R.id.fragment_container)

        val bundle = intent!!.extras
        val laptopPort = bundle?.getString("port")?.toInt() ?: 0
        if (laptopPort != 0) {
            serverHandler = ServerHandler(laptopPort)
            // TODO: Add retry or app fail if no connection established
            serverHandler.startConnection()
        } else {
            Log.d(ContentValues.TAG, "No port information received form server. Unable to establish connection.")
        }
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        container.postDelayed({
            container.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    internal fun analyze(fileUri: Uri) {
        val videoProcessor = VideoProcessor()
        val lagCalculator = LagCalculator()

        if (!videoProcessor.createVideoReader(applicationContext, fileUri)) {
            finish()
        }

        val serverTimestamps = serverHandler.downloadServerTimeStamps()

        val resultsOcr = videoProcessor.doOcr()

        lagCalculator.calculateLag(serverTimestamps, videoProcessor.videoFrameTimestamp, resultsOcr, serverHandler.getSyncOffset())
    }

    companion object {
        /** Combination of all flags required to put activity into immersive mode */
        const val FLAGS_FULLSCREEN =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        /** Milliseconds used for UI animations */
        const val ANIMATION_FAST_MILLIS = 50L
        const val ANIMATION_SLOW_MILLIS = 100L
        private const val IMMERSIVE_FLAG_TIMEOUT = 500L
    }
}
