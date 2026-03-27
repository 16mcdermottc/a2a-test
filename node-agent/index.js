const express = require('express');
const axios = require('axios');
const { v4: uuidv4 } = require('uuid');

const app = express();
app.use(express.json());

const tasks = {};

const AGENT_CARD = {
  name: "Node.js Coordinator",
  description: "Coordinates work across the A2A agent network.",
  supportedInterfaces: [{
    url: "http://node-agent:8002",
    protocolBinding: "JSONRPC",
    protocolVersion: "1.0"
  }],
  provider: { organization: "A2A Test Network" },
  version: "1.0.0",
  capabilities: { streaming: false, pushNotifications: false },
  defaultInputModes: ["text/plain", "application/json"],
  defaultOutputModes: ["application/json"],
  skills: [{
    id: "coordinator",
    name: "Task Coordinator",
    description: "Fans out work to other agents in the A2A network and aggregates results.",
    tags: ["coordination", "orchestration", "routing"],
    examples: ["Coordinate analysis of this text across agents"],
    inputModes: ["text/plain"],
    outputModes: ["application/json"]
  }]
};

const PEER_AGENTS = [
  { name: "python-agent", url: "http://python-agent:8001" },
  { name: "go-agent", url: "http://go-agent:8003" },
  { name: "java-agent", url: "http://java-agent:8004" },
  { name: "kotlin-agent", url: "http://kotlin-agent:8005" }
];

async function sendA2AMessage(agentUrl, text) {
  try {
    const response = await axios.post(agentUrl, {
      jsonrpc: "2.0",
      id: 1,
      method: "SendMessage",
      params: {
        message: {
          messageId: uuidv4(),
          role: "user",
          parts: [{ text }]
        }
      }
    }, { timeout: 10000 });
    return response.data.result;
  } catch (err) {
    return { error: err.message };
  }
}

async function handleSendMessage(params) {
  const message = params.message || {};
  const parts = message.parts || [];
  const textContent = parts.map(p => p.text || '').join('');

  const taskId = uuidv4();
  const contextId = message.contextId || uuidv4();

  const task = {
    id: taskId,
    contextId,
    status: { state: "submitted", timestamp: Date.now() },
    artifacts: [],
    history: [message]
  };
  tasks[taskId] = task;

  task.status = { state: "working", timestamp: Date.now() };

  // Fan out to all peer agents via A2A
  const results = await Promise.allSettled(
    PEER_AGENTS.map(agent => sendA2AMessage(agent.url, textContent))
  );

  const aggregated = PEER_AGENTS.map((agent, i) => ({
    agent: agent.name,
    result: results[i].status === 'fulfilled' ? results[i].value : { error: results[i].reason?.message }
  }));

  task.artifacts = [{
    artifactId: uuidv4(),
    name: "Coordinated Results",
    parts: [{ data: { aggregatedResults: aggregated }, mediaType: "application/json" }]
  }];
  task.status = { state: "completed", timestamp: Date.now() };

  console.log(`Task ${taskId} completed with ${aggregated.length} agent results`);
  return { task };
}

function handleGetTask(params) {
  const task = tasks[params.id];
  return task ? { task } : null;
}

function handleCancelTask(params) {
  const task = tasks[params.id];
  if (task) {
    task.status = { state: "canceled", timestamp: Date.now() };
    return { task };
  }
  return null;
}

app.get('/.well-known/agent.json', (req, res) => res.json(AGENT_CARD));

app.post('/', async (req, res) => {
  const { method, params = {}, id: rpcId } = req.body;

  let result;
  if (method === 'SendMessage') {
    result = await handleSendMessage(params);
  } else if (method === 'GetTask') {
    result = handleGetTask(params);
  } else if (method === 'CancelTask') {
    result = handleCancelTask(params);
  } else {
    return res.json({
      jsonrpc: "2.0", id: rpcId,
      error: { code: -32601, message: `Method not found: ${method}` }
    });
  }

  if (result === null) {
    return res.json({
      jsonrpc: "2.0", id: rpcId,
      error: { code: -32002, message: "Task not found" }
    });
  }

  res.json({ jsonrpc: "2.0", id: rpcId, result });
});

app.listen(8002, () => console.log('Node.js Coordinator agent on port 8002'));
