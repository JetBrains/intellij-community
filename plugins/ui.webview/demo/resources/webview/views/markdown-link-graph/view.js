import { o as __toESM } from "./assets/rolldown-runtime.js";
import { t as cytoscape } from "./assets/cytoscape.js";
import { t as require_cytoscape_fcose } from "./assets/cytoscape-fcose.js";
import { n as marked } from "./assets/marked.js";
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
var import_cytoscape_fcose = /* @__PURE__ */ __toESM(require_cytoscape_fcose(), 1);
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
//#region views/markdown-link-graph/src/main.ts
cytoscape.use(import_cytoscape_fcose.default);
var hostApi = webView.callable(apiId()("markdown.linkGraph"));
var contentElement = requiredElement("content");
var graphElement = requiredElement("graph");
var splitterElement = requiredElement("splitter");
var refreshButton = requiredElement("refreshButton");
var statsElement = requiredElement("stats");
var previewPanelElement = requiredElement("previewPanel");
var previewTitleElement = requiredElement("previewTitle");
var previewPathElement = requiredElement("previewPath");
var previewBodyElement = requiredElement("previewBody");
var cy;
var graph;
var mermaidRenderId = 0;
var previewRequest = 0;
var resizePointerId;
refreshButton.addEventListener("click", () => void loadGraph());
graphElement.addEventListener("wheel", handleGraphWheel, {
	capture: true,
	passive: false
});
splitterElement.addEventListener("pointerdown", startPreviewResize);
splitterElement.addEventListener("keydown", handleSplitterKeyDown);
webViewTheme.onChanged(() => {
	cy?.style(graphStyles()).update();
	configureMermaid();
	renderMermaidDiagrams(previewBodyElement, previewRequest);
});
configureMermaid();
loadGraph();
async function loadGraph() {
	refreshButton.disabled = true;
	statsElement.textContent = "Loading...";
	try {
		graph = await hostApi.getGraph();
		hidePreview();
		renderGraph(graph);
		updateStats(graph);
	} catch (error) {
		statsElement.textContent = error instanceof Error ? error.message : "Failed to load graph";
	} finally {
		refreshButton.disabled = false;
	}
}
function renderGraph(dto) {
	const elements = [...dto.nodes.map((node) => ({
		group: "nodes",
		data: {
			id: node.id,
			label: node.label,
			kind: node.kind,
			path: node.path,
			parent: node.parent
		}
	})), ...dto.edges.map((edge) => ({
		group: "edges",
		data: {
			id: edge.id,
			source: edge.source,
			target: edge.target
		}
	}))];
	if (!cy) {
		cy = cytoscape({
			container: graphElement,
			elements,
			minZoom: .03,
			maxZoom: 4,
			wheelSensitivity: .08,
			style: graphStyles()
		});
		cy.on("dbltap", "node[kind = 'file']", (event) => void openFile(event));
		cy.on("tap", "node", (event) => void selectNode(event));
		cy.on("tap", (event) => {
			if (event.target === cy) clearSelection();
		});
		cy.on("mouseover", "node[kind = 'file']", (event) => event.target.addClass("focused"));
		cy.on("mouseout", "node[kind = 'file']", (event) => event.target.removeClass("focused"));
	} else {
		cy.elements().remove();
		cy.add(elements);
	}
	runLayout();
}
function runLayout() {
	if (!cy) return;
	cy.layout({
		...compoundLayoutOptions(),
		stop: () => resetViewportForScroll()
	}).run();
}
function compoundLayoutOptions() {
	return {
		name: "fcose",
		animate: false,
		fit: false,
		padding: 72,
		nodeDimensionsIncludeLabels: false,
		quality: "default",
		randomize: false,
		gravityRangeCompound: 1.5,
		idealEdgeLength: 120,
		nodeRepulsion: 6800
	};
}
function handleGraphWheel(event) {
	if (event.ctrlKey) return;
	const graphCore = cy;
	if (!graphCore) return;
	event.preventDefault();
	event.stopImmediatePropagation();
	graphCore.panBy(normalizedPanDelta(event));
}
function normalizedPanDelta(event) {
	let deltaX = event.deltaX;
	let deltaY = event.deltaY;
	if (event.shiftKey && deltaX === 0) {
		deltaX = deltaY;
		deltaY = 0;
	}
	const multiplier = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? 16 : event.deltaMode === WheelEvent.DOM_DELTA_PAGE ? graphElement.clientHeight : 1;
	return {
		x: -deltaX * multiplier,
		y: -deltaY * multiplier
	};
}
function resetViewportForScroll() {
	const graphCore = cy;
	if (!graphCore) return;
	const bounds = graphCore.elements().boundingBox({
		includeLabels: false,
		includeOverlays: false
	});
	if (!Number.isFinite(bounds.x1) || !Number.isFinite(bounds.y1) || bounds.w <= 0 || bounds.h <= 0) return;
	const padding = 72;
	const fitZoom = Math.min(graphElement.clientWidth / Math.max(bounds.w + padding * 2, 1), graphElement.clientHeight / Math.max(bounds.h + padding * 2, 1));
	const zoom = Math.min(1, Math.max(.35, fitZoom * 1.7));
	graphCore.zoom(zoom);
	graphCore.pan({
		x: padding - bounds.x1 * zoom,
		y: padding - bounds.y1 * zoom
	});
}
async function openFile(event) {
	const fileId = event.target.data("id");
	if (fileId) await hostApi.openFile({ fileId });
}
async function selectNode(event) {
	if (!graph) return;
	const node = event.target;
	const path = node.data("path");
	updateStats(graph, path);
	if (node.data("kind") !== "file") {
		hidePreview();
		return;
	}
	const fileId = node.data("id");
	if (!fileId) {
		hidePreview();
		return;
	}
	await loadPreview(fileId, node.data("label"), path);
}
async function loadPreview(fileId, title, path) {
	const request = ++previewRequest;
	showPreview();
	previewTitleElement.textContent = title || "Loading...";
	previewPathElement.textContent = path || "";
	previewBodyElement.textContent = "Loading...";
	try {
		const preview = await hostApi.getFilePreview({ fileId });
		if (request !== previewRequest) return;
		if (!preview.found) {
			showPreviewPlaceholder(preview.title, preview.path || "File not found");
			return;
		}
		await showFilePreview(preview, request);
	} catch (error) {
		if (request !== previewRequest) return;
		showPreviewPlaceholder("Preview failed", error instanceof Error ? error.message : "Failed to load file preview");
	}
}
function clearSelection() {
	if (graph) updateStats(graph);
	hidePreview();
}
function showPreview() {
	if (contentElement.classList.contains("previewVisible")) return;
	contentElement.classList.add("previewVisible");
	previewPanelElement.setAttribute("aria-hidden", "false");
	scheduleGraphResize();
}
function hidePreview() {
	previewRequest++;
	contentElement.classList.remove("previewVisible");
	previewPanelElement.setAttribute("aria-hidden", "true");
	previewTitleElement.textContent = "";
	previewPathElement.textContent = "";
	previewBodyElement.textContent = "";
	scheduleGraphResize();
}
function scheduleGraphResize() {
	requestAnimationFrame(() => cy?.resize());
}
function startPreviewResize(event) {
	if (event.button !== 0 || !contentElement.classList.contains("previewVisible")) return;
	event.preventDefault();
	resizePointerId = event.pointerId;
	splitterElement.setPointerCapture(event.pointerId);
	splitterElement.classList.add("resizing");
	document.body.classList.add("resizingPreview");
	window.addEventListener("pointermove", resizePreview);
	window.addEventListener("pointerup", stopPreviewResize);
	window.addEventListener("pointercancel", stopPreviewResize);
}
function resizePreview(event) {
	if (event.pointerId !== resizePointerId) return;
	setPreviewWidth(contentElement.getBoundingClientRect().right - event.clientX);
}
function stopPreviewResize(event) {
	if (event.pointerId !== resizePointerId) return;
	if (splitterElement.hasPointerCapture(event.pointerId)) splitterElement.releasePointerCapture(event.pointerId);
	resizePointerId = void 0;
	splitterElement.classList.remove("resizing");
	document.body.classList.remove("resizingPreview");
	window.removeEventListener("pointermove", resizePreview);
	window.removeEventListener("pointerup", stopPreviewResize);
	window.removeEventListener("pointercancel", stopPreviewResize);
}
function handleSplitterKeyDown(event) {
	if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
	event.preventDefault();
	setPreviewWidth((previewPanelElement.getBoundingClientRect().width || DEFAULT_PREVIEW_WIDTH) + (event.key === "ArrowLeft" ? 1 : -1) * KEYBOARD_RESIZE_STEP);
}
function setPreviewWidth(width) {
	const maxWidth = Math.max(MIN_PREVIEW_WIDTH, contentElement.clientWidth * MAX_PREVIEW_WIDTH_RATIO);
	const nextWidth = Math.min(maxWidth, Math.max(MIN_PREVIEW_WIDTH, width));
	contentElement.style.setProperty("--preview-width", `${Math.round(nextWidth)}px`);
	cy?.resize();
}
async function showFilePreview(preview, request) {
	previewTitleElement.textContent = preview.title;
	previewPathElement.textContent = preview.path;
	const markdown = preview.truncated ? `${preview.text}\n\n> Preview truncated.` : preview.text;
	previewBodyElement.innerHTML = marked.parse(markdown, {
		async: false,
		gfm: true
	});
	await renderMermaidDiagrams(previewBodyElement, request);
}
function showPreviewPlaceholder(title, text) {
	previewTitleElement.textContent = title;
	previewPathElement.textContent = "";
	previewBodyElement.textContent = text;
}
async function renderMermaidDiagrams(root, request) {
	if (request !== previewRequest) return;
	const codeBlocks = root.querySelectorAll("pre > code.language-mermaid");
	for (let i = 0; i < codeBlocks.length; i++) {
		const codeElement = codeBlocks[i];
		const preElement = codeElement.parentElement;
		if (!preElement) continue;
		const host = document.createElement("div");
		host.className = "mermaidHost";
		host.dataset.mermaidSource = codeElement.textContent || "";
		preElement.replaceWith(host);
	}
	const hosts = root.querySelectorAll(".mermaidHost[data-mermaid-source]");
	for (let i = 0; i < hosts.length; i++) {
		if (request !== previewRequest) return;
		await renderMermaidCode(hosts[i], hosts[i].dataset.mermaidSource || "", i, request);
	}
}
async function renderMermaidCode(host, code, index, request) {
	configureMermaid();
	host.classList.remove("hasError");
	host.textContent = "Rendering diagram...";
	try {
		const id = `markdown-link-graph-mermaid-${++mermaidRenderId}-${index}`;
		const { svg } = await mermaid_default.render(id, code);
		if (request !== previewRequest) return;
		host.innerHTML = svg;
	} catch (error) {
		if (request !== previewRequest) return;
		host.classList.add("hasError");
		const message = document.createElement("div");
		message.className = "mermaidError";
		message.textContent = error instanceof Error ? error.message : "Failed to render Mermaid diagram";
		const source = document.createElement("pre");
		source.textContent = code;
		host.replaceChildren(message, source);
	}
}
function configureMermaid() {
	const isLight = document.documentElement.getAttribute("data-theme") === "light";
	const panel = cssVariable("--ij-bg-panel", isLight ? "#F7F8F9" : "#212326");
	const panelAlt = cssVariable("--ij-bg-panel-alt", isLight ? "#FFFFFF" : "#26282C");
	const hover = cssVariable("--ij-bg-hover", isLight ? "#00000012" : "#FFFFFF17");
	const border = cssVariable("--ij-border-strong", isLight ? "#D1D3D9" : "#40434A");
	const textPrimary = cssVariable("--ij-text-primary", isLight ? "#000000" : "#D1D3D9");
	const textSecondary = cssVariable("--ij-text-secondary", "#73767C");
	const accent = cssVariable("--ij-accent", "#3871E1");
	const font = cssVariable("--ij-font", "Inter, Segoe UI, -apple-system, BlinkMacSystemFont, Helvetica Neue, sans-serif");
	mermaid_default.initialize({
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
function updateStats(dto, selectedPath) {
	const truncated = dto.truncated ? " truncated" : "";
	const selected = selectedPath ? ` selected: ${selectedPath}` : "";
	statsElement.textContent = `${dto.nodes.length} nodes, ${dto.edges.length} edges${truncated}${selected}`;
}
function graphStyles() {
	const theme = graphThemeColors();
	return [
		{
			selector: "node",
			style: {
				"background-color": theme.neutral,
				"border-color": theme.neutralBorder,
				"border-width": 1,
				color: theme.textPrimary,
				label: "",
				"font-family": theme.fontFamily,
				"font-size": 11,
				"min-zoomed-font-size": 7,
				"text-halign": "center",
				"text-valign": "bottom",
				"text-margin-y": 4,
				"text-wrap": "wrap",
				"text-max-width": "120px",
				height: 10,
				width: 10
			}
		},
		{
			selector: "node[kind = 'file']",
			style: {
				"background-color": theme.success,
				"border-color": theme.successBorder,
				shape: "round-rectangle"
			}
		},
		{
			selector: "node[kind = 'file'].focused, node[kind = 'file']:selected",
			style: {
				label: "data(label)",
				height: 18,
				width: 18,
				"font-size": 12,
				"text-background-color": theme.panel,
				"text-background-opacity": .86,
				"text-background-padding": "3px",
				"text-border-color": theme.border,
				"text-border-opacity": .8,
				"text-border-width": 1,
				"z-index": 20
			}
		},
		{
			selector: "node[kind = 'module']",
			style: {
				"background-color": theme.accent,
				"background-opacity": .07,
				"border-color": theme.accentSoftBorder,
				"border-style": "solid",
				"border-opacity": .42,
				"border-width": 1,
				color: theme.textMuted,
				"compound-sizing-wrt-labels": "include",
				label: "data(label)",
				"padding": "22px",
				shape: "round-rectangle",
				"text-valign": "top",
				"z-index": 0
			}
		},
		{
			selector: "node[kind = 'folder']",
			style: {
				"background-color": theme.neutral,
				"background-opacity": .04,
				"border-color": theme.neutralBorder,
				"border-style": "dashed",
				"border-opacity": .36,
				color: theme.textMuted,
				"compound-sizing-wrt-labels": "include",
				label: "data(label)",
				"padding": "16px",
				shape: "round-rectangle",
				"text-valign": "top",
				"z-index": 0
			}
		},
		{
			selector: "edge",
			style: {
				"curve-style": "bezier",
				"line-color": theme.textMuted,
				"line-opacity": .26,
				"target-arrow-color": theme.textMuted,
				"target-arrow-shape": "triangle",
				"arrow-scale": .58,
				width: .8
			}
		},
		{
			selector: ":selected",
			style: {
				"border-width": 3,
				"border-color": theme.warning,
				"line-color": theme.warning,
				"target-arrow-color": theme.warning
			}
		}
	];
}
function graphThemeColors() {
	return {
		accent: cssVariable("--ij-accent", "#3871E1"),
		accentSoftBorder: cssVariable("--ij-accent-soft-border", "#2E4D89"),
		border: cssVariable("--ij-border", "#3C3F41"),
		fontFamily: cssVariable("--ij-font", "Inter, sans-serif"),
		neutral: cssVariable("--ij-neutral-text", "#B5B7BD"),
		neutralBorder: cssVariable("--ij-neutral-border", "#FFFFFF21"),
		panel: cssVariable("--ij-bg-panel", "#212326"),
		success: cssVariable("--ij-success", "#6DB083"),
		successBorder: cssVariable("--ij-success-border", "#29583C"),
		textMuted: cssVariable("--ij-text-muted", "#9FA2A8"),
		textPrimary: cssVariable("--ij-text-primary", "#D1D3D9"),
		warning: cssVariable("--ij-warning", "#D59637")
	};
}
function cssVariable(name, fallback) {
	return (getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback).replace(/^#([0-9a-fA-F]{6})[0-9a-fA-F]{2}$/, "#$1");
}
function requiredElement(id) {
	const element = document.getElementById(id);
	if (!element) throw new Error(`Missing element #${id}`);
	return element;
}
var DEFAULT_PREVIEW_WIDTH = 420;
var KEYBOARD_RESIZE_STEP = 32;
var MAX_PREVIEW_WIDTH_RATIO = .75;
var MIN_PREVIEW_WIDTH = 280;
//#endregion
