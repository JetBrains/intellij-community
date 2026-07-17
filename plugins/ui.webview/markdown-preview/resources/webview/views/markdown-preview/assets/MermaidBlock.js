import { n as require_react, t as require_jsx_runtime } from "./react.js";
import { h as select_default, n as identity, t as zoom_default } from "./d3.js";
import { i as shouldHandleZoomEvent, n as MARKDOWN_ZOOM_SCALE_EXTENT, r as MarkdownZoomToolbar, t as MARKDOWN_ZOOM_BUTTON_FACTOR } from "./MarkdownZoomControls.js";
var import_react = require_react();
var import_jsx_runtime = require_jsx_runtime();
var mermaidBlockId = 0;
var mermaidRenderId = 0;
var mermaidModule;
var PRESERVED_SVG_TAGS = new Set([
	"defs",
	"style",
	"title",
	"desc",
	"metadata",
	"marker"
]);
function MermaidBlock({ chart, theme }) {
	const hostId = (0, import_react.useRef)(`markdown-preview-mermaid-${++mermaidBlockId}`);
	const [state, setState] = (0, import_react.useState)({ kind: "rendering" });
	(0, import_react.useEffect)(() => {
		let cancelled = false;
		const renderId = `${hostId.current}-${++mermaidRenderId}`;
		setState({ kind: "rendering" });
		loadMermaid().then((mermaid) => {
			configureMermaid(mermaid, theme);
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
	}, [chart, theme]);
	if (state.kind === "rendered") return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderedMermaidDiagram, { svg: state.svg });
	if (state.kind === "error") return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "mermaidBlock hasError",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "mermaidError",
			children: state.message
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("code", { children: chart }) })]
	});
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "mermaidBlock isRendering",
		children: "Rendering diagram..."
	});
}
function RenderedMermaidDiagram({ svg }) {
	const hostRef = (0, import_react.useRef)(null);
	const svgRef = (0, import_react.useRef)(null);
	const zoomBehaviorRef = (0, import_react.useRef)(null);
	(0, import_react.useEffect)(() => {
		const host = hostRef.current;
		if (!host) return;
		host.innerHTML = svg;
		const svgElement = host.querySelector("svg");
		if (!svgElement) return () => {
			host.innerHTML = "";
		};
		prepareSvg(svgElement, "mermaidSvg");
		const panZoomGroup = wrapSvgContent(svgElement, "mermaidPanZoom");
		fitSvgViewBoxToContent(svgElement, panZoomGroup);
		svgRef.current = svgElement;
		const zoomBehavior = zoom_default().filter(shouldHandleZoomEvent).scaleExtent(MARKDOWN_ZOOM_SCALE_EXTENT).on("zoom", (event) => {
			panZoomGroup.setAttribute("transform", event.transform.toString());
		});
		zoomBehaviorRef.current = zoomBehavior;
		const svgSelection = select_default(svgElement);
		svgSelection.call(zoomBehavior);
		svgSelection.call(zoomBehavior.transform, identity);
		return () => {
			svgSelection.on(".zoom", null);
			host.innerHTML = "";
			svgRef.current = null;
			zoomBehaviorRef.current = null;
		};
	}, [svg]);
	function zoomBy(factor) {
		const svgElement = svgRef.current;
		const zoomBehavior = zoomBehaviorRef.current;
		if (!svgElement || !zoomBehavior) return;
		select_default(svgElement).call(zoomBehavior.scaleBy, factor);
	}
	function resetZoom() {
		const svgElement = svgRef.current;
		const zoomBehavior = zoomBehaviorRef.current;
		if (!svgElement || !zoomBehavior) return;
		select_default(svgElement).call(zoomBehavior.transform, identity);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "mermaidBlock isInteractive",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "mermaidViewport",
			ref: hostRef
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MarkdownZoomToolbar, {
			targetLabel: "diagram",
			className: "mermaidToolbar",
			buttonClassName: "mermaidToolbarButton",
			onZoomOut: () => zoomBy(1 / MARKDOWN_ZOOM_BUTTON_FACTOR),
			onResetZoom: resetZoom,
			onZoomIn: () => zoomBy(MARKDOWN_ZOOM_BUTTON_FACTOR)
		})]
	});
}
function prepareSvg(svgElement, className) {
	svgElement.classList.add(className);
	svgElement.setAttribute("preserveAspectRatio", "xMidYMid meet");
	if (!svgElement.hasAttribute("viewBox")) {
		const width = svgDimension(svgElement.getAttribute("width"));
		const height = svgDimension(svgElement.getAttribute("height"));
		if (width && height) svgElement.setAttribute("viewBox", `0 0 ${width} ${height}`);
	}
	svgElement.removeAttribute("width");
	svgElement.removeAttribute("height");
	svgElement.style.removeProperty("width");
	svgElement.style.removeProperty("height");
	svgElement.style.removeProperty("max-width");
}
function wrapSvgContent(svgElement, className) {
	for (const child of Array.from(svgElement.children)) if (child.tagName.toLowerCase() === "g" && child.classList.contains(className)) return child;
	const group = document.createElementNS("http://www.w3.org/2000/svg", "g");
	group.setAttribute("class", className);
	for (const child of Array.from(svgElement.childNodes)) {
		if (child.nodeType !== Node.ELEMENT_NODE) continue;
		const element = child;
		if (PRESERVED_SVG_TAGS.has(element.tagName.toLowerCase())) continue;
		group.appendChild(element);
	}
	svgElement.appendChild(group);
	return group;
}
function fitSvgViewBoxToContent(svgElement, contentElement) {
	try {
		const box = contentElement.getBBox();
		if (box.width <= 0 || box.height <= 0) return;
		const padding = 24;
		svgElement.setAttribute("viewBox", `${box.x - padding} ${box.y - padding} ${box.width + padding * 2} ${box.height + padding * 2}`);
	} catch {}
}
function svgDimension(value) {
	if (!value) return void 0;
	const dimension = Number.parseFloat(value);
	return Number.isFinite(dimension) && dimension > 0 ? dimension : void 0;
}
function loadMermaid() {
	mermaidModule ||= import("./mermaid.js").then((n) => n.t).then((module) => module.default);
	return mermaidModule;
}
function configureMermaid(mermaid, theme) {
	const isLight = theme === "light";
	const panel = cssVariable("--ij-bg-panel", isLight ? "#F7F8F9" : "#212326");
	const panelAlt = cssVariable("--ij-bg-panel-alt", isLight ? "#FFFFFF" : "#26282C");
	const hover = cssVariable("--ij-bg-hover", isLight ? "#00000012" : "#FFFFFF17");
	const border = cssVariable("--ij-border-strong", isLight ? "#D1D3D9" : "#40434A");
	const textPrimary = cssVariable("--ij-text-primary", isLight ? "#000000" : "#D1D3D9");
	const textSecondary = cssVariable("--ij-text-secondary", "#73767C");
	const accent = cssVariable("--ij-accent", "#3871E1");
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
export { MermaidBlock };
