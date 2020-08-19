package main

import (
	"encoding/json"
	"io/ioutil"
	"testing"
)

type LagTestData struct {
	OcrData            hostData `json:"ocrData"`
	TestStartTimestamp int64    `json:"testStartTimestamp"`
	CameraStartupDelay int64    `json:"cameraStartupDelay"`
	KeyPressTimestamp  []int64  `json:"keyPressTimestamp"`
	ExpectedLag        []int64  `json:"expectedLag"`
}

func TestCalculateLag(t *testing.T) {
	file, err := ioutil.ReadFile("calculateLag_test_data.json")
	if err != nil {
		t.Fatal("Unable to read ocr-data.json:", err)
	}
	var lagTestData LagTestData
	err = json.Unmarshal([]byte(file), &lagTestData)

	if err != nil {
		t.Fatal("Unable to read ocr-data.json:", err)
	}

	lagResultCalculated := calculateLag(lagTestData.OcrData, lagTestData.TestStartTimestamp, lagTestData.KeyPressTimestamp, lagTestData.CameraStartupDelay)

	for i := 0; i < 10; i++ {
		if lagTestData.ExpectedLag[i] != lagResultCalculated[i] {
			t.Errorf("Lag result incorrect, got: %d, want: %d", lagResultCalculated[i], lagTestData.ExpectedLag[i])
		}
	}
}
