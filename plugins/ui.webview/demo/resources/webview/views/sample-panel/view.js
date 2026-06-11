import { o as __toESM } from "./assets/rolldown-runtime.js";
import { t as require_react } from "./assets/react.js";
import { t as require_client } from "./assets/react-dom.js";
import { u as mermaid_default } from "./assets/mermaid.js";
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
var webViewTheme = createLazyWebViewTheme();
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
//#region views/sample-panel/src/main.ts
window.mermaid = mermaid_default;
/**
* @typedef {Object} Task
* @property {string} id
* @property {string} title
* @property {string} owner
* @property {string} status
* @property {string} priority
* @property {boolean} blocked
* @property {number} progress
* @property {number} dueOffset
* @property {string} dueLabel
* @property {number} estimateHours
* @property {string[]} tags
*/
/**
* @typedef {Object} TimelineEvent
* @property {string} id
* @property {string} taskId
* @property {string} label
*/
/**
* @typedef {Object} BoardSnapshot
* @property {Task[]} tasks
* @property {TimelineEvent[]} timeline
* @property {number} updatedAt
*/
var METHODS = {
	SNAPSHOT: "demo/board/snapshot",
	READY: "demo/board/ready",
	TASK_CLICKED: "demo/board/taskClicked",
	ADVANCE: "demo/board/advance",
	TOGGLE_BLOCKED: "demo/board/toggleBlocked",
	MOVE_STATUS: "demo/board/moveStatus",
	SET_STATUS: "demo/board/setStatus",
	SET_AUTO_TICK: "demo/board/setAutoTick",
	ADD_TASK: "demo/board/addTask",
	DELETE_TASK: "demo/board/deleteTask"
};
var NOTIFICATIONS = {
	SNAPSHOT: { method: METHODS.SNAPSHOT },
	READY: { method: METHODS.READY },
	TASK_CLICKED: { method: METHODS.TASK_CLICKED },
	ADVANCE: { method: METHODS.ADVANCE },
	TOGGLE_BLOCKED: { method: METHODS.TOGGLE_BLOCKED },
	MOVE_STATUS: { method: METHODS.MOVE_STATUS },
	SET_STATUS: { method: METHODS.SET_STATUS },
	SET_AUTO_TICK: { method: METHODS.SET_AUTO_TICK },
	ADD_TASK: { method: METHODS.ADD_TASK },
	DELETE_TASK: { method: METHODS.DELETE_TASK }
};
var DRAG_MIME = "application/x-ij-webview-task-id";
function bindNotification(notification) {
	if (!webView || typeof webView.notification !== "function") {
		console.warn("[app] __WVI__ bridge missing; cannot bind " + notification.method);
		return;
	}
	return webView.notification(notification);
}
function notifyKotlin(notification, params) {
	try {
		const binding = bindNotification(notification);
		if (binding) binding.send(params);
	} catch (err) {
		console.error("[app] notify failed for " + notification.method, err);
	}
}
function registerNotificationHandler(notification, handler) {
	const binding = bindNotification(notification);
	if (!binding) return;
	const registration = binding.on(handler);
	return function closeRegistration() {
		registration.close();
	};
}
var rootNode = document.getElementById("root");
if (!rootNode) throw new Error("#root missing");
function renderFallback(reason) {
	rootNode.innerHTML = "";
	const wrapper = document.createElement("div");
	wrapper.className = "fallback";
	const title = document.createElement("h2");
	title.textContent = "Demo runtime is unavailable";
	wrapper.appendChild(title);
	const info = document.createElement("p");
	info.textContent = reason;
	wrapper.appendChild(info);
	rootNode.appendChild(wrapper);
}
var h = import_react.createElement;
var useCallback = import_react.useCallback;
var useEffect = import_react.useEffect;
var useMemo = import_react.useMemo;
var useRef = import_react.useRef;
var useState = import_react.useState;
var STATUSES = [
	"Backlog",
	"In Progress",
	"Review",
	"Done"
];
var PRIORITIES = [
	"Low",
	"Medium",
	"High",
	"Critical"
];
var STATUS_FILTER_OPTIONS = [{
	value: "All",
	label: "All statuses"
}].concat(STATUSES.map(function(status) {
	return {
		value: status,
		label: status
	};
}));
var PRIORITY_FILTER_OPTIONS = [{
	value: "All",
	label: "All priorities"
}].concat(PRIORITIES.map(function(priority) {
	return {
		value: priority,
		label: priority
	};
}));
var PRIORITY_WEIGHT = {
	Low: 1,
	Medium: 2,
	High: 3,
	Critical: 4
};
function cssStatus(status) {
	return "status-" + status.toLowerCase().replace(/\s+/g, "-");
}
function cssPriority(priority) {
	return "priority-" + priority.toLowerCase();
}
var ACTION_BUTTON_STYLE = {
	padding: "2px 6px",
	fontSize: "11px",
	lineHeight: "1.1",
	minHeight: "20px"
};
var ACTION_ROW_STYLE = {
	display: "flex",
	gap: "4px",
	marginTop: "6px",
	flexWrap: "wrap"
};
/**
* @param {{task: Task, active: boolean, onSelect: function(string): void, onDragStart: function(string, Event): void, onDragEnd: function(): void}} props
*/
function TaskCard(props) {
	const task = props.task;
	function actionHandler(notification, params) {
		return function(event) {
			event.stopPropagation();
			notifyKotlin(notification, params || {});
		};
	}
	return h("article", {
		className: props.active ? "task-card is-active" : "task-card",
		draggable: true,
		onDragStart: function(event) {
			props.onDragStart(task.id, event);
		},
		onDragEnd: function() {
			props.onDragEnd();
		},
		onClick: function() {
			props.onSelect(task.id);
		}
	}, h("div", { className: "task-title" }, task.title), h("div", { className: "task-subtitle" }, task.id + " • " + task.owner), h("div", { className: "badge-row" }, h("span", { className: "badge " + cssStatus(task.status) }, task.status), h("span", { className: "badge " + cssPriority(task.priority) }, task.priority), task.blocked ? h("span", { className: "badge priority-critical" }, "Blocked") : null), h("div", { className: "progress" }, h("div", {
		className: "progress-fill",
		style: { width: String(task.progress) + "%" }
	})), h("div", {
		className: "task-actions",
		style: ACTION_ROW_STYLE
	}, h("button", {
		className: "btn-control",
		type: "button",
		style: ACTION_BUTTON_STYLE,
		title: "Move to previous status",
		onClick: actionHandler(NOTIFICATIONS.MOVE_STATUS, {
			taskId: task.id,
			direction: "prev"
		})
	}, "◀"), h("button", {
		className: "btn-control",
		type: "button",
		style: ACTION_BUTTON_STYLE,
		title: "Advance progress by 10%",
		onClick: actionHandler(NOTIFICATIONS.ADVANCE, { taskId: task.id })
	}, "+10%"), h("button", {
		className: "btn-control",
		type: "button",
		style: ACTION_BUTTON_STYLE,
		title: "Move to next status",
		onClick: actionHandler(NOTIFICATIONS.MOVE_STATUS, {
			taskId: task.id,
			direction: "next"
		})
	}, "▶"), h("button", {
		className: "btn-control",
		type: "button",
		style: ACTION_BUTTON_STYLE,
		title: task.blocked ? "Unblock" : "Mark blocked",
		onClick: actionHandler(NOTIFICATIONS.TOGGLE_BLOCKED, { taskId: task.id })
	}, task.blocked ? "Unblock" : "Block"), h("button", {
		className: "btn-control",
		type: "button",
		style: ACTION_BUTTON_STYLE,
		title: "Delete task",
		onClick: actionHandler(NOTIFICATIONS.DELETE_TASK, { taskId: task.id })
	}, "✕")));
}
/**
* @param {{accent: string, label: string, value: string, note: string}} props
*/
function MetricCard(props) {
	return h("div", {
		className: "metric-card",
		"data-accent": props.accent
	}, h("div", { className: "metric-label" }, props.label), h("div", { className: "metric-value" }, props.value), h("div", { className: "metric-note" }, props.note));
}
function selectedOptionIndex(options, value) {
	for (let i = 0; i < options.length; i++) if (options[i].value === value) return i;
	return 0;
}
function usePopupDismiss(open, rootRef, setOpen) {
	useEffect(function() {
		if (!open) return void 0;
		function onPointerDown(event) {
			if (rootRef.current && !rootRef.current.contains(event.target)) setOpen(false);
		}
		function onKeyDown(event) {
			if (event.key === "Escape") setOpen(false);
		}
		function onFocusIn(event) {
			if (rootRef.current && !rootRef.current.contains(event.target)) setOpen(false);
		}
		window.addEventListener("pointerdown", onPointerDown, true);
		window.addEventListener("keydown", onKeyDown, true);
		window.addEventListener("focusin", onFocusIn, true);
		return function() {
			window.removeEventListener("pointerdown", onPointerDown, true);
			window.removeEventListener("keydown", onKeyDown, true);
			window.removeEventListener("focusin", onFocusIn, true);
		};
	}, [
		open,
		rootRef,
		setOpen
	]);
}
function DropdownSelect(props) {
	const options = props.options || [];
	const rootRef = useRef(null);
	const [open, setOpen] = useState(false);
	const [activeIndex, setActiveIndex] = useState(0);
	const selectedIndex = selectedOptionIndex(options, props.value);
	const selected = options[selectedIndex] || {
		value: "",
		label: ""
	};
	const listboxId = props.id + "-listbox";
	useEffect(function() {
		setActiveIndex(selectedIndex);
	}, [selectedIndex, open]);
	usePopupDismiss(open, rootRef, setOpen);
	function moveActive(delta) {
		if (options.length === 0) return;
		setActiveIndex(function(index) {
			return (index + delta + options.length) % options.length;
		});
	}
	function selectAt(index) {
		const option = options[index];
		if (!option) return;
		props.onChange(option.value);
		setOpen(false);
	}
	function onButtonKeyDown(event) {
		if (event.key === "ArrowDown") {
			event.preventDefault();
			if (!open) {
				setActiveIndex(selectedIndex);
				setOpen(true);
			} else moveActive(1);
		} else if (event.key === "ArrowUp") {
			event.preventDefault();
			if (!open) {
				setActiveIndex(selectedIndex);
				setOpen(true);
			} else moveActive(-1);
		} else if (event.key === "Enter" || event.key === " ") {
			event.preventDefault();
			if (open) selectAt(activeIndex);
			else {
				setActiveIndex(selectedIndex);
				setOpen(true);
			}
		} else if (event.key === "Escape" && open) {
			event.preventDefault();
			setOpen(false);
		}
	}
	return h("div", {
		className: "combo-control",
		ref: rootRef
	}, h("button", {
		className: open ? "combo-button is-open" : "combo-button",
		type: "button",
		role: "combobox",
		"aria-controls": listboxId,
		"aria-expanded": open ? "true" : "false",
		"aria-haspopup": "listbox",
		"aria-label": props.label,
		onClick: function() {
			setActiveIndex(selectedIndex);
			setOpen(!open);
		},
		onKeyDown: onButtonKeyDown
	}, h("span", { className: "combo-button-label" }, selected.label)), open ? h("div", {
		className: "ij-popup combo-list",
		id: listboxId,
		role: "listbox"
	}, options.map(function(option, index) {
		const selectedOption = option.value === props.value;
		return h("button", {
			className: "combo-option" + (selectedOption ? " is-selected" : "") + (index === activeIndex ? " is-active" : ""),
			key: option.value,
			type: "button",
			tabIndex: -1,
			role: "option",
			"aria-selected": selectedOption ? "true" : "false",
			onMouseDown: function(event) {
				event.preventDefault();
			},
			onMouseEnter: function() {
				setActiveIndex(index);
			},
			onClick: function() {
				selectAt(index);
			}
		}, h("span", { className: "combo-option-mark" }), h("span", { className: "combo-option-label" }, option.label));
	})) : null);
}
function PopupMenuButton(props) {
	const items = props.items || [];
	const rootRef = useRef(null);
	const [open, setOpen] = useState(false);
	const [activeIndex, setActiveIndex] = useState(0);
	usePopupDismiss(open, rootRef, setOpen);
	function moveActive(delta) {
		if (items.length === 0) return;
		setActiveIndex(function(index) {
			return (index + delta + items.length) % items.length;
		});
	}
	function runItem(index) {
		const item = items[index];
		if (!item || item.disabled) return;
		setOpen(false);
		item.onSelect();
	}
	function onButtonKeyDown(event) {
		if (event.key === "ArrowDown") {
			event.preventDefault();
			if (!open) {
				setActiveIndex(0);
				setOpen(true);
			} else moveActive(1);
		} else if (event.key === "ArrowUp") {
			event.preventDefault();
			if (!open) {
				setActiveIndex(Math.max(0, items.length - 1));
				setOpen(true);
			} else moveActive(-1);
		} else if (event.key === "Enter" || event.key === " ") {
			event.preventDefault();
			if (open) runItem(activeIndex);
			else {
				setActiveIndex(0);
				setOpen(true);
			}
		} else if (event.key === "Escape" && open) {
			event.preventDefault();
			setOpen(false);
		}
	}
	return h("div", {
		className: "menu-control",
		ref: rootRef
	}, h("button", {
		className: open ? "btn-control menu-button is-open" : "btn-control menu-button",
		type: "button",
		title: props.title,
		"aria-haspopup": "menu",
		"aria-expanded": open ? "true" : "false",
		onClick: function() {
			setActiveIndex(0);
			setOpen(!open);
		},
		onKeyDown: onButtonKeyDown
	}, h("span", { className: "menu-button-label" }, props.label)), open ? h("div", {
		className: "ij-popup popup-menu",
		role: "menu"
	}, items.map(function(item, index) {
		return h("button", {
			className: "menu-item" + (index === activeIndex ? " is-active" : "") + (item.checked ? " is-checked" : ""),
			key: item.label,
			type: "button",
			tabIndex: -1,
			role: item.checked ? "menuitemcheckbox" : "menuitem",
			"aria-checked": item.checked ? "true" : null,
			disabled: item.disabled || null,
			onMouseDown: function(event) {
				event.preventDefault();
			},
			onMouseEnter: function() {
				setActiveIndex(index);
			},
			onClick: function() {
				runItem(index);
			}
		}, h("span", { className: "menu-item-check" }), h("span", { className: "menu-item-label" }, item.label));
	})) : null);
}
function WaitingForModel() {
	return h("div", { className: "app-shell" }, h("section", { className: "panel header" }, h("div", { className: "title-block" }, h("h1", null, "Waiting for Kotlin model…"), h("p", null, "The board data now lives in Kotlin and is streamed over the WebView bridge."))));
}
/**
* Renders a mermaid diagram whose source string comes from Kotlin. Re-renders
* only when the `source` prop changes; stale async renders are discarded via
* a monotonic render id so that fast-arriving snapshots do not stomp on
* later SVGs.
*
* Interactive layer on top of the raw mermaid output:
*   - mouse-wheel zoom around the cursor (clamped to [0.3, 3])
*   - drag-to-pan when grabbing empty space
*   - per-node drag (translates the node group's `transform` attribute)
*   - node positions survive snapshot re-renders (captured by the node's
*     mermaid id and re-applied after each new SVG is injected)
*   - Reset button returns to identity transform and discards saved drags
*/
function MermaidDiagram(props) {
	const source = props.source;
	const theme = props.theme;
	const wrapperRef = useRef(null);
	const viewportRef = useRef(null);
	const hostRef = useRef(null);
	const renderIdRef = useRef(0);
	const transformRef = useRef({
		zoom: 1,
		x: 0,
		y: 0
	});
	const positionsRef = useRef({});
	function getOrCreatePanZoomGroup() {
		if (!hostRef.current) return null;
		const svg = hostRef.current.querySelector("svg");
		if (!svg) return null;
		let g = svg.querySelector(":scope > g.ij-pan-zoom");
		if (g) return g;
		g = document.createElementNS("http://www.w3.org/2000/svg", "g");
		g.setAttribute("class", "ij-pan-zoom");
		g.style.transformBox = "fill-box";
		g.style.transformOrigin = "0 0";
		const keep = {
			defs: 1,
			style: 1,
			title: 1,
			desc: 1,
			metadata: 1,
			marker: 1
		};
		const kids = [];
		for (let i = 0; i < svg.childNodes.length; i++) kids.push(svg.childNodes[i]);
		for (let i = 0; i < kids.length; i++) {
			const c = kids[i];
			if (c.nodeType !== 1) continue;
			if (keep[c.tagName.toLowerCase()]) continue;
			g.appendChild(c);
		}
		svg.appendChild(g);
		svg.style.overflow = "visible";
		return g;
	}
	function applyViewportTransform(animate = false) {
		const g = getOrCreatePanZoomGroup();
		if (!g) return;
		if (animate) {
			g.style.transition = "transform 180ms ease-out";
			clearTimeout(g.__mmTrTimer);
			g.__mmTrTimer = setTimeout(function() {
				g.style.transition = "";
			}, 220);
		} else if (g.style.transition) g.style.transition = "";
		const t = transformRef.current;
		g.style.transform = "translate(" + t.x + "px, " + t.y + "px) scale(" + t.zoom + ")";
	}
	function cssVar(name, fallback) {
		try {
			return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
		} catch (err) {
			return fallback;
		}
	}
	function buildMermaidConfig() {
		const isLight = document.documentElement.getAttribute("data-theme") === "light";
		const bg = cssVar("--ij-bg-panel", isLight ? "#F7F8F9" : "#212326");
		const bgAlt = cssVar("--ij-bg-panel-alt", isLight ? "#FFFFFF" : "#26282C");
		const bgHover = cssVar("--ij-bg-hover", isLight ? "#00000012" : "#FFFFFF17");
		const border = cssVar("--ij-border-strong", isLight ? "#D1D3D9" : "#40434A");
		const txtPrim = cssVar("--ij-text-primary", isLight ? "#000000" : "#D1D3D9");
		const txtSec = cssVar("--ij-text-secondary", "#73767C");
		const fontStack = cssVar("--ij-font", "\"Inter\", \"Segoe UI\", -apple-system, BlinkMacSystemFont, \"Helvetica Neue\", sans-serif");
		return {
			startOnLoad: false,
			securityLevel: "strict",
			fontFamily: fontStack,
			theme: "base",
			themeVariables: {
				fontFamily: fontStack,
				fontSize: "13px",
				primaryColor: bg,
				primaryBorderColor: border,
				primaryTextColor: txtPrim,
				secondaryColor: bgHover,
				tertiaryColor: bgAlt,
				lineColor: txtSec,
				textColor: txtPrim,
				titleColor: txtPrim,
				nodeBorder: border,
				mainBkg: bg,
				clusterBkg: bgAlt,
				clusterBorder: border,
				edgeLabelBackground: bg
			},
			themeCSS: ".node rect, .node polygon, .node circle, .node path, .node ellipse {  stroke-width: 1.6px;  rx: 4px; ry: 4px;}.node .nodeLabel, .node foreignObject span, .node text {  font-weight: 500;}.edgePath .path, path.flowchart-link {  stroke-width: 1.75px;}.cluster rect {  stroke-width: 1.6px;  rx: 4px; ry: 4px;}.cluster .nodeLabel, .cluster text {  font-weight: 600;  font-size: 12px;}.marker { fill: " + txtSec + "; stroke: " + txtSec + "; }"
		};
	}
	function getNodeKey(el) {
		const id = el.id || "";
		const m = id.match(/^flowchart-(.+?)(?:-\d+)?$/);
		return m ? m[1] : id;
	}
	function getNodeCenter(nodeEl) {
		const m = (nodeEl.getAttribute("transform") || "").match(/translate\s*\(\s*(-?[\d.]+)[\s,]+(-?[\d.]+)\s*\)/);
		return m ? {
			x: parseFloat(m[1]),
			y: parseFloat(m[2])
		} : null;
	}
	function getNodeKeysSet() {
		const set = /* @__PURE__ */ new Set();
		if (!hostRef.current) return set;
		const groups = hostRef.current.querySelectorAll("g.node, g.cluster");
		for (let i = 0; i < groups.length; i++) {
			const k = getNodeKey(groups[i]);
			if (k) set.add(k);
		}
		return set;
	}
	function getEdgeEndpoints(edgeEl, nodeKeys) {
		const cls = edgeEl.classList;
		let src = null;
		let tgt = null;
		for (let i = 0; i < cls.length; i++) {
			const c = cls[i];
			if (c.indexOf("LS-") === 0) src = c.slice(3);
			else if (c.indexOf("LE-") === 0) tgt = c.slice(3);
		}
		if (src && tgt) return {
			src,
			tgt
		};
		const id = edgeEl.id || "";
		if (id.indexOf("L_") !== 0) return null;
		const lastUs = id.lastIndexOf("_");
		if (lastUs <= 2) return null;
		const middle = id.slice(2, lastUs);
		for (let i = 1; i < middle.length; i++) {
			if (middle.charCodeAt(i) !== 95) continue;
			const s = middle.slice(0, i);
			const t = middle.slice(i + 1);
			if (nodeKeys.has(s) && nodeKeys.has(t)) return {
				src: s,
				tgt: t
			};
		}
		return null;
	}
	function findNodeByKey(key) {
		if (!hostRef.current) return null;
		const groups = hostRef.current.querySelectorAll("g.node, g.cluster");
		for (let i = 0; i < groups.length; i++) if (getNodeKey(groups[i]) === key) return groups[i];
		return null;
	}
	function redrawEdge(edgeEl, srcKey, tgtKey) {
		const sN = findNodeByKey(srcKey);
		const tN = findNodeByKey(tgtKey);
		if (!sN || !tN) return;
		const sC = getNodeCenter(sN);
		const tC = getNodeCenter(tN);
		if (!sC || !tC) return;
		edgeEl.setAttribute("d", "M " + sC.x + "," + sC.y + " L " + tC.x + "," + tC.y);
	}
	function updateEdgesFor(nodeKey) {
		if (!hostRef.current || !nodeKey) return;
		const keys = getNodeKeysSet();
		const edges = hostRef.current.querySelectorAll("path.flowchart-link");
		for (let i = 0; i < edges.length; i++) {
			const ep = getEdgeEndpoints(edges[i], keys);
			if (!ep) continue;
			if (ep.src === nodeKey || ep.tgt === nodeKey) redrawEdge(edges[i], ep.src, ep.tgt);
		}
	}
	function updateEdgesForDraggedNodes() {
		if (!hostRef.current) return;
		const dragged = positionsRef.current;
		if (!dragged) return;
		const keys = getNodeKeysSet();
		const edges = hostRef.current.querySelectorAll("path.flowchart-link");
		for (let i = 0; i < edges.length; i++) {
			const ep = getEdgeEndpoints(edges[i], keys);
			if (!ep) continue;
			if (dragged[ep.src] || dragged[ep.tgt]) redrawEdge(edges[i], ep.src, ep.tgt);
		}
	}
	function capturePositions() {
		if (!hostRef.current) return;
		const nodes = hostRef.current.querySelectorAll("g.node");
		const acc = {};
		for (let i = 0; i < nodes.length; i++) {
			const key = getNodeKey(nodes[i]);
			const t = nodes[i].getAttribute("transform");
			if (key && t) acc[key] = t;
		}
		positionsRef.current = acc;
	}
	function restorePositions() {
		if (!hostRef.current) return;
		const saved = positionsRef.current;
		if (!saved) return;
		const nodes = hostRef.current.querySelectorAll("g.node");
		for (let i = 0; i < nodes.length; i++) {
			const key = getNodeKey(nodes[i]);
			if (saved[key]) nodes[i].setAttribute("transform", saved[key]);
		}
		updateEdgesForDraggedNodes();
	}
	function bumpZoom(factor, anchor, animate) {
		const t = transformRef.current;
		const newZoom = Math.max(.3, Math.min(3, t.zoom * factor));
		const s = newZoom / t.zoom;
		if (anchor) {
			t.x = anchor.x - (anchor.x - t.x) * s;
			t.y = anchor.y - (anchor.y - t.y) * s;
		}
		t.zoom = newZoom;
		applyViewportTransform(!!animate);
	}
	function resetView() {
		transformRef.current = {
			zoom: 1,
			x: 0,
			y: 0
		};
		positionsRef.current = {};
		applyViewportTransform(true);
		if (window.mermaid && hostRef.current && source) {
			const renderId = ++renderIdRef.current;
			const domId = "mm-reset-" + renderId + "-" + Date.now();
			window.mermaid.render(domId, source).then(function(result) {
				if (renderIdRef.current !== renderId || !hostRef.current) return;
				hostRef.current.innerHTML = result && result.svg ? result.svg : "";
			});
		}
	}
	function onPointerDown(e) {
		if (e.button !== 0) return;
		const nodeGroup = e.target.closest ? e.target.closest("g.node") : null;
		if (nodeGroup) startNodeDrag(e, nodeGroup);
		else startPan(e);
	}
	function startPan(e) {
		e.preventDefault();
		const v = viewportRef.current;
		if (v) v.classList.add("is-panning");
		const startX = e.clientX;
		const startY = e.clientY;
		const t0 = {
			x: transformRef.current.x,
			y: transformRef.current.y
		};
		function move(ev) {
			transformRef.current.x = t0.x + (ev.clientX - startX);
			transformRef.current.y = t0.y + (ev.clientY - startY);
			applyViewportTransform();
		}
		function up() {
			window.removeEventListener("pointermove", move);
			window.removeEventListener("pointerup", up);
			if (v) v.classList.remove("is-panning");
		}
		window.addEventListener("pointermove", move);
		window.addEventListener("pointerup", up);
	}
	function startNodeDrag(e, nodeGroup) {
		e.preventDefault();
		e.stopPropagation();
		const startX = e.clientX;
		const startY = e.clientY;
		const origTransform = nodeGroup.getAttribute("transform") || "";
		const m = origTransform.match(/translate\s*\(\s*(-?[\d.]+)[\s,]+(-?[\d.]+)\s*\)/);
		const origX = m ? parseFloat(m[1]) : 0;
		const origY = m ? parseFloat(m[2]) : 0;
		const zoom = transformRef.current.zoom || 1;
		const key = getNodeKey(nodeGroup);
		nodeGroup.classList.add("is-dragging");
		function move(ev) {
			const dx = (ev.clientX - startX) / zoom;
			const dy = (ev.clientY - startY) / zoom;
			const nx = origX + dx;
			const ny = origY + dy;
			const next = m ? origTransform.replace(/translate\s*\(\s*-?[\d.]+[\s,]+-?[\d.]+\s*\)/, "translate(" + nx + ", " + ny + ")") : "translate(" + nx + ", " + ny + ") " + origTransform;
			nodeGroup.setAttribute("transform", next);
			if (key) positionsRef.current[key] = next;
			if (key) updateEdgesFor(key);
		}
		function up() {
			window.removeEventListener("pointermove", move);
			window.removeEventListener("pointerup", up);
			nodeGroup.classList.remove("is-dragging");
		}
		window.addEventListener("pointermove", move);
		window.addEventListener("pointerup", up);
	}
	useEffect(function() {
		if (!window.mermaid || !hostRef.current || !source) return;
		capturePositions();
		try {
			window.mermaid.initialize(buildMermaidConfig());
		} catch (err) {
			console.warn("[mermaid] initialize failed", err);
		}
		const renderId = ++renderIdRef.current;
		const domId = "mm-" + renderId + "-" + Date.now();
		window.mermaid.render(domId, source).then(function(result) {
			if (renderIdRef.current !== renderId || !hostRef.current) return;
			hostRef.current.innerHTML = result && result.svg ? result.svg : "";
			applyViewportTransform(false);
			restorePositions();
		}).catch(function(err) {
			if (!hostRef.current) return;
			hostRef.current.textContent = "mermaid render failed: " + (err && err.message ? err.message : String(err));
		});
	}, [source, theme]);
	useEffect(function() {
		const v = viewportRef.current;
		const wrap = wrapperRef.current;
		if (!v || !wrap) return;
		function onWheel(e) {
			e.preventDefault();
			let deltaX = e.deltaX;
			let deltaY = e.deltaY;
			if (e.deltaMode === 1) {
				deltaX *= 16;
				deltaY *= 16;
			} else if (e.deltaMode === 2) {
				deltaX *= wrap.clientWidth;
				deltaY *= wrap.clientHeight;
			}
			if (e.ctrlKey) {
				const rect = wrap.getBoundingClientRect();
				const anchor = {
					x: e.clientX - rect.left,
					y: e.clientY - rect.top
				};
				bumpZoom(Math.exp(-deltaY * .01), anchor, false);
			} else {
				transformRef.current.x -= deltaX;
				transformRef.current.y -= deltaY;
				applyViewportTransform(false);
			}
		}
		v.addEventListener("wheel", onWheel, { passive: false });
		return function() {
			v.removeEventListener("wheel", onWheel);
		};
	}, []);
	return h("div", {
		ref: wrapperRef,
		className: "mermaid-wrapper"
	}, h("div", { className: "mermaid-toolbar" }, h("button", {
		type: "button",
		title: "Zoom in",
		onClick: function() {
			bumpZoom(1.15, null, true);
		}
	}, "+"), h("button", {
		type: "button",
		title: "Zoom out",
		onClick: function() {
			bumpZoom(1 / 1.15, null, true);
		}
	}, "−"), h("button", {
		type: "button",
		title: "Reset view & node drags",
		onClick: resetView
	}, "Reset")), h("div", {
		ref: viewportRef,
		className: "mermaid-viewport",
		onPointerDown
	}, h("div", {
		ref: hostRef,
		className: "mermaid-host"
	})));
}
function App() {
	/** @type {[BoardSnapshot | null, function(BoardSnapshot | null): void]} */
	const [board, setBoard] = useState(null);
	const [query, setQuery] = useState("");
	const [statusFilter, setStatusFilter] = useState("All");
	const [priorityFilter, setPriorityFilter] = useState("All");
	const [blockedOnly, setBlockedOnly] = useState(false);
	const [selectedTaskId, setSelectedTaskId] = useState(null);
	const [theme, setTheme] = useState(webViewTheme.current);
	const [clock, setClock] = useState(/* @__PURE__ */ new Date());
	const [draggingTaskId, setDraggingTaskId] = useState(null);
	const [dragOverStatus, setDragOverStatus] = useState(null);
	useEffect(function subscribeTheme() {
		const registration = webViewTheme.onChanged(function(nextTheme) {
			setTheme(nextTheme);
		});
		return function() {
			registration.close();
		};
	}, []);
	useEffect(function subscribeBoard() {
		if (!webView || typeof webView.notification !== "function") {
			console.warn("[app] __WVI__ bridge missing; demo board will stay empty.");
			return;
		}
		const unsubscribe = registerNotificationHandler(NOTIFICATIONS.SNAPSHOT, function(snapshot) {
			setBoard(snapshot || null);
		});
		notifyKotlin(NOTIFICATIONS.READY, {});
		return unsubscribe;
	}, []);
	useEffect(function tickClock() {
		const timer = window.setInterval(function() {
			setClock(/* @__PURE__ */ new Date());
		}, 1e3);
		return function() {
			window.clearInterval(timer);
		};
	}, []);
	const onCardSelect = useCallback(function(taskId) {
		setSelectedTaskId(taskId);
		notifyKotlin(NOTIFICATIONS.TASK_CLICKED, { taskId });
	}, []);
	const onAddTask = useCallback(function() {
		notifyKotlin(NOTIFICATIONS.ADD_TASK, {});
	}, []);
	const resetFilters = useCallback(function() {
		setQuery("");
		setStatusFilter("All");
		setPriorityFilter("All");
		setBlockedOnly(false);
	}, []);
	const autoTickOn = !board || board.autoTick !== false;
	const toggleAutoTick = useCallback(function() {
		notifyKotlin(NOTIFICATIONS.SET_AUTO_TICK, { enabled: !autoTickOn });
	}, [autoTickOn]);
	const onTaskDragStart = useCallback(function(taskId, event) {
		setDraggingTaskId(taskId);
		if (event && event.dataTransfer) try {
			event.dataTransfer.setData(DRAG_MIME, taskId);
			event.dataTransfer.setData("text/plain", taskId);
			event.dataTransfer.effectAllowed = "move";
		} catch (err) {
			console.warn("[app] dataTransfer.setData failed", err);
		}
	}, []);
	const onTaskDragEnd = useCallback(function() {
		setDraggingTaskId(null);
		setDragOverStatus(null);
	}, []);
	const onColumnDragOver = useCallback(function(status, event) {
		event.preventDefault();
		if (event.dataTransfer) event.dataTransfer.dropEffect = "move";
		if (dragOverStatus !== status) setDragOverStatus(status);
	}, [dragOverStatus]);
	const onColumnDragLeave = useCallback(function(status, event) {
		if (event && event.currentTarget && event.relatedTarget) {
			if (event.currentTarget.contains(event.relatedTarget)) return;
		}
		setDragOverStatus(function(prev) {
			return prev === status ? null : prev;
		});
	}, []);
	const onColumnDrop = useCallback(function(status, event) {
		event.preventDefault();
		let taskId = null;
		if (event.dataTransfer) taskId = event.dataTransfer.getData(DRAG_MIME) || event.dataTransfer.getData("text/plain") || null;
		if (!taskId && draggingTaskId) taskId = draggingTaskId;
		setDraggingTaskId(null);
		setDragOverStatus(null);
		if (!taskId) return;
		notifyKotlin(NOTIFICATIONS.SET_STATUS, {
			taskId,
			status
		});
	}, [draggingTaskId]);
	/** @type {Task[]} */
	const allTasks = board && board.tasks || [];
	/** @type {TimelineEvent[]} */
	const allTimeline = board && board.timeline || [];
	/** @type {Task[]} */
	const filteredTasks = useMemo(function() {
		const normalizedQuery = query.trim().toLowerCase();
		return allTasks.filter(function(task) {
			if (statusFilter !== "All" && task.status !== statusFilter) return false;
			if (priorityFilter !== "All" && task.priority !== priorityFilter) return false;
			if (blockedOnly && !task.blocked) return false;
			if (!normalizedQuery) return true;
			return task.id.toLowerCase().includes(normalizedQuery) || task.title.toLowerCase().includes(normalizedQuery) || task.owner.toLowerCase().includes(normalizedQuery);
		}).sort(function(a, b) {
			if (PRIORITY_WEIGHT[b.priority] !== PRIORITY_WEIGHT[a.priority]) return PRIORITY_WEIGHT[b.priority] - PRIORITY_WEIGHT[a.priority];
			return a.id.localeCompare(b.id);
		});
	}, [
		allTasks,
		query,
		statusFilter,
		priorityFilter,
		blockedOnly
	]);
	const grouped = useMemo(function() {
		const map = {};
		STATUSES.forEach(function(status) {
			map[status] = [];
		});
		filteredTasks.forEach(function(task) {
			if (!map[task.status]) map[task.status] = [];
			map[task.status].push(task);
		});
		return map;
	}, [filteredTasks]);
	/** @type {Task | null} */
	const selectedTask = useMemo(function() {
		if (filteredTasks.length === 0) return null;
		for (let i = 0; i < filteredTasks.length; i++) if (filteredTasks[i].id === selectedTaskId) return filteredTasks[i];
		return filteredTasks[0];
	}, [filteredTasks, selectedTaskId]);
	useEffect(function() {
		if (selectedTask && selectedTask.id !== selectedTaskId) setSelectedTaskId(selectedTask.id);
	}, [selectedTask, selectedTaskId]);
	/** @type {TimelineEvent[]} */
	const timelineItems = useMemo(function() {
		const selectedId = selectedTask ? selectedTask.id : null;
		return allTimeline.filter(function(event, index) {
			return selectedId == null || event.taskId === selectedId || index % 7 === 0;
		}).slice(0, 180);
	}, [allTimeline, selectedTask]);
	if (!board) return h(WaitingForModel);
	const doneCount = filteredTasks.filter(function(task) {
		return task.status === "Done";
	}).length;
	const blockedCount = filteredTasks.filter(function(task) {
		return task.blocked;
	}).length;
	const avgProgress = filteredTasks.length > 0 ? Math.round(filteredTasks.reduce(function(sum, task) {
		return sum + task.progress;
	}, 0) / filteredTasks.length) : 0;
	const urgentCount = filteredTasks.filter(function(task) {
		return task.priority === "Critical" || task.priority === "High";
	}).length;
	return h("div", { className: "app-shell" }, h("section", { className: "panel header" }, h("div", { className: "title-block" }, h("h1", null, "WebView React Rich UI Sample"), h("p", null, "Board is owned by Kotlin (DemoBoardProducer). JS subscribes to \"" + METHODS.SNAPSHOT + "\" and sends edits back as notifications.")), h("div", { className: "header-meta" }, h("div", { className: "filters" }, h("div", { className: "filter-row" }, h("input", {
		className: "search-input",
		value: query,
		placeholder: "Search by id, title or owner",
		onChange: function(event) {
			setQuery(event.target.value);
		}
	}), h(DropdownSelect, {
		id: "status-filter",
		label: "Status filter",
		value: statusFilter,
		options: STATUS_FILTER_OPTIONS,
		onChange: setStatusFilter
	}), h(DropdownSelect, {
		id: "priority-filter",
		label: "Priority filter",
		value: priorityFilter,
		options: PRIORITY_FILTER_OPTIONS,
		onChange: setPriorityFilter
	})), h("div", { className: "filter-row" }, h("button", {
		className: blockedOnly ? "btn-control is-active" : "btn-control",
		type: "button",
		onClick: function() {
			setBlockedOnly(!blockedOnly);
		}
	}, "Blocked only"), h(PopupMenuButton, {
		label: "Actions",
		title: "Board actions",
		items: [{
			label: "Reset filters",
			onSelect: resetFilters
		}, {
			label: "Add task",
			onSelect: onAddTask
		}]
	}), h("button", {
		className: autoTickOn ? "btn-control is-active" : "btn-control",
		type: "button",
		title: "Enable/disable the Kotlin producer's 2s auto-tick",
		onClick: toggleAutoTick
	}, "Auto-tick: " + (autoTickOn ? "ON" : "OFF")), h("span", { className: "clock" }, "Now: " + clock.toLocaleTimeString()))))), h("section", { className: "metrics-grid" }, h(MetricCard, {
		accent: "a",
		label: "Visible Tasks",
		value: String(filteredTasks.length),
		note: "Filtered from " + String(allTasks.length)
	}), h(MetricCard, {
		accent: "b",
		label: "Completion",
		value: String(doneCount) + " done",
		note: filteredTasks.length === 0 ? "0%" : String(Math.round(doneCount / filteredTasks.length * 100)) + "% complete"
	}), h(MetricCard, {
		accent: "c",
		label: "Average Progress",
		value: String(avgProgress) + "%",
		note: "Across active cards"
	}), h(MetricCard, {
		accent: "d",
		label: "Urgent / Blocked",
		value: String(urgentCount) + " / " + String(blockedCount),
		note: "High+Critical / blocked"
	})), h("section", { className: "main-grid" }, h("div", { style: {
		display: "flex",
		flexDirection: "column",
		gap: "12px"
	} }, h("div", { className: "panel board-panel" }, h("h2", { className: "panel-title" }, "Kanban Board"), h("div", { className: "kanban-columns" }, STATUSES.map(function(status) {
		const items = grouped[status] || [];
		const isDropTarget = dragOverStatus === status;
		return h("div", {
			className: "kanban-column" + (isDropTarget ? " is-drop-target" : ""),
			key: status,
			style: isDropTarget ? {
				outline: "2px dashed var(--ij-accent)",
				outlineOffset: "-2px"
			} : null,
			onDragOver: function(event) {
				onColumnDragOver(status, event);
			},
			onDragEnter: function(event) {
				onColumnDragOver(status, event);
			},
			onDragLeave: function(event) {
				onColumnDragLeave(status, event);
			},
			onDrop: function(event) {
				onColumnDrop(status, event);
			}
		}, h("div", { className: "column-header" }, status + " (" + String(items.length) + ")"), h("div", { className: "task-list" }, items.slice(0, 20).map(function(task) {
			return h(TaskCard, {
				key: task.id,
				task,
				active: selectedTask != null && selectedTask.id === task.id,
				onSelect: onCardSelect,
				onDragStart: onTaskDragStart,
				onDragEnd: onTaskDragEnd
			});
		})));
	}))), h("div", { className: "panel board-panel" }, h("h2", { className: "panel-title" }, "Release Timeline (scroll)"), h("div", { className: "timeline-list" }, timelineItems.map(function(event) {
		return h("div", {
			className: "timeline-item",
			key: event.id
		}, event.label);
	}))), h("div", { className: "panel board-panel" }, h("h2", { className: "panel-title" }, "Product Roadmap (mermaid, from Kotlin)"), h("div", { className: "mermaid-hint" }, "scroll / drag = pan  ·  pinch (ctrl + scroll) = zoom  ·  drag node = move"), board && board.roadmapMermaid ? h(MermaidDiagram, {
		source: board.roadmapMermaid,
		theme
	}) : h("div", { className: "details-value" }, "Waiting for roadmap from Kotlin…"))), h("div", { className: "side-stack" }, h("div", { className: "panel details-panel" }, h("h2", { className: "panel-title" }, "Selected Task"), selectedTask == null ? h("div", { className: "details-value" }, "No tasks match current filters.") : h("div", null, h("div", { className: "task-title" }, selectedTask.title), h("div", { className: "task-subtitle" }, selectedTask.id + " • due " + selectedTask.dueLabel), h("div", {
		className: "details-grid",
		style: { marginTop: "10px" }
	}, h("div", { className: "details-key" }, "Owner"), h("div", { className: "details-value" }, selectedTask.owner), h("div", { className: "details-key" }, "Status"), h("div", { className: "details-value" }, selectedTask.status), h("div", { className: "details-key" }, "Priority"), h("div", { className: "details-value" }, selectedTask.priority), h("div", { className: "details-key" }, "Estimate"), h("div", { className: "details-value" }, String(selectedTask.estimateHours) + "h"), h("div", { className: "details-key" }, "Blocked"), h("div", { className: "details-value" }, selectedTask.blocked ? "Yes" : "No")), h("div", { className: "details-tags" }, (selectedTask.tags || []).map(function(tag) {
		return h("span", {
			className: "badge status-backlog",
			key: tag
		}, tag);
	})))), h("div", { className: "panel activity-panel" }, h("h2", { className: "panel-title" }, "Activity Feed"), h("div", { className: "activity-list" }, allTimeline.slice(0, 220).map(function(event) {
		return h("div", {
			className: "activity-item",
			key: event.id
		}, event.label);
	}))))), h("div", { className: "footer-row" }, "Snapshot updated at " + (board.updatedAt ? new Date(board.updatedAt).toLocaleTimeString() : "—") + ". Edits round-trip through Kotlin."));
}
try {
	(0, import_client.createRoot)(rootNode).render(h(App));
} catch (error) {
	renderFallback("React app initialization failed: " + (error && error.message ? error.message : "Unknown React bootstrap error"));
}
//#endregion
