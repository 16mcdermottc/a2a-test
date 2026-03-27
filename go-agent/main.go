package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"
)

type Part struct {
	Text      string      `json:"text,omitempty"`
	Data      interface{} `json:"data,omitempty"`
	MediaType string      `json:"mediaType,omitempty"`
}

type Message struct {
	MessageID string `json:"messageId"`
	Role      string `json:"role"`
	Parts     []Part `json:"parts"`
	ContextID string `json:"contextId,omitempty"`
	TaskID    string `json:"taskId,omitempty"`
}

type TaskStatus struct {
	State     string  `json:"state"`
	Timestamp float64 `json:"timestamp"`
}

type Artifact struct {
	ArtifactID string `json:"artifactId"`
	Name       string `json:"name"`
	Parts      []Part `json:"parts"`
}

type Task struct {
	ID        string     `json:"id"`
	ContextID string     `json:"contextId"`
	Status    TaskStatus `json:"status"`
	Artifacts []Artifact `json:"artifacts"`
	History   []Message  `json:"history"`
}

type JSONRPCRequest struct {
	JSONRPC string                 `json:"jsonrpc"`
	ID      interface{}            `json:"id"`
	Method  string                 `json:"method"`
	Params  map[string]interface{} `json:"params"`
}

type JSONRPCResponse struct {
	JSONRPC string      `json:"jsonrpc"`
	ID      interface{} `json:"id"`
	Result  interface{} `json:"result,omitempty"`
	Error   interface{} `json:"error,omitempty"`
}

var (
	tasks   = make(map[string]*Task)
	tasksMu sync.RWMutex
	idCount int64
	idMu    sync.Mutex
)

var agentCard = map[string]interface{}{
	"name":        "Go Task Executor",
	"description": "High-performance task execution agent built in Go.",
	"supportedInterfaces": []map[string]string{{
		"url":             "http://go-agent:8003",
		"protocolBinding": "JSONRPC",
		"protocolVersion": "1.0",
	}},
	"provider":           map[string]string{"organization": "A2A Test Network"},
	"version":            "1.0.0",
	"capabilities":       map[string]bool{"streaming": false, "pushNotifications": false},
	"defaultInputModes":  []string{"text/plain", "application/json"},
	"defaultOutputModes": []string{"application/json"},
	"skills": []map[string]interface{}{{
		"id":          "task-executor",
		"name":        "Task Executor",
		"description": "Executes computational tasks with high concurrency.",
		"tags":        []string{"compute", "execution", "performance"},
		"examples":    []string{"Execute computation on: Hello World"},
		"inputModes":  []string{"text/plain"},
		"outputModes": []string{"application/json"},
	}},
}

func generateID() string {
	idMu.Lock()
	idCount++
	id := idCount
	idMu.Unlock()
	return fmt.Sprintf("go-task-%d-%d", time.Now().UnixNano(), id)
}

func executeTask(text string) map[string]interface{} {
	return map[string]interface{}{
		"reversed":  reverse(text),
		"length":    len(text),
		"processed": true,
		"agent":     "go-task-executor",
	}
}

func reverse(s string) string {
	runes := []rune(s)
	for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
		runes[i], runes[j] = runes[j], runes[i]
	}
	return string(runes)
}

func handleSendMessage(params map[string]interface{}) interface{} {
	msgRaw, _ := params["message"]
	msgMap, _ := msgRaw.(map[string]interface{})
	partsRaw, _ := msgMap["parts"].([]interface{})

	textContent := ""
	for _, p := range partsRaw {
		part, _ := p.(map[string]interface{})
		if t, ok := part["text"].(string); ok {
			textContent += t
		}
	}

	taskID := generateID()
	contextID := generateID()

	task := &Task{
		ID:        taskID,
		ContextID: contextID,
		Status:    TaskStatus{State: "working", Timestamp: float64(time.Now().Unix())},
		History:   []Message{},
	}

	result := executeTask(textContent)
	task.Artifacts = []Artifact{{
		ArtifactID: generateID(),
		Name:       "Execution Result",
		Parts:      []Part{{Data: result, MediaType: "application/json"}},
	}}
	task.Status = TaskStatus{State: "completed", Timestamp: float64(time.Now().Unix())}

	tasksMu.Lock()
	tasks[taskID] = task
	tasksMu.Unlock()

	log.Printf("Task %s completed", taskID)
	return map[string]interface{}{"task": task}
}

func handleGetTask(params map[string]interface{}) interface{} {
	id, _ := params["id"].(string)
	tasksMu.RLock()
	task, ok := tasks[id]
	tasksMu.RUnlock()
	if !ok {
		return nil
	}
	return map[string]interface{}{"task": task}
}

func handleCancelTask(params map[string]interface{}) interface{} {
	id, _ := params["id"].(string)
	tasksMu.Lock()
	task, ok := tasks[id]
	if ok {
		task.Status = TaskStatus{State: "canceled", Timestamp: float64(time.Now().Unix())}
	}
	tasksMu.Unlock()
	if !ok {
		return nil
	}
	return map[string]interface{}{"task": task}
}

func agentCardHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(agentCard)
}

func jsonrpcHandler(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path == "/.well-known/agent.json" {
		agentCardHandler(w, r)
		return
	}

	var req JSONRPCRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(JSONRPCResponse{
			JSONRPC: "2.0", ID: nil,
			Error: map[string]interface{}{"code": -32700, "message": "Parse error"},
		})
		return
	}

	var result interface{}
	switch req.Method {
	case "SendMessage":
		result = handleSendMessage(req.Params)
	case "GetTask":
		result = handleGetTask(req.Params)
	case "CancelTask":
		result = handleCancelTask(req.Params)
	default:
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(JSONRPCResponse{
			JSONRPC: "2.0", ID: req.ID,
			Error: map[string]interface{}{"code": -32601, "message": "Method not found"},
		})
		return
	}

	if result == nil {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(JSONRPCResponse{
			JSONRPC: "2.0", ID: req.ID,
			Error: map[string]interface{}{"code": -32002, "message": "Task not found"},
		})
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(JSONRPCResponse{JSONRPC: "2.0", ID: req.ID, Result: result})
}

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/.well-known/agent.json", agentCardHandler)
	mux.HandleFunc("/", jsonrpcHandler)
	log.Println("Go Task Executor agent on port 8003")
	log.Fatal(http.ListenAndServe(":8003", mux))
}
