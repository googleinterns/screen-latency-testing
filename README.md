# Screen Latency Automated Testing (SLATE)

**This is not an officially supported Google product**

This is a 2 part software that helps to measure typing latency on display devices.

In a more elaborate sense, it measures how much time it takes a key you typed to appear on your screen. As the latency can vary with the use of a different keyboard, this software carries the measurement only from the point operating system gets to know a key-press input is received, as that measurement will be keyboard independent.

## Install instructions
1. Clone the repository or download it from the source.

   `git clone https://github.com/googleinterns/screen-latency-testing.git`

2. Import the `Camera2SlowMotion` android app in Android Studio.

3. Install the app on your smartphone from Android Studio. (App will not run without step 5)

4. Build the server script.

   `go build Server/serverSlate.go`

5. Enable USB-debugging mode on your phone from Developer options. Used to communicate with phone during testing.

6. Connect your smartphone to your laptop/pc/testing-device using a USB cable and run the server script on your laptop/pc/testing-device.

   `go run Server/serverSlate.go`

   The app will be opened on your phone automatically.

7. Open a text editor on your laptop/pc/testing device and place the cursor somewhere in the editor. Make sure the editor is in the camera field of view of the opened app.

8. Press and hold the capture button for some time. The text will be typed/simulated automatically on the editor.

9. Wait for the lag to be calculated and displayed on the screen.

## Demo
![Demo](slate-demo.gif)

## Internal Working

1. The server script finds the connected android device and opens the target app.

2. Establishes a socket communication between both ends and finds the drift between the clock of android and laptop device. Used in synchronization mechanism.

3. On press and hold of the capture button on the app, a key simulation signal is sent to the laptop. The laptop in turn simulates the key-press event on the text editor.

4. A high-speed recording is also started on the press of the capture button.

5. OCR is performed after the capture session on each video frame in the recorded video.

6. The OCR data is sent back to the server for analysis.

7. For every new character, the server finds the video frame in which it first appeared.

8. The timestamp of key-press on the server and timestamp of character appearance found from OCR data give the lag. Drift in the clock is used to offset timestamps here.

## Motivating use case

This app will be used to measure typing latency on ChromeOS devices.
