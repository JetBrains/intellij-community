const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["./assets/mermaid.js","./assets/rolldown-runtime.js","./assets/braintree-sanitize-url.js","./assets/iconify-utils.js","./assets/cytoscape-cose-bilkent.js","./assets/cose-base.js","./assets/cytoscape-fcose.js","./assets/cytoscape.js","./assets/d3-array.js","./assets/d3-axis.js","./assets/d3.js","./assets/d3-format.js","./assets/d3-hierarchy.js","./assets/d3-interpolate.js","./assets/d3-color.js","./assets/d3-sankey.js","./assets/d3-path.js","./assets/d3-scale-chromatic.js","./assets/d3-scale.js","./assets/d3-shape.js","./assets/dagre-d3-es.js","./assets/dayjs.js","./assets/dompurify.js","./assets/es-toolkit.js","./assets/khroma.js","./assets/marked.js"])))=>i.map(i=>d[i]);
import { n as require_react, t as require_jsx_runtime } from "./assets/react.js";
import { t as require_client } from "./assets/react-dom.js";
import { t as Markdown } from "./assets/react-markdown.js";
import { t as rehypeHighlight } from "./assets/rehype-highlight.js";
import { t as rehypeRaw } from "./assets/rehype-raw.js";
import { n as defaultSchema } from "./assets/hast-util-sanitize.js";
import { t as rehypeSanitize } from "./assets/rehype-sanitize.js";
import { t as rehypeSlug } from "./assets/rehype-slug.js";
import { t as remarkFrontmatter } from "./assets/remark-frontmatter.js";
import { t as remarkGfm } from "./assets/remark-gfm.js";
import { h as select_default, n as identity, t as zoom_default } from "./assets/d3.js";
import { f as __vitePreload } from "./assets/mermaid.js";
import { t as renderMathInElement } from "./assets/katex.js";
import { i, n as A, r as b, t as i$1 } from "./assets/lit.js";
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
var AllIcons = /* @__PURE__ */ IconSet.define("AllIcons");
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
//#endregion
//#region ../../webview-src/packages/api/src/focus.ts
var WEBVIEW_FOCUS_LEAVE_EVENT = "wvi-focus-leave";
function addWebViewFocusLeaveListener(listener) {
	window.addEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, listener);
	return () => window.removeEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, listener);
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
//#region views/markdown-preview/src/FloatingMarkdownControls.tsx
var import_react = require_react();
var import_jsx_runtime = require_jsx_runtime();
var headingSelector = "h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]";
var activeHeadingTopOffset = 80;
function FloatingMarkdownControls({ markdown, settings, onSetFontSize }) {
	const [entries, setEntries] = (0, import_react.useState)([]);
	const [openPanel, setOpenPanel] = (0, import_react.useState)();
	const [activeId, setActiveId] = (0, import_react.useState)();
	const hasTableOfContents = entries.length >= 2;
	const fontSizeOptions = (0, import_react.useMemo)(() => normalizedFontSizeOptions(settings), [settings]);
	const currentFontSizeIndex = fontSizeIndex(settings.effectiveFontSize, fontSizeOptions);
	(0, import_react.useEffect)(() => {
		const nextEntries = collectTableOfContentsEntries();
		setEntries(nextEntries);
		setActiveId(nextEntries[0]?.id);
	}, [markdown]);
	(0, import_react.useEffect)(() => {
		if (entries.length < 2) {
			setActiveId(void 0);
			return;
		}
		let scheduledFrame;
		const updateActiveHeading = () => {
			let active = entries[0].id;
			for (const entry of entries) if (entry.element.getBoundingClientRect().top <= activeHeadingTopOffset) active = entry.id;
			else break;
			setActiveId((current) => current === active ? current : active);
		};
		const scheduleUpdate = () => {
			if (scheduledFrame !== void 0) return;
			scheduledFrame = window.requestAnimationFrame(() => {
				scheduledFrame = void 0;
				updateActiveHeading();
			});
		};
		scheduleUpdate();
		window.addEventListener("scroll", scheduleUpdate, { passive: true });
		window.addEventListener("resize", scheduleUpdate);
		return () => {
			if (scheduledFrame !== void 0) window.cancelAnimationFrame(scheduledFrame);
			window.removeEventListener("scroll", scheduleUpdate);
			window.removeEventListener("resize", scheduleUpdate);
		};
	}, [entries]);
	(0, import_react.useEffect)(() => {
		return addWebViewFocusLeaveListener(() => setOpenPanel(void 0));
	}, []);
	(0, import_react.useEffect)(() => {
		if (openPanel === void 0) return;
		const handleKeyDown = (event) => {
			if (event.key === "Escape") setOpenPanel(void 0);
		};
		window.addEventListener("keydown", handleKeyDown);
		return () => window.removeEventListener("keydown", handleKeyDown);
	}, [openPanel]);
	(0, import_react.useEffect)(() => {
		if (!hasTableOfContents && openPanel === "toc") setOpenPanel(void 0);
	}, [hasTableOfContents, openPanel]);
	function togglePanel(panel) {
		setOpenPanel((current) => current === panel ? void 0 : panel);
	}
	function closePanel() {
		setOpenPanel(void 0);
	}
	function scrollToEntry(entry) {
		const target = document.getElementById(entry.id);
		if (!target) return;
		target.scrollIntoView({
			block: "start",
			behavior: "smooth"
		});
		setActiveId(entry.id);
	}
	function requestFontSize(fontSize) {
		const normalizedFontSize = closestFontSize(fontSize, fontSizeOptions);
		if (normalizedFontSize === settings.effectiveFontSize) return;
		onSetFontSize(normalizedFontSize);
	}
	function handleSliderChange(event) {
		const requestedFontSize = fontSizeOptions[Number(event.currentTarget.value)];
		if (requestedFontSize !== void 0) requestFontSize(requestedFontSize);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
		/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "markdownFloatingRail",
			"aria-label": "Markdown preview controls",
			children: [hasTableOfContents && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: classNames$1("markdownFloatingRailButton", openPanel === "toc" ? "is-active" : void 0),
				title: "Table of contents",
				"aria-label": "Show table of contents",
				"aria-expanded": openPanel === "toc",
				onClick: () => togglePanel("toc"),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "markdownTocRailIcon",
					"aria-hidden": "true"
				})
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: classNames$1("markdownFloatingRailButton", openPanel === "settings" ? "is-active" : void 0),
				title: "Font size settings",
				"aria-label": "Show font size settings",
				"aria-expanded": openPanel === "settings",
				onClick: () => togglePanel("settings"),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
					className: "markdownFloatingRailIcon",
					src: AllIcons.src("general/settings.svg"),
					alt: "",
					draggable: false
				})
			})]
		}),
		openPanel === "toc" && hasTableOfContents && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("nav", {
			className: "markdownFloatingPanel markdownTocPanel",
			"aria-label": "Table of contents",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(FloatingPanelHeader, {
				title: "Contents",
				closeLabel: "Collapse table of contents",
				onClose: closePanel
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("ol", {
				className: "markdownTocList",
				children: entries.map((entry) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", {
					className: "markdownTocItem",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						className: classNames$1("markdownTocLink", entry.id === activeId ? "is-active" : void 0),
						style: { paddingLeft: `${8 + Math.max(0, entry.level - 1) * 12}px` },
						"aria-current": entry.id === activeId ? "location" : void 0,
						title: entry.text,
						onClick: () => scrollToEntry(entry),
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "markdownTocText",
							children: entry.text
						})
					})
				}, entry.id))
			})]
		}),
		openPanel === "settings" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
			className: "markdownFloatingPanel markdownFontSettingsPanel",
			"aria-label": "Options",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(FloatingPanelHeader, {
				title: "Options",
				closeLabel: "Collapse font size settings",
				onClose: closePanel
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
				className: "markdownFontSettingsBody",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "markdownFontSetting",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "markdownFontSizeHeader",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("label", {
								className: "markdownFontSizeSliderLabel",
								htmlFor: "markdown-font-size-slider",
								children: "Font size"
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "markdownFontSizeValue",
								children: [settings.effectiveFontSize, " px"]
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
							id: "markdown-font-size-slider",
							type: "range",
							className: "markdownFontSizeSlider",
							min: 0,
							max: Math.max(0, fontSizeOptions.length - 1),
							step: 1,
							value: currentFontSizeIndex,
							disabled: fontSizeOptions.length < 2,
							"aria-valuetext": `${settings.effectiveFontSize} px`,
							onChange: handleSliderChange
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "markdownFontSizeButtons",
							"aria-label": "Font size controls",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(FontSizeButton, {
									icon: "general/remove.svg",
									label: "Decrease font size",
									disabled: currentFontSizeIndex <= 0,
									onClick: () => requestFontSize(fontSizeOptions[currentFontSizeIndex - 1] ?? settings.effectiveFontSize)
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(FontSizeButton, {
									icon: "general/reset.svg",
									label: "Reset font size",
									disabled: settings.effectiveFontSize === settings.defaultFontSize,
									onClick: () => requestFontSize(settings.defaultFontSize)
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(FontSizeButton, {
									icon: "general/add.svg",
									label: "Increase font size",
									disabled: currentFontSizeIndex >= fontSizeOptions.length - 1,
									onClick: () => requestFontSize(fontSizeOptions[currentFontSizeIndex + 1] ?? settings.effectiveFontSize)
								})
							]
						})
					]
				})
			})]
		})
	] });
}
function FloatingPanelHeader({ title, closeLabel, onClose }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "markdownFloatingPanelHeader",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "markdownFloatingPanelTitle",
			children: title
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
			type: "button",
			className: "markdownFloatingPanelCloseButton",
			title: "Collapse",
			"aria-label": closeLabel,
			onClick: onClose,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "markdownFloatingPanelCloseIcon",
				"aria-hidden": "true"
			})
		})]
	});
}
function FontSizeButton({ icon, label, disabled, onClick }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
		type: "button",
		className: "markdownFontSizeButton",
		title: label,
		"aria-label": label,
		disabled,
		onClick,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
			src: AllIcons.src(icon),
			alt: "",
			draggable: false
		})
	});
}
function collectTableOfContentsEntries() {
	const contentElement = document.getElementById("content");
	if (!contentElement) return [];
	return Array.from(contentElement.querySelectorAll(headingSelector)).map((heading) => {
		const text = normalizeHeadingText(heading.textContent ?? "");
		if (!heading.id || !text || heading.classList.contains("sr-only") || heading.closest(".footnotes")) return void 0;
		return {
			id: heading.id,
			text,
			level: headingLevel(heading),
			element: heading
		};
	}).filter((entry) => entry !== void 0);
}
function headingLevel(heading) {
	const level = Number(heading.tagName.substring(1));
	return Number.isFinite(level) ? Math.min(6, Math.max(1, level)) : 1;
}
function normalizeHeadingText(text) {
	return text.replace(/\s+/g, " ").trim();
}
function normalizedFontSizeOptions(settings) {
	return Array.from(new Set([
		...settings.fontSizeOptions,
		settings.effectiveFontSize,
		settings.defaultFontSize
	].filter((value) => Number.isFinite(value) && value > 0))).sort((left, right) => left - right);
}
function fontSizeIndex(fontSize, options) {
	return Math.max(0, options.indexOf(closestFontSize(fontSize, options)));
}
function closestFontSize(fontSize, options) {
	return options.reduce((closest, candidate) => {
		return Math.abs(candidate - fontSize) < Math.abs(closest - fontSize) ? candidate : closest;
	}, options[0] ?? fontSize);
}
function classNames$1(...names) {
	return names.filter(Boolean).join(" ") || void 0;
}
//#endregion
//#region views/markdown-preview/src/markdownReactUtils.ts
function codeToString(node) {
	if (typeof node === "string" || typeof node === "number") return String(node);
	if (Array.isArray(node)) return node.map(codeToString).join("");
	return "";
}
function classNames(...names) {
	return names.filter(Boolean).join(" ") || void 0;
}
//#endregion
//#region views/markdown-preview/src/MarkdownZoomControls.tsx
var MARKDOWN_ZOOM_SCALE_EXTENT = [.25, 4];
var MARKDOWN_ZOOM_BUTTON_FACTOR = 1.2;
function MarkdownZoomToolbar({ targetLabel, className, buttonClassName, onZoomOut, onResetZoom, onZoomIn }) {
	const normalizedTargetLabel = targetLabel.toLowerCase();
	const accessibleTargetLabel = `${normalizedTargetLabel.charAt(0).toUpperCase()}${normalizedTargetLabel.slice(1)}`;
	const buttonClass = classNames("markdownZoomToolbarButton", buttonClassName);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: classNames("markdownZoomToolbar", className),
		"aria-label": `${accessibleTargetLabel} zoom controls`,
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: buttonClass,
				"aria-label": `Zoom out ${normalizedTargetLabel}`,
				title: "Zoom out",
				onClick: onZoomOut,
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
					src: AllIcons.src("graph/zoomOut.svg"),
					alt: "",
					draggable: false
				})
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: buttonClass,
				"aria-label": `Reset ${normalizedTargetLabel} zoom`,
				title: "Reset zoom",
				onClick: onResetZoom,
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
					src: AllIcons.src("general/reset.svg"),
					alt: "",
					draggable: false
				})
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: buttonClass,
				"aria-label": `Zoom in ${normalizedTargetLabel}`,
				title: "Zoom in",
				onClick: onZoomIn,
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
					src: AllIcons.src("graph/zoomIn.svg"),
					alt: "",
					draggable: false
				})
			})
		]
	});
}
function shouldHandleZoomEvent(event) {
	return event.type !== "wheel" || event.ctrlKey;
}
//#endregion
//#region views/markdown-preview/src/MarkdownImageBlock.tsx
function MarkdownImageBlock({ src, alt, title, className, style, ...props }) {
	const viewportRef = (0, import_react.useRef)(null);
	const imageRef = (0, import_react.useRef)(null);
	const zoomBehaviorRef = (0, import_react.useRef)(null);
	const [aspectRatio, setAspectRatio] = (0, import_react.useState)();
	(0, import_react.useEffect)(() => {
		const viewport = viewportRef.current;
		const image = imageRef.current;
		if (!viewport || !image) return;
		const zoomBehavior = zoom_default().filter(shouldHandleZoomEvent).scaleExtent(MARKDOWN_ZOOM_SCALE_EXTENT).on("zoom", (event) => {
			image.style.transform = `translate(${event.transform.x}px, ${event.transform.y}px) scale(${event.transform.k})`;
		});
		zoomBehaviorRef.current = zoomBehavior;
		const viewportSelection = select_default(viewport);
		viewportSelection.call(zoomBehavior);
		viewportSelection.call(zoomBehavior.transform, identity);
		return () => {
			viewportSelection.on(".zoom", null);
			image.style.removeProperty("transform");
			zoomBehaviorRef.current = null;
		};
	}, [src]);
	(0, import_react.useEffect)(() => {
		updateAspectRatio();
	}, [src]);
	function zoomBy(factor) {
		const viewport = viewportRef.current;
		const zoomBehavior = zoomBehaviorRef.current;
		if (!viewport || !zoomBehavior) return;
		select_default(viewport).call(zoomBehavior.scaleBy, factor);
	}
	function resetZoom() {
		const viewport = viewportRef.current;
		const zoomBehavior = zoomBehaviorRef.current;
		if (!viewport || !zoomBehavior) return;
		select_default(viewport).call(zoomBehavior.transform, identity);
	}
	function updateAspectRatio() {
		const image = imageRef.current;
		const width = image?.naturalWidth ?? 0;
		const height = image?.naturalHeight ?? 0;
		setAspectRatio(width > 0 && height > 0 ? width / height : void 0);
	}
	const blockStyle = aspectRatio === void 0 ? style : {
		...style,
		"--markdown-image-aspect-ratio": String(aspectRatio)
	};
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		...props,
		className: classNames("markdownImageBlock", "isInteractive", className),
		style: blockStyle,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "markdownImageViewport",
			ref: viewportRef,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
				className: "markdownImage",
				ref: imageRef,
				src,
				alt: alt ?? "",
				title,
				draggable: false,
				onLoad: updateAspectRatio
			})
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MarkdownZoomToolbar, {
			targetLabel: "image",
			className: "markdownImageToolbar",
			buttonClassName: "markdownImageToolbarButton",
			onZoomOut: () => zoomBy(1 / MARKDOWN_ZOOM_BUTTON_FACTOR),
			onResetZoom: resetZoom,
			onZoomIn: () => zoomBy(MARKDOWN_ZOOM_BUTTON_FACTOR)
		})]
	});
}
//#endregion
//#region views/markdown-preview/src/MermaidBlock.tsx
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
	mermaidModule ||= __vitePreload(() => import("./assets/mermaid.js").then((n) => n.t).then((module) => module.default), __vite__mapDeps([0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25]), import.meta.url);
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
//#endregion
//#region views/markdown-preview/src/markdownHastUtils.ts
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
//#endregion
//#region views/markdown-preview/src/markdownLatex.ts
var latexDelimiters = [
	{
		left: "$$",
		right: "$$",
		display: true
	},
	{
		left: "\\[",
		right: "\\]",
		display: true
	},
	{
		left: "\\(",
		right: "\\)",
		display: false
	},
	{
		left: "$",
		right: "$",
		display: false
	},
	{
		left: "\\begin{equation}",
		right: "\\end{equation}",
		display: true
	},
	{
		left: "\\begin{align}",
		right: "\\end{align}",
		display: true
	},
	{
		left: "\\begin{alignat}",
		right: "\\end{alignat}",
		display: true
	},
	{
		left: "\\begin{gather}",
		right: "\\end{gather}",
		display: true
	},
	{
		left: "\\begin{CD}",
		right: "\\end{CD}",
		display: true
	}
];
function renderMarkdownLatex() {
	const contentElement = document.getElementById("content");
	if (!contentElement) return;
	renderMathInElement(contentElement, {
		delimiters: latexDelimiters,
		ignoredClasses: ["katex"],
		throwOnError: false
	});
}
//#endregion
//#region views/markdown-preview/src/markdownPathLinks.tsx
function collectPathLinkCandidates(markdown) {
	const codeSegments = markdownCodeSegments(markdown);
	const candidates = [];
	const seen = /* @__PURE__ */ new Set();
	for (const codeSegment of codeSegments) for (const token of pathTokens(codeSegment)) {
		if (seen.has(token.rawPath)) continue;
		seen.add(token.rawPath);
		candidates.push({
			id: `path-${candidates.length}`,
			rawPath: token.rawPath
		});
	}
	return candidates;
}
function renderPathLinks(node, resolvedRawPaths, keyPrefix, contentVersion, onNavigatePathLink) {
	const content = pathTextContent(node);
	const tokens = pathTokens(content.text).filter((token) => resolvedRawPaths.has(token.rawPath));
	if (tokens.length === 0) return node;
	const parts = [];
	let offset = 0;
	for (const [index, token] of tokens.entries()) {
		if (token.start < offset) continue;
		if (offset < token.start) parts.push(...renderPathTextRange(content.leaves, offset, token.start, `${keyPrefix}-text-${index}`));
		parts.push(/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
			type: "button",
			className: "markdownPathLink",
			onClick: (event) => {
				event.preventDefault();
				event.stopPropagation();
				onNavigatePathLink({
					contentVersion,
					rawPath: token.rawPath,
					clientX: Math.round(event.clientX),
					clientY: Math.round(event.clientY)
				});
			},
			children: renderPathTextRange(content.leaves, token.start, token.end, `${keyPrefix}-link-${index}`)
		}, `${keyPrefix}-${token.start}-${index}`));
		offset = token.end;
	}
	if (offset < content.text.length) parts.push(...renderPathTextRange(content.leaves, offset, content.text.length, `${keyPrefix}-text-end`));
	return parts;
}
function markdownCodeSegments(markdown) {
	const segments = [];
	const markdownWithoutFencedCode = markdown.replace(FENCED_CODE_BLOCK_PATTERN, (match, _prefix, _fence, info, code) => {
		if (String(info).trim().split(/\s+/)[0]?.toLowerCase() !== "mermaid") segments.push(String(code));
		return " ".repeat(match.length);
	});
	for (const match of markdownWithoutFencedCode.matchAll(INLINE_CODE_PATTERN)) segments.push(match[1]);
	return segments;
}
function pathTextContent(node) {
	const leaves = [];
	let text = "";
	function collect(current, wrappers) {
		if (typeof current === "string" || typeof current === "number") {
			const value = String(current);
			if (value.length === 0) return;
			const start = text.length;
			text += value;
			leaves.push({
				text: value,
				start,
				end: text.length,
				wrappers
			});
			return;
		}
		if (Array.isArray(current)) {
			current.forEach((child) => collect(child, wrappers));
			return;
		}
		if ((0, import_react.isValidElement)(current)) {
			const element = current;
			if (element.props.children == null) return;
			collect(element.props.children, [...wrappers, element]);
		}
	}
	collect(node, []);
	return {
		text,
		leaves
	};
}
function renderPathTextRange(leaves, start, end, keyPrefix) {
	const parts = [];
	for (const leaf of leaves) {
		const sliceStart = Math.max(start, leaf.start);
		const sliceEnd = Math.min(end, leaf.end);
		if (sliceStart >= sliceEnd) continue;
		parts.push(renderPathTextLeafSlice(leaf, sliceStart, sliceEnd, `${keyPrefix}-${parts.length}`));
	}
	return parts;
}
function renderPathTextLeafSlice(leaf, start, end, key) {
	let result = leaf.text.slice(start - leaf.start, end - leaf.start);
	for (let index = leaf.wrappers.length - 1; index >= 0; index--) result = (0, import_react.cloneElement)(leaf.wrappers[index], void 0, result);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_react.Fragment, { children: result }, key);
}
function pathTokens(text) {
	const tokens = [];
	let lineStart = 0;
	while (lineStart <= text.length) {
		const nextLineBreak = text.indexOf("\n", lineStart);
		const lineEnd = nextLineBreak < 0 ? text.length : nextLineBreak;
		tokens.push(...pathTokensInLine(text, lineStart, lineEnd));
		if (nextLineBreak < 0) break;
		lineStart = nextLineBreak + 1;
	}
	return tokens;
}
function pathTokensInLine(text, lineStart, lineEnd) {
	const contentStart = firstNonWhitespaceOffset(text, lineStart, lineEnd);
	if (contentStart === void 0) return [];
	const contentEnd = lastNonWhitespaceOffset(text, contentStart, lineEnd);
	const lineText = text.slice(contentStart, contentEnd);
	const linePath = trimPathCandidate(lineText);
	if (isStandalonePathLine(linePath)) {
		const start = contentStart + lineText.indexOf(linePath);
		return [{
			rawPath: linePath,
			start,
			end: start + linePath.length
		}];
	}
	return pathTokenChunks(text, lineStart, lineEnd);
}
function pathTokenChunks(text, startOffset, endOffset) {
	const tokens = [];
	let chunkStart;
	for (let offset = startOffset; offset <= endOffset; offset++) {
		if (offset < endOffset && !isPathTokenSeparator(text[offset])) {
			chunkStart ??= offset;
			continue;
		}
		if (chunkStart === void 0) continue;
		const chunk = text.slice(chunkStart, offset);
		const rawPath = trimPathCandidate(chunk);
		if (rawPath && isPathLike(rawPath)) {
			const leadingTrim = chunk.indexOf(rawPath);
			const start = chunkStart + leadingTrim;
			tokens.push({
				rawPath,
				start,
				end: start + rawPath.length
			});
		}
		chunkStart = void 0;
	}
	return tokens;
}
function firstNonWhitespaceOffset(text, startOffset, endOffset) {
	for (let offset = startOffset; offset < endOffset; offset++) if (!isWhitespace(text[offset])) return offset;
}
function lastNonWhitespaceOffset(text, startOffset, endOffset) {
	let offset = endOffset;
	while (offset > startOffset && isWhitespace(text[offset - 1])) offset--;
	return offset;
}
function trimPathCandidate(candidate) {
	let start = 0;
	let end = candidate.length;
	while (start < end && PATH_TRIM_START.has(candidate[start])) start++;
	while (end > start && PATH_TRIM_END.has(candidate[end - 1])) end--;
	return candidate.slice(start, end);
}
function isPathLike(rawPath) {
	return !URL_SCHEME_PATTERN.test(rawPath) && (rawPath.includes("/") || rawPath.includes("\\") || FILE_EXTENSION_PATTERN.test(rawPath));
}
function isStandalonePathLine(rawPath) {
	return rawPath.length > 0 && !HAS_WHITESPACE_PATTERN.test(rawPath) && isPathLike(rawPath);
}
function isPathTokenSeparator(char) {
	return isWhitespace(char) || PATH_TOKEN_SEPARATORS.has(char);
}
function isWhitespace(char) {
	return WHITESPACE_PATTERN.test(char);
}
var FENCED_CODE_BLOCK_PATTERN = /(^|\n)(`{3,}|~{3,})([^\n]*)\n([\s\S]*?)\n\2(?=\n|$)/g;
var INLINE_CODE_PATTERN = /`([^`\n]+)`/g;
var FILE_EXTENSION_PATTERN = /\.[A-Za-z0-9]+(?:#L\d+|:\d+(?::\d+)?)?$/;
var WHITESPACE_PATTERN = /\s/;
var HAS_WHITESPACE_PATTERN = /\s/;
var PATH_TOKEN_SEPARATORS = new Set([
	"`",
	"<",
	">",
	"\"",
	"'",
	"(",
	")",
	"[",
	"]",
	"{",
	"}"
]);
var PATH_TRIM_START = new Set([
	"(",
	"[",
	"{",
	"<"
]);
var PATH_TRIM_END = new Set([
	")",
	"]",
	"}",
	">",
	".",
	",",
	";"
]);
var URL_SCHEME_PATTERN = /^[a-z][a-z0-9+.-]*:\/\//i;
//#endregion
//#region views/markdown-preview/src/markdownResources.ts
var markdownResourcePrefix = "./__markdown-preview-resource/";
function markdownResourceSrc(src) {
	if (!src || !isLocalMarkdownResource(src)) return src;
	return `${markdownResourcePrefix}${base64UrlEncode(src)}`;
}
function isLocalMarkdownResource(src) {
	const trimmed = src.trim();
	if (!trimmed || trimmed.startsWith("#") || trimmed.startsWith("//")) return false;
	const scheme = trimmed.match(/^([A-Za-z][A-Za-z\d+.-]*):/)?.[1]?.toLowerCase();
	return scheme === void 0 || scheme === "file";
}
function base64UrlEncode(value) {
	const bytes = new TextEncoder().encode(value);
	let binary = "";
	for (const byte of bytes) binary += String.fromCharCode(byte);
	return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
//#endregion
//#region views/markdown-preview/src/markdownRemarkPlugins.ts
function remarkFrontmatterBlocks() {
	return (tree) => transformFrontmatterNodes(tree);
}
function remarkSourcePositionAttributes() {
	return (tree) => addSourcePositionAttributes(tree);
}
function frontmatterLanguageFromPreNode(node) {
	const language = codeNodeFromPreNode(node)?.properties?.dataFrontmatter;
	return typeof language === "string" ? language : void 0;
}
function frontmatterBlockFromPreNode(node) {
	const language = frontmatterLanguageFromPreNode(node);
	if (!language) return void 0;
	const entries = parseFrontmatterEntries(hastText(codeNodeFromPreNode(node)), language);
	if (entries.length === 0) return void 0;
	return { metadata: entries };
}
function transformFrontmatterNodes(node) {
	if (!node.children) return;
	node.children = node.children.map((child) => {
		if (isFrontmatterNode(child)) return frontmatterCodeNode(child);
		transformFrontmatterNodes(child);
		return child;
	});
}
function isFrontmatterNode(node) {
	return node.type === "yaml" || node.type === "toml";
}
function frontmatterCodeNode(node) {
	const language = node.type === "toml" ? "toml" : "yaml";
	return {
		type: "code",
		value: node.value ?? "",
		position: node.position,
		data: {
			...node.data,
			hProperties: {
				...node.data?.hProperties,
				className: [`language-${language}`, "frontmatterCode"],
				dataFrontmatter: language
			}
		}
	};
}
function parseFrontmatterEntries(source, language) {
	return source.split(/\r?\n/).map((line) => parseFrontmatterEntry(line, language)).filter((entry) => entry !== void 0);
}
function parseFrontmatterEntry(line, language) {
	if (line.startsWith(" ") || line.startsWith("	")) return void 0;
	const trimmedLine = line.trim();
	if (!trimmedLine || trimmedLine.startsWith("#")) return void 0;
	const match = language === "toml" ? trimmedLine.match(/^([A-Za-z0-9_.-]+)\s*=\s*(.+)$/) : trimmedLine.match(/^([A-Za-z0-9_.-]+):\s*(.*)$/);
	if (!match) return void 0;
	const key = match[1];
	const value = formatFrontmatterValue(match[2]);
	if (!value) return void 0;
	return {
		key,
		value
	};
}
function formatFrontmatterValue(value) {
	const trimmedValue = value.trim();
	if (!trimmedValue || trimmedValue === "|" || trimmedValue === ">" || trimmedValue.startsWith("{")) return void 0;
	if (trimmedValue.startsWith("[") && trimmedValue.endsWith("]")) return formatFrontmatterArray(trimmedValue.substring(1, trimmedValue.length - 1));
	if (trimmedValue.startsWith("[")) return void 0;
	return unquoteFrontmatterValue(trimmedValue);
}
function formatFrontmatterArray(value) {
	const items = [];
	for (const item of splitFrontmatterArrayItems(value)) {
		const trimmedItem = item.trim();
		if (trimmedItem.startsWith("[") || trimmedItem.startsWith("{")) return void 0;
		const formattedItem = unquoteFrontmatterValue(trimmedItem);
		if (!formattedItem) return void 0;
		items.push(formattedItem);
	}
	return items.length > 0 ? items.join(", ") : void 0;
}
function splitFrontmatterArrayItems(value) {
	const items = [];
	let start = 0;
	let quote;
	let escaped = false;
	for (let index = 0; index < value.length; index++) {
		const character = value[index];
		if (escaped) {
			escaped = false;
			continue;
		}
		if (character === "\\" && quote === "\"") {
			escaped = true;
			continue;
		}
		if (quote) {
			if (character === quote) quote = void 0;
			continue;
		}
		if (character === "\"" || character === "'") {
			quote = character;
			continue;
		}
		if (character === ",") {
			items.push(value.substring(start, index));
			start = index + 1;
		}
	}
	items.push(value.substring(start));
	return items;
}
function unquoteFrontmatterValue(value) {
	const quote = value[0];
	const trimmedValue = ((quote === "\"" || quote === "'") && value.endsWith(quote) ? value.substring(1, value.length - 1) : value).trim();
	return trimmedValue.length > 0 ? trimmedValue : void 0;
}
function addSourcePositionAttributes(node) {
	const position = node.position;
	if (position?.start?.line && position.end?.line) {
		node.data ??= {};
		node.data.hProperties = {
			...node.data.hProperties,
			dataSourcepos: `${position.start.line}:${position.start.column ?? 1}-${position.end.line}:${position.end.column ?? 1}`
		};
	}
	node.children?.forEach(addSourcePositionAttributes);
}
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/define.ts
function defineControl(tagName, constructor, registry = customElements) {
	if (!registry.get(tagName)) registry.define(tagName, constructor);
}
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/styles.ts
var hostStyles = i`
  :host {
    box-sizing: border-box;
    color: var(--jb-text-color);
    font-family: var(--jb-font-family);
    font-size: var(--jb-font-size);
    line-height: var(--jb-line-height);
    -webkit-user-select: none;
    user-select: none;
  }

  :host([hidden]) {
    display: none !important;
  }

  *,
  *::before,
  *::after {
    box-sizing: inherit;
  }

  button,
  input,
  select,
  textarea {
    font: inherit;
  }

  [disabled],
  :host([disabled]) {
    cursor: default;
  }
`;
i`
  .button {
    appearance: none;
    align-items: center;
    background: var(--jb-bg-control);
    border: 1px solid var(--jb-border-color);
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    cursor: default;
    display: inline-flex;
    gap: var(--jb-control-gap);
    justify-content: center;
    line-height: var(--jb-line-height);
    min-height: var(--jb-control-height);
    min-width: var(--jb-control-height);
    outline: none;
    padding: 0 var(--jb-control-padding-x);
    position: relative;
    -webkit-user-select: none;
    user-select: none;
    white-space: nowrap;
  }

  .button:hover:not(:disabled) {
    background: var(--jb-bg-hover);
  }

  .button:active:not(:disabled),
  .button[data-pressed="true"] {
    background: var(--jb-bg-pressed);
  }

  .button:focus-visible {
    box-shadow: var(--jb-focus-ring);
  }

  .button:disabled {
    border-color: var(--jb-border-color-muted);
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .button.primary {
    background: var(--jb-accent-color);
    border-color: var(--jb-accent-color);
    color: var(--jb-text-on-accent);
  }

  .button.primary:hover:not(:disabled) {
    background: var(--jb-accent-hover-color);
    border-color: var(--jb-accent-hover-color);
  }

  .button.danger {
    color: var(--jb-danger-color);
  }

  .button.link {
    background: transparent;
    border-color: transparent;
    color: var(--jb-accent-text-color);
    min-height: var(--jb-control-height-compact);
    min-width: 0;
    padding: 0;
  }

  .button.link:hover:not(:disabled) {
    background: transparent;
    color: var(--jb-accent-hover-color);
    text-decoration: underline;
  }

  .button.toolbar,
  .button.icon {
    background: transparent;
    border-color: transparent;
    height: var(--jb-control-height-compact);
    min-height: var(--jb-control-height-compact);
    min-width: var(--jb-control-height-compact);
    padding: 0 var(--jb-space-xs);
  }

  .button.icon {
    width: var(--jb-control-height-compact);
  }

  .button.selected,
  .button[aria-pressed="true"] {
    background: var(--jb-bg-selected-muted);
    border-color: var(--jb-accent-soft-bg);
    color: var(--jb-text-color);
  }

  .button.small {
    min-height: var(--jb-control-height-compact);
    padding-inline: var(--jb-space-sm);
  }

  .button [part="label"] {
    align-items: center;
    display: inline-flex;
    justify-content: center;
    line-height: var(--jb-line-height);
    min-height: var(--jb-line-height);
  }

  .button .icon-slot.empty {
    display: none;
  }

  .button-icon {
    color: currentColor;
    display: inline-flex;
    flex: 0 0 auto;
    height: 12px;
    line-height: 1;
    position: relative;
    width: 12px;
  }

  .button-icon::before,
  .button-icon::after {
    background: currentColor;
    border-radius: 1px;
    content: "";
    height: 1.5px;
    left: 50%;
    position: absolute;
    top: 50%;
    transform: translate(-50%, -50%);
    width: 8px;
  }

  .button-icon.plus::after {
    transform: translate(-50%, -50%) rotate(90deg);
  }

  .button-icon.minus::after {
    display: none;
  }

  .icon-slot,
  .chevron {
    align-items: center;
    display: inline-flex;
    flex: 0 0 auto;
    height: 12px;
    justify-content: center;
    line-height: 1;
    position: relative;
    width: 12px;
  }

  .chevron {
    color: var(--jb-text-muted);
  }

  .chevron::before {
    border: solid currentColor;
    border-width: 0 1.5px 1.5px 0;
    content: "";
    height: 5px;
    margin-top: -3px;
    transform: rotate(45deg);
    width: 5px;
  }

  .chevron.right::before {
    margin-left: -3px;
    margin-top: 0;
    transform: rotate(-45deg);
  }
`;
i`
  .field-control,
  .textarea,
  .select {
    appearance: none;
    background: var(--jb-bg-input);
    border: 1px solid var(--jb-border-color);
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    min-height: var(--jb-control-height);
    outline: none;
    padding: 0 var(--jb-control-padding-x);
    width: 100%;
  }

  .field-control:hover:not(:disabled):not([readonly]),
  .textarea:hover:not(:disabled):not([readonly]),
  .select:hover:not(:disabled) {
    border-color: var(--jb-border-color-strong);
  }

  .field-control:focus,
  .textarea:focus,
  .select:focus {
    border-color: var(--jb-accent-color);
    box-shadow: var(--jb-focus-ring);
    outline: none;
  }

  .field-control:disabled,
  .textarea:disabled,
  .select:disabled {
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .field-control[aria-invalid="true"],
  .textarea[aria-invalid="true"],
  .select[aria-invalid="true"] {
    border-color: var(--jb-danger-color);
  }

  .field-control::placeholder,
  .textarea::placeholder {
    color: var(--jb-text-secondary);
  }

  .textarea {
    line-height: var(--jb-line-height-paragraph);
    min-height: 72px;
    padding-block: var(--jb-space-xs);
    resize: vertical;
  }

  .select-wrap,
  .combo-wrap {
    position: relative;
  }

  .select {
    padding-right: 26px;
    -webkit-user-select: none;
    user-select: none;
  }

  .field-control,
  .textarea {
    -webkit-user-select: text;
    user-select: text;
  }

  .select-wrap::after {
    border: solid currentColor;
    border-width: 0 1.5px 1.5px 0;
    color: var(--jb-text-muted);
    content: "";
    height: 5px;
    pointer-events: none;
    position: absolute;
    right: 9px;
    top: 50%;
    transform: translateY(-65%) rotate(45deg);
    -webkit-user-select: none;
    user-select: none;
    width: 5px;
  }
`;
i`
  .popup {
    background: var(--jb-bg-panel);
    border: 1px solid var(--jb-border-color-muted);
    border-radius: var(--jb-control-radius);
    box-shadow: var(--jb-popup-shadow);
    display: grid;
    gap: 1px;
    margin-top: var(--jb-space-xs);
    min-width: 160px;
    padding: var(--jb-space-xs);
    position: absolute;
    z-index: 10;
  }

  .menu-root {
    display: inline-block;
    position: relative;
  }

  .menu-item {
    appearance: none;
    background: transparent;
    border: 0;
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    min-height: var(--jb-control-height-compact);
    padding: 0 var(--jb-space-sm);
    text-align: left;
    -webkit-user-select: none;
    user-select: none;
    white-space: nowrap;
  }

  .menu-item:hover:not(:disabled),
  .menu-item:focus-visible {
    background: var(--jb-bg-hover);
    outline: none;
  }

  .menu-item:disabled {
    color: var(--jb-text-disabled);
  }
`;
i`
  :host {
    display: inline-flex;
    vertical-align: middle;
  }

  .choice {
    align-items: flex-start;
    color: var(--jb-text-color);
    display: inline-flex;
    gap: var(--jb-control-gap);
    min-height: var(--jb-control-height-compact);
    position: relative;
    -webkit-user-select: none;
    user-select: none;
  }

  .native-check {
    height: 1px;
    left: 8px;
    opacity: 0;
    position: absolute;
    top: 8px;
    width: 1px;
  }

  .mark {
    align-items: center;
    background: var(--jb-bg-input);
    border: 1px solid var(--jb-border-color);
    color: var(--jb-text-on-accent);
    display: inline-flex;
    flex: 0 0 auto;
    height: 16px;
    justify-content: center;
    margin-top: 1px;
    width: 16px;
  }

  .mark::before {
    box-sizing: border-box;
    content: "";
    flex: 0 0 auto;
    opacity: 0;
  }

  .checkbox .mark {
    border-radius: 3px;
  }

  .radio .mark {
    border-radius: 50%;
  }

  .native-check:focus-visible + .mark {
    box-shadow: var(--jb-focus-ring);
  }

  .native-check:checked + .mark,
  .native-check:indeterminate + .mark {
    background: var(--jb-accent-color);
    border-color: var(--jb-accent-color);
  }

  .native-check:disabled + .mark,
  .native-check:disabled ~ .choice-label {
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .checkbox .mark::before {
    border: solid currentColor;
    border-width: 0 2px 2px 0;
    height: 8px;
    margin-top: -1px;
    transform: rotate(45deg);
    width: 4px;
  }

  .checkbox .native-check:checked + .mark::before {
    opacity: 1;
  }

  .checkbox .native-check:indeterminate + .mark::before {
    background: currentColor;
    border: 0;
    height: 2px;
    margin-top: 0;
    opacity: 1;
    transform: none;
    width: 8px;
  }

  .radio .mark::before {
    background: currentColor;
    border-radius: 50%;
    height: 6px;
    width: 6px;
  }

  .radio .native-check:checked + .mark::before {
    opacity: 1;
  }
`;
//#endregion
//#region ../../webview-src/packages/controls/src/elements/icon/icon.ts
var JbIcon = class extends i$1 {
	static properties = {
		label: {
			type: String,
			reflect: true
		},
		name: {
			type: String,
			reflect: true
		},
		size: {
			type: String,
			reflect: true
		},
		src: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    :host {
      display: inline-flex;
      vertical-align: middle;
    }

    .icon {
      align-items: center;
      color: currentColor;
      display: inline-flex;
      height: 16px;
      justify-content: center;
      line-height: 1;
      -webkit-user-select: none;
      user-select: none;
      width: 16px;
    }

    .icon.large {
      height: 20px;
      width: 20px;
    }

    img {
      display: block;
      height: 100%;
      width: 100%;
    }
  `];
	constructor() {
		super();
		this.label = "";
		this.name = "";
		this.size = "default";
		this.src = "";
	}
	render() {
		return b`<span part="icon" class=${["icon", this.size].join(" ")} role=${this.label ? "img" : A} aria-label=${this.label || A}>${this.renderContent()}</span>`;
	}
	renderContent() {
		if (this.src) return b`<img src=${this.src} alt="" draggable="false">`;
		return b`<slot>${this.name}</slot>`;
	}
};
function defineJbIcon(registry) {
	defineControl("jb-icon", JbIcon, registry);
}
//#endregion
//#region ../../webview-src/packages/controls/src/define/icon.ts
defineJbIcon();
//#endregion
//#region views/markdown-preview/src/markdownSourcePositions.ts
var sourcePositionPattern = /^(\d+):(\d+)-(\d+):(\d+)$/;
var sourceDecorationClassNames = [
	"is-source-selected",
	"is-vcs-added",
	"is-vcs-modified"
];
var sourceDecorationClassSelector = sourceDecorationClassNames.map((className) => `.${className}`).join(", ");
var sourceDecorationBlockTagNames = new Set([
	"BLOCKQUOTE",
	"DD",
	"DETAILS",
	"DIV",
	"DL",
	"DT",
	"H1",
	"H2",
	"H3",
	"H4",
	"H5",
	"H6",
	"LI",
	"OL",
	"P",
	"PRE",
	"SECTION",
	"TABLE",
	"TBODY",
	"TD",
	"TFOOT",
	"TH",
	"THEAD",
	"TR",
	"UL"
]);
var removedBlockPlaceholderClassName = "markdownRemovedBlockPlaceholder";
var scheduledScrollFrame;
function scrollMarkdownPreviewToLine(line) {
	cancelScheduledMarkdownPreviewScroll();
	scheduledScrollFrame = window.requestAnimationFrame(() => {
		scheduledScrollFrame = void 0;
		scrollToSourceLine(line);
	});
}
function cancelScheduledMarkdownPreviewScroll() {
	if (scheduledScrollFrame === void 0) return;
	window.cancelAnimationFrame(scheduledScrollFrame);
	scheduledScrollFrame = void 0;
}
function decorateSourceBlocks(selection, changes) {
	const contentElement = markdownContentElement();
	if (!contentElement) return;
	clearSourceDecorations(contentElement);
	const elements = sourceDecorationElements(contentElement);
	if (selection) {
		for (const element of elements) if (sourceRangesIntersect(element, selection)) element.element.classList.add("is-source-selected");
	}
	for (const change of changes) {
		if (change.kind === "REMOVED") {
			insertRemovedBlockPlaceholder(contentElement, elements, change);
			continue;
		}
		const className = change.kind === "ADDED" ? "is-vcs-added" : "is-vcs-modified";
		for (const element of elements) if (sourceLinesIntersect(element, change)) element.element.classList.add(className);
	}
}
function clearSourceDecorations(root = markdownContentElement()) {
	if (!root) return;
	root.querySelectorAll(sourceDecorationClassSelector).forEach((element) => {
		element.classList.remove(...sourceDecorationClassNames);
	});
	root.querySelectorAll(`.${removedBlockPlaceholderClassName}`).forEach((placeholder) => {
		placeholder.remove();
	});
}
function sourcePositionFromPreNode(node) {
	const prePosition = sourcePositionFromHastNode(node);
	if (prePosition) return prePosition;
	return sourcePositionFromHastNode(codeNodeFromPreNode(node));
}
function sourcePositionFromHastNode(node) {
	const value = node?.properties?.dataSourcepos;
	return typeof value === "string" ? parseSourcePosition(value) : void 0;
}
function positionKey(sourcePosition) {
	return `${sourcePosition.startLine}:${sourcePosition.startColumn}-${sourcePosition.endLine}:${sourcePosition.endColumn}`;
}
function scrollToSourceLine(line) {
	const contentElement = markdownContentElement();
	if (!contentElement) return;
	const targetLine = Math.max(1, line + 1);
	const target = findElementForLine(sourcePositionElements(contentElement), targetLine);
	if (target) target.scrollIntoView({
		block: "start",
		behavior: "instant"
	});
	else if (targetLine === 1) window.scrollTo({
		top: 0,
		behavior: "instant"
	});
}
function markdownContentElement() {
	return document.querySelector(".markdownPreviewContent") ?? document.getElementById("content");
}
function sourcePositionElements(root) {
	return Array.from(root.querySelectorAll("[data-sourcepos]")).map((element) => {
		const sourcePosition = parseSourcePosition(element.dataset.sourcepos);
		return sourcePosition ? {
			element,
			...sourcePosition
		} : void 0;
	}).filter((element) => element !== void 0);
}
function sourceDecorationElements(root) {
	const elements = [];
	const seenTargets = /* @__PURE__ */ new Set();
	for (const sourcePosition of sourcePositionElements(root)) {
		const target = sourceDecorationTarget(sourcePosition.element);
		if (!target || seenTargets.has(target)) continue;
		seenTargets.add(target);
		elements.push({
			...sourcePosition,
			element: target
		});
	}
	return elements;
}
function sourceDecorationTarget(element) {
	if (sourceDecorationBlockTagNames.has(element.tagName)) return element;
	if (element.tagName === "CODE" && element.parentElement?.tagName === "PRE") return element.parentElement;
}
function sourceRangesIntersect(first, second) {
	if (first.endLine < second.startLine || second.endLine < first.startLine) return false;
	if (first.endLine === second.startLine && first.endColumn < second.startColumn) return false;
	if (second.endLine === first.startLine && second.endColumn < first.startColumn) return false;
	return true;
}
function sourceLinesIntersect(sourcePosition, change) {
	return sourcePosition.startLine <= change.endLine && change.startLine <= sourcePosition.endLine;
}
function insertRemovedBlockPlaceholder(root, elements, change) {
	const placeholder = document.createElement("div");
	placeholder.className = removedBlockPlaceholderClassName;
	placeholder.setAttribute("aria-hidden", "true");
	const anchorElement = elements.find((element) => element.startLine >= change.startLine)?.element;
	const insertionTarget = anchorElement ? directChildOf(root, anchorElement) : void 0;
	root.insertBefore(placeholder, insertionTarget ?? null);
}
function directChildOf(root, element) {
	let current = element;
	while (current.parentElement && current.parentElement !== root && root.contains(current.parentElement)) current = current.parentElement;
	return current.parentElement === root ? current : void 0;
}
function parseSourcePosition(sourcePosition) {
	const match = sourcePosition?.match(sourcePositionPattern);
	if (!match) return void 0;
	return {
		startLine: Number(match[1]),
		startColumn: Number(match[2]),
		endLine: Number(match[3]),
		endColumn: Number(match[4])
	};
}
function findElementForLine(elements, targetLine) {
	let containingElement;
	let nextElement;
	let previousElement;
	for (const element of elements) {
		if (element.startLine <= targetLine && targetLine <= element.endLine) {
			if (!containingElement || lineSpan(element) < lineSpan(containingElement)) containingElement = element;
			continue;
		}
		if (element.startLine > targetLine) {
			nextElement = element;
			break;
		}
		previousElement = element;
	}
	return containingElement?.element ?? nextElement?.element ?? previousElement?.element;
}
function lineSpan(element) {
	return element.endLine - element.startLine;
}
//#endregion
//#region views/markdown-preview/src/markdownRunCommands.tsx
var runLineIcon = { src: () => AllIcons.src("expui/gutter/run.svg") };
var runBlockIcon = { src: () => AllIcons.src("expui/gutter/rerun.svg") };
function codeFenceCommandCandidates(sourcePosition, codeNode) {
	const code = hastText(codeNode);
	const language = codeFenceLanguage(codeNode);
	if (isMermaidLanguage(language)) return [];
	const lineCommands = lineCommandCandidates(sourcePosition, code);
	const result = [];
	if (language) result.push({
		...commandSource(sourcePosition),
		id: commandId("BLOCK", sourcePosition, code),
		kind: "BLOCK",
		rawCommand: code,
		language,
		firstLineCommandId: lineCommands.length > 1 ? lineCommands[0].id : void 0
	});
	result.push(...lineCommands);
	return result;
}
function inlineCommandCandidate(sourcePosition, rawCommand) {
	return {
		...commandSource(sourcePosition),
		id: commandId("INLINE", sourcePosition, rawCommand),
		kind: "INLINE",
		rawCommand
	};
}
function uniqueCommandCandidates(candidates) {
	const result = /* @__PURE__ */ new Map();
	for (const candidate of candidates) result.set(candidate.id, candidate);
	return Array.from(result.values());
}
function isMermaidCodeNode(codeNode) {
	return isMermaidLanguage(codeFenceLanguage(codeNode));
}
function hasLanguageClass(className) {
	return className?.split(/\s+/).some((name) => name.startsWith("language-")) ?? false;
}
function createCommandLookup(commands) {
	const inlineCommands = /* @__PURE__ */ new Map();
	const blockCommands = [];
	const lineCommands = [];
	for (const command of commands) if (command.kind === "BLOCK") blockCommands.push(command);
	else if (command.kind === "LINE") lineCommands.push(command);
	else if (command.kind === "INLINE") inlineCommands.set(positionKey(command), command);
	return {
		blockCommands,
		lineCommands,
		inlineCommands
	};
}
function findBlockCommand(lookup, sourcePosition) {
	return lookup.blockCommands.find((command) => command.startLine === sourcePosition.startLine);
}
function findLineCommands(lookup, sourcePosition, blockFirstLineCommandId) {
	return lookup.lineCommands.filter((command) => {
		return command.id !== blockFirstLineCommandId && command.startLine > sourcePosition.startLine && command.endLine <= sourcePosition.endLine;
	});
}
function findInlineCommand(lookup, sourcePosition) {
	return lookup.inlineCommands.get(positionKey(sourcePosition));
}
function CodeFenceRunGutter({ contentVersion, sourcePosition, blockCommand, lineCommands, onRunCommand }) {
	if (!sourcePosition || !blockCommand && lineCommands.length === 0) return null;
	const contentStartLine = sourcePosition.startLine + 1;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "codeFenceRunGutter",
		"aria-hidden": false,
		children: [blockCommand && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RunCommandButton, {
			contentVersion,
			command: blockCommand,
			variant: "block",
			onRunCommand
		}), lineCommands.map((command) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RunCommandButton, {
			contentVersion,
			command,
			variant: "line",
			style: { top: `calc(${Math.max(0, command.startLine - contentStartLine)} * var(--markdown-code-line-height))` },
			onRunCommand
		}, command.id))]
	});
}
function RunCommandButton({ contentVersion, command, variant, style, onRunCommand }) {
	function handleClick(event) {
		event.preventDefault();
		event.stopPropagation();
		onRunCommand({
			contentVersion,
			id: command.id,
			clientX: Math.round(event.clientX),
			clientY: Math.round(event.clientY)
		});
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
		type: "button",
		className: classNames("markdownRunButton", `is-${variant}`),
		title: command.title,
		"aria-label": command.title,
		style,
		onClick: handleClick,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-icon", {
			className: "markdownRunIcon",
			src: runCommandIcon(variant).src(),
			"aria-hidden": true
		})
	});
}
function lineCommandCandidates(sourcePosition, code) {
	const result = [];
	let offset = 0;
	let lineIndex = 0;
	while (offset < code.length) {
		const delimiter = code.indexOf("\n", offset);
		const lineEndOffset = delimiter < 0 ? code.length : delimiter;
		const rawCommand = code.slice(offset, lineEndOffset);
		const lineSource = codeLineSourcePosition(sourcePosition, lineIndex, rawCommand);
		result.push({
			...commandSource(lineSource),
			id: commandId("LINE", lineSource, rawCommand),
			kind: "LINE",
			rawCommand
		});
		if (delimiter < 0) break;
		offset = delimiter + 1;
		lineIndex++;
	}
	return result;
}
function commandSource(sourcePosition) {
	return {
		startLine: sourcePosition.startLine,
		startColumn: sourcePosition.startColumn,
		endLine: sourcePosition.endLine,
		endColumn: sourcePosition.endColumn
	};
}
function codeLineSourcePosition(sourcePosition, lineIndex, rawCommand) {
	const line = sourcePosition.startLine + lineIndex + 1;
	return {
		startLine: line,
		startColumn: 1,
		endLine: line,
		endColumn: rawCommand.length + 1
	};
}
function commandId(kind, sourcePosition, rawCommand) {
	return `${kind}:${positionKey(sourcePosition)}:${hashString(rawCommand)}`;
}
function hashString(value) {
	let hash = 0;
	for (let index = 0; index < value.length; index++) hash = Math.imul(hash, 31) + value.charCodeAt(index) | 0;
	return (hash >>> 0).toString(16);
}
function codeFenceLanguage(codeNode) {
	return hastClassNames(codeNode).find((className) => className.startsWith("language-"))?.substring(9);
}
function isMermaidLanguage(language) {
	return language?.toLowerCase() === "mermaid";
}
function runCommandIcon(variant) {
	return variant === "block" ? runBlockIcon : runLineIcon;
}
//#endregion
//#region views/markdown-preview/src/markdownSanitizeSchema.ts
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
		"*": mergeAttributes("*", [["dataSourcepos"]]),
		a: mergeAttributes("a", [["ariaLabel"], ["dataFootnoteBackref"]]),
		code: mergeAttributes("code", [[
			"className",
			/^language-./,
			"frontmatterCode",
			"no-highlight",
			"nohighlight"
		], [
			"dataFrontmatter",
			"yaml",
			"toml"
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
	},
	protocols: {
		...defaultSchema.protocols,
		href: mergeProtocols("href", ["file"]),
		src: mergeProtocols("src", ["file"])
	}
};
function mergeAttributes(tagName, additions) {
	return [...defaultAttributes[tagName] || [], ...additions];
}
function unique(values) {
	return Array.from(new Set(values));
}
function mergeProtocols(attributeName, additions) {
	return unique([...defaultSchema.protocols?.[attributeName] || [], ...additions]);
}
//#endregion
//#region views/markdown-preview/src/MarkdownPreviewApp.tsx
var emptyPathSet = /* @__PURE__ */ new Set();
var remarkPlugins = [
	remarkGfm,
	[remarkFrontmatter, ["yaml", "toml"]],
	remarkFrontmatterBlocks,
	remarkSourcePositionAttributes
];
var rehypePlugins = [
	rehypeRaw,
	rehypeSlug,
	[rehypeSanitize, markdownSanitizeSchema],
	[rehypeHighlight, {
		detect: true,
		plainText: [
			"mermaid",
			"text",
			"txt"
		]
	}]
];
function MarkdownPreviewApp({ markdown, scrollLine, contentVersion, changes, selection, settings, theme, onOpenLink, onResolveRunCommands, onRunCommand, onResolvePathLinks, onNavigatePathLink, onSetFontSize }) {
	const commandCandidates = [];
	const pathLinkCandidates = (0, import_react.useMemo)(() => collectPathLinkCandidates(markdown), [markdown]);
	const [resolvedCommands, setResolvedCommands] = (0, import_react.useState)({
		contentVersion: -1,
		commands: []
	});
	const [resolvedPathLinks, setResolvedPathLinks] = (0, import_react.useState)({
		contentVersion: -1,
		rawPaths: emptyPathSet
	});
	const commandsReady = resolvedCommands.contentVersion === contentVersion;
	const pathLinksReady = resolvedPathLinks.contentVersion === contentVersion;
	const commands = commandsReady ? resolvedCommands.commands : [];
	const resolvedRawPaths = pathLinksReady ? resolvedPathLinks.rawPaths : emptyPathSet;
	const commandLookup = createCommandLookup(commands);
	const components = {
		a({ href, children, ...props }) {
			function handleClick(event) {
				if (!href) return;
				event.preventDefault();
				const localFragment = localAnchorFragment(href);
				if (localFragment !== void 0) {
					navigateToLocalAnchor(localFragment);
					return;
				}
				onOpenLink(href);
			}
			return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
				...props,
				href,
				onClick: handleClick,
				children
			});
		},
		img({ src, alt, ...props }) {
			return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
				...props,
				src: markdownResourceSrc(src),
				alt
			});
		},
		p({ node, className, children, ...props }) {
			const image = standaloneImageFromParagraphNode(node);
			if (image) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MarkdownImageBlock, {
				...props,
				className,
				src: markdownResourceSrc(image.src) ?? image.src,
				alt: image.alt,
				title: image.title
			});
			return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				...props,
				className,
				children
			});
		},
		pre({ node, className, children, ...props }) {
			if (frontmatterLanguageFromPreNode(node)) {
				const frontmatterBlock = frontmatterBlockFromPreNode(node);
				if (!frontmatterBlock) return null;
				const sourcePosition = sourcePositionFromPreNode(node);
				return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
					className: "frontmatterBlock",
					"data-sourcepos": sourcePosition ? positionKey(sourcePosition) : void 0,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("details", {
						className: "frontmatterMetadata",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("summary", { children: "Frontmatter metadata" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("dl", { children: frontmatterBlock.metadata.map((entry, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "frontmatterMetadataEntry",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("dt", { children: entry.key }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("dd", { children: entry.value })]
						}, `${entry.key}-${index}`)) })]
					})
				});
			}
			const sourcePosition = sourcePositionFromPreNode(node);
			const codeNode = codeNodeFromPreNode(node);
			const isMermaidFence = codeNode ? isMermaidCodeNode(codeNode) : false;
			if (sourcePosition && codeNode && !isMermaidFence) commandCandidates.push(...codeFenceCommandCandidates(sourcePosition, codeNode));
			if (isMermaidFence) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_jsx_runtime.Fragment, { children });
			const blockCommand = sourcePosition ? findBlockCommand(commandLookup, sourcePosition) : void 0;
			const lineCommands = sourcePosition ? findLineCommands(commandLookup, sourcePosition, blockCommand?.firstLineCommandId) : [];
			if (!blockCommand && lineCommands.length === 0) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", {
				className,
				...props,
				children
			});
			return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "codeFenceWithCommands",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", {
					className,
					...props,
					children
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(CodeFenceRunGutter, {
					contentVersion,
					sourcePosition,
					blockCommand,
					lineCommands,
					onRunCommand
				})]
			});
		},
		code({ node, className, children, ...props }) {
			const code = codeToString(children).replace(/\n$/, "");
			if (className?.split(/\s+/).includes("language-mermaid")) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MermaidBlock, {
				chart: code,
				theme
			});
			const sourcePosition = sourcePositionFromHastNode(node);
			if (sourcePosition && !hasLanguageClass(className)) commandCandidates.push(inlineCommandCandidate(sourcePosition, code));
			const inlineCommand = sourcePosition ? findInlineCommand(commandLookup, sourcePosition) : void 0;
			const linkedChildren = renderPathLinks(children, resolvedRawPaths, "code", contentVersion, onNavigatePathLink);
			if (inlineCommand) return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("code", {
				className,
				...props,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(RunCommandButton, {
					contentVersion,
					command: inlineCommand,
					variant: "inline",
					onRunCommand
				}), linkedChildren]
			});
			return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("code", {
				className,
				...props,
				children: linkedChildren
			});
		}
	};
	(0, import_react.useEffect)(() => {
		if (pathLinkCandidates.length === 0) {
			setResolvedPathLinks({
				contentVersion,
				rawPaths: emptyPathSet
			});
			return;
		}
		let cancelled = false;
		setResolvedPathLinks({
			contentVersion: -1,
			rawPaths: emptyPathSet
		});
		onResolvePathLinks({
			contentVersion,
			candidates: pathLinkCandidates
		}).then((response) => {
			if (cancelled) return;
			const resolvedIds = new Set(response.resolvedIds);
			setResolvedPathLinks({
				contentVersion,
				rawPaths: new Set(pathLinkCandidates.filter((candidate) => resolvedIds.has(candidate.id)).map((candidate) => candidate.rawPath))
			});
		}).catch(() => {
			if (!cancelled) setResolvedPathLinks({
				contentVersion,
				rawPaths: emptyPathSet
			});
		});
		return () => {
			cancelled = true;
		};
	}, [
		contentVersion,
		onResolvePathLinks,
		pathLinkCandidates
	]);
	(0, import_react.useEffect)(() => {
		let cancelled = false;
		onResolveRunCommands({
			contentVersion,
			candidates: uniqueCommandCandidates(commandCandidates)
		}).then((response) => {
			if (!cancelled) setResolvedCommands({
				contentVersion,
				commands: response.commands
			});
		});
		return () => {
			cancelled = true;
		};
	}, [contentVersion, onResolveRunCommands]);
	(0, import_react.useEffect)(() => {
		if (commandsReady && pathLinksReady) renderMarkdownLatex();
	}, [
		commandsReady,
		markdown,
		pathLinksReady,
		theme
	]);
	(0, import_react.useEffect)(() => {
		scrollMarkdownPreviewToLine(scrollLine);
		return cancelScheduledMarkdownPreviewScroll;
	}, [markdown, scrollLine]);
	(0, import_react.useEffect)(() => {
		decorateSourceBlocks(selection, changes);
		return clearSourceDecorations;
	}, [
		markdown,
		selection,
		changes
	]);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "markdownPreviewContent webview-selectable-text",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Markdown, {
			remarkPlugins,
			rehypePlugins,
			components,
			urlTransform: (url) => url,
			children: markdown
		})
	}, contentVersion), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(FloatingMarkdownControls, {
		markdown,
		settings,
		onSetFontSize
	})] });
}
function standaloneImageFromParagraphNode(node) {
	const contentChildren = (node?.children ?? []).filter((child) => !isWhitespaceTextNode(child));
	if (contentChildren.length !== 1) return void 0;
	const imageNode = contentChildren[0];
	if (imageNode.tagName !== "img") return void 0;
	const src = stringProperty(imageNode.properties?.src);
	if (!src) return void 0;
	return {
		src,
		alt: stringProperty(imageNode.properties?.alt),
		title: stringProperty(imageNode.properties?.title)
	};
}
function localAnchorFragment(href) {
	if (href.startsWith("#")) return href.slice(1);
	try {
		const url = new URL(href, window.location.href);
		if (url.origin === window.location.origin && url.pathname === window.location.pathname && url.search === window.location.search && url.hash.startsWith("#")) return url.hash.slice(1);
	} catch {}
}
function navigateToLocalAnchor(fragment) {
	if (fragment.length === 0) {
		updateLocationHash("#");
		window.scrollTo({
			top: 0,
			left: 0
		});
		return;
	}
	const target = findLocalAnchorTarget(decodeFragment(fragment));
	updateLocationHash(`#${fragment}`);
	target?.scrollIntoView({ block: "start" });
}
function findLocalAnchorTarget(id) {
	const normalizedId = id.replace(/^-+/, "");
	return document.getElementById(id) ?? document.getElementById(normalizedId) ?? document.getElementById(`user-content-${id}`) ?? document.getElementById(`user-content-${normalizedId}`);
}
function decodeFragment(fragment) {
	try {
		return decodeURIComponent(fragment);
	} catch {
		return fragment;
	}
}
function updateLocationHash(href) {
	if (!window.history) return;
	try {
		window.history.pushState(null, "", href);
	} catch {}
}
function isWhitespaceTextNode(node) {
	return node.tagName === void 0 && (node.value ?? "").trim().length === 0;
}
function stringProperty(value) {
	return typeof value === "string" ? value : void 0;
}
//#endregion
//#region views/markdown-preview/src/main.tsx
var markdownPreviewPageApiId = apiId()("markdown.preview");
var markdownPreviewHostApi = webView.callable(apiId()("markdown.preview"));
var root = (0, import_client.createRoot)(requiredElement("content"));
var markdown = "";
var scrollLine = 0;
var contentVersion = -1;
var changes = [];
var selection;
var theme = webViewTheme.current;
var previewSettings = defaultPreviewSettings();
webView.implement(markdownPreviewPageApiId, {
	contentChanged(params) {
		markdown = params.markdown;
		scrollLine = params.scrollLine;
		contentVersion = params.contentVersion;
		previewSettings = normalizePreviewSettings(params.settings);
		applyPreviewSettings(previewSettings);
		changes = params.changes ?? [];
		renderPreview();
	},
	scrollToLine(params) {
		scrollLine = params.line;
		scrollMarkdownPreviewToLine(scrollLine);
	},
	selectionChanged(params) {
		selection = params.selection ?? void 0;
		decorateSourceBlocks(selection, changes);
	}
});
applyTheme(theme);
renderPreview();
webViewTheme.onChanged((nextTheme) => {
	theme = nextTheme;
	applyTheme(nextTheme);
});
markdownPreviewHostApi.pageReady();
function renderPreview() {
	root.render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MarkdownPreviewApp, {
		markdown,
		scrollLine,
		contentVersion,
		changes,
		selection,
		settings: previewSettings,
		theme,
		onOpenLink: openMarkdownLink,
		onResolveRunCommands: resolveMarkdownRunCommands,
		onRunCommand: runMarkdownCommand,
		onResolvePathLinks: resolveMarkdownPathLinks,
		onNavigatePathLink: navigateMarkdownPathLink,
		onSetFontSize: setMarkdownFontSize
	}));
}
function openMarkdownLink(href) {
	markdownPreviewHostApi.openLink({ href });
}
function resolveMarkdownRunCommands(request) {
	return markdownPreviewHostApi.resolveRunCommands(request);
}
function runMarkdownCommand(request) {
	markdownPreviewHostApi.runCommand(request);
}
function resolveMarkdownPathLinks(request) {
	return markdownPreviewHostApi.resolvePathLinks(request);
}
function navigateMarkdownPathLink(request) {
	markdownPreviewHostApi.navigatePathLink(request);
}
function setMarkdownFontSize(fontSize) {
	if (fontSize === previewSettings.effectiveFontSize) return;
	markdownPreviewHostApi.setFontSize({ fontSize });
}
function applyTheme(theme) {
	document.documentElement.dataset.theme = theme;
}
function applyPreviewSettings(settings) {
	const fontSize = settings.fontSize;
	if (typeof fontSize === "number" && Number.isFinite(fontSize) && fontSize > 0) document.documentElement.style.setProperty("--markdown-preview-font-size", `${fontSize}px`);
	else document.documentElement.style.removeProperty("--markdown-preview-font-size");
}
function defaultPreviewSettings() {
	const fontSize = 13;
	return {
		fontSize: null,
		effectiveFontSize: fontSize,
		defaultFontSize: fontSize,
		fontSizeOptions: [fontSize]
	};
}
function normalizePreviewSettings(settings) {
	const effectiveFontSize = positiveFiniteNumber(settings.effectiveFontSize) ?? previewSettings.effectiveFontSize;
	const defaultFontSize = positiveFiniteNumber(settings.defaultFontSize) ?? previewSettings.defaultFontSize;
	const fontSizeOptions = uniqueSortedNumbers([
		...Array.isArray(settings.fontSizeOptions) ? settings.fontSizeOptions : [],
		effectiveFontSize,
		defaultFontSize
	]);
	return {
		fontSize: settings.fontSize,
		effectiveFontSize,
		defaultFontSize,
		fontSizeOptions
	};
}
function positiveFiniteNumber(value) {
	return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : void 0;
}
function uniqueSortedNumbers(values) {
	return Array.from(new Set(values.filter((value) => Number.isFinite(value) && value > 0))).sort((left, right) => left - right);
}
function requiredElement(id) {
	const element = document.getElementById(id);
	if (!element) throw new Error(`Missing element #${id}`);
	return element;
}
//#endregion
