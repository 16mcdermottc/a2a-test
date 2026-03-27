"""End-to-end test for the A2A agent network."""
import requests
import json
import sys

AGENTS = [
    ("Python Agent",  "http://localhost:8001"),
    ("Node.js Agent", "http://localhost:8002"),
    ("Go Agent",      "http://localhost:8003"),
    ("Java Agent",    "http://localhost:8004"),
    ("Kotlin Agent",  "http://localhost:8005"),
]


def test_agent_card(name, base_url):
    print(f"\n--- {name}: Agent Card ---")
    r = requests.get(f"{base_url}/.well-known/agent.json")
    assert r.status_code == 200, f"Agent card failed: {r.status_code}"
    card = r.json()
    assert "name" in card, "Missing 'name' in Agent Card"
    assert "skills" in card, "Missing 'skills' in Agent Card"
    assert "supportedInterfaces" in card, "Missing 'supportedInterfaces'"
    print(f"  Name: {card['name']}")
    print(f"  Skills: {[s['id'] for s in card['skills']]}")
    return card


def test_send_message(name, base_url, text="Hello from test"):
    print(f"\n--- {name}: SendMessage ---")
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "SendMessage",
        "params": {
            "message": {
                "messageId": "test-msg-001",
                "role": "user",
                "parts": [{"text": text}]
            }
        }
    }
    r = requests.post(base_url, json=payload)
    assert r.status_code == 200, f"SendMessage failed: {r.status_code}"
    data = r.json()
    assert "result" in data, f"No result in response: {data}"
    task = data["result"]["task"]
    assert task["status"]["state"] == "completed", f"Unexpected state: {task['status']['state']}"
    print(f"  Task ID: {task['id']}")
    print(f"  State: {task['status']['state']}")
    print(f"  Artifacts: {len(task.get('artifacts', []))}")
    return task


def test_get_task(name, base_url, task_id):
    print(f"\n--- {name}: GetTask ---")
    payload = {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "GetTask",
        "params": {"id": task_id}
    }
    r = requests.post(base_url, json=payload)
    assert r.status_code == 200
    data = r.json()
    assert "result" in data, f"GetTask failed: {data}"
    print(f"  Retrieved task {task_id}: {data['result']['task']['status']['state']}")


def main():
    print("=" * 60)
    print("A2A Agent Network End-to-End Test")
    print("=" * 60)

    errors = []
    for name, url in AGENTS:
        try:
            test_agent_card(name, url)
            task = test_send_message(name, url, "Test data for analysis")
            test_get_task(name, url, task["id"])
        except Exception as e:
            errors.append(f"{name}: {e}")
            print(f"  ERROR: {e}")

    print("\n" + "=" * 60)
    if errors:
        print(f"FAILED - {len(errors)} agent(s) had errors:")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    else:
        print("ALL TESTS PASSED")


if __name__ == "__main__":
    main()
