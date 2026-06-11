const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["./assets/mermaid.js","./assets/rolldown-runtime.js","./assets/braintree-sanitize-url.js","./assets/iconify-utils.js","./assets/cytoscape-cose-bilkent.js","./assets/cose-base.js","./assets/cytoscape-fcose.js","./assets/cytoscape.js","./assets/d3-array.js","./assets/d3-axis.js","./assets/d3.js","./assets/d3-format.js","./assets/d3-hierarchy.js","./assets/d3-interpolate.js","./assets/d3-color.js","./assets/d3-sankey.js","./assets/d3-path.js","./assets/d3-scale-chromatic.js","./assets/d3-scale.js","./assets/d3-selection.js","./assets/d3-shape.js","./assets/dagre-d3-es.js","./assets/dayjs.js","./assets/dompurify.js","./assets/es-toolkit.js","./assets/khroma.js","./assets/marked.js"])))=>i.map(i=>d[i]);
import { n as require_react, t as require_jsx_runtime } from "./assets/react.js";
import { t as require_client } from "./assets/react-dom.js";
import { t as renderMathInElement } from "./assets/katex.js";
import { t as Markdown } from "./assets/react-markdown.js";
import { t as rehypeHighlight } from "./assets/rehype-highlight.js";
import { t as rehypeRaw } from "./assets/rehype-raw.js";
import { n as defaultSchema } from "./assets/hast-util-sanitize.js";
import { t as rehypeSanitize } from "./assets/rehype-sanitize.js";
import { t as rehypeSlug } from "./assets/rehype-slug.js";
import { t as remarkFrontmatter } from "./assets/remark-frontmatter.js";
import { t as remarkGfm } from "./assets/remark-gfm.js";
import { f as __vitePreload } from "./assets/mermaid.js";
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
//#region views/markdown-preview/src/MermaidBlock.tsx
var import_react = require_react();
var import_jsx_runtime = require_jsx_runtime();
var mermaidBlockId = 0;
var mermaidRenderId = 0;
var mermaidModule;
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
	if (state.kind === "rendered") return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "mermaidBlock",
		dangerouslySetInnerHTML: { __html: state.svg }
	});
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
function loadMermaid() {
	mermaidModule ||= __vitePreload(() => import("./assets/mermaid.js").then((n) => n.t).then((module) => module.default), __vite__mapDeps([0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26]), import.meta.url);
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
var headingSelector = "h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]";
var activeHeadingTopOffset = 80;
var markdownResourcePrefix = "./__markdown-preview-resource/";
var markdownIconPrefix = "./__markdown-preview-icon/";
var scheduledScrollFrame;
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
function MarkdownPreviewApp({ markdown, scrollLine, commands, changes, selection, theme, onOpenLink, onRunCommand }) {
	const commandLookup = createCommandLookup(commands);
	const components = {
		a({ href, children, ...props }) {
			function handleClick(event) {
				if (!href) return;
				event.preventDefault();
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
		pre({ node, className, children, ...props }) {
			const frontmatterLanguage = frontmatterLanguageFromPreNode(node);
			if (frontmatterLanguage) return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
				className: "frontmatterBlock",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
					className: "frontmatterHeader",
					children: frontmatterTitle(frontmatterLanguage)
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("pre", {
					className: classNames("frontmatterPre", className),
					...props,
					children
				})]
			});
			const sourcePosition = sourcePositionFromPreNode(node);
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
			const inlineCommand = sourcePosition ? findInlineCommand(commandLookup, sourcePosition) : void 0;
			if (inlineCommand) return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("code", {
				className,
				...props,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(RunCommandButton, {
					command: inlineCommand,
					variant: "inline",
					onRunCommand
				}), children]
			});
			return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("code", {
				className,
				...props,
				children
			});
		}
	};
	(0, import_react.useEffect)(() => {
		renderLatex();
	}, [markdown, theme]);
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
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Markdown, {
		remarkPlugins,
		rehypePlugins,
		components,
		urlTransform: (url) => url,
		children: markdown
	}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(FloatingTableOfContents, { markdown })] });
}
function FloatingTableOfContents({ markdown }) {
	const [entries, setEntries] = (0, import_react.useState)([]);
	const [expanded, setExpanded] = (0, import_react.useState)(false);
	const [activeId, setActiveId] = (0, import_react.useState)();
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
	if (entries.length < 2) return null;
	if (!expanded) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
		type: "button",
		className: "markdownTocRail",
		title: "Table of contents",
		"aria-label": "Show table of contents",
		"aria-expanded": "false",
		onClick: () => setExpanded(true),
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "markdownTocRailIcon",
			"aria-hidden": "true"
		})
	});
	function scrollToEntry(entry) {
		const target = document.getElementById(entry.id);
		if (!target) return;
		target.scrollIntoView({
			block: "start",
			behavior: "smooth"
		});
		setActiveId(entry.id);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("nav", {
		className: "markdownTocPanel",
		"aria-label": "Table of contents",
		onKeyDown: (event) => {
			if (event.key === "Escape") setExpanded(false);
		},
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "markdownTocHeader",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "markdownTocTitle",
				children: "Contents"
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: "markdownTocCollapseButton",
				title: "Collapse",
				"aria-label": "Collapse table of contents",
				"aria-expanded": "true",
				onClick: () => setExpanded(false),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "markdownTocCollapseIcon",
					"aria-hidden": "true"
				})
			})]
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("ol", {
			className: "markdownTocList",
			children: entries.map((entry) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", {
				className: "markdownTocItem",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "button",
					className: classNames("markdownTocLink", entry.id === activeId ? "is-active" : void 0),
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
function CodeFenceRunGutter({ sourcePosition, blockCommand, lineCommands, onRunCommand }) {
	if (!sourcePosition || !blockCommand && lineCommands.length === 0) return null;
	const contentStartLine = sourcePosition.startLine + 1;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "codeFenceRunGutter",
		"aria-hidden": false,
		children: [blockCommand && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RunCommandButton, {
			command: blockCommand,
			variant: "block",
			onRunCommand
		}), lineCommands.map((command) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RunCommandButton, {
			command,
			variant: "line",
			style: { top: `calc(${Math.max(0, command.startLine - contentStartLine)} * var(--markdown-code-line-height))` },
			onRunCommand
		}, command.id))]
	});
}
function RunCommandButton({ command, variant, style, onRunCommand }) {
	function handleClick(event) {
		event.preventDefault();
		event.stopPropagation();
		onRunCommand({
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
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
			src: runCommandIconSrc(variant),
			alt: ""
		})
	});
}
function runCommandIconSrc(variant) {
	return `${markdownIconPrefix}${variant === "block" ? "runBlock.png" : "run.png"}`;
}
function renderLatex() {
	const contentElement = document.getElementById("content");
	if (!contentElement) return;
	renderMathInElement(contentElement, {
		delimiters: latexDelimiters,
		ignoredClasses: ["katex"],
		throwOnError: false
	});
}
function codeToString(node) {
	if (typeof node === "string" || typeof node === "number") return String(node);
	if (Array.isArray(node)) return node.map(codeToString).join("");
	return "";
}
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
function scrollToSourceLine(line) {
	const contentElement = document.getElementById("content");
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
function decorateSourceBlocks(selection, changes) {
	const contentElement = document.getElementById("content");
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
function clearSourceDecorations(root = document.getElementById("content")) {
	if (!root) return;
	root.querySelectorAll(sourceDecorationClassSelector).forEach((element) => {
		element.classList.remove(...sourceDecorationClassNames);
	});
	root.querySelectorAll(`.${removedBlockPlaceholderClassName}`).forEach((placeholder) => {
		placeholder.remove();
	});
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
function sourcePositionFromPreNode(node) {
	const prePosition = sourcePositionFromHastNode(node);
	if (prePosition) return prePosition;
	const codeNode = node?.children?.find((child) => child.tagName === "code");
	return sourcePositionFromHastNode(codeNode);
}
function sourcePositionFromHastNode(node) {
	const value = node?.properties?.dataSourcepos;
	return typeof value === "string" ? parseSourcePosition(value) : void 0;
}
function positionKey(sourcePosition) {
	return `${sourcePosition.startLine}:${sourcePosition.startColumn}-${sourcePosition.endLine}:${sourcePosition.endColumn}`;
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
function remarkFrontmatterBlocks() {
	return (tree) => transformFrontmatterNodes(tree);
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
function frontmatterLanguageFromPreNode(node) {
	const language = (node?.children?.find((child) => child.tagName === "code"))?.properties?.dataFrontmatter;
	return typeof language === "string" ? language : void 0;
}
function frontmatterTitle(language) {
	return language === "toml" ? "Front matter (TOML)" : "Front matter (YAML)";
}
function classNames(...names) {
	return names.filter(Boolean).join(" ") || void 0;
}
function remarkSourcePositionAttributes() {
	return (tree) => addSourcePositionAttributes(tree);
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
//#region views/markdown-preview/src/main.tsx
var markdownPreviewPageApiId = apiId()("markdown.preview");
var markdownPreviewHostApi = webView.callable(apiId()("markdown.preview"));
var root = (0, import_client.createRoot)(requiredElement("content"));
var markdown = "";
var scrollLine = 0;
var commands = [];
var changes = [];
var selection;
var theme = webViewTheme.current;
webView.implement(markdownPreviewPageApiId, {
	contentChanged(params) {
		markdown = params.markdown;
		scrollLine = params.scrollLine;
		applyPreviewSettings(params.settings);
		commands = params.commands;
		changes = params.changes ?? [];
		renderPreview();
	},
	scrollToLine(params) {
		scrollLine = params.line;
		scrollMarkdownPreviewToLine(scrollLine);
	},
	selectionChanged(params) {
		selection = params.selection ?? void 0;
		renderPreview();
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
		commands,
		changes,
		selection,
		theme,
		onOpenLink: openMarkdownLink,
		onRunCommand: runMarkdownCommand
	}));
}
function openMarkdownLink(href) {
	markdownPreviewHostApi.openLink({ href });
}
function runMarkdownCommand(request) {
	markdownPreviewHostApi.runCommand(request);
}
function applyTheme(theme) {
	document.documentElement.dataset.theme = theme;
}
function applyPreviewSettings(settings) {
	const fontSize = Number.isFinite(settings.fontSize) && settings.fontSize > 0 ? settings.fontSize : 14;
	document.documentElement.style.setProperty("--default-font-size", `${fontSize}px`);
}
function requiredElement(id) {
	const element = document.getElementById(id);
	if (!element) throw new Error(`Missing element #${id}`);
	return element;
}
//#endregion
