import { n as require_react, t as require_jsx_runtime } from "./react.js";
import { t as classNames } from "../view.js";
import { h as select_default, n as identity, t as zoom_default } from "./d3.js";
import { i as shouldHandleZoomEvent, n as MARKDOWN_ZOOM_SCALE_EXTENT, r as MarkdownZoomToolbar, t as MARKDOWN_ZOOM_BUTTON_FACTOR } from "./MarkdownZoomControls.js";
//#region views/markdown-preview/src/MarkdownImageBlock.tsx
var import_react = require_react();
var import_jsx_runtime = require_jsx_runtime();
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
export { MarkdownImageBlock };
