import uuid
import time
import logging
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

app = FastAPI()
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("python-agent")

# In-memory task store
tasks = {}

AGENT_CARD = {
    "name": "Python Data Processor",
    "description": "Processes and analyzes text data using Python.",
    "supportedInterfaces": [
        {
            "url": "http://python-agent:8001",
            "protocolBinding": "JSONRPC",
            "protocolVersion": "1.0"
        }
    ],
    "provider": {"organization": "A2A Test Network"},
    "version": "1.0.0",
    "capabilities": {"streaming": False, "pushNotifications": False},
    "defaultInputModes": ["text/plain", "application/json"],
    "defaultOutputModes": ["application/json"],
    "skills": [
        {
            "id": "data-processor",
            "name": "Data Processor",
            "description": "Analyzes text data and returns word count, character count, and summary.",
            "tags": ["data", "analysis", "text"],
            "examples": ["Analyze this text: Hello World"],
            "inputModes": ["text/plain"],
            "outputModes": ["application/json"]
        }
    ]
}


def process_data(text: str) -> dict:
    """Simulate data processing."""
    words = text.split()
    return {
        "wordCount": len(words),
        "charCount": len(text),
        "upperCase": text.upper(),
        "agent": "python-data-processor"
    }


def handle_send_message(params: dict) -> dict:
    message = params.get("message", {})
    parts = message.get("parts", [])
    text_content = ""
    for part in parts:
        if "text" in part:
            text_content += part["text"]

    task_id = str(uuid.uuid4())
    context_id = message.get("contextId", str(uuid.uuid4()))

    task = {
        "id": task_id,
        "contextId": context_id,
        "status": {"state": "submitted", "timestamp": time.time()},
        "artifacts": [],
        "history": [message]
    }
    tasks[task_id] = task

    # Process immediately (synchronous for prototype)
    task["status"] = {"state": "working", "timestamp": time.time()}
    result = process_data(text_content)
    task["artifacts"] = [{
        "artifactId": str(uuid.uuid4()),
        "name": "Data Analysis Result",
        "parts": [{"data": result, "mediaType": "application/json"}]
    }]
    task["status"] = {"state": "completed", "timestamp": time.time()}

    logger.info(f"Task {task_id} completed")
    return {"task": task}


def handle_get_task(params: dict) -> dict:
    task_id = params.get("id")
    if task_id not in tasks:
        return None
    return {"task": tasks[task_id]}


def handle_cancel_task(params: dict) -> dict:
    task_id = params.get("id")
    if task_id in tasks:
        tasks[task_id]["status"] = {"state": "canceled", "timestamp": time.time()}
        return {"task": tasks[task_id]}
    return None


@app.get("/.well-known/agent.json")
def agent_card():
    return AGENT_CARD


@app.post("/")
async def jsonrpc_handler(request: Request):
    body = await request.json()
    method = body.get("method")
    params = body.get("params", {})
    rpc_id = body.get("id")

    if method == "SendMessage":
        result = handle_send_message(params)
    elif method == "GetTask":
        result = handle_get_task(params)
    elif method == "CancelTask":
        result = handle_cancel_task(params)
    else:
        return JSONResponse({"jsonrpc": "2.0", "id": rpc_id, "error": {
            "code": -32601, "message": f"Method not found: {method}"
        }})

    if result is None:
        return JSONResponse({"jsonrpc": "2.0", "id": rpc_id, "error": {
            "code": -32002, "message": "Task not found"
        }})

    return JSONResponse({"jsonrpc": "2.0", "id": rpc_id, "result": result})
