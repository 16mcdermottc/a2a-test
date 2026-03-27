package com.a2a.kotlinagent

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

val tasks = ConcurrentHashMap<String, JsonObject>()

val agentCard = buildJsonObject {
    put("name", "Kotlin Analytics Engine")
    put("description", "Coroutine-based analytics and aggregation agent.")
    putJsonArray("supportedInterfaces") {
        addJsonObject {
            put("url", "http://kotlin-agent:8005")
            put("protocolBinding", "JSONRPC")
            put("protocolVersion", "1.0")
        }
    }
    putJsonObject("provider") { put("organization", "A2A Test Network") }
    put("version", "1.0.0")
    putJsonObject("capabilities") {
        put("streaming", false)
        put("pushNotifications", false)
    }
    putJsonArray("defaultInputModes") { add("text/plain"); add("application/json") }
    putJsonArray("defaultOutputModes") { add("application/json") }
    putJsonArray("skills") {
        addJsonObject {
            put("id", "analytics-engine")
            put("name", "Analytics Engine")
            put("description", "Performs text analytics including sentiment approximation and statistics.")
            putJsonArray("tags") { add("analytics"); add("statistics"); add("aggregation") }
            putJsonArray("examples") { add("Analyze: Hello World") }
            putJsonArray("inputModes") { add("text/plain") }
            putJsonArray("outputModes") { add("application/json") }
        }
    }
}

fun analyzeText(text: String): JsonObject = buildJsonObject {
    val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    put("wordCount", words.size)
    put("charCount", text.length)
    put("uniqueWords", words.map { it.lowercase() }.distinct().size)
    put("avgWordLength", if (words.isEmpty()) 0.0 else words.map { it.length }.average())
    put("agent", "kotlin-analytics-engine")
}

fun handleSendMessage(params: JsonObject): JsonObject {
    val message = params["message"]?.jsonObject ?: buildJsonObject {}
    val parts = message["parts"]?.jsonArray ?: buildJsonArray {}
    val textContent = parts.mapNotNull {
        it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
    }.joinToString("")

    val taskId = UUID.randomUUID().toString()
    val contextId = UUID.randomUUID().toString()
    val result = analyzeText(textContent)

    val task = buildJsonObject {
        put("id", taskId)
        put("contextId", contextId)
        putJsonObject("status") {
            put("state", "completed")
            put("timestamp", System.currentTimeMillis())
        }
        putJsonArray("artifacts") {
            addJsonObject {
                put("artifactId", UUID.randomUUID().toString())
                put("name", "Analytics Result")
                putJsonArray("parts") {
                    addJsonObject {
                        put("data", result)
                        put("mediaType", "application/json")
                    }
                }
            }
        }
        putJsonArray("history") { add(message) }
    }

    tasks[taskId] = task
    println("Task $taskId completed")
    return buildJsonObject { put("task", task) }
}

fun handleGetTask(params: JsonObject): JsonObject? {
    val id = params["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val task = tasks[id] ?: return null
    return buildJsonObject { put("task", task) }
}

fun handleCancelTask(params: JsonObject): JsonObject? {
    val id = params["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val task = tasks[id] ?: return null
    val canceled = buildJsonObject {
        task.entries.forEach { (k, v) ->
            if (k == "status") {
                putJsonObject("status") {
                    put("state", "canceled")
                    put("timestamp", System.currentTimeMillis())
                }
            } else {
                put(k, v)
            }
        }
    }
    tasks[id] = canceled
    return buildJsonObject { put("task", canceled) }
}

fun main() {
    embeddedServer(Netty, port = 8005, host = "0.0.0.0") {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        routing {
            get("/.well-known/agent.json") {
                call.respond(agentCard)
            }

            post("/") {
                val body = call.receive<JsonObject>()
                val method = body["method"]?.jsonPrimitive?.contentOrNull
                val params = body["params"]?.jsonObject ?: buildJsonObject {}
                val rpcId = body["id"]

                val response = when (method) {
                    "SendMessage" -> buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", rpcId ?: JsonNull)
                        put("result", handleSendMessage(params))
                    }
                    "GetTask" -> {
                        val r = handleGetTask(params)
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", rpcId ?: JsonNull)
                            if (r != null) put("result", r)
                            else putJsonObject("error") {
                                put("code", -32002)
                                put("message", "Task not found")
                            }
                        }
                    }
                    "CancelTask" -> {
                        val r = handleCancelTask(params)
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", rpcId ?: JsonNull)
                            if (r != null) put("result", r)
                            else putJsonObject("error") {
                                put("code", -32002)
                                put("message", "Task not found")
                            }
                        }
                    }
                    else -> buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", rpcId ?: JsonNull)
                        putJsonObject("error") {
                            put("code", -32601)
                            put("message", "Method not found: $method")
                        }
                    }
                }
                call.respond(response)
            }
        }
    }.start(wait = true)
}
