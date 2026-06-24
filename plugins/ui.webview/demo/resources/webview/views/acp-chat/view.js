import { i as __toESM } from "./assets/rolldown-runtime.js";
import { J as require_react, M as require_jsx_runtime, T as useExternalStoreRuntime } from "./assets/assistant-ui-core.js";
import { i as AssistantRuntimeProvider, n as message_exports, r as composer_exports, t as thread_exports } from "./assets/assistant-ui-react.js";
import { t as require_client } from "./assets/react-dom.js";
import { t as marked } from "./assets/marked.js";
import { n as ndJsonStream, t as ClientSideConnection } from "./assets/agentclientprotocol-sdk.js";
//#region \0vite/modulepreload-polyfill.js
(function polyfill() {
	const relList = document.createElement("link").relList;
	if (relList && relList.supports && relList.supports("modulepreload")) return;
	for (const link of document.querySelectorAll("link[rel=\"modulepreload\"]")) processPreload(link);
	new MutationObserver((mutations) => {
		for (const mutation of mutations) {
			if (mutation.type !== "childList") continue;
			for (const node of mutation.addedNodes) if (node.tagName === "LINK" && node.rel === "modulepreload") processPreload(node);
		}
	}).observe(document, {
		childList: true,
		subtree: true
	});
	function getFetchOpts(link) {
		const fetchOpts = {};
		if (link.integrity) fetchOpts.integrity = link.integrity;
		if (link.referrerPolicy) fetchOpts.referrerPolicy = link.referrerPolicy;
		if (link.crossOrigin === "use-credentials") fetchOpts.credentials = "include";
		else if (link.crossOrigin === "anonymous") fetchOpts.credentials = "omit";
		else fetchOpts.credentials = "same-origin";
		return fetchOpts;
	}
	function processPreload(link) {
		if (link.ep) return;
		link.ep = true;
		const fetchOpts = getFetchOpts(link);
		fetch(link.href, fetchOpts);
	}
})();
//#endregion
//#region ../../webview-src/packages/api/src/webViewApi.ts
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var import_client = require_client();
function apiId() {
	return function createApiId(namespace) {
		validateApiNamespace(namespace);
		return { namespace };
	};
}
function validateApiNamespace(namespace) {
	if (typeof namespace !== "string" || namespace.length === 0) throw new Error("WebView API namespace must be a non-empty string");
	if (namespace.startsWith(".") || namespace.endsWith(".") || namespace.startsWith("/") || namespace.endsWith("/")) throw new Error("WebView API namespace must not start or end with '.' or '/': " + namespace);
	if (!/^[A-Za-z0-9_.-]+$/.test(namespace)) throw new Error("WebView API namespace contains unsupported characters: " + namespace);
}
apiId()("webview.theme");
apiId()("webview.theme");
function getWebViewTheme() {
	return window.__WVI_THEME__;
}
function requireWebViewTheme() {
	const theme = getWebViewTheme();
	if (!theme) throw new Error("WebView theme is not installed. Load /__webview/wvi-platform-features.js after /__webview/wvi-bridge.js before theme-aware application code.");
	return theme;
}
function createLazyWebViewTheme() {
	return new Proxy({}, {
		get(_target, property, receiver) {
			return Reflect.get(requireWebViewTheme(), property, receiver);
		},
		set(_target, property, value, receiver) {
			return Reflect.set(requireWebViewTheme(), property, value, receiver);
		},
		has(_target, property) {
			return property in requireWebViewTheme();
		}
	});
}
createLazyWebViewTheme();
apiId()("webview.focus");
apiId()("webview.focus");
//#endregion
//#region ../../webview-src/packages/api/src/bridge.ts
function getWebViewBridge() {
	return window.__WVI__;
}
function requireWebViewBridge() {
	const bridge = getWebViewBridge();
	if (!bridge) throw new Error("WebView bridge is not installed. Load /__webview/wvi-bridge.js before application code.");
	return bridge;
}
function createLazyWebViewBridge() {
	return new Proxy({}, {
		get(_target, property, receiver) {
			return Reflect.get(requireWebViewBridge(), property, receiver);
		},
		set(_target, property, value, receiver) {
			return Reflect.set(requireWebViewBridge(), property, value, receiver);
		},
		has(_target, property) {
			return property in requireWebViewBridge();
		}
	});
}
var webView = createLazyWebViewBridge();
//#endregion
//#region views/acp-chat/src/bridge/webviewApi.ts
var acpBridgeHost = webView.callable(apiId()("acp.bridge"));
var currentHandlers = null;
webView.implement(apiId()("acp.bridge"), {
	onAgentStdout(params) {
		currentHandlers?.onAgentStdout(params);
	},
	onAgentExit(params) {
		currentHandlers?.onAgentExit(params);
	}
});
function setBridgePageHandlers(handlers) {
	currentHandlers = handlers;
}
//#endregion
//#region views/acp-chat/src/bridge/acpStdioStream.ts
function createAgentStdioStream() {
	const encoder = new TextEncoder();
	const decoder = new TextDecoder();
	let inputController = null;
	let exitCallback = null;
	const input = new ReadableStream({
		start(controller) {
			inputController = controller;
		},
		cancel() {
			inputController = null;
			setBridgePageHandlers(null);
		}
	});
	setBridgePageHandlers({
		onAgentStdout(params) {
			inputController?.enqueue(encoder.encode(params.line + "\n"));
		},
		onAgentExit(params) {
			try {
				inputController?.close();
			} catch {}
			inputController = null;
			exitCallback?.(params.code ?? null);
		}
	});
	return {
		stream: ndJsonStream(new WritableStream({ write(chunk) {
			const text = decoder.decode(chunk);
			for (const line of text.split("\n")) if (line.length > 0) acpBridgeHost.sendStdin({ line });
		} }), input),
		onExit(callback) {
			exitCallback = callback;
		}
	};
}
//#endregion
//#region views/acp-chat/src/acp/client.ts
/**
* One ACP session over a spawned agent. The protocol is handled by the ACP TypeScript SDK; the transport is the
* Kotlin-bridged process stdio. ACP wire objects are accessed defensively (`any`) so this stays resilient to minor
* SDK shape differences.
*/
var AcpSession = class {
	connection = null;
	sessionId = null;
	get isActive() {
		return this.connection != null && this.sessionId != null;
	}
	async start(agentId, sink) {
		const result = await acpBridgeHost.startAgent({ agentId });
		if (!result.ok) throw new Error(result.error ?? `Failed to start agent '${agentId}'`);
		const cwd = result.cwd ?? ".";
		const io = createAgentStdioStream();
		io.onExit((code) => sink.onAgentExit(code));
		const client = {
			sessionUpdate(notification) {
				handleUpdate(notification?.update, sink);
			},
			async requestPermission(request) {
				const optionId = await sink.requestPermission(toPermissionView(request));
				if (optionId == null) return { outcome: { outcome: "cancelled" } };
				return { outcome: {
					outcome: "selected",
					optionId
				} };
			}
		};
		const connection = new ClientSideConnection(() => client, io.stream);
		this.connection = connection;
		await connection.initialize({
			protocolVersion: 1,
			clientCapabilities: {
				fs: {
					readTextFile: false,
					writeTextFile: false
				},
				terminal: false
			}
		});
		const session = await connection.newSession({
			cwd,
			mcpServers: []
		});
		this.sessionId = session.sessionId;
	}
	async prompt(text) {
		const connection = this.connection;
		const sessionId = this.sessionId;
		if (!connection || !sessionId) throw new Error("No active ACP session");
		await connection.prompt({
			sessionId,
			prompt: [{
				type: "text",
				text
			}]
		});
	}
	async cancel() {
		const connection = this.connection;
		const sessionId = this.sessionId;
		if (connection && sessionId) await connection.cancel({ sessionId });
	}
	async stop() {
		this.connection = null;
		this.sessionId = null;
		try {
			await acpBridgeHost.stopAgent();
		} catch {}
	}
};
function handleUpdate(update, sink) {
	if (!update) return;
	switch (update.sessionUpdate) {
		case "agent_message_chunk":
			sink.onMessageChunk(textOf(update.content));
			break;
		case "agent_thought_chunk":
			sink.onThoughtChunk(textOf(update.content));
			break;
		case "tool_call":
		case "tool_call_update":
			sink.onToolCall(toToolView(update));
			break;
		case "plan":
			sink.onPlan(Array.isArray(update.entries) ? update.entries.map(toPlanView) : []);
			break;
		default: break;
	}
}
function textOf(content) {
	if (!content) return "";
	if (Array.isArray(content)) return content.map(textOf).join("");
	if (content.type === "text" && typeof content.text === "string") return content.text;
	return "";
}
function toToolView(update) {
	let text;
	let diff;
	const content = update.content;
	if (Array.isArray(content)) {
		for (const item of content) if (item?.type === "content") text = (text ?? "") + textOf(item.content);
		else if (item?.type === "diff") diff = {
			path: String(item.path ?? ""),
			oldText: item.oldText ?? null,
			newText: String(item.newText ?? "")
		};
	}
	const kind = typeof update.kind === "string" ? update.kind : "other";
	return {
		toolCallId: String(update.toolCallId ?? ""),
		title: typeof update.title === "string" ? update.title : kind,
		kind,
		status: normalizeToolStatus(update.status),
		text,
		diff
	};
}
function normalizeToolStatus(status) {
	switch (status) {
		case "pending":
		case "in_progress":
		case "completed":
		case "failed": return status;
		default: return "in_progress";
	}
}
function toPlanView(entry) {
	const status = entry?.status;
	return {
		content: typeof entry?.content === "string" ? entry.content : "",
		status: status === "pending" || status === "in_progress" || status === "completed" ? status : "pending",
		priority: typeof entry?.priority === "string" ? entry.priority : void 0
	};
}
function toPermissionView(request) {
	const options = Array.isArray(request?.options) ? request.options : [];
	return {
		title: typeof request?.toolCall?.title === "string" ? request.toolCall.title : "Permission requested",
		options: options.map((option) => ({
			optionId: String(option.optionId),
			name: String(option.name ?? option.optionId),
			kind: typeof option.kind === "string" ? option.kind : void 0
		}))
	};
}
//#endregion
//#region views/acp-chat/src/runtime/useAcpChat.ts
function useAcpChat() {
	const [messages, setMessages] = (0, import_react.useState)([]);
	const [isRunning, setIsRunning] = (0, import_react.useState)(false);
	const [agents, setAgents] = (0, import_react.useState)([]);
	const [selectedAgentId, setSelectedAgentId] = (0, import_react.useState)(null);
	const [starting, setStarting] = (0, import_react.useState)(false);
	const [status, setStatus] = (0, import_react.useState)("");
	const [plan, setPlan] = (0, import_react.useState)([]);
	const [permission, setPermission] = (0, import_react.useState)(null);
	const sessionRef = (0, import_react.useRef)(null);
	const turnRef = (0, import_react.useRef)(null);
	const assistantSeqRef = (0, import_react.useRef)(0);
	(0, import_react.useEffect)(() => {
		let cancelled = false;
		acpBridgeHost.listAgents().then((result) => {
			if (!cancelled) setAgents(result.agents);
		}).catch((error) => {
			if (!cancelled) setStatus(errorText(error));
		});
		return () => {
			cancelled = true;
		};
	}, []);
	const flushTurn = (0, import_react.useCallback)(() => {
		const turn = turnRef.current;
		if (!turn) return;
		const parts = [];
		if (turn.reasoning) parts.push({
			type: "reasoning",
			text: turn.reasoning
		});
		for (const tool of turn.tools) parts.push({
			type: "tool-call",
			toolCallId: tool.toolCallId,
			toolName: tool.kind,
			args: {},
			argsText: tool.title,
			result: {
				status: tool.status,
				title: tool.title,
				kind: tool.kind,
				text: tool.text,
				diff: tool.diff
			}
		});
		if (turn.text || parts.length === 0) parts.push({
			type: "text",
			text: turn.text
		});
		setMessages((previous) => {
			const next = previous.slice();
			for (let i = next.length - 1; i >= 0; i--) if (next[i].role === "assistant") {
				next[i] = {
					...next[i],
					content: parts
				};
				return next;
			}
			return next;
		});
	}, []);
	const sink = (0, import_react.useMemo)(() => ({
		onMessageChunk(text) {
			const turn = turnRef.current;
			if (turn) {
				turn.text += text;
				flushTurn();
			}
		},
		onThoughtChunk(text) {
			const turn = turnRef.current;
			if (turn) {
				turn.reasoning += text;
				flushTurn();
			}
		},
		onToolCall(view) {
			const turn = turnRef.current;
			if (!turn) return;
			const index = turn.tools.findIndex((t) => t.toolCallId === view.toolCallId);
			if (index >= 0) {
				const existing = turn.tools[index];
				turn.tools[index] = {
					...existing,
					...view,
					title: view.title || existing.title,
					text: view.text ?? existing.text,
					diff: view.diff ?? existing.diff
				};
			} else turn.tools.push(view);
			flushTurn();
		},
		onPlan(entries) {
			setPlan(entries);
		},
		requestPermission(view) {
			return new Promise((resolve) => {
				setPermission({
					view,
					resolve: (optionId) => {
						setPermission(null);
						resolve(optionId);
					}
				});
			});
		},
		onAgentExit(code) {
			setStatus(`Agent exited (code ${code ?? "unknown"})`);
			setIsRunning(false);
		}
	}), [flushTurn]);
	return {
		runtime: useExternalStoreRuntime({
			isRunning,
			messages,
			convertMessage: (message) => message,
			onNew: (0, import_react.useCallback)(async (message) => {
				const session = sessionRef.current;
				if (!session || !session.isActive) {
					setStatus("Select an agent to start a session first.");
					return;
				}
				const text = message.content.filter((part) => part.type === "text").map((part) => part.text).join("");
				const assistantId = `assistant-${++assistantSeqRef.current}`;
				setMessages((previous) => [
					...previous,
					{
						id: `user-${assistantSeqRef.current}`,
						role: "user",
						content: [{
							type: "text",
							text
						}]
					},
					{
						id: assistantId,
						role: "assistant",
						content: []
					}
				]);
				turnRef.current = {
					reasoning: "",
					text: "",
					tools: []
				};
				setPlan([]);
				setStatus("");
				setIsRunning(true);
				try {
					await session.prompt(text);
				} catch (error) {
					setStatus(errorText(error));
				} finally {
					setIsRunning(false);
				}
			}, []),
			onCancel: (0, import_react.useCallback)(async () => {
				try {
					await sessionRef.current?.cancel();
				} catch (error) {
					setStatus(errorText(error));
				}
				setIsRunning(false);
			}, [])
		}),
		agents,
		selectedAgentId,
		starting,
		status,
		plan,
		permission,
		selectAgent: (0, import_react.useCallback)((agentId) => {
			setStarting(true);
			setStatus("");
			const previous = sessionRef.current;
			const session = new AcpSession();
			sessionRef.current = session;
			(async () => {
				try {
					await previous?.stop();
					setMessages([]);
					setPlan([]);
					setPermission(null);
					turnRef.current = null;
					await session.start(agentId, sink);
					setSelectedAgentId(agentId);
				} catch (error) {
					setStatus(errorText(error));
				} finally {
					setStarting(false);
				}
			})();
		}, [sink])
	};
}
function errorText(error) {
	return error instanceof Error ? error.message : String(error);
}
//#endregion
//#region views/acp-chat/src/components/AgentSelector.tsx
var import_jsx_runtime = require_jsx_runtime();
function AgentSelector(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
		className: "acpAgentSelector",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpAgentSelectorLabel",
				children: "Agent"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("select", {
				className: "acpAgentSelect",
				value: props.selectedAgentId ?? "",
				disabled: props.starting,
				onChange: (event) => {
					if (event.target.value) props.onSelect(event.target.value);
				},
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", {
					value: "",
					disabled: true,
					children: props.agents.length ? "Select an agent…" : "No agents in ~/.jetbrains/acp.json"
				}), props.agents.map((agent) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", {
					value: agent.id,
					children: agent.name
				}, agent.id))]
			}),
			props.starting && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpAgentStarting",
				children: "Starting…"
			})
		]
	});
}
//#endregion
//#region views/acp-chat/src/components/ApprovalPrompt.tsx
function ApprovalPrompt({ permission }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "acpApprovalOverlay",
		role: "dialog",
		"aria-modal": "true",
		"aria-label": "Permission request",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "acpApproval",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
				className: "acpApprovalTitle",
				children: permission.view.title
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "acpApprovalOptions",
				children: [permission.view.options.map((option) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "button",
					className: `acpApprovalButton acpApprovalButton--${option.kind ?? "default"}`,
					onClick: () => permission.resolve(option.optionId),
					children: option.name
				}, option.optionId)), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "button",
					className: "acpApprovalButton acpApprovalButton--cancel",
					onClick: () => permission.resolve(null),
					children: "Cancel"
				})]
			})]
		})
	});
}
//#endregion
//#region views/acp-chat/src/components/PlanView.tsx
function PlanView({ plan }) {
	if (plan.length === 0) return null;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
		className: "acpPlan",
		"aria-label": "Agent plan",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpPlanTitle",
			children: "Plan"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("ul", {
			className: "acpPlanList",
			children: plan.map((entry, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("li", {
				className: `acpPlanItem acpPlanItem--${entry.status}`,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpPlanMark",
					"aria-hidden": "true",
					children: planMark(entry.status)
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpPlanContent",
					children: entry.content
				})]
			}, index))
		})]
	});
}
function planMark(status) {
	switch (status) {
		case "completed": return "✓";
		case "in_progress": return "▸";
		default: return "○";
	}
}
//#endregion
//#region views/acp-chat/src/components/ThinkingBlock.tsx
function ThinkingBlock(props) {
	const text = props?.text ?? "";
	if (!text) return null;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("details", {
		className: "acpThinking",
		open: true,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("summary", {
			className: "acpThinkingSummary",
			children: "Thinking"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpThinkingBody",
			children: text
		})]
	});
}
//#endregion
//#region views/acp-chat/src/components/ToolCallCard.tsx
function ToolCallCard(props) {
	const result = props?.result ?? {};
	const title = result.title ?? props?.toolName ?? "Tool call";
	const kind = result.kind ?? props?.toolName ?? "other";
	const status = result.status ?? "in_progress";
	const text = result.text;
	const diff = result.diff;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: `acpTool acpTool--${status}`,
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "acpToolHeader",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: `acpToolKind acpToolKind--${kind}`,
						children: kind
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "acpToolTitle",
						children: title
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: `acpToolStatus acpToolStatus--${status}`,
						children: status.replace("_", " ")
					})
				]
			}),
			text ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", {
				className: "acpToolText",
				children: text
			}) : null,
			diff ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "acpToolDiff",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
					className: "acpToolDiffPath",
					children: diff.path
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", {
					className: "acpToolDiffBody",
					children: renderDiff(diff)
				})]
			}) : null
		]
	});
}
function renderDiff(diff) {
	const removed = (diff.oldText ?? "").split("\n").filter((line) => line.length > 0).map((line) => `- ${line}`);
	const added = diff.newText.split("\n").filter((line) => line.length > 0).map((line) => `+ ${line}`);
	return [...removed, ...added].join("\n");
}
//#endregion
//#region views/acp-chat/src/components/ChatView.tsx
function ChatView() {
	const chat = useAcpChat();
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AssistantRuntimeProvider, {
		runtime: chat.runtime,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "acpChatLayout",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("header", {
					className: "acpChatHeader",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(AgentSelector, {
						agents: chat.agents,
						selectedAgentId: chat.selectedAgentId,
						starting: chat.starting,
						onSelect: chat.selectAgent
					}), chat.status ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "acpChatStatus",
						children: chat.status
					}) : null]
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(PlanView, { plan: chat.plan }),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(thread_exports.Root, {
					className: "acpThread",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(thread_exports.Viewport, {
						className: "acpThreadViewport",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(thread_exports.Empty, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
							className: "acpEmpty",
							children: "Select an agent and send a message to start."
						}) }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(thread_exports.Messages, { components: {
							UserMessage,
							AssistantMessage
						} })]
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(composer_exports.Root, {
						className: "acpComposer",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Input, {
							className: "acpComposerInput",
							placeholder: "Message the agent…"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Send, {
							className: "acpComposerSend",
							children: "Send"
						})]
					})]
				}),
				chat.permission ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ApprovalPrompt, { permission: chat.permission }) : null
			]
		})
	});
}
function UserMessage() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "acpMsg acpMsgUser",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(message_exports.Parts, { components: { Text: PlainText } })
	});
}
function AssistantMessage() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "acpMsg acpMsgAssistant",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(message_exports.Parts, { components: {
			Text: MarkdownText,
			Reasoning: ThinkingBlock,
			tools: { Fallback: ToolCallCard }
		} })
	});
}
function PlainText(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
		className: "acpText",
		children: props?.text ?? ""
	});
}
function MarkdownText(props) {
	const text = props?.text ?? "";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "acpMarkdown",
		dangerouslySetInnerHTML: { __html: marked.parse(text, { async: false }) }
	});
}
//#endregion
//#region views/acp-chat/src/main.tsx
var container = document.getElementById("root");
if (container) (0, import_client.createRoot)(container).render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ChatView, {}));
//#endregion
