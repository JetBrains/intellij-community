const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["./assets/mermaid.js","./assets/rolldown-runtime.js","./assets/braintree-sanitize-url.js","./assets/iconify-utils.js","./assets/chevrotain-allstar.js","./assets/chevrotain.js","./assets/cytoscape-cose-bilkent.js","./assets/cose-base.js","./assets/cytoscape-fcose.js","./assets/cytoscape.js","./assets/d3-array.js","./assets/d3-axis.js","./assets/d3.js","./assets/d3-format.js","./assets/d3-hierarchy.js","./assets/d3-interpolate.js","./assets/d3-color.js","./assets/d3-sankey.js","./assets/d3-path.js","./assets/d3-scale-chromatic.js","./assets/d3-scale.js","./assets/d3-selection.js","./assets/d3-shape.js","./assets/dagre-d3-es.js","./assets/dayjs.js","./assets/dompurify.js","./assets/khroma.js","./assets/langium.js","./assets/marked.js"])))=>i.map(i=>d[i]);
import { o as __toESM } from "./assets/rolldown-runtime.js";
import { M as require_jsx_runtime, T as useExternalStoreRuntime, Y as require_react } from "./assets/assistant-ui-core.js";
import { a as message_exports, b as AssistantRuntimeProvider, c as composer_exports, i as thread_exports, l as useTriggerPopoverScopeContext, n as useMessagePartReasoning, o as useSmooth, r as selectionToolbar_exports, s as useMessagePartText, t as unstable_useSlashCommandAdapter, u as attachment_exports, v as useMessage, y as useComposerRuntime } from "./assets/assistant-ui-react.js";
import { t as require_client } from "./assets/react-dom.js";
import { a as SelectItem$1, c as SelectPortal, d as SelectTrigger$1, i as SelectIcon, l as SelectScrollDownButton$1, n as SelectContent$1, o as SelectItemIndicator, p as SelectViewport, s as SelectItemText, t as Select$1, u as SelectScrollUpButton$1 } from "./assets/radix-ui-react-select.js";
import { i as Trigger$1, n as Portal, r as Root2, t as Content2 } from "./assets/radix-ui-react-popover.js";
import { n as SwitchThumb, t as Switch } from "./assets/radix-ui-react-switch.js";
import { n as ndJsonStream, t as ClientSideConnection } from "./assets/agentclientprotocol-sdk.js";
import { n as defaultUrlTransform, t as Markdown } from "./assets/react-markdown.js";
import { t as rehypeHighlight } from "./assets/rehype-highlight.js";
import { t as rehypeKatex } from "./assets/rehype-katex.js";
import { t as rehypeRaw } from "./assets/rehype-raw.js";
import { n as defaultSchema } from "./assets/hast-util-sanitize.js";
import { t as rehypeSanitize } from "./assets/rehype-sanitize.js";
import { t as remarkGfm } from "./assets/remark-gfm.js";
import { t as remarkMath } from "./assets/remark-math.js";
import { d as __vitePreload } from "./assets/mermaid.js";
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
var webViewTheme = createLazyWebViewTheme();
//#endregion
//#region ../../webview-src/packages/api/src/iconSet.ts
var IconSet = /* @__PURE__ */ Object.freeze({ define(id) {
	validateIconSetId(id);
	return new DefinedIconSet(id);
} });
var DefinedIconSet = class {
	id;
	constructor(id) {
		this.id = id;
	}
	src(resourcePath) {
		validateIconResourcePath(resourcePath);
		return `./__ij-icons/${this.id}/${webViewTheme.current}/${encodeIconResourcePath(resourcePath)}`;
	}
};
function validateIconSetId(id) {
	if (!/^[A-Za-z][A-Za-z0-9._-]*$/.test(id)) throw new Error(`Invalid WebView icon set id: ${id}`);
}
function validateIconResourcePath(resourcePath) {
	if (resourcePath.length === 0 || resourcePath.startsWith("/") || resourcePath.includes("\\")) throw new Error(`Invalid WebView icon resource path: ${resourcePath}`);
	if (/^[A-Za-z][A-Za-z0-9+.-]*:/.test(resourcePath)) throw new Error(`Invalid WebView icon resource path: ${resourcePath}`);
	if (resourcePath.split("/").some((segment) => segment.length === 0 || segment === "." || segment === "..")) throw new Error(`Invalid WebView icon resource path: ${resourcePath}`);
	if (!resourcePath.endsWith(".svg") && !resourcePath.endsWith(".png")) throw new Error(`Unsupported WebView icon resource extension: ${resourcePath}`);
}
function encodeIconResourcePath(resourcePath) {
	return resourcePath.split("/").map((segment) => encodeURIComponent(segment)).join("/");
}
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
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
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
		},
		close() {
			try {
				inputController?.error(/* @__PURE__ */ new Error("ACP connection closed"));
			} catch {}
			inputController = null;
			setBridgePageHandlers(null);
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
	cwd = ".";
	authMethods = [];
	io = null;
	sink = null;
	generation = 0;
	get isActive() {
		return this.connection != null && this.sessionId != null;
	}
	/** Spawn + connect the agent and attempt to open a session. */
	async start(agentId, sink) {
		try {
			await this.connect(agentId, sink);
		} catch (error) {
			return {
				kind: "error",
				message: messageOf(error)
			};
		}
		return this.openSession();
	}
	/** Re-spawn the agent with extra environment (an entered API key) and reconnect; used for env_var auth methods. */
	async reconnectWithEnv(agentId, env, sink) {
		await this.stop();
		await this.connect(agentId, sink, env);
	}
	/** Authenticate the live connection with the chosen method; for OAuth methods the agent drives a device flow. */
	async authenticate(methodId) {
		const connection = this.connection;
		if (!connection) throw new Error("No agent connection");
		await connection.authenticate({ methodId });
	}
	/** (Re)try `session/new`, classifying an auth_required rejection into an actionable outcome. */
	async openSession() {
		const connection = this.connection;
		if (!connection) return {
			kind: "error",
			message: "No agent connection"
		};
		try {
			const session = await connection.newSession({
				cwd: this.cwd,
				mcpServers: []
			});
			this.sessionId = session.sessionId;
			this.sink?.onSessionModes(toSessionModeViews(session.modes?.availableModes), stringOrNull(session.modes?.currentModeId));
			this.sink?.onConfigOptions(toConfigOptionViews(session.configOptions));
			return { kind: "ready" };
		} catch (error) {
			if (!isAuthRequired(error)) return {
				kind: "error",
				message: messageOf(error)
			};
			const methods = this.authMethodViews(error?.data);
			if (methods.length === 0) return {
				kind: "error",
				message: `${authMessage(error)} Authenticate the agent's own CLI, then reselect it.`
			};
			return {
				kind: "auth-required",
				methods,
				message: authMessage(error)
			};
		}
	}
	async prompt(blocks) {
		const connection = this.connection;
		const sessionId = this.sessionId;
		if (!connection || !sessionId) throw new Error("No active ACP session");
		await connection.prompt({
			sessionId,
			prompt: blocks
		});
	}
	async promptText(text) {
		await this.prompt([{
			type: "text",
			text
		}]);
	}
	async setMode(modeId) {
		const connection = this.connection;
		const sessionId = this.sessionId;
		if (!connection || !sessionId) throw new Error("No active ACP session");
		if (typeof connection.setSessionMode !== "function") throw new Error("The selected ACP agent does not support session modes.");
		await connection.setSessionMode({
			sessionId,
			modeId
		});
		this.sink?.onCurrentMode(modeId);
	}
	async setConfigOption(configId, type, value) {
		const connection = this.connection;
		const sessionId = this.sessionId;
		if (!connection || !sessionId) throw new Error("No active ACP session");
		if (typeof connection.setSessionConfigOption !== "function") throw new Error("The selected ACP agent does not support session config options.");
		const response = type === "boolean" ? await connection.setSessionConfigOption({
			sessionId,
			configId,
			type,
			value: value === true
		}) : await connection.setSessionConfigOption({
			sessionId,
			configId,
			value: String(value)
		});
		this.sink?.onConfigOptions(toConfigOptionViews(response?.configOptions));
	}
	async cancel() {
		const connection = this.connection;
		const sessionId = this.sessionId;
		if (connection && sessionId) await connection.cancel({ sessionId });
	}
	async stop() {
		this.generation++;
		this.connection = null;
		this.sessionId = null;
		this.sink = null;
		const io = this.io;
		this.io = null;
		try {
			io?.close();
		} catch {}
		try {
			await acpBridgeHost.stopAgent();
		} catch {}
	}
	/** Spawn the process, wire the stdio stream, and run `initialize` (capturing the advertised auth methods). */
	async connect(agentId, sink, extraEnv) {
		const generation = ++this.generation;
		const io = createAgentStdioStream();
		io.onExit((code) => sink.onAgentExit(code));
		try {
			const result = await acpBridgeHost.startAgent({
				agentId,
				extraEnv
			});
			if (!result.ok) throw new Error(result.error ?? `Failed to start agent '${agentId}'`);
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
				},
				extNotification(method, params) {
					if (method !== "authenticate/update") return;
					const authUri = params?._meta?.authUri ?? params?.authUri;
					if (typeof authUri === "string" && authUri) sink.onAuthUpdate(authUri);
				}
			};
			const connection = new ClientSideConnection(() => client, io.stream);
			const init = await connection.initialize({
				protocolVersion: 1,
				clientCapabilities: {
					fs: {
						readTextFile: false,
						writeTextFile: false
					},
					terminal: false
				}
			});
			if (this.generation !== generation) throw new Error("ACP connection superseded");
			this.cwd = result.cwd ?? ".";
			this.io = io;
			this.connection = connection;
			this.sink = sink;
			this.authMethods = Array.isArray(init?.authMethods) ? init.authMethods : [];
			sink.onPromptCapabilities(toPromptCapabilitiesView(init?.agentCapabilities?.promptCapabilities));
		} catch (error) {
			io.close();
			await acpBridgeHost.stopAgent().catch(() => {});
			throw error;
		}
	}
	authMethodViews(errorData) {
		return authMethodsOf(errorData, { authMethods: this.authMethods }).map(toAuthMethodView);
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
		case "plan_update": {
			const plan = toPlanUpdateView(update.plan);
			if (plan) sink.onPlanUpdate(plan.planId, plan.entries);
			break;
		}
		case "plan_removed":
			if (typeof update.id === "string") sink.onPlanRemoved(update.id);
			break;
		case "available_commands_update":
			sink.onCommands(toCommandViews(update.availableCommands));
			break;
		case "current_mode_update":
			if (typeof update.currentModeId === "string") sink.onCurrentMode(update.currentModeId);
			break;
		case "config_option_update":
			sink.onConfigOptions(toConfigOptionViews(update.configOptions));
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
function toPlanView(entry, planId) {
	const status = entry?.status;
	const view = {
		content: typeof entry?.content === "string" ? entry.content : "",
		status: status === "pending" || status === "in_progress" || status === "completed" ? status : "pending",
		priority: typeof entry?.priority === "string" ? entry.priority : void 0
	};
	if (planId) view.planId = planId;
	return view;
}
function toPlanUpdateView(plan) {
	const planId = typeof plan?.id === "string" ? plan.id : null;
	if (!planId) return null;
	if (plan.type === "items") return {
		planId,
		entries: Array.isArray(plan.entries) ? plan.entries.map((entry) => toPlanView(entry, planId)) : []
	};
	if (plan.type === "markdown") return {
		planId,
		entries: [{
			planId,
			content: typeof plan.content === "string" ? plan.content : "",
			status: "in_progress"
		}]
	};
	if (plan.type === "file") {
		const uri = typeof plan.uri === "string" ? plan.uri : "";
		return {
			planId,
			entries: [{
				planId,
				content: uri ? `Plan file: ${uri}` : "Plan file",
				status: "in_progress"
			}]
		};
	}
	return null;
}
function toPromptCapabilitiesView(capabilities) {
	return {
		image: capabilities?.image === true,
		audio: capabilities?.audio === true,
		embeddedContext: capabilities?.embeddedContext === true
	};
}
function toSessionModeViews(modes) {
	if (!Array.isArray(modes)) return [];
	const result = [];
	for (const mode of modes) {
		const id = typeof mode?.id === "string" ? mode.id : "";
		if (!id) continue;
		result.push({
			id,
			name: stringOrDefault(mode.name, id),
			description: stringOrUndefined(mode.description)
		});
	}
	return result;
}
function toConfigOptionViews(options) {
	if (!Array.isArray(options)) return [];
	const result = [];
	for (const option of options) {
		const id = typeof option?.id === "string" ? option.id : "";
		if (!id) continue;
		const base = {
			id,
			name: stringOrDefault(option.name, id),
			description: stringOrUndefined(option.description),
			category: stringOrUndefined(option.category)
		};
		if (option.type === "select") result.push({
			...base,
			type: "select",
			currentValue: typeof option.currentValue === "string" ? option.currentValue : "",
			options: toConfigOptionSelectChoices(option.options)
		});
		else if (option.type === "boolean") result.push({
			...base,
			type: "boolean",
			currentValue: option.currentValue === true
		});
	}
	return result;
}
function toConfigOptionSelectChoices(options) {
	if (!Array.isArray(options)) return [];
	const result = [];
	for (const option of options) if (Array.isArray(option?.options)) {
		const group = stringOrUndefined(option.group);
		const groupName = stringOrUndefined(option.name);
		for (const groupedOption of option.options) {
			const choice = toConfigOptionSelectChoice(groupedOption, group, groupName);
			if (choice) result.push(choice);
		}
	} else {
		const choice = toConfigOptionSelectChoice(option);
		if (choice) result.push(choice);
	}
	return result;
}
function toConfigOptionSelectChoice(option, group, groupName) {
	const value = typeof option?.value === "string" ? option.value : "";
	if (!value) return null;
	const choice = {
		value,
		name: stringOrDefault(option.name, value),
		description: stringOrUndefined(option.description)
	};
	if (group) choice.group = group;
	if (groupName) choice.groupName = groupName;
	return choice;
}
function toCommandViews(commands) {
	if (!Array.isArray(commands)) return [];
	const result = [];
	for (const command of commands) {
		const name = typeof command?.name === "string" ? command.name : "";
		if (!name) continue;
		result.push({
			name,
			description: typeof command.description === "string" ? command.description : "",
			inputHint: stringOrUndefined(command.input?.hint)
		});
	}
	return result;
}
function stringOrDefault(value, fallback) {
	return typeof value === "string" && value ? value : fallback;
}
function stringOrUndefined(value) {
	return typeof value === "string" ? value : void 0;
}
function stringOrNull(value) {
	return typeof value === "string" ? value : null;
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
/** ACP `auth_required` JSON-RPC error code (see `RequestError.authRequired` in the ACP SDK). */
var ACP_AUTH_REQUIRED_CODE = -32e3;
function isAuthRequired(error) {
	return error?.code === ACP_AUTH_REQUIRED_CODE;
}
/** The agent's own auth_required message (e.g. "Authentication required: …"), or a generic fallback. */
function authMessage(error) {
	return messageOf(error).trim() || "This agent requires authentication.";
}
/** Prefer auth methods carried in the error payload, falling back to those advertised at initialize. */
function authMethodsOf(errorData, init) {
	const fromError = Array.isArray(errorData?.authMethods) ? errorData.authMethods : [];
	if (fromError.length > 0) return fromError;
	return Array.isArray(init?.authMethods) ? init.authMethods : [];
}
function toAuthMethodView(method) {
	const vars = Array.isArray(method?.vars) ? method.vars.map((v) => ({
		name: String(v?.name ?? ""),
		label: typeof v?.label === "string" ? v.label : void 0,
		secret: v?.secret !== false,
		optional: v?.optional === true
	})).filter((v) => v.name) : [];
	return {
		id: String(method?.id ?? ""),
		name: typeof method?.name === "string" && method.name ? method.name : String(method?.id ?? "auth"),
		description: typeof method?.description === "string" ? method.description : void 0,
		vars
	};
}
function messageOf(error) {
	return error instanceof Error ? error.message : String(error);
}
//#endregion
//#region views/acp-chat/src/runtime/useAcpChat.ts
var emptyPromptCapabilities = {
	image: false,
	audio: false,
	embeddedContext: false
};
var legacyPlanId = "legacy";
var textAttachmentAccept = "text/*,.csv,.json,.jsonl,.md,.markdown,.txt,.xml,.yaml,.yml";
var attachmentIdSeq = 0;
function useAcpChat() {
	const [messages, setMessages] = (0, import_react.useState)([]);
	const [isRunning, setIsRunning] = (0, import_react.useState)(false);
	const [agents, setAgents] = (0, import_react.useState)([]);
	const [selectedAgentId, setSelectedAgentId] = (0, import_react.useState)(null);
	const [starting, setStarting] = (0, import_react.useState)(false);
	const [status, setStatus] = (0, import_react.useState)("");
	const [plan, setPlan] = (0, import_react.useState)([]);
	const [promptCapabilities, setPromptCapabilities] = (0, import_react.useState)(emptyPromptCapabilities);
	const [modes, setModes] = (0, import_react.useState)([]);
	const [configOptions, setConfigOptions] = (0, import_react.useState)([]);
	const [currentModeId, setCurrentModeId] = (0, import_react.useState)(null);
	const [commands, setCommands] = (0, import_react.useState)([]);
	const [permission, setPermission] = (0, import_react.useState)(null);
	const [auth, setAuth] = (0, import_react.useState)(null);
	const sessionRef = (0, import_react.useRef)(null);
	const turnRef = (0, import_react.useRef)(null);
	const plansByIdRef = (0, import_react.useRef)(/* @__PURE__ */ new Map());
	const assistantSeqRef = (0, import_react.useRef)(0);
	const authResolveRef = (0, import_react.useRef)(null);
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
	(0, import_react.useEffect)(() => () => {
		authResolveRef.current?.(null);
		sessionRef.current?.stop();
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
	const publishPlans = (0, import_react.useCallback)(() => {
		setPlan(Array.from(plansByIdRef.current.values()).flat());
	}, []);
	const clearPlans = (0, import_react.useCallback)(() => {
		plansByIdRef.current.clear();
		setPlan([]);
	}, []);
	const resetSessionMetadata = (0, import_react.useCallback)(() => {
		setPromptCapabilities(emptyPromptCapabilities);
		setModes([]);
		setConfigOptions([]);
		setCurrentModeId(null);
		setCommands([]);
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
			plansByIdRef.current.clear();
			if (entries.length > 0) plansByIdRef.current.set(legacyPlanId, entries);
			publishPlans();
		},
		onPlanUpdate(planId, entries) {
			plansByIdRef.current.set(planId, entries);
			publishPlans();
		},
		onPlanRemoved(planId) {
			plansByIdRef.current.delete(planId);
			publishPlans();
		},
		onPromptCapabilities(capabilities) {
			setPromptCapabilities(capabilities);
		},
		onSessionModes(nextModes, nextCurrentModeId) {
			setModes(nextModes);
			setCurrentModeId(nextCurrentModeId);
		},
		onCurrentMode(nextCurrentModeId) {
			setCurrentModeId(nextCurrentModeId);
		},
		onConfigOptions(nextConfigOptions) {
			setConfigOptions(nextConfigOptions);
		},
		onCommands(nextCommands) {
			setCommands(nextCommands);
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
		onAuthUpdate(authUri) {
			setAuth((previous) => previous ? {
				...previous,
				authUri
			} : previous);
		},
		onAgentExit(code) {
			setStatus(`Agent exited (code ${code ?? "unknown"})`);
			setIsRunning(false);
			authResolveRef.current?.(null);
		}
	}), [flushTurn, publishPlans]);
	const attachmentAdapter = (0, import_react.useMemo)(() => createAttachmentAdapter(promptCapabilities), [promptCapabilities]);
	const onNew = (0, import_react.useCallback)(async (message) => {
		const session = sessionRef.current;
		if (!session || !session.isActive) {
			setStatus("Select an agent to start a session first.");
			return;
		}
		let blocks;
		try {
			blocks = buildPromptBlocks(message, promptCapabilities);
		} catch (error) {
			setStatus(errorText(error));
			return;
		}
		const text = textFromAppendMessage(message);
		const assistantId = `assistant-${++assistantSeqRef.current}`;
		setMessages((previous) => [
			...previous,
			{
				id: `user-${assistantSeqRef.current}`,
				role: "user",
				content: text ? [{
					type: "text",
					text
				}] : [],
				attachments: message.attachments,
				metadata: message.metadata
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
		clearPlans();
		setStatus("");
		setIsRunning(true);
		try {
			await session.prompt(blocks);
		} catch (error) {
			setStatus(errorText(error));
		} finally {
			setIsRunning(false);
		}
	}, [clearPlans, promptCapabilities]);
	const onCancel = (0, import_react.useCallback)(async () => {
		try {
			await sessionRef.current?.cancel();
		} catch (error) {
			setStatus(errorText(error));
		}
		setIsRunning(false);
	}, []);
	return {
		runtime: useExternalStoreRuntime({
			isRunning,
			messages,
			setMessages: (next) => setMessages([...next]),
			unstable_capabilities: { copy: true },
			convertMessage: (message) => message,
			adapters: { attachments: attachmentAdapter },
			onNew,
			onCancel
		}),
		agents,
		selectedAgentId,
		starting,
		status,
		plan,
		promptCapabilities,
		modes,
		configOptions,
		currentModeId,
		commands,
		permission,
		auth,
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
					clearPlans();
					resetSessionMetadata();
					setPermission(null);
					setAuth(null);
					setSelectedAgentId(null);
					turnRef.current = null;
					let outcome = await session.start(agentId, sink);
					let authError;
					while (outcome.kind === "auth-required") {
						const { methods, message } = outcome;
						const choice = await new Promise((resolve) => {
							authResolveRef.current = resolve;
							setAuth({
								methods,
								message,
								phase: "select",
								error: authError,
								onChoose: resolve
							});
						});
						authResolveRef.current = null;
						if (!choice) {
							await session.stop();
							setAuth(null);
							setStatus("Authentication cancelled.");
							return;
						}
						let cancelledDuringAuth = false;
						setAuth({
							methods,
							message,
							phase: "authenticating",
							onChoose: () => {
								cancelledDuringAuth = true;
								session.stop();
							}
						});
						try {
							if (choice.env) await session.reconnectWithEnv(agentId, choice.env, sink);
							await session.authenticate(choice.methodId);
							outcome = await session.openSession();
							authError = void 0;
						} catch (error) {
							if (cancelledDuringAuth) {
								setAuth(null);
								setStatus("Authentication cancelled.");
								return;
							}
							authError = errorText(error);
							outcome = {
								kind: "auth-required",
								methods,
								message
							};
						}
						if (cancelledDuringAuth) {
							setAuth(null);
							setStatus("Authentication cancelled.");
							return;
						}
					}
					setAuth(null);
					if (outcome.kind === "error") {
						setStatus(outcome.message);
						return;
					}
					setSelectedAgentId(agentId);
				} catch (error) {
					setAuth(null);
					setStatus(errorText(error));
				} finally {
					setStarting(false);
				}
			})();
		}, [
			clearPlans,
			resetSessionMetadata,
			sink
		]),
		selectMode: (0, import_react.useCallback)((modeId) => {
			const session = sessionRef.current;
			if (!session || !session.isActive) {
				setStatus("Select an agent to start a session first.");
				return;
			}
			(async () => {
				try {
					setStatus("");
					await session.setMode(modeId);
				} catch (error) {
					setStatus(errorText(error));
				}
			})();
		}, []),
		selectConfigOption: (0, import_react.useCallback)((option, value) => {
			const session = sessionRef.current;
			if (!session || !session.isActive) {
				setStatus("Select an agent to start a session first.");
				return;
			}
			(async () => {
				try {
					setStatus("");
					await session.setConfigOption(option.id, option.type, value);
				} catch (error) {
					setStatus(errorText(error));
				}
			})();
		}, []),
		notifyAttachmentCapabilitiesUnavailable: (0, import_react.useCallback)(() => {
			if (selectedAgentId == null) setStatus("Image attachment support can be detected only after an ACP agent is activated.");
			else if (!promptCapabilities.image && promptCapabilities.embeddedContext) setStatus("The active ACP agent does not advertise image prompt attachments.");
			else setStatus("The active ACP agent does not advertise image or embedded-context prompt attachments.");
		}, [selectedAgentId, promptCapabilities])
	};
}
function createAttachmentAdapter(capabilities) {
	const accept = attachmentAccept(capabilities);
	if (!accept) return void 0;
	return {
		accept,
		async add({ file }) {
			if (!canAttachFile(file, capabilities)) throw new Error(`The selected agent does not support '${file.name}' as a prompt attachment.`);
			return {
				id: `attachment-${++attachmentIdSeq}`,
				type: file.type.startsWith("image/") ? "image" : "document",
				name: file.name,
				contentType: file.type || contentTypeForFileName(file.name),
				file,
				status: {
					type: "requires-action",
					reason: "composer-send"
				}
			};
		},
		async send(attachment) {
			return completeAttachment(attachment);
		},
		async remove() {}
	};
}
function attachmentAccept(capabilities) {
	const accepted = [];
	if (capabilities.image) accepted.push("image/*");
	if (capabilities.embeddedContext) accepted.push(textAttachmentAccept);
	return accepted.length > 0 ? accepted.join(",") : void 0;
}
function canAttachFile(file, capabilities) {
	if (capabilities.image && file.type.startsWith("image/")) return true;
	return capabilities.embeddedContext && isTextLikeFile(file.name, file.type);
}
async function completeAttachment(attachment) {
	const contentType = attachment.contentType || attachment.file.type || contentTypeForFileName(attachment.name);
	if (attachment.type === "image") return {
		...attachment,
		contentType,
		status: { type: "complete" },
		content: [{
			type: "image",
			image: await readFileAsDataUrl(attachment.file),
			filename: attachment.name
		}]
	};
	return {
		...attachment,
		contentType,
		status: { type: "complete" },
		content: [{
			type: "file",
			filename: attachment.name,
			mimeType: contentType,
			data: await attachment.file.text()
		}]
	};
}
function buildPromptBlocks(message, capabilities) {
	const blocks = [];
	const quote = quoteFromAppendMessage(message);
	if (quote) blocks.push({
		type: "text",
		text: quoteContextText(quote)
	});
	const text = textFromAppendMessage(message);
	if (text) blocks.push({
		type: "text",
		text
	});
	for (const attachment of message.attachments ?? []) for (const part of attachment.content ?? []) {
		const block = contentBlockFromAttachmentPart(attachment, part, capabilities);
		if (block) blocks.push(block);
	}
	return blocks;
}
function quoteFromAppendMessage(message) {
	const quote = message.metadata?.custom?.quote;
	if (!quote || typeof quote !== "object") return null;
	const text = quote.text;
	const messageId = quote.messageId;
	if (typeof text !== "string" || typeof messageId !== "string" || text.length === 0 || messageId.length === 0) return null;
	return {
		text,
		messageId
	};
}
function quoteContextText(quote) {
	const quotedText = quote.text.split(/\r?\n/u).map((line) => `> ${line}`).join("\n");
	return `Quoted context from message ${quote.messageId}:\n${quotedText}`;
}
function contentBlockFromAttachmentPart(attachment, part, capabilities) {
	if (part.type === "image") {
		if (!capabilities.image) throw new Error("The selected agent does not support image prompt attachments.");
		const image = acpImageData(part.image, attachment.contentType);
		return {
			type: "image",
			data: image.data,
			mimeType: image.mimeType,
			uri: attachmentUri(attachment)
		};
	}
	if (part.type === "file") {
		if (!capabilities.embeddedContext) throw new Error("The selected agent does not support embedded prompt attachments.");
		return {
			type: "resource",
			resource: {
				uri: attachmentUri(attachment),
				mimeType: part.mimeType,
				text: part.data
			}
		};
	}
	return null;
}
function textFromAppendMessage(message) {
	return message.content.filter((part) => part.type === "text").map((part) => part.text).join("");
}
function acpImageData(image, fallbackMimeType) {
	const match = /^data:([^;,]+);base64,(.*)$/s.exec(image);
	if (!match) return {
		data: image,
		mimeType: fallbackMimeType || "application/octet-stream"
	};
	return {
		mimeType: match[1],
		data: match[2]
	};
}
function attachmentUri(attachment) {
	return `attachment://${encodeURIComponent(attachment.id)}/${attachment.name.split("/").map(encodeURIComponent).join("/")}`;
}
function readFileAsDataUrl(file) {
	return new Promise((resolve, reject) => {
		const reader = new FileReader();
		reader.onload = () => resolve(String(reader.result ?? ""));
		reader.onerror = () => reject(reader.error ?? /* @__PURE__ */ new Error(`Failed to read '${file.name}'.`));
		reader.readAsDataURL(file);
	});
}
function isTextLikeFile(name, contentType) {
	if (contentType.startsWith("text/")) return true;
	switch (extensionOf(name)) {
		case "csv":
		case "json":
		case "jsonl":
		case "md":
		case "markdown":
		case "txt":
		case "xml":
		case "yaml":
		case "yml": return true;
		default: return false;
	}
}
function contentTypeForFileName(name) {
	switch (extensionOf(name)) {
		case "csv": return "text/csv";
		case "json":
		case "jsonl": return "application/json";
		case "md":
		case "markdown": return "text/markdown";
		case "xml": return "application/xml";
		case "yaml":
		case "yml": return "application/yaml";
		default: return "text/plain";
	}
}
function extensionOf(name) {
	const index = name.lastIndexOf(".");
	return index >= 0 ? name.slice(index + 1).toLocaleLowerCase() : "";
}
function errorText(error) {
	return error instanceof Error ? error.message : String(error);
}
//#endregion
//#region views/acp-chat/src/components/Select.tsx
var import_jsx_runtime = require_jsx_runtime();
var SelectRoot = Select$1;
function SelectTrigger({ className, children, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectTrigger$1, {
		"data-slot": "select-trigger",
		className,
		...props,
		children: [children, /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectIcon, {
			className: "acpSelectIcon",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectChevron, { direction: "down" })
		})]
	});
}
function SelectScrollUpButton(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectScrollUpButton$1, {
		"data-slot": "select-scroll-up-button",
		className: "acpSelectScrollButton",
		...props,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectChevron, { direction: "up" })
	});
}
function SelectScrollDownButton(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectScrollDownButton$1, {
		"data-slot": "select-scroll-down-button",
		className: "acpSelectScrollButton",
		...props,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectChevron, { direction: "down" })
	});
}
function SelectChevron(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		className: `acpSelectChevron acpSelectChevron--${props.direction}`,
		width: "12",
		height: "12",
		viewBox: "0 0 12 12",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M3 4.5L6 7.5L9 4.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.5",
			strokeLinecap: "round",
			strokeLinejoin: "round"
		})
	});
}
function SelectContent({ children, position = "popper", ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectPortal, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectContent$1, {
		"data-slot": "select-content",
		className: "acpSelectContent",
		position,
		sideOffset: 6,
		...props,
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectScrollUpButton, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectViewport, {
				className: "acpSelectViewport",
				children
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectScrollDownButton, {})
		]
	}) });
}
function SelectItem({ children, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectItem$1, {
		"data-slot": "select-item",
		className: "acpSelectItem",
		...props,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "acpSelectItemIndicator",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectItemIndicator, { children: "✓" })
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectItemText, { children })]
	});
}
function Select({ options, placeholder, className, ...props }) {
	const selectedOption = options.find((option) => option.value === props.value);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectRoot, {
		...props,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectTrigger, {
			className,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: selectedOption?.label ?? placeholder })
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectContent, { children: options.map(({ label, disabled, textValue, value }) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectItem, {
			value,
			disabled,
			textValue: textValue ?? (typeof label === "string" ? label : value),
			children: label
		}, value)) })]
	});
}
//#endregion
//#region views/acp-chat/src/components/AgentSelector.tsx
function AgentSelector(props) {
	const placeholder = props.agents.length ? "Select an agent…" : "No agents in ~/.jetbrains/acp.json";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
		className: "acpAgentSelector",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpAgentSelectorLabel",
				children: "Agent"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Select, {
				className: "acpAgentSelect",
				value: props.selectedAgentId ?? "",
				disabled: props.starting || props.agents.length === 0,
				placeholder,
				options: props.agents.map((agent) => ({
					value: agent.id,
					label: agent.name
				})),
				onValueChange: (agentId) => {
					if (agentId) props.onSelect(agentId);
				}
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
//#region views/acp-chat/src/components/AuthPrompt.tsx
/**
* In-chat authorization dialog. Mirrors {@link ApprovalPrompt}: the runtime resolves `auth.onChoose` with the chosen
* method (and, for env_var methods, the entered credentials) or `null` to cancel. While the agent runs an OAuth device
* flow the dialog switches to the `authenticating` phase and shows the verification URL pushed via `authenticate/update`.
*/
function AuthPrompt({ auth }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "acpApprovalOverlay",
		role: "dialog",
		"aria-modal": "true",
		"aria-label": "Authentication",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpApproval acpAuth",
			children: auth.phase === "select" ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuthMethodPicker, { auth }) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuthInProgress, { auth })
		})
	});
}
function AuthMethodPicker({ auth }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
		/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpApprovalTitle",
			children: auth.message || "Authentication required"
		}),
		auth.error ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpAuthError",
			children: auth.error
		}) : null,
		/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpAuthMethods",
			children: auth.methods.map((method) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuthMethod, {
				method,
				onChoose: auth.onChoose
			}, method.id))
		}),
		/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpApprovalOptions",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: "acpApprovalButton acpApprovalButton--cancel",
				onClick: () => auth.onChoose(null),
				children: "Cancel"
			})
		})
	] });
}
/**
* One auth method. Variables the agent declares (ACP `env_var` methods) are pre-listed; the user can add more (some
* agents, e.g. qwen's "Use OpenAI API key", expect `OPENAI_API_KEY`/`OPENAI_BASE_URL`/`OPENAI_MODEL` in the env without
* declaring them). On submit, entered variables are injected via re-spawn before `authenticate`; with none entered this
* is a plain agent-driven sign-in / OAuth device flow.
*/
function AuthMethod({ method, onChoose }) {
	const [rows, setRows] = (0, import_react.useState)(() => method.vars.map((variable) => ({
		name: variable.name,
		value: "",
		secret: variable.secret,
		fixed: true
	})));
	const env = collectEnv(rows);
	const missingRequired = method.vars.some((variable) => !variable.optional && !env[variable.name]);
	const update = (index, patch) => setRows((previous) => previous.map((row, i) => i === index ? {
		...row,
		...patch
	} : row));
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
		className: "acpAuthMethod",
		onSubmit: (event) => {
			event.preventDefault();
			if (!missingRequired) onChoose({
				methodId: method.id,
				env: Object.keys(env).length > 0 ? env : void 0
			});
		},
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
				className: "acpAuthMethodName",
				children: method.name
			}),
			method.description ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
				className: "acpAuthMethodDesc",
				children: method.description
			}) : null,
			rows.map((row, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "acpAuthVarRow",
				children: [row.fixed ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpAuthVarLabel acpAuthVarName",
					children: row.name
				}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
					className: "acpAuthVarInput acpAuthVarName",
					placeholder: "ENV_VAR",
					autoComplete: "off",
					spellCheck: false,
					value: row.name,
					onChange: (event) => update(index, { name: event.target.value })
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
					className: "acpAuthVarInput",
					type: row.secret ? "password" : "text",
					placeholder: "value",
					autoComplete: "off",
					spellCheck: false,
					value: row.value,
					onChange: (event) => update(index, { value: event.target.value })
				})]
			}, index)),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "acpAuthActions",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "button",
					className: "acpAuthAddVar",
					onClick: () => setRows((previous) => [...previous, {
						name: "",
						value: "",
						secret: false,
						fixed: false
					}]),
					children: "+ Add variable"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "submit",
					className: "acpApprovalButton acpApprovalButton--allow_once",
					disabled: missingRequired,
					children: "Authenticate"
				})]
			})
		]
	});
}
function AuthInProgress({ auth }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
		/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpApprovalTitle",
			children: "Authenticating…"
		}),
		auth.authUri ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "acpAuthUri",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", { children: "Open this URL in your browser to finish signing in:" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
				href: auth.authUri,
				target: "_blank",
				rel: "noreferrer",
				className: "acpAuthUriLink",
				children: auth.authUri
			})]
		}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpAuthHint",
			children: "Waiting for the agent…"
		}),
		auth.authUri ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpAuthHint",
			children: "If it doesn't continue after you approve, the agent's OAuth may be unavailable — Cancel and use an API key instead."
		}) : null,
		/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpApprovalOptions",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: "acpApprovalButton acpApprovalButton--cancel",
				onClick: () => auth.onChoose(null),
				children: "Cancel"
			})
		})
	] });
}
function collectEnv(rows) {
	const env = {};
	for (const row of rows) {
		const name = row.name.trim();
		const value = row.value.trim();
		if (name && value) env[name] = value;
	}
	return env;
}
//#endregion
//#region views/acp-chat/src/components/MermaidBlock.tsx
var mermaidBlockId = 0;
var mermaidRenderId = 0;
var mermaidModule;
function MermaidBlock({ chart }) {
	const hostId = (0, import_react.useRef)(`acp-chat-mermaid-${++mermaidBlockId}`);
	const [state, setState] = (0, import_react.useState)({ kind: "rendering" });
	(0, import_react.useEffect)(() => {
		let cancelled = false;
		const renderId = `${hostId.current}-${++mermaidRenderId}`;
		setState({ kind: "rendering" });
		loadMermaid().then((mermaid) => {
			configureMermaid(mermaid);
			return mermaid.render(renderId, chart);
		}).then(({ svg }) => {
			if (!cancelled) setState({
				kind: "rendered",
				svg
			});
		}).catch((error) => {
			if (!cancelled) setState({
				kind: "error",
				message: error instanceof Error ? error.message : "Failed to render Mermaid diagram"
			});
		});
		return () => {
			cancelled = true;
		};
	}, [chart]);
	if (state.kind === "rendered") return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "acpMermaidBlock",
		dangerouslySetInnerHTML: { __html: state.svg }
	});
	if (state.kind === "error") return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "acpMermaidBlock acpMermaidBlock--error",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpMermaidError",
			children: state.message
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("code", { children: chart }) })]
	});
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "acpMermaidBlock acpMermaidBlock--rendering",
		children: "Rendering diagram..."
	});
}
function loadMermaid() {
	mermaidModule ||= __vitePreload(() => import("./assets/mermaid.js").then((n) => n.t).then((module) => module.default), __vite__mapDeps([0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28]), import.meta.url);
	return mermaidModule;
}
function configureMermaid(mermaid) {
	const panel = cssVariable("--ij-bg-panel", "#2b2d30");
	const panelAlt = cssVariable("--ij-bg-panel-alt", "#1e1f22");
	const hover = cssVariable("--ij-bg-hover", "#ffffff17");
	const border = cssVariable("--ij-border-strong", "#393b40");
	const textPrimary = cssVariable("--ij-text-primary", "#dfe1e5");
	const textSecondary = cssVariable("--ij-text-secondary", "#9da0a8");
	const accent = cssVariable("--ij-accent", "#3574f0");
	const font = cssVariable("--ij-font", "Inter, Segoe UI, -apple-system, BlinkMacSystemFont, Helvetica Neue, sans-serif");
	mermaid.initialize({
		startOnLoad: false,
		theme: "base",
		securityLevel: "strict",
		suppressErrorRendering: true,
		themeVariables: {
			fontFamily: font,
			fontSize: "13px",
			primaryColor: panel,
			primaryBorderColor: border,
			primaryTextColor: textPrimary,
			secondaryColor: hover,
			secondaryBorderColor: border,
			secondaryTextColor: textPrimary,
			tertiaryColor: panelAlt,
			tertiaryBorderColor: border,
			tertiaryTextColor: textPrimary,
			mainBkg: panel,
			clusterBkg: panelAlt,
			clusterBorder: border,
			lineColor: textSecondary,
			textColor: textPrimary,
			titleColor: textPrimary,
			nodeBorder: border,
			edgeLabelBackground: panel,
			signalColor: textPrimary,
			actorBorder: border,
			actorBkg: panel,
			actorTextColor: textPrimary,
			noteBkgColor: panelAlt,
			noteBorderColor: border,
			noteTextColor: textPrimary,
			activationBkgColor: hover,
			activationBorderColor: accent
		},
		themeCSS: `
      .node rect,
      .node circle,
      .node ellipse,
      .node polygon,
      .node path {
        rx: 4px;
        ry: 4px;
      }
      .label,
      .edgeLabel,
      .cluster-label,
      .messageText {
        color: ${textPrimary};
        fill: ${textPrimary};
        font-family: ${font};
      }
      .edgeLabel,
      .edgeLabel p,
      .edgeLabel span {
        background: ${panel};
        color: ${textPrimary};
      }
      .flowchart-link,
      .messageLine0,
      .messageLine1 {
        stroke: ${textSecondary};
      }
      .marker {
        fill: ${textSecondary};
        stroke: ${textSecondary};
      }
    `
	});
}
function cssVariable(name, fallback) {
	return (getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback).replace(/^#([0-9a-fA-F]{6})[0-9a-fA-F]{2}$/, "#$1");
}
//#endregion
//#region views/acp-chat/src/components/markdownSanitizeSchema.ts
var defaultAttributes = defaultSchema.attributes || {};
var markdownSanitizeSchema = {
	...defaultSchema,
	tagNames: unique([
		...defaultSchema.tagNames || [],
		"abbr",
		"br",
		"col",
		"colgroup",
		"details",
		"kbd",
		"mark",
		"section",
		"summary",
		"sub",
		"sup"
	]),
	attributes: {
		...defaultAttributes,
		a: mergeAttributes("a", [["ariaLabel"], ["dataFootnoteBackref"]]),
		code: mergeAttributes("code", [[
			"className",
			/^language-./,
			"math-display",
			"math-inline",
			"no-highlight",
			"nohighlight"
		]]),
		details: mergeAttributes("details", [["open"]]),
		h2: mergeAttributes("h2", [["className", "sr-only"]]),
		input: mergeAttributes("input", [
			["checked"],
			["disabled"],
			["type", "checkbox"]
		]),
		li: mergeAttributes("li", [["className", "task-list-item"]]),
		section: mergeAttributes("section", [["className", "footnotes"], ["dataFootnotes"]]),
		ul: mergeAttributes("ul", [["className", "contains-task-list"]])
	}
};
function mergeAttributes(tagName, additions) {
	return [...defaultAttributes[tagName] || [], ...additions];
}
function unique(values) {
	return Array.from(new Set(values));
}
//#endregion
//#region views/acp-chat/src/components/MarkdownRenderer.tsx
var remarkPlugins = [remarkGfm, remarkMath];
var rehypePlugins = [
	rehypeRaw,
	[rehypeSanitize, markdownSanitizeSchema],
	[rehypeKatex, {
		strict: "warn",
		throwOnError: false
	}],
	[rehypeHighlight, {
		detect: true,
		plainText: [
			"mermaid",
			"text",
			"txt"
		]
	}]
];
function MarkdownRenderer({ text, streaming = false, className = "acpMarkdown" }) {
	const idPrefix = `acp-md-${(0, import_react.useId)().replace(/[^a-zA-Z0-9_-]/g, "")}-`;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: classNames(className, streaming ? "acpMarkdown--streaming" : void 0),
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Markdown, {
			remarkPlugins,
			rehypePlugins,
			remarkRehypeOptions: { clobberPrefix: idPrefix },
			components: {
				a({ href, children, ...props }) {
					return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
						...props,
						href,
						target: "_blank",
						rel: "noreferrer",
						children
					});
				},
				pre({ node, className, children, ...props }) {
					const codeNode = codeNodeFromPreNode(node);
					const code = hastText(codeNode).replace(/\n$/, "");
					if (!streaming && hastClassNames(codeNode).includes("language-mermaid")) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MermaidBlock, { chart: code });
					return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", {
						className,
						...props,
						children
					});
				},
				code({ className, children, ...props }) {
					return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("code", {
						className,
						...props,
						children
					});
				}
			},
			urlTransform: defaultUrlTransform,
			children: text
		})
	});
}
function codeNodeFromPreNode(node) {
	return node?.children?.find((child) => child.tagName === "code");
}
function hastClassNames(node) {
	const className = node?.properties?.className;
	if (Array.isArray(className)) return className.filter((name) => typeof name === "string");
	if (typeof className === "string") return className.split(/\s+/);
	return [];
}
function hastText(node) {
	if (!node) return "";
	if (typeof node.value === "string") return node.value;
	return node.children?.map(hastText).join("") ?? "";
}
function classNames(...names) {
	return names.filter(Boolean).join(" ");
}
//#endregion
//#region views/acp-chat/src/components/ModelSelector.tsx
var ModelSelectorContext = (0, import_react.createContext)(null);
function useModelSelectorContext() {
	const context = (0, import_react.useContext)(ModelSelectorContext);
	if (!context) throw new Error("ModelSelector components must be used inside ModelSelector.Root");
	return context;
}
function Root({ value, disabled, children, onValueChange }) {
	const [open, setOpen] = (0, import_react.useState)(false);
	const [query, setQuery] = (0, import_react.useState)("");
	const context = (0, import_react.useMemo)(() => ({
		value,
		disabled: disabled === true,
		query,
		setQuery,
		selectValue(nextValue) {
			onValueChange(nextValue);
			setQuery("");
			setOpen(false);
		}
	}), [
		disabled,
		onValueChange,
		query,
		value
	]);
	function handleOpenChange(nextOpen) {
		setOpen(nextOpen);
		if (!nextOpen) setQuery("");
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelectorContext.Provider, {
		value: context,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Root2, {
			open,
			onOpenChange: handleOpenChange,
			children
		})
	});
}
function Trigger({ className, children, placeholder = "Select model", ...props }) {
	const context = useModelSelectorContext();
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Trigger$1, {
		asChild: true,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
			type: "button",
			className: className ?? "acpModelSelectorTrigger",
			disabled: context.disabled,
			...props,
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpModelSelectorTriggerText",
				children: children ?? placeholder
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpModelSelectorChevron",
				"aria-hidden": "true",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelectorChevron, {})
			})]
		})
	});
}
function ModelSelectorChevron() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		width: "12",
		height: "12",
		viewBox: "0 0 12 12",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M3 4.5L6 7.5L9 4.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.5",
			strokeLinecap: "round",
			strokeLinejoin: "round"
		})
	});
}
function Content({ className, children, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Portal, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Content2, {
		align: "start",
		sideOffset: 6,
		className: className ?? "acpModelSelectorContent",
		...props,
		children
	}) });
}
function Search({ className, placeholder = "Search models...", ...props }) {
	const context = useModelSelectorContext();
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
		type: "search",
		className: className ?? "acpModelSelectorSearch",
		placeholder,
		value: context.query,
		onChange: (event) => context.setQuery(event.currentTarget.value),
		...props
	});
}
function List({ className, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		role: "listbox",
		className: className ?? "acpModelSelectorList",
		...props
	});
}
function Group({ className, label, children, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: className ?? "acpModelSelectorGroup",
		...props,
		children: [label ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpModelSelectorGroupLabel",
			children: label
		}) : null, children]
	});
}
function Item({ className, value, label, description, searchValue, disabled, ...props }) {
	const context = useModelSelectorContext();
	const query = context.query.trim().toLocaleLowerCase();
	const haystack = (searchValue ?? `${label} ${description ?? ""} ${value}`).toLocaleLowerCase();
	if (query && !haystack.includes(query)) return null;
	const selected = context.value === value;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
		type: "button",
		role: "option",
		"aria-selected": selected,
		className: className ?? "acpModelSelectorItem",
		disabled,
		onClick: () => context.selectValue(value),
		...props,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
			className: "acpModelSelectorItemText",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpModelSelectorItemName",
				children: label
			}), description ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpModelSelectorItemDesc",
				children: description
			}) : null]
		}), selected ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "acpModelSelectorItemCheck",
			"aria-hidden": "true",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelectorCheck, {})
		}) : null]
	});
}
function ModelSelectorCheck() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		width: "12",
		height: "12",
		viewBox: "0 0 12 12",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M2.5 6L5 8.5L9.5 3.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.5",
			strokeLinecap: "round",
			strokeLinejoin: "round"
		})
	});
}
var ModelSelector = {
	Root,
	Trigger,
	Content,
	Search,
	List,
	Group,
	Item
};
//#endregion
//#region views/acp-chat/src/components/ModelPicker.tsx
function ModelPicker(props) {
	const selectedMode = props.modes.find((mode) => mode.id === props.currentModeId);
	const modelOption = props.configOptions.find(isModelSelectOption);
	const selectedModel = modelOption?.options.find((option) => option.value === modelOption.currentValue);
	const otherConfigOptions = props.configOptions.filter((option) => option !== modelOption);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "acpModelPicker",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
				className: "acpModelPickerControl",
				title: selectedMode?.description,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpModelPickerLabel",
					children: "Mode"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(ModelSelector.Root, {
					value: props.currentModeId ?? "",
					disabled: props.disabled || props.modes.length === 0,
					onValueChange: props.onSelectMode,
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Trigger, { children: selectedMode?.name ?? (props.modes.length > 0 ? "Select mode..." : "No modes") }), props.modes.length > 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(ModelSelector.Content, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Search, { placeholder: "Search modes..." }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.List, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Group, {
						label: "Modes",
						children: props.modes.map((mode) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Item, {
							value: mode.id,
							label: mode.name,
							description: mode.description,
							searchValue: `${mode.name} ${mode.id} ${mode.description ?? ""}`
						}, mode.id))
					}) })] }) : null]
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
				className: "acpModelPickerControl",
				title: modelOption?.description,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpModelPickerLabel",
					children: "Model"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(ModelSelector.Root, {
					value: modelOption?.currentValue ?? "",
					disabled: props.disabled || !modelOption || modelOption.options.length === 0,
					onValueChange: (value) => {
						if (modelOption) props.onSelectConfigOption(modelOption, value);
					},
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Trigger, { children: selectedModel?.name ?? (modelOption ? "Select model..." : "No models") }), modelOption ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(ModelSelector.Content, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Search, { placeholder: "Search models..." }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.List, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Group, {
						label: modelOption.name,
						children: modelOption.options.map((option) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelSelector.Item, {
							value: option.value,
							label: option.name,
							description: option.description,
							searchValue: `${option.name} ${option.value} ${option.description ?? ""}`
						}, option.value))
					}) })] }) : null]
				})]
			}),
			otherConfigOptions.map((option) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ConfigOptionControl, {
				option,
				disabled: props.disabled,
				onChange: (value) => props.onSelectConfigOption(option, value)
			}, option.id))
		]
	});
}
function ConfigOptionControl(props) {
	const { option } = props;
	if (option.type === "select") return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
		className: "acpModelPickerControl",
		title: option.description,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "acpModelPickerLabel",
			children: option.name
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Select, {
			className: "acpConfigOptionSelect",
			value: option.currentValue,
			disabled: props.disabled || option.options.length === 0,
			placeholder: "Select...",
			options: option.options.map((choice) => ({
				value: choice.value,
				label: choice.name,
				textValue: `${choice.name} ${choice.value} ${choice.description ?? ""}`
			})),
			onValueChange: props.onChange
		})]
	});
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
		className: "acpModelPickerControl acpConfigBooleanControl",
		title: option.description,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "acpModelPickerLabel",
			children: option.name
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Switch, {
			className: "acpConfigSwitch",
			checked: option.currentValue,
			disabled: props.disabled,
			onCheckedChange: props.onChange,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SwitchThumb, { className: "acpConfigSwitchThumb" })
		})]
	});
}
function isModelSelectOption(option) {
	if (option.type !== "select") return false;
	if (option.category === "model") return true;
	if (isModelText(option.id) || isModelText(option.name)) return true;
	return option.options.some((choice) => isKnownModelText(choice.value) || isKnownModelText(choice.name));
}
function isModelText(value) {
	return value?.toLocaleLowerCase().includes("model") === true;
}
function isKnownModelText(value) {
	const normalized = value?.toLocaleLowerCase() ?? "";
	return normalized.includes("gemini") || normalized.includes("claude") || normalized.includes("gpt") || normalized.includes("llama") || normalized.includes("mistral") || normalized.includes("sonnet") || normalized.includes("opus") || normalized.includes("haiku");
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
//#region views/acp-chat/src/components/SlashCommandMenu.tsx
var slashCommandFormatter = {
	serialize(item) {
		return commandPrefix(item.id);
	},
	parse(text) {
		return [{
			kind: "text",
			text
		}];
	}
};
function SlashCommandMenu(props) {
	const slashCommands = (0, import_react.useMemo)(() => props.commands.map((command) => ({
		id: command.name,
		label: commandPrefix(command.name),
		description: command.description || command.inputHint,
		execute() {}
	})), [props.commands]);
	const commandByName = (0, import_react.useMemo)(() => new Map(props.commands.map((command) => [command.name, command])), [props.commands]);
	const slash = unstable_useSlashCommandAdapter({
		commands: slashCommands,
		removeOnExecute: false
	});
	if (props.commands.length === 0) return null;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(composer_exports.Unstable_TriggerPopover, {
		char: "/",
		adapter: slash.adapter,
		className: "acpSlashCommandMenu",
		"aria-label": "Slash commands",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Unstable_TriggerPopover.Action, {
			...slash.action,
			formatter: slashCommandFormatter
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SlashCommandItems, { commandByName })]
	});
}
function SlashCommandItems(props) {
	const popover = useTriggerPopoverScopeContext();
	const listRef = (0, import_react.useRef)(null);
	(0, import_react.useEffect)(() => {
		const list = listRef.current;
		const highlighted = list?.querySelector(".acpSlashCommandItem[data-highlighted]");
		if (!list || !highlighted) return;
		scrollElementIntoNearestView(list, highlighted);
	}, [popover.highlightedIndex, popover.items]);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Unstable_TriggerPopoverItems, {
		ref: listRef,
		className: "acpSlashCommandItems",
		children: (items) => items.length > 0 ? items.map((item, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SlashCommandItem, {
			item,
			index,
			command: props.commandByName.get(item.id)
		}, item.id)) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "acpSlashCommandEmpty",
			children: "No commands"
		})
	});
}
function SlashCommandItem(props) {
	const composer = useComposerRuntime();
	const popover = useTriggerPopoverScopeContext();
	function insertCommand() {
		composer.setText(replaceActiveSlashCommand(composer.getState().text, popover.query, props.item.id));
		popover.close();
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Unstable_TriggerPopoverItem, {
		item: props.item,
		index: props.index,
		className: "acpSlashCommandItem",
		onMouseDown: (event) => {
			event.preventDefault();
			insertCommand();
		},
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
			className: "acpSlashCommandText",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpSlashCommandName",
					children: props.item.label
				}),
				props.command?.description ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpSlashCommandDesc",
					children: props.command.description
				}) : null,
				props.command?.inputHint ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "acpSlashCommandHint",
					children: props.command.inputHint
				}) : null
			]
		})
	});
}
function commandPrefix(name) {
	return name.startsWith("/") ? name : `/${name}`;
}
function replaceActiveSlashCommand(text, query, commandName) {
	const token = `/${query}`;
	const index = text.lastIndexOf(token);
	if (index >= 0 && isTokenBoundary(text, index)) {
		const before = text.slice(0, index);
		const after = text.slice(index + token.length);
		return before + commandPrefix(commandName) + (after.startsWith(" ") ? after : ` ${after}`);
	}
	return `${text}${text.length === 0 || text.endsWith(" ") ? "" : " "}${commandPrefix(commandName)} `;
}
function isTokenBoundary(text, index) {
	return index === 0 || /\s/u.test(text[index - 1] ?? "");
}
function scrollElementIntoNearestView(container, element) {
	const containerRect = container.getBoundingClientRect();
	const elementRect = element.getBoundingClientRect();
	if (elementRect.top < containerRect.top) container.scrollTop -= containerRect.top - elementRect.top;
	else if (elementRect.bottom > containerRect.bottom) container.scrollTop += elementRect.bottom - containerRect.bottom;
}
//#endregion
//#region views/acp-chat/src/components/ThinkingBlock.tsx
var SMOOTH_TEXT_OPTIONS$1 = {
	drainMs: 250,
	maxCharIntervalMs: 5,
	minCommitMs: 33
};
function ThinkingBlock() {
	const { text, status } = useSmooth(useMessagePartReasoning(), SMOOTH_TEXT_OPTIONS$1);
	const messageStatus = useMessage((message) => message.status);
	const running = status.type === "running" || messageStatus?.type === "running";
	if (!text && !running) return null;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("details", {
		className: "acpThinking",
		open: true,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("summary", {
			className: "acpThinkingSummary",
			children: "Thinking"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "acpThinkingBody",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MarkdownRenderer, {
				text,
				className: "acpMarkdown acpThinkingMarkdown"
			}), running ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				"aria-hidden": "true",
				className: "acpStreamingCaret"
			}) : null]
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
var SMOOTH_TEXT_OPTIONS = {
	drainMs: 250,
	maxCharIntervalMs: 5,
	minCommitMs: 33
};
var ACP_CHAT_ICONS = IconSet.define("AcpChatIcons");
var SEND_ICON_PATH = "icons/acpChatSend.svg";
function ChatView() {
	const chat = useAcpChat();
	const notifyOnUnsupportedImagePaste = (event) => {
		if (chat.promptCapabilities.image) return;
		if (!Array.from(event.clipboardData.files).some((file) => file.type.startsWith("image/"))) return;
		event.preventDefault();
		chat.notifyAttachmentCapabilitiesUnavailable();
	};
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AssistantRuntimeProvider, {
		runtime: chat.runtime,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "acpChatLayout",
			children: [
				chat.status ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("header", {
					className: "acpChatHeader",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "acpChatStatus",
						children: chat.status
					})
				}) : null,
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(PlanView, { plan: chat.plan }),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(thread_exports.Root, {
					className: "acpThread",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(thread_exports.Viewport, {
							className: "acpThreadViewport",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(thread_exports.Empty, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
								className: "acpEmpty",
								children: "Select an agent and send a message to start."
							}) }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(thread_exports.Messages, { components: {
								UserMessage,
								AssistantMessage
							} })]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(selectionToolbar_exports.Root, {
							className: "acpSelectionToolbar",
							"aria-label": "Selection actions",
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(selectionToolbar_exports.Quote, {
								className: "acpSelectionToolbarButton",
								children: "Quote"
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "acpComposerShell",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Unstable_TriggerPopoverRoot, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(composer_exports.Root, {
								className: "acpComposer",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "acpComposerMain",
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(composer_exports.Quote, {
											className: "acpComposerQuote",
											children: [
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
													className: "acpComposerQuoteLabel",
													children: "Quote"
												}),
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.QuoteText, { className: "acpComposerQuoteText" }),
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.QuoteDismiss, {
													className: "acpComposerQuoteDismiss",
													"aria-label": "Remove quote",
													title: "Remove quote",
													children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RemoveIcon, {})
												})
											]
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
											className: "acpAttachmentList acpComposerAttachments",
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Attachments, { children: () => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AttachmentChip, { removable: true }) })
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Input, {
											className: "acpComposerInput",
											placeholder: "Message the agent…",
											onPaste: notifyOnUnsupportedImagePaste
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(composer_exports.Send, {
											className: "acpComposerSend",
											"aria-label": "Send",
											title: "Send",
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
												className: "acpComposerSendIcon",
												src: ACP_CHAT_ICONS.src(SEND_ICON_PATH),
												alt: "",
												draggable: false
											})
										})
									]
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SlashCommandMenu, { commands: chat.commands })]
							}) }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "acpComposerToolbar",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(AgentSelector, {
									agents: chat.agents,
									selectedAgentId: chat.selectedAgentId,
									starting: chat.starting,
									onSelect: chat.selectAgent
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ModelPicker, {
									modes: chat.modes,
									configOptions: chat.configOptions,
									currentModeId: chat.currentModeId,
									disabled: chat.starting || chat.selectedAgentId == null,
									onSelectMode: chat.selectMode,
									onSelectConfigOption: chat.selectConfigOption
								})]
							})]
						})
					]
				}),
				chat.permission ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ApprovalPrompt, { permission: chat.permission }) : null,
				chat.auth ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuthPrompt, { auth: chat.auth }) : null
			]
		})
	});
}
function UserMessage() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(message_exports.Root, {
		className: "acpMsg acpMsgUser",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(message_exports.Quote, { children: (quote) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("blockquote", {
				className: "acpMessageQuote",
				children: quote.text
			}) }),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(message_exports.Parts, { components: { Text: PlainText } }),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
				className: "acpAttachmentList acpMessageAttachments",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(message_exports.Attachments, { children: () => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AttachmentChip, {}) })
			})
		]
	});
}
function AssistantMessage() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(message_exports.Root, {
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
function AttachmentChip(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(attachment_exports.Root, {
		className: "acpAttachmentChip",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(attachment_exports.unstable_Thumb, { className: "acpAttachmentThumb" }),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "acpAttachmentName",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(attachment_exports.Name, {})
			}),
			props.removable ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(attachment_exports.Remove, {
				className: "acpAttachmentRemove",
				"aria-label": "Remove attachment",
				title: "Remove attachment",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RemoveIcon, {})
			}) : null
		]
	});
}
function RemoveIcon() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		width: "10",
		height: "10",
		viewBox: "0 0 10 10",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M2.5 2.5L7.5 7.5M7.5 2.5L2.5 7.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.4",
			strokeLinecap: "round"
		})
	});
}
function MarkdownText() {
	const { text, status } = useSmooth(useMessagePartText(), SMOOTH_TEXT_OPTIONS);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MarkdownRenderer, {
		text,
		streaming: status.type === "running"
	});
}
//#endregion
//#region views/acp-chat/src/main.tsx
var container = document.getElementById("root");
if (container) (0, import_client.createRoot)(container).render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ChatView, {}));
//#endregion
