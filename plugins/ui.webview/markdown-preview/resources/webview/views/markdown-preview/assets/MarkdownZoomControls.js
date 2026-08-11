import { t as require_jsx_runtime } from "./react.js";
import { n as AllIcons, t as classNames } from "../view.js";
var import_jsx_runtime = require_jsx_runtime();
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
export { shouldHandleZoomEvent as i, MARKDOWN_ZOOM_SCALE_EXTENT as n, MarkdownZoomToolbar as r, MARKDOWN_ZOOM_BUTTON_FACTOR as t };
