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

package com.example.android.camera2.slowmo.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.android.camera2.slowmo.R

private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO)

// TODO: Change fragment name in refactor commit. Waiting for merge of CameraActivity and AnalyserActivity.
/**
 * This [Fragment] requests permissions and, once granted, it will navigate to the next fragment
 * with the lowest camera settings available.
 */
class PermissionsFragment : Fragment() {

    private lateinit var lowestSetting: CameraInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cameraManager =
                requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraList = enumerateHighSpeedCameras(cameraManager)

        lowestSetting = findLowestCameraSetting(cameraList)

        if (hasPermissions(requireContext())) {
            // If permissions have already been granted, proceed
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    PermissionsFragmentDirections.actionPermissionsFragmentToCameraFragment(
                            lowestSetting.cameraId, lowestSetting.size.width, lowestSetting.size.height, lowestSetting.fps))
        } else {
            // Request camera-related permissions
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
    }

    /** Selects the lowest camera fps and the lowest image capture resolution available among them.*/
    private fun findLowestCameraSetting(cameraList: List<CameraInfo>): CameraInfo {
        var bestLowestSettingSeen = cameraList.get(0)
        for (setting in cameraList) {
            if (setting.fps < bestLowestSettingSeen.fps) {
                bestLowestSettingSeen = setting
            } else if (setting.fps == bestLowestSettingSeen.fps &&
                    setting.size.width < bestLowestSettingSeen.size.width) {
                bestLowestSettingSeen = setting
            } else if (setting.fps == bestLowestSettingSeen.fps &&
                    setting.size.width == bestLowestSettingSeen.size.width &&
                    setting.size.height < bestLowestSettingSeen.size.height) {
                bestLowestSettingSeen = setting
            }
        }
        return bestLowestSettingSeen
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Takes the user to the success fragment when permission is granted
                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                        PermissionsFragmentDirections.actionPermissionsFragmentToCameraFragment(lowestSetting.cameraId, lowestSetting.size.width, lowestSetting.size.height, lowestSetting.fps))
            } else {
                // TODO: Notify server of this failure for granting permissions.
                Toast.makeText(context, "Permission request denied. You must grant permissions for this app to function. Please restart the app and grant permissions.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        private data class CameraInfo(
                val title: String,
                val cameraId: String,
                val size: Size,
                val fps: Int)

        /** Converts a lens orientation enum into a human-readable string */
        private fun lensOrientationString(value: Int) = when (value) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        /** Lists all high speed cameras and supported resolution and FPS combinations */
        @SuppressLint("InlinedApi")
        private fun enumerateHighSpeedCameras(cameraManager: CameraManager): List<CameraInfo> {
            val availableCameras: MutableList<CameraInfo> = mutableListOf()

            // Iterate over the list of cameras and add those with high speed video recording
            //  capability to our output. This function only returns those cameras that declare
            //  constrained high speed video recording, but some cameras may be capable of doing
            //  unconstrained video recording with high enough FPS for some use cases and they will
            //  not necessarily declare constrained high speed video capability.
            cameraManager.cameraIdList.forEach { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val orientation = lensOrientationString(
                        characteristics.get(CameraCharacteristics.LENS_FACING)!!)

                // Query the available capabilities and output formats
                val capabilities = characteristics.get(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
                val cameraConfig = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

                // Return cameras that support constrained high video capability
                if (capabilities.contains(CameraCharacteristics
                                .REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
                    // For each camera, list its compatible sizes and FPS ranges
                    cameraConfig.highSpeedVideoSizes.forEach { size ->
                        cameraConfig.getHighSpeedVideoFpsRangesFor(size).forEach { fpsRange ->
                            val fps = fpsRange.upper
                            val info = CameraInfo(
                                    "$orientation ($id) $size $fps FPS", id, size, fps)

                            // Only report the highest FPS in the range, avoid duplicates
                            if (!availableCameras.contains(info)) availableCameras.add(info)
                        }
                    }
                }

            }

            return availableCameras
        }
    }
}
