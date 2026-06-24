(function() {
	//#region packages/api/src/webViewApi.ts
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
	//#region packages/api/src/bridge.ts
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
	createLazyWebViewBridge();
	//#endregion
	//#region packages/impl/src/browserZoomGuard.ts
	var browserZoomGuardInstalled = false;
	function installWebViewBrowserZoomGuard(transport) {
		if (transport !== "webview2") return;
		if (browserZoomGuardInstalled) return;
		browserZoomGuardInstalled = true;
		window.addEventListener("wheel", handleBrowserZoomWheel, { passive: false });
	}
	function handleBrowserZoomWheel(event) {
		if (event.ctrlKey && event.cancelable) event.preventDefault();
	}
	//#endregion
	//#region packages/impl/src/focusInterop.ts
	var FOCUS_API_NAMESPACE = "webview.focus";
	var FOCUS_BOUNDARY_ATTRIBUTE = "data-webview-focus-boundary";
	var webViewFocusPageApiId = { namespace: FOCUS_API_NAMESPACE };
	var webViewFocusHostApiId = { namespace: FOCUS_API_NAMESPACE };
	var installedBridges = /* @__PURE__ */ new WeakSet();
	function installWebViewFocusInterop(bridge) {
		if (installedBridges.has(bridge)) return;
		installedBridges.add(bridge);
		const hostApi = bridge.callable(webViewFocusHostApiId);
		bridge.implement(webViewFocusPageApiId, { enter(params) {
			enterDocumentFocus(params.direction, hostApi);
		} });
		document.addEventListener("pointerdown", (event) => handlePointerActivation(event, hostApi), true);
		document.addEventListener("keydown", (event) => handleFocusBoundaryKey(event, hostApi), true);
	}
	function enterDocumentFocus(direction, hostApi) {
		const tabbableElements = collectTabbableElements();
		if (tabbableElements.length === 0) {
			hostApi.exit({ direction });
			return;
		}
		(direction === "forward" ? tabbableElements[0] : tabbableElements[tabbableElements.length - 1]).focus();
	}
	function handlePointerActivation(event, hostApi) {
		const focusTarget = findPointerFocusTarget(event);
		hostApi.activated();
		if (focusTarget) schedulePointerFocus(focusTarget);
	}
	function findPointerFocusTarget(event) {
		const path = typeof event.composedPath === "function" ? event.composedPath() : [event.target];
		for (const item of path) {
			const element = asElement(item);
			if (!element) continue;
			if (isInsideNativeFocusBoundary(element)) return null;
			if (sequentialTabIndex(element) >= 0 && isRendered(element)) return element;
		}
		return null;
	}
	function schedulePointerFocus(target) {
		const focusTarget = () => {
			if (isRendered(target) && !isInsideNativeFocusBoundary(target) && activeElementDeep(document) !== target) target.focus();
		};
		queueMicrotask(focusTarget);
		setTimeout(focusTarget, 0);
	}
	function asElement(value) {
		if (typeof value !== "object" || value === null) return null;
		return typeof value.tagName === "string" ? value : null;
	}
	function handleFocusBoundaryKey(event, hostApi) {
		if (!isPlainTabEvent(event) || event.isComposing) return;
		const activeElement = activeElementDeep(document);
		if (activeElement && isInsideNativeFocusBoundary(activeElement)) return;
		const tabbableElements = collectTabbableElements();
		const direction = event.shiftKey ? "backward" : "forward";
		if (tabbableElements.length === 0) {
			event.preventDefault();
			hostApi.exit({ direction });
			return;
		}
		if (activeElement !== (direction === "forward" ? tabbableElements[tabbableElements.length - 1] : tabbableElements[0])) return;
		event.preventDefault();
		hostApi.exit({ direction });
	}
	function isPlainTabEvent(event) {
		return event.key === "Tab" && !event.altKey && !event.ctrlKey && !event.metaKey;
	}
	function collectTabbableElements(root = document.body || document.documentElement) {
		const candidates = [];
		let documentOrder = 0;
		function visitChildren(parent) {
			for (const child of Array.from(parent.children)) visitElement(child);
		}
		function visitElement(element) {
			const tabIndex = sequentialTabIndex(element);
			if (tabIndex >= 0 && isRendered(element)) candidates.push({
				element,
				tabIndex,
				documentOrder
			});
			documentOrder++;
			const shadowRoot = element.shadowRoot;
			if (shadowRoot) visitChildren(shadowRoot);
			visitChildren(element);
		}
		visitChildren(root);
		return candidates.sort(compareTabbableCandidates).map((candidate) => candidate.element);
	}
	function compareTabbableCandidates(left, right) {
		const leftPositive = left.tabIndex > 0;
		const rightPositive = right.tabIndex > 0;
		if (leftPositive && rightPositive && left.tabIndex !== right.tabIndex) return left.tabIndex - right.tabIndex;
		if (leftPositive !== rightPositive) return leftPositive ? -1 : 1;
		return left.documentOrder - right.documentOrder;
	}
	function sequentialTabIndex(element) {
		if (isDisabledControl(element) || hasHiddenOrInertAncestor(element)) return -1;
		const declaredTabIndex = parseDeclaredTabIndex(element);
		if (declaredTabIndex !== void 0) return declaredTabIndex;
		return isNaturallyFocusable(element) ? 0 : -1;
	}
	function parseDeclaredTabIndex(element) {
		const value = element.getAttribute("tabindex");
		if (value == null) return;
		const parsed = Number.parseInt(value, 10);
		return Number.isFinite(parsed) ? parsed : void 0;
	}
	function isNaturallyFocusable(element) {
		const tagName = element.tagName.toLowerCase();
		if (tagName === "input") return (element.getAttribute("type") || "").toLowerCase() !== "hidden";
		if (tagName === "button" || tagName === "select" || tagName === "textarea" || tagName === "iframe" || tagName === "object" || tagName === "embed" || tagName === "summary") return true;
		if ((tagName === "a" || tagName === "area") && element.hasAttribute("href")) return true;
		if ((tagName === "audio" || tagName === "video") && element.hasAttribute("controls")) return true;
		return isContentEditable(element);
	}
	function isContentEditable(element) {
		const value = element.getAttribute("contenteditable");
		return value != null && value.toLowerCase() !== "false";
	}
	function isDisabledControl(element) {
		const tagName = element.tagName.toLowerCase();
		return (tagName === "button" || tagName === "input" || tagName === "select" || tagName === "textarea") && element.hasAttribute("disabled");
	}
	function hasHiddenOrInertAncestor(element) {
		for (let current = element; current; current = parentElementOrShadowHost(current)) if (current.hasAttribute("hidden") || current.hasAttribute("inert")) return true;
		return false;
	}
	function isRendered(element) {
		for (let current = element; current; current = parentElementOrShadowHost(current)) {
			const style = window.getComputedStyle(current);
			if (style.display === "none" || style.visibility === "hidden" || style.visibility === "collapse") return false;
		}
		const htmlElement = element;
		return htmlElement.offsetWidth > 0 || htmlElement.offsetHeight > 0 || htmlElement.getClientRects().length > 0;
	}
	function parentElementOrShadowHost(element) {
		if (element.parentElement) return element.parentElement;
		const root = element.getRootNode();
		return typeof ShadowRoot === "function" && root instanceof ShadowRoot ? root.host : null;
	}
	function activeElementDeep(root) {
		let activeElement = root.activeElement;
		while (activeElement?.shadowRoot?.activeElement) activeElement = activeElement.shadowRoot.activeElement;
		return activeElement;
	}
	function isInsideNativeFocusBoundary(element) {
		for (let current = element; current; current = parentElementOrShadowHost(current)) if (current.getAttribute(FOCUS_BOUNDARY_ATTRIBUTE) === "native") return true;
		return false;
	}
	//#endregion
	//#region packages/styles/src/theming/ij-themes.css?raw
	var ij_themes_default = "/* Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license. */\n\n@font-face {\n  font-family: \"Inter\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/inter/Inter-Regular.otf\") format(\"opentype\");\n  font-weight: 400;\n  font-style: normal;\n}\n\n@font-face {\n  font-family: \"Inter\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/inter/Inter-Italic.otf\") format(\"opentype\");\n  font-weight: 400;\n  font-style: italic;\n}\n\n@font-face {\n  font-family: \"Inter\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/inter/Inter-SemiBold.otf\") format(\"opentype\");\n  font-weight: 600;\n  font-style: normal;\n}\n\n@font-face {\n  font-family: \"Inter\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/inter/Inter-SemiBoldItalic.otf\") format(\"opentype\");\n  font-weight: 600;\n  font-style: italic;\n}\n\n@font-face {\n  font-family: \"JetBrains Mono\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/jetbrains-mono/JetBrainsMono-Regular.ttf\") format(\"truetype\");\n  font-weight: 400;\n  font-style: normal;\n}\n\n@font-face {\n  font-family: \"JetBrains Mono\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/jetbrains-mono/JetBrainsMono-Italic.ttf\") format(\"truetype\");\n  font-weight: 400;\n  font-style: italic;\n}\n\n@font-face {\n  font-family: \"JetBrains Mono\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/jetbrains-mono/JetBrainsMono-Bold.ttf\") format(\"truetype\");\n  font-weight: 700;\n  font-style: normal;\n}\n\n@font-face {\n  font-family: \"JetBrains Mono\";\n  /*noinspection CssUnknownTarget*/\n  src: url(\"/__webview/fonts/jetbrains-mono/JetBrainsMono-BoldItalic.ttf\") format(\"truetype\");\n  font-weight: 700;\n  font-style: italic;\n}\n\n:root {\n  --ij-font: \"Inter\", \"Segoe UI\", -apple-system, BlinkMacSystemFont, \"Helvetica Neue\", sans-serif;\n  --ij-font-size: 13px;\n  --ij-font-size-h0: calc(var(--ij-font-size) + 12px);\n  --ij-font-size-h1: calc(var(--ij-font-size) + 9px);\n  --ij-font-size-h2: calc(var(--ij-font-size) + 5px);\n  --ij-font-size-h3: calc(var(--ij-font-size) + 3px);\n  --ij-font-size-h4: calc(var(--ij-font-size) + 1px);\n  --ij-font-size-regular: var(--ij-font-size);\n  --ij-font-size-medium: calc(var(--ij-font-size) - 1px);\n  --ij-font-size-small: max(calc(var(--ij-font-size) - 2px), 11px);\n  --ij-font-size-mini: max(calc(var(--ij-font-size) - 4px), 9px);\n  --ij-line-height-default: 16px;\n  --ij-line-height-compact: calc(var(--ij-line-height-default) - 2px);\n  --ij-line-height-paragraph: calc(var(--ij-line-height-default) + 2px);\n  --ij-line-height-heading: calc(var(--ij-line-height-default) + 4px);\n  --ij-font-weight-regular: 400;\n  --ij-font-weight-medium: 500;\n  --ij-font-weight-semibold: 600;\n  --ij-control-height: max(28px, calc(var(--ij-line-height-default) + 12px));\n  --ij-control-height-compact: max(24px, calc(var(--ij-line-height-default) + 8px));\n  --ij-editor-font: \"JetBrains Mono\", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;\n  --ij-editor-font-size: 13px;\n  --ij-editor-line-height: 22px;\n  --ij-editor-font-variant-ligatures: normal;\n  --ij-editor-font-feature-settings: normal;\n  --ij-radius-control: 4px;\n  --ij-radius-compact: 3px;\n  --ij-radius-panel: 6px;\n  --ij-focus-ring: 0 0 0 2px rgba(56, 113, 225, 0.32);\n}\n\n:root,\n:root[data-theme=\"dark\"] {\n  --ij-bg-window: #191A1C;\n  --ij-bg-panel: #212326;\n  --ij-bg-panel-alt: #26282C;\n  --ij-bg-input: #191A1C;\n  --ij-bg-control-raised: #26282C;\n  --ij-bg-hover: #FFFFFF17;\n  --ij-bg-pressed: #FFFFFF29;\n  --ij-bg-selected: #2A4371;\n  --ij-bg-selected-muted: #233558;\n  --ij-border: #26282C;\n  --ij-border-inline: #33353B;\n  --ij-border-strong: #40434A;\n  --ij-control-border-raised: #5F6269;\n  --ij-text-primary: #D1D3D9;\n  --ij-text-secondary: #73767C;\n  --ij-text-muted: #9FA2A8;\n  --ij-text-disabled: #4C4F56;\n  --ij-text-on-accent: #FFFFFF;\n  --ij-accent: #3871E1;\n  --ij-accent-hover: #538AF9;\n  --ij-accent-text: #71A1FE;\n  --ij-accent-soft: #233558;\n  --ij-accent-soft-border: #2E4D89;\n  --ij-success: #6DB083;\n  --ij-success-soft: #203B2A;\n  --ij-success-border: #29583C;\n  --ij-warning: #D59637;\n  --ij-warning-soft: #44321D;\n  --ij-warning-border: #694820;\n  --ij-danger: #F57E84;\n  --ij-danger-soft: #56272B;\n  --ij-danger-border: #80383E;\n  --ij-neutral-soft: #B5B7BD33;\n  --ij-neutral-border: #FFFFFF21;\n  --ij-neutral-text: #B5B7BD;\n  --ij-badge-blue-bg: #2E4D89CC;\n  --ij-badge-blue-text: #D0DFFE;\n  --ij-badge-green-bg: #29583CCC;\n  --ij-badge-green-text: #CDE5D1;\n  --ij-badge-purple-bg: #574092CC;\n  --ij-badge-purple-text: #E2DBFC;\n  --ij-badge-gray-bg: #B5B7BD33;\n  --ij-badge-gray-text: #B5B7BD;\n  --ij-progress-track: #40434A;\n  --ij-shadow: 0 4px 14px rgba(0, 0, 0, 0.28);\n  --ij-popup-shadow: 0 8px 24px #00000059;\n  --ij-scrollbar-thumb: #80808059;\n  --ij-scrollbar-thumb-hover: #8080808C;\n}\n\n:root[data-theme=\"light\"] {\n  --ij-bg-window: #FFFFFF;\n  --ij-bg-panel: #F7F8F9;\n  --ij-bg-panel-alt: #FFFFFF;\n  --ij-bg-input: #FFFFFF;\n  --ij-bg-control-raised: #FFFFFF;\n  --ij-bg-hover: #00000012;\n  --ij-bg-pressed: #00000020;\n  --ij-bg-selected: #D0DFFE;\n  --ij-bg-selected-muted: #E3EBFE;\n  --ij-border: #E9EAEE;\n  --ij-border-inline: #E9EAEE;\n  --ij-border-strong: #D1D3D9;\n  --ij-control-border-raised: #B5B7BD;\n  --ij-text-primary: #000000;\n  --ij-text-secondary: #73767C;\n  --ij-text-muted: #5F6269;\n  --ij-text-disabled: #9FA2A8;\n  --ij-text-on-accent: #FFFFFF;\n  --ij-accent: #3871E1;\n  --ij-accent-hover: #2F5EB9;\n  --ij-accent-text: #2F5EB9;\n  --ij-accent-soft: #3871E129;\n  --ij-accent-soft-border: #BDD3FF;\n  --ij-success: #338555;\n  --ij-success-soft: #33855529;\n  --ij-success-border: #BBDBC2;\n  --ij-warning: #A56906;\n  --ij-warning-soft: #FFF6E9;\n  --ij-warning-border: #F4CD9A;\n  --ij-danger: #C54E58;\n  --ij-danger-soft: #FFF6F5;\n  --ij-danger-border: #FFC4C5;\n  --ij-neutral-soft: #73767C1F;\n  --ij-neutral-border: #00000020;\n  --ij-neutral-text: #73767C;\n  --ij-badge-blue-bg: #3871E129;\n  --ij-badge-blue-text: #2F5EB9;\n  --ij-badge-green-bg: #33855529;\n  --ij-badge-green-text: #2A6E47;\n  --ij-badge-purple-bg: #8060DB29;\n  --ij-badge-purple-text: #6C4EBB;\n  --ij-badge-gray-bg: #73767C1F;\n  --ij-badge-gray-text: #73767C;\n  --ij-progress-track: #DDDFE4;\n  --ij-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);\n  --ij-popup-shadow: 0 8px 24px #00000026;\n  --ij-scrollbar-thumb: #80808059;\n  --ij-scrollbar-thumb-hover: #8080808C;\n}\n\n.ij-webview-root,\n.ij-webview-root *,\n.ij-webview-root *::before,\n.ij-webview-root *::after {\n  box-sizing: border-box;\n}\n\nbody.ij-webview-root {\n  margin: 0;\n  min-height: 100vh;\n  background: var(--ij-bg-window);\n  color: var(--ij-text-primary);\n  font-family: var(--ij-font), sans-serif;\n  font-size: var(--ij-font-size);\n  line-height: var(--ij-line-height-default);\n  -webkit-font-smoothing: antialiased;\n  -moz-osx-font-smoothing: grayscale;\n}\n\n.ij-webview-editor-font {\n  font-family: var(--ij-editor-font), monospace;\n  font-size: var(--ij-editor-font-size);\n  line-height: var(--ij-editor-line-height);\n  font-variant-ligatures: var(--ij-editor-font-variant-ligatures);\n  font-feature-settings: var(--ij-editor-font-feature-settings);\n}\n\n.ij-webview-root::-webkit-scrollbar,\n.ij-webview-root *::-webkit-scrollbar {\n  width: 10px;\n  height: 10px;\n  background-color: transparent;\n}\n\n.ij-webview-root::-webkit-scrollbar-track,\n.ij-webview-root *::-webkit-scrollbar-track,\n.ij-webview-root::-webkit-scrollbar-corner,\n.ij-webview-root *::-webkit-scrollbar-corner {\n  background-color: transparent;\n}\n\n.ij-webview-root::-webkit-scrollbar-thumb,\n.ij-webview-root *::-webkit-scrollbar-thumb {\n  background-color: var(--ij-scrollbar-thumb);\n  background-clip: padding-box;\n  border: 2px solid transparent;\n  border-radius: 6px;\n  min-height: 24px;\n}\n\n.ij-webview-root::-webkit-scrollbar-thumb:hover,\n.ij-webview-root *::-webkit-scrollbar-thumb:hover {\n  background-color: var(--ij-scrollbar-thumb-hover);\n  background-clip: padding-box;\n}\n";
	//#endregion
	//#region packages/impl/src/theme.ts
	var IJ_THEME_STYLES_ID = "__wvi-ij-themes";
	var JB_THEME_TOKENS_ID = "jb-webview-theme-tokens";
	var THEME_QUERY_PARAMETER = "__webviewTheme";
	var THEME_API_NAMESPACE = "webview.theme";
	var webViewThemeHostEventsId = { namespace: THEME_API_NAMESPACE };
	var webViewThemePageEventsId = { namespace: THEME_API_NAMESPACE };
	var jbThemeTokenStyles = `
:root {
  --jb-font-family: var(--ij-font, "Inter", "Segoe UI", -apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif);
  --jb-font-size: var(--ij-font-size, 13px);
  --jb-font-size-h0: var(--ij-font-size-h0, calc(var(--ij-font-size, 13px) + 12px));
  --jb-font-size-h1: var(--ij-font-size-h1, calc(var(--ij-font-size, 13px) + 9px));
  --jb-font-size-h2: var(--ij-font-size-h2, calc(var(--ij-font-size, 13px) + 5px));
  --jb-font-size-h3: var(--ij-font-size-h3, calc(var(--ij-font-size, 13px) + 3px));
  --jb-font-size-h4: var(--ij-font-size-h4, calc(var(--ij-font-size, 13px) + 1px));
  --jb-font-size-regular: var(--ij-font-size-regular, var(--ij-font-size, 13px));
  --jb-font-size-medium: var(--ij-font-size-medium, calc(var(--ij-font-size, 13px) - 1px));
  --jb-font-size-small: var(--ij-font-size-small, max(calc(var(--ij-font-size, 13px) - 2px), 11px));
  --jb-font-size-mini: var(--ij-font-size-mini, max(calc(var(--ij-font-size, 13px) - 4px), 9px));
  --jb-line-height: var(--ij-line-height-default, 16px);
  --jb-line-height-compact: var(--ij-line-height-compact, calc(var(--ij-line-height-default, 16px) - 2px));
  --jb-line-height-paragraph: var(--ij-line-height-paragraph, calc(var(--ij-line-height-default, 16px) + 2px));
  --jb-line-height-heading: var(--ij-line-height-heading, calc(var(--ij-line-height-default, 16px) + 4px));
  --jb-font-weight-regular: var(--ij-font-weight-regular, 400);
  --jb-font-weight-medium: var(--ij-font-weight-medium, 500);
  --jb-control-height: var(--ij-control-height, max(28px, calc(var(--ij-line-height-default, 16px) + 12px)));
  --jb-control-height-compact: var(--ij-control-height-compact, max(24px, calc(var(--ij-line-height-default, 16px) + 8px)));
  --jb-control-radius: var(--ij-radius-control, 4px);
  --jb-control-padding-x: 8px;
  --jb-control-gap: 6px;
  --jb-space-xs: 4px;
  --jb-space-sm: 8px;
  --jb-space-md: 12px;
  --jb-space-lg: 16px;
  --jb-bg-window: var(--ij-bg-window, #ffffff);
  --jb-bg-panel: var(--ij-bg-panel, #f7f8f9);
  --jb-bg-control: var(--ij-bg-control-raised, #ffffff);
  --jb-bg-input: var(--ij-bg-input, #ffffff);
  --jb-bg-hover: var(--ij-bg-hover, #00000012);
  --jb-bg-pressed: var(--ij-bg-pressed, #00000020);
  --jb-bg-selected: var(--ij-bg-selected, #d0dffe);
  --jb-bg-selected-muted: var(--ij-bg-selected-muted, #e3ebfe);
  --jb-border-color: var(--ij-control-border-raised, #b5b7bd);
  --jb-border-color-muted: var(--ij-border-inline, #e9eaee);
  --jb-border-color-strong: var(--ij-border-strong, #d1d3d9);
  --jb-text-color: var(--ij-text-primary, #000000);
  --jb-text-muted: var(--ij-text-muted, #5f6269);
  --jb-text-secondary: var(--ij-text-secondary, #73767c);
  --jb-text-disabled: var(--ij-text-disabled, #9fa2a8);
  --jb-text-on-accent: var(--ij-text-on-accent, #ffffff);
  --jb-accent-color: var(--ij-accent, #3871e1);
  --jb-accent-hover-color: var(--ij-accent-hover, #2f5eb9);
  --jb-accent-text-color: var(--ij-accent-text, #2f5eb9);
  --jb-accent-soft-bg: var(--ij-accent-soft, #3871e129);
  --jb-danger-color: var(--ij-danger, #c54e58);
  --jb-danger-bg: var(--ij-danger-soft, #fff6f5);
  --jb-danger-border-color: var(--ij-danger-border, #ffc4c5);
  --jb-warning-color: var(--ij-warning, #a56906);
  --jb-warning-bg: var(--ij-warning-soft, #fff6e9);
  --jb-warning-border-color: var(--ij-warning-border, #f4cd9a);
  --jb-focus-ring: var(--ij-focus-ring, 0 0 0 2px rgba(56, 113, 225, 0.32));
  --jb-popup-shadow: var(--ij-popup-shadow, 0 8px 24px #00000026);
}
`;
	/**
	* Installs IntelliJ theming for the current WebView page: injects shared IJ theme styles,
	* exposes the page theme API, applies the initial theme, and keeps the page reactive to
	* theme changes reported by the host over the WebView bridge.
	*/
	function installIJTheming(bridge) {
		ensureIJThemeStylesInstalled();
		ensureJBThemeTokensInstalled();
		if (window.__WVI_THEME__) return;
		const theme = createWebViewTheme();
		window.__WVI_THEME__ = theme.api;
		theme.install(bridge);
	}
	function createWebViewTheme() {
		let currentTheme = readInitialTheme();
		const listeners = [];
		let hostEventsRegistration;
		applyThemeAttribute(currentTheme);
		const api = {
			get current() {
				return currentTheme;
			},
			onChanged(handler) {
				if (typeof handler !== "function") throw new Error("WebView theme listener must be a function");
				listeners.push(handler);
				return { close() {
					const index = listeners.indexOf(handler);
					if (index >= 0) listeners.splice(index, 1);
				} };
			}
		};
		function applyHostTheme(params) {
			const payload = params && typeof params === "object" ? params : void 0;
			applyThemeFonts(payload?.fonts);
			const theme = normalizeTheme(payload?.theme);
			if (theme && theme !== currentTheme) {
				currentTheme = theme;
				applyThemeAttribute(theme);
				for (const listener of listeners.slice()) try {
					listener(theme);
				} catch (err) {
					console.error("[__WVI__] theme listener threw:", err);
				}
			}
		}
		function install(bridge) {
			hostEventsRegistration?.close();
			hostEventsRegistration = bridge.implement(webViewThemeHostEventsId, { themeChanged(params) {
				applyHostTheme(params);
			} });
			bridge.callable(webViewThemePageEventsId).themeRequest();
		}
		return {
			api,
			install
		};
	}
	function ensureIJThemeStylesInstalled() {
		if (document.getElementById(IJ_THEME_STYLES_ID)) return;
		const style = document.createElement("style");
		style.id = IJ_THEME_STYLES_ID;
		style.textContent = ij_themes_default;
		const target = document.head || document.documentElement;
		target.insertBefore(style, target.firstChild);
	}
	function ensureJBThemeTokensInstalled() {
		const existing = document.getElementById(JB_THEME_TOKENS_ID);
		if (existing) {
			if (existing.textContent !== jbThemeTokenStyles) existing.textContent = jbThemeTokenStyles;
			return;
		}
		const style = document.createElement("style");
		style.id = JB_THEME_TOKENS_ID;
		style.textContent = jbThemeTokenStyles;
		const target = document.head || document.documentElement;
		target.insertBefore(style, target.firstChild);
	}
	function readInitialTheme() {
		try {
			const themes = new URLSearchParams(window.location.search).getAll(THEME_QUERY_PARAMETER);
			return normalizeTheme(themes[themes.length - 1]) ?? "dark";
		} catch (_) {
			return "dark";
		}
	}
	function normalizeTheme(theme) {
		return theme === "light" || theme === "dark" ? theme : void 0;
	}
	function applyThemeAttribute(theme) {
		document.documentElement.setAttribute("data-theme", theme);
		document.documentElement.style.colorScheme = theme;
		ensureJBThemeTokensInstalled();
	}
	function applyThemeFonts(fonts) {
		if (!fonts || typeof fonts !== "object") return;
		const payload = fonts;
		applyUiFont(payload.ui);
		applyEditorFont(payload.editor);
	}
	function applyUiFont(font) {
		const payload = normalizeFontInfo(font);
		if (!payload) return;
		const style = document.documentElement.style;
		style.setProperty("--ij-font", toCssFontFamily(payload.families));
		style.setProperty("--ij-font-size", `${payload.size}px`);
		style.setProperty("--ij-font-size-regular", `${payload.sizes?.regular ?? payload.size}px`);
		applyUiFontSizes(style, payload.sizes);
		if (payload.lineHeight !== void 0) style.setProperty("--ij-line-height-default", `${payload.lineHeight}px`);
	}
	function applyUiFontSizes(style, sizes) {
		if (!sizes) return;
		for (const [role, variable] of uiFontSizeVariables) {
			const size = sizes[role];
			if (typeof size === "number" && Number.isFinite(size) && size > 0) style.setProperty(variable, `${size}px`);
		}
	}
	function applyEditorFont(font) {
		const payload = normalizeEditorFontInfo(font);
		if (!payload) return;
		const style = document.documentElement.style;
		style.setProperty("--ij-editor-font", toCssFontFamily(payload.families));
		style.setProperty("--ij-editor-font-size", `${payload.size}px`);
		if (payload.lineHeight !== void 0) style.setProperty("--ij-editor-line-height", `${payload.lineHeight}`);
		style.setProperty("--ij-editor-font-variant-ligatures", payload.ligatures ? "normal" : "none");
		style.setProperty("--ij-editor-font-feature-settings", toCssFontFeatureSettings(payload.fontFeatureSettings));
	}
	function normalizeFontInfo(font) {
		if (!font || typeof font !== "object") return;
		const payload = font;
		const families = Array.isArray(payload.families) ? payload.families.filter((family) => typeof family === "string" && family.trim().length > 0) : [];
		if (families.length === 0 || typeof payload.size !== "number" || !Number.isFinite(payload.size) || payload.size <= 0) return;
		const lineHeight = typeof payload.lineHeight === "number" && Number.isFinite(payload.lineHeight) && payload.lineHeight > 0 ? payload.lineHeight : void 0;
		const sizes = normalizeUiFontSizes(payload.sizes);
		return {
			families,
			size: payload.size,
			lineHeight,
			...sizes ? { sizes } : {}
		};
	}
	function normalizeUiFontSizes(sizes) {
		if (!sizes || typeof sizes !== "object") return;
		const payload = sizes;
		const result = {};
		for (const [role] of uiFontSizeVariables) {
			const size = payload[role];
			if (typeof size === "number" && Number.isFinite(size) && size > 0) result[role] = size;
		}
		return Object.keys(result).length > 0 ? result : void 0;
	}
	function normalizeEditorFontInfo(font) {
		const payload = normalizeFontInfo(font);
		if (!payload || !font || typeof font !== "object") return;
		const editorPayload = font;
		const fontFeatureSettings = Array.isArray(editorPayload.fontFeatureSettings) ? editorPayload.fontFeatureSettings.filter((feature) => typeof feature === "string") : [];
		return {
			...payload,
			ligatures: editorPayload.ligatures !== false,
			fontFeatureSettings
		};
	}
	function toCssFontFamily(families) {
		return families.map((family) => cssString(family.trim())).join(", ");
	}
	function toCssFontFeatureSettings(features) {
		const cssFeatures = features.filter((feature) => /^[A-Za-z0-9]{4}$/.test(feature)).map((feature) => `${cssString(feature)} 1`);
		return cssFeatures.length === 0 ? "normal" : cssFeatures.join(", ");
	}
	function cssString(value) {
		return `"${value.replace(/\\/g, "\\\\").replace(/"/g, "\\\"")}"`;
	}
	var uiFontSizeVariables = [
		["h0", "--ij-font-size-h0"],
		["h1", "--ij-font-size-h1"],
		["h2", "--ij-font-size-h2"],
		["h3", "--ij-font-size-h3"],
		["h4", "--ij-font-size-h4"],
		["regular", "--ij-font-size-regular"],
		["medium", "--ij-font-size-medium"],
		["small", "--ij-font-size-small"],
		["mini", "--ij-font-size-mini"]
	];
	//#endregion
	//#region packages/impl/src/platformFeatures.ts
	function installWebViewPlatformFeatures(bridge) {
		installWebViewBrowserZoomGuard(bridge.transport());
		installIJTheming(bridge);
		installWebViewFocusInterop(bridge);
	}
	//#endregion
	//#region packages/impl/src/platformFeaturesEntry.ts
	installWebViewPlatformFeatures(requireWebViewBridge());
	//#endregion
})();
