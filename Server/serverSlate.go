package main

import (
	"bufio"
	"bytes"
	"fmt"
	"net"
	"os/exec"
	"strconv"
	"time"

	"github.com/go-vgo/robotgo"
)

const KEY = "m"
const KEY_PRESS_COUNT = 10

func main() {
	fmt.Println("Start server...")

	ln, _ := net.Listen("tcp", "127.0.0.1:")
	_, serverPort, _ := net.SplitHostPort(ln.Addr().String())
	fmt.Println("Listening on address:")
	fmt.Println(ln.Addr())

	openAppCommand := exec.Command("adb", "shell", "am", "start", "-n",
		"com.android.example.camera2.slowmo/com.example.android.camera2.slowmo.CameraActivity",
		"--es", "port "+serverPort)
	runOsCommand(openAppCommand)

	portForwardingCommand := exec.Command("adb", "reverse", "tcp:"+serverPort, "tcp:"+serverPort)
	runOsCommand(portForwardingCommand)

	// accept connection
	conn, _ := ln.Accept()
	fmt.Println("Got connection from:")
	fmt.Println(conn.LocalAddr().String())

	hostReader := bufio.NewReader(conn)
	timeStamps := make([]string, KEY_PRESS_COUNT+1)
	keyPressSequence := make([]string, KEY_PRESS_COUNT+1)

	message, _ := hostReader.ReadString('*') // using * as delim and hardcoded in app to append * at end of message
	fmt.Println(message)
	if message == "started capture"+"*" {
		simulateKeyPress(KEY, KEY_PRESS_COUNT, timeStamps, keyPressSequence)
	}
	fmt.Println("Key simulation ended")

	message, _ = hostReader.ReadString('*') // using * as delim and hardcoded in app to append * at end of message
	fmt.Println(message)
	if message == "send timestamps"+"*" {
		sendTimeStamps(conn, timeStamps, keyPressSequence)
	}
}

func runOsCommand(cmd *exec.Cmd) int {
	var out bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &stderr
	err := cmd.Run()
	if err != nil {
		fmt.Println(fmt.Sprint(err) + ": " + stderr.String())
		return 0
	}
	fmt.Println("Result: " + out.String())
	return 1
}

func simulateKeyPress(key string, keyPressCount int, timeStamps []string, keyPressSequence []string) {
	timeStamps[0] = strconv.Itoa(int(time.Now().UnixNano() / 1000000))
	time.Sleep(1 * time.Second)
	runningSeq := ""
	keyPressSequence[0] = runningSeq
	for i := 1; i <= keyPressCount; i++ {
		robotgo.TypeStr(key)
		runningSeq += key
		keyPressSequence[i] = runningSeq
		timeStamps[i] = strconv.Itoa(int(time.Now().UnixNano() / 1000000))
		time.Sleep(100 * time.Millisecond)
	}
}

func sendTimeStamps(conn net.Conn, timeStamps []string, keyPressSequence []string) {
	for i := 0; i < len(timeStamps); i++ {
		fmt.Fprint(conn, timeStamps[i]+"\n")
		fmt.Fprint(conn, keyPressSequence[i]+"\n")
	}
}
