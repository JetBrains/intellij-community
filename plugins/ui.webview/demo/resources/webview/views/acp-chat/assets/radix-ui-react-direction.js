import { o as __toESM } from "./rolldown-runtime.js";
import { V as require_jsx_runtime, it as require_react } from "./assistant-ui-core.js";
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
require_jsx_runtime();
var DirectionContext = import_react.createContext(void 0);
function useDirection(localDir) {
	const globalDir = import_react.useContext(DirectionContext);
	return localDir || globalDir || "ltr";
}
export { useDirection as t };
