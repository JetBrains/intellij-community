import { i as __toESM } from "./rolldown-runtime.js";
import { M as require_jsx_runtime, Y as require_react } from "./assistant-ui-core.js";
import { g as require_react_dom, u as Primitive } from "./assistant-ui-react.js";
import { n as useLayoutEffect2 } from "./radix-ui-react-id.js";
//#region node_modules/@radix-ui/react-portal/dist/index.mjs
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var import_react_dom = /* @__PURE__ */ __toESM(require_react_dom(), 1);
var import_jsx_runtime = require_jsx_runtime();
var PORTAL_NAME = "Portal";
var Portal = import_react.forwardRef((props, forwardedRef) => {
	const { container: containerProp, ...portalProps } = props;
	const [mounted, setMounted] = import_react.useState(false);
	useLayoutEffect2(() => setMounted(true), []);
	const container = containerProp || mounted && globalThis?.document?.body;
	return container ? import_react_dom.createPortal(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
		...portalProps,
		ref: forwardedRef
	}), container) : null;
});
Portal.displayName = PORTAL_NAME;
//#endregion
export { Portal as t };
