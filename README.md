# A2A Agent Network Prototype

A multi-language implementation of the [Agent-to-Agent (A2A) Protocol](https://a2a-protocol.org/latest/specification/) with 5 agents communicating via JSON-RPC 2.0.

## Agents

| Agent | Language | Port | Skill |
|---|---|---|---|
| Data Processor | Python (FastAPI) | 8001 | Text analysis |
| Coordinator | Node.js (Express) | 8002 | Orchestrates other agents |
| Task Executor | Go (net/http) | 8003 | Computational tasks |
| Enterprise Processor | Java (Spring Boot) | 8004 | Data validation |
| Analytics Engine | Kotlin (Ktor) | 8005 | Text analytics |

## Quick Start

```bash
docker-compose up --build
```

## Test

```bash
pip install requests
python test_network.py
```

## A2A Protocol Endpoints

Each agent exposes:
- `GET /.well-known/agent.json` — Agent Card (discovery)
- `POST /` — JSON-RPC 2.0 (`SendMessage`, `GetTask`, `CancelTask`)

## Architecture

All agents implement the A2A protocol specification:
- **Agent Cards** for discovery at `/.well-known/agent.json`
- **JSON-RPC 2.0** message envelope
- **Task lifecycle**: `submitted → working → completed | failed | canceled`
- **Cross-agent communication**: The Node.js Coordinator fans out work to all other agents via A2A

## Manual Testing

Send a message to any agent:

```bash
curl -X POST http://localhost:8001 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "test-001",
        "role": "user",
        "parts": [{"text": "Hello from curl"}]
      }
    }
  }'
```

Discover an agent:

```bash
curl http://localhost:8001/.well-known/agent.json
```
