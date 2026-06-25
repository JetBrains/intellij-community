import { i as __toESM } from "./rolldown-runtime.js";
import { Y as require_react } from "./assistant-ui-core.js";
//#region node_modules/@radix-ui/react-use-layout-effect/dist/index.mjs
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var useLayoutEffect2 = globalThis?.document ? import_react.useLayoutEffect : () => {};
//#endregion
//#region node_modules/@radix-ui/react-id/dist/index.mjs
var useReactId = import_react[" useId ".trim().toString()] || (() => void 0);
var count = 0;
function useId(deterministicId) {
	const [id, setId] = import_react.useState(useReactId());
	useLayoutEffect2(() => {
		if (!deterministicId) setId((reactId) => reactId ?? String(count++));
	}, [deterministicId]);
	return deterministicId || (id ? `radix-${id}` : "");
}
//#endregion
export { useLayoutEffect2 as n, useId as t };
