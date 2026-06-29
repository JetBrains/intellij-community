import { o as __toESM } from "./rolldown-runtime.js";
import { M as require_jsx_runtime, Y as require_react } from "./assistant-ui-core.js";
import { m as Primitive } from "./assistant-ui-react.js";
//#region node_modules/@radix-ui/react-arrow/dist/index.mjs
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var import_jsx_runtime = require_jsx_runtime();
var NAME = "Arrow";
var Arrow = import_react.forwardRef((props, forwardedRef) => {
	const { children, width = 10, height = 5, ...arrowProps } = props;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.svg, {
		...arrowProps,
		ref: forwardedRef,
		width,
		height,
		viewBox: "0 0 30 10",
		preserveAspectRatio: "none",
		children: props.asChild ? children : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("polygon", { points: "0,0 30,0 15,10" })
	});
});
Arrow.displayName = NAME;
var Root = Arrow;
//#endregion
export { Root as t };
