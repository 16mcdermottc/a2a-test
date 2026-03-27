package com.a2a.javaagent;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class A2AController {

    private final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();

    private Map<String, Object> getAgentCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", "Java Enterprise Processor");
        card.put("description", "Enterprise-grade data validation and transformation agent.");
        card.put("supportedInterfaces", List.of(Map.of(
            "url", "http://java-agent:8004",
            "protocolBinding", "JSONRPC",
            "protocolVersion", "1.0"
        )));
        card.put("provider", Map.of("organization", "A2A Test Network"));
        card.put("version", "1.0.0");
        card.put("capabilities", Map.of("streaming", false, "pushNotifications", false));
        card.put("defaultInputModes", List.of("text/plain", "application/json"));
        card.put("defaultOutputModes", List.of("application/json"));
        card.put("skills", List.of(Map.of(
            "id", "enterprise-processor",
            "name", "Enterprise Data Processor",
            "description", "Validates and transforms structured data with enterprise rules.",
            "tags", List.of("enterprise", "validation", "transformation"),
            "examples", List.of("Process enterprise data: Hello World"),
            "inputModes", List.of("text/plain"),
            "outputModes", List.of("application/json")
        )));
        return card;
    }

    @GetMapping("/.well-known/agent.json")
    public Map<String, Object> agentCard() {
        return getAgentCard();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/")
    public Map<String, Object> jsonrpc(@RequestBody Map<String, Object> body) {
        String method = (String) body.get("method");
        Object rpcId = body.get("id");
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", rpcId);

        switch (method) {
            case "SendMessage" -> response.put("result", handleSendMessage(params));
            case "GetTask" -> {
                var result = handleGetTask(params);
                if (result == null) {
                    response.put("error", Map.of("code", -32002, "message", "Task not found"));
                } else {
                    response.put("result", result);
                }
            }
            case "CancelTask" -> {
                var result = handleCancelTask(params);
                if (result == null) {
                    response.put("error", Map.of("code", -32002, "message", "Task not found"));
                } else {
                    response.put("result", result);
                }
            }
            default -> response.put("error", Map.of("code", -32601, "message", "Method not found: " + method));
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleSendMessage(Map<String, Object> params) {
        Map<String, Object> message = (Map<String, Object>) params.getOrDefault("message", Map.of());
        List<Map<String, Object>> parts = (List<Map<String, Object>>) message.getOrDefault("parts", List.of());

        StringBuilder text = new StringBuilder();
        for (var part : parts) {
            if (part.containsKey("text")) {
                text.append(part.get("text"));
            }
        }

        String taskId = UUID.randomUUID().toString();
        String contextId = UUID.randomUUID().toString();

        // Process: validate and transform
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("original", text.toString());
        result.put("validated", true);
        result.put("trimmed", text.toString().trim());
        result.put("lowercase", text.toString().toLowerCase());
        result.put("agent", "java-enterprise-processor");

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", taskId);
        task.put("contextId", contextId);
        task.put("status", Map.of("state", "completed", "timestamp", System.currentTimeMillis()));
        task.put("artifacts", List.of(Map.of(
            "artifactId", UUID.randomUUID().toString(),
            "name", "Enterprise Processing Result",
            "parts", List.of(Map.of("data", result, "mediaType", "application/json"))
        )));
        task.put("history", List.of(message));

        tasks.put(taskId, task);
        System.out.println("Task " + taskId + " completed");
        return Map.of("task", task);
    }

    private Map<String, Object> handleGetTask(Map<String, Object> params) {
        String id = (String) params.get("id");
        var task = tasks.get(id);
        return task != null ? Map.of("task", task) : null;
    }

    private Map<String, Object> handleCancelTask(Map<String, Object> params) {
        String id = (String) params.get("id");
        var task = tasks.get(id);
        if (task != null) {
            task.put("status", Map.of("state", "canceled", "timestamp", System.currentTimeMillis()));
            return Map.of("task", task);
        }
        return null;
    }
}
