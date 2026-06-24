import { i as __toESM, n as __exportAll, r as __reExport, t as __commonJSMin } from "./rolldown-runtime.js";
import { a as customAlphabet, i as createAssistantStreamController, n as ToolResponse, o as AssistantMetaTransformStream, r as parsePartialJsonObject, t as toolResultStream } from "./assistant-stream.js";
//#region node_modules/react/cjs/react.production.min.js
/**
* @license React
* react.production.min.js
*
* Copyright (c) Facebook, Inc. and its affiliates.
*
* This source code is licensed under the MIT license found in the
* LICENSE file in the root directory of this source tree.
*/
var require_react_production_min = /* @__PURE__ */ __commonJSMin(((exports) => {
	var l = Symbol.for("react.element"), n = Symbol.for("react.portal"), p = Symbol.for("react.fragment"), q = Symbol.for("react.strict_mode"), r = Symbol.for("react.profiler"), t = Symbol.for("react.provider"), u = Symbol.for("react.context"), v = Symbol.for("react.forward_ref"), w = Symbol.for("react.suspense"), x = Symbol.for("react.memo"), y = Symbol.for("react.lazy"), z = Symbol.iterator;
	function A(a) {
		if (null === a || "object" !== typeof a) return null;
		a = z && a[z] || a["@@iterator"];
		return "function" === typeof a ? a : null;
	}
	var B = {
		isMounted: function() {
			return !1;
		},
		enqueueForceUpdate: function() {},
		enqueueReplaceState: function() {},
		enqueueSetState: function() {}
	}, C = Object.assign, D = {};
	function E(a, b, e) {
		this.props = a;
		this.context = b;
		this.refs = D;
		this.updater = e || B;
	}
	E.prototype.isReactComponent = {};
	E.prototype.setState = function(a, b) {
		if ("object" !== typeof a && "function" !== typeof a && null != a) throw Error("setState(...): takes an object of state variables to update or a function which returns an object of state variables.");
		this.updater.enqueueSetState(this, a, b, "setState");
	};
	E.prototype.forceUpdate = function(a) {
		this.updater.enqueueForceUpdate(this, a, "forceUpdate");
	};
	function F() {}
	F.prototype = E.prototype;
	function G(a, b, e) {
		this.props = a;
		this.context = b;
		this.refs = D;
		this.updater = e || B;
	}
	var H = G.prototype = new F();
	H.constructor = G;
	C(H, E.prototype);
	H.isPureReactComponent = !0;
	var I = Array.isArray, J = Object.prototype.hasOwnProperty, K = { current: null }, L = {
		key: !0,
		ref: !0,
		__self: !0,
		__source: !0
	};
	function M(a, b, e) {
		var d, c = {}, k = null, h = null;
		if (null != b) for (d in void 0 !== b.ref && (h = b.ref), void 0 !== b.key && (k = "" + b.key), b) J.call(b, d) && !L.hasOwnProperty(d) && (c[d] = b[d]);
		var g = arguments.length - 2;
		if (1 === g) c.children = e;
		else if (1 < g) {
			for (var f = Array(g), m = 0; m < g; m++) f[m] = arguments[m + 2];
			c.children = f;
		}
		if (a && a.defaultProps) for (d in g = a.defaultProps, g) void 0 === c[d] && (c[d] = g[d]);
		return {
			$$typeof: l,
			type: a,
			key: k,
			ref: h,
			props: c,
			_owner: K.current
		};
	}
	function N(a, b) {
		return {
			$$typeof: l,
			type: a.type,
			key: b,
			ref: a.ref,
			props: a.props,
			_owner: a._owner
		};
	}
	function O(a) {
		return "object" === typeof a && null !== a && a.$$typeof === l;
	}
	function escape(a) {
		var b = {
			"=": "=0",
			":": "=2"
		};
		return "$" + a.replace(/[=:]/g, function(a) {
			return b[a];
		});
	}
	var P = /\/+/g;
	function Q(a, b) {
		return "object" === typeof a && null !== a && null != a.key ? escape("" + a.key) : b.toString(36);
	}
	function R(a, b, e, d, c) {
		var k = typeof a;
		if ("undefined" === k || "boolean" === k) a = null;
		var h = !1;
		if (null === a) h = !0;
		else switch (k) {
			case "string":
			case "number":
				h = !0;
				break;
			case "object": switch (a.$$typeof) {
				case l:
				case n: h = !0;
			}
		}
		if (h) return h = a, c = c(h), a = "" === d ? "." + Q(h, 0) : d, I(c) ? (e = "", null != a && (e = a.replace(P, "$&/") + "/"), R(c, b, e, "", function(a) {
			return a;
		})) : null != c && (O(c) && (c = N(c, e + (!c.key || h && h.key === c.key ? "" : ("" + c.key).replace(P, "$&/") + "/") + a)), b.push(c)), 1;
		h = 0;
		d = "" === d ? "." : d + ":";
		if (I(a)) for (var g = 0; g < a.length; g++) {
			k = a[g];
			var f = d + Q(k, g);
			h += R(k, b, e, f, c);
		}
		else if (f = A(a), "function" === typeof f) for (a = f.call(a), g = 0; !(k = a.next()).done;) k = k.value, f = d + Q(k, g++), h += R(k, b, e, f, c);
		else if ("object" === k) throw b = String(a), Error("Objects are not valid as a React child (found: " + ("[object Object]" === b ? "object with keys {" + Object.keys(a).join(", ") + "}" : b) + "). If you meant to render a collection of children, use an array instead.");
		return h;
	}
	function S(a, b, e) {
		if (null == a) return a;
		var d = [], c = 0;
		R(a, d, "", "", function(a) {
			return b.call(e, a, c++);
		});
		return d;
	}
	function T(a) {
		if (-1 === a._status) {
			var b = a._result;
			b = b();
			b.then(function(b) {
				if (0 === a._status || -1 === a._status) a._status = 1, a._result = b;
			}, function(b) {
				if (0 === a._status || -1 === a._status) a._status = 2, a._result = b;
			});
			-1 === a._status && (a._status = 0, a._result = b);
		}
		if (1 === a._status) return a._result.default;
		throw a._result;
	}
	var U = { current: null }, V = { transition: null }, W = {
		ReactCurrentDispatcher: U,
		ReactCurrentBatchConfig: V,
		ReactCurrentOwner: K
	};
	function X() {
		throw Error("act(...) is not supported in production builds of React.");
	}
	exports.Children = {
		map: S,
		forEach: function(a, b, e) {
			S(a, function() {
				b.apply(this, arguments);
			}, e);
		},
		count: function(a) {
			var b = 0;
			S(a, function() {
				b++;
			});
			return b;
		},
		toArray: function(a) {
			return S(a, function(a) {
				return a;
			}) || [];
		},
		only: function(a) {
			if (!O(a)) throw Error("React.Children.only expected to receive a single React element child.");
			return a;
		}
	};
	exports.Component = E;
	exports.Fragment = p;
	exports.Profiler = r;
	exports.PureComponent = G;
	exports.StrictMode = q;
	exports.Suspense = w;
	exports.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED = W;
	exports.act = X;
	exports.cloneElement = function(a, b, e) {
		if (null === a || void 0 === a) throw Error("React.cloneElement(...): The argument must be a React element, but you passed " + a + ".");
		var d = C({}, a.props), c = a.key, k = a.ref, h = a._owner;
		if (null != b) {
			void 0 !== b.ref && (k = b.ref, h = K.current);
			void 0 !== b.key && (c = "" + b.key);
			if (a.type && a.type.defaultProps) var g = a.type.defaultProps;
			for (f in b) J.call(b, f) && !L.hasOwnProperty(f) && (d[f] = void 0 === b[f] && void 0 !== g ? g[f] : b[f]);
		}
		var f = arguments.length - 2;
		if (1 === f) d.children = e;
		else if (1 < f) {
			g = Array(f);
			for (var m = 0; m < f; m++) g[m] = arguments[m + 2];
			d.children = g;
		}
		return {
			$$typeof: l,
			type: a.type,
			key: c,
			ref: k,
			props: d,
			_owner: h
		};
	};
	exports.createContext = function(a) {
		a = {
			$$typeof: u,
			_currentValue: a,
			_currentValue2: a,
			_threadCount: 0,
			Provider: null,
			Consumer: null,
			_defaultValue: null,
			_globalName: null
		};
		a.Provider = {
			$$typeof: t,
			_context: a
		};
		return a.Consumer = a;
	};
	exports.createElement = M;
	exports.createFactory = function(a) {
		var b = M.bind(null, a);
		b.type = a;
		return b;
	};
	exports.createRef = function() {
		return { current: null };
	};
	exports.forwardRef = function(a) {
		return {
			$$typeof: v,
			render: a
		};
	};
	exports.isValidElement = O;
	exports.lazy = function(a) {
		return {
			$$typeof: y,
			_payload: {
				_status: -1,
				_result: a
			},
			_init: T
		};
	};
	exports.memo = function(a, b) {
		return {
			$$typeof: x,
			type: a,
			compare: void 0 === b ? null : b
		};
	};
	exports.startTransition = function(a) {
		var b = V.transition;
		V.transition = {};
		try {
			a();
		} finally {
			V.transition = b;
		}
	};
	exports.unstable_act = X;
	exports.useCallback = function(a, b) {
		return U.current.useCallback(a, b);
	};
	exports.useContext = function(a) {
		return U.current.useContext(a);
	};
	exports.useDebugValue = function() {};
	exports.useDeferredValue = function(a) {
		return U.current.useDeferredValue(a);
	};
	exports.useEffect = function(a, b) {
		return U.current.useEffect(a, b);
	};
	exports.useId = function() {
		return U.current.useId();
	};
	exports.useImperativeHandle = function(a, b, e) {
		return U.current.useImperativeHandle(a, b, e);
	};
	exports.useInsertionEffect = function(a, b) {
		return U.current.useInsertionEffect(a, b);
	};
	exports.useLayoutEffect = function(a, b) {
		return U.current.useLayoutEffect(a, b);
	};
	exports.useMemo = function(a, b) {
		return U.current.useMemo(a, b);
	};
	exports.useReducer = function(a, b, e) {
		return U.current.useReducer(a, b, e);
	};
	exports.useRef = function(a) {
		return U.current.useRef(a);
	};
	exports.useState = function(a) {
		return U.current.useState(a);
	};
	exports.useSyncExternalStore = function(a, b, e) {
		return U.current.useSyncExternalStore(a, b, e);
	};
	exports.useTransition = function() {
		return U.current.useTransition();
	};
	exports.version = "18.3.1";
}));
//#endregion
//#region node_modules/react/index.js
var require_react = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	module.exports = require_react_production_min();
}));
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/helpers/execution-context.js
var currentResourceFiber = null;
function withResourceFiber(fiber, fn) {
	fiber.currentIndex = 0;
	fiber.wipContextDeps = null;
	fiber.wipCommitCallbacks = [];
	const previousContext = currentResourceFiber;
	currentResourceFiber = fiber;
	try {
		fn();
		fiber.isFirstRender = false;
		if (fiber.cells.length !== fiber.currentIndex) throw new Error(`Rendered ${fiber.currentIndex} hooks but expected ${fiber.cells.length}. Hooks must be called in the exact same order in every render.`);
	} finally {
		currentResourceFiber = previousContext;
	}
}
function getCurrentResourceFiber() {
	if (!currentResourceFiber) throw new Error("No resource fiber available");
	return currentResourceFiber;
}
function peekResourceFiber() {
	return currentResourceFiber;
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/context.js
var defaultContextValue = Symbol("tap.Context.defaultValue");
var asTap = (context) => context;
var currentContext = /* @__PURE__ */ new Map();
var changedContexts = /* @__PURE__ */ new Set();
var cloneCurrentTapContext = () => new Map(currentContext);
var withTapContextRoot = (context, fn) => {
	const previousContext = currentContext;
	currentContext = context;
	try {
		return fn();
	} finally {
		currentContext = previousContext;
	}
};
var attachDefaultValueToContext = (context, defaultValue) => {
	context[defaultContextValue] = defaultValue;
};
var isTapContext = (context) => typeof context === "object" && context !== null && defaultContextValue in context;
var isReactContext = (context) => typeof context === "object" && context !== null && "$$typeof" in context && context.$$typeof === Symbol.for("react.context");
var isReadableTapContext = (context) => isTapContext(context) || isReactContext(context);
var assertTapContext = (context) => {
	if (isTapContext(context)) return;
	if (isReactContext(context)) {
		attachDefaultValueToContext(context, context._currentValue ?? context._currentValue2);
		return;
	}
	throw new Error("A tap resource's `use()` only accepts a tap context.");
};
var useContextProvider = (context, value, fn) => {
	if (typeof context !== "object" || context === null) throw new Error("useContextProvider only accepts a React context.");
	assertTapContext(context);
	const key = context;
	const currentFiber = getCurrentResourceFiber();
	const committedValueRef = useRef(void 0);
	const didChange = committedValueRef.current === void 0 || !Object.is(committedValueRef.current.value, value);
	useEffect(() => {
		committedValueRef.current = { value };
	}, [value]);
	const previousValue = currentContext.get(key);
	const hadPreviousValue = previousValue !== void 0 || currentContext.has(key);
	currentContext.set(key, {
		value,
		source: currentFiber
	});
	try {
		return withChangedContext(key, didChange, fn);
	} finally {
		if (hadPreviousValue) currentContext.set(key, previousValue);
		else currentContext.delete(key);
	}
};
var withChangedContext = (context, didChange, fn) => {
	const restoreChangedContext = changedContexts.has(context);
	if (didChange) changedContexts.add(context);
	else changedContexts.delete(context);
	try {
		return fn();
	} finally {
		if (restoreChangedContext) changedContexts.add(context);
		else changedContexts.delete(context);
	}
};
var useTapContext = (context) => {
	assertTapContext(context);
	const key = context;
	const contextValue = getCurrentContextValue(key, context);
	const currentFiber = getCurrentResourceFiber();
	(currentFiber.wipContextDeps ??= /* @__PURE__ */ new Map()).set(key, contextValue.source);
	return contextValue.value;
};
var getCurrentContextValue = (key, context) => currentContext.get(key) ?? {
	value: asTap(context)[defaultContextValue],
	source: null
};
var mergeContextDeps = (targetFiber, sourceFiber, target, source) => {
	if (!source) return target;
	let next = target;
	for (const [context, providerFiber] of source) {
		if (providerFiber === sourceFiber || providerFiber === targetFiber) continue;
		(next ??= /* @__PURE__ */ new Map()).set(context, providerFiber);
	}
	return next;
};
var bubbleContextDeps = (fiber, contextDeps = fiber.wipContextDeps) => {
	const currentFiber = peekResourceFiber();
	if (!currentFiber || !contextDeps) return;
	currentFiber.wipContextDeps = mergeContextDeps(currentFiber, fiber, currentFiber.wipContextDeps, contextDeps);
};
var hasChangedContexts = () => changedContexts.size > 0;
var hasContextDepsChanged = (fiber) => {
	if (!fiber.contextDeps || !hasChangedContexts()) return false;
	for (const context of changedContexts.keys()) if (fiber.contextDeps.has(context)) return true;
	return false;
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/helpers/commit.js
var COMMIT_PRIORITIES = [
	0,
	1,
	2,
	3
];
function commitAllCallbacks(callbacks) {
	const errors = [];
	for (const priority of COMMIT_PRIORITIES) {
		const lane = callbacks[priority];
		if (lane === void 0) continue;
		for (let i = 0; i < lane.length; i++) try {
			lane[i]();
		} catch (error) {
			errors.push(error);
		}
	}
	if (errors.length > 0) if (errors.length === 1) throw errors[0];
	else {
		for (const error of errors) console.error(error);
		throw new AggregateError(errors, "Errors during commit");
	}
}
function cleanupAllEffects(executionContext) {
	const errors = [];
	for (const cell of executionContext.cells) if (cell?.type === "effect") {
		cell.deps = null;
		if (cell.cleanup) try {
			cell.cleanup?.();
		} catch (e) {
			errors.push(e);
		} finally {
			cell.cleanup = void 0;
		}
	}
	if (errors.length > 0) if (errors.length === 1) throw errors[0];
	else {
		for (const error of errors) console.error(error);
		throw new AggregateError(errors, "Errors during cleanup");
	}
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/helpers/env.js
var isDevelopment = typeof process !== "undefined" && false;
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/helpers/root.js
var createResourceFiberRoot = (dispatchUpdate) => {
	return {
		version: 0,
		committedVersion: 0,
		context: cloneCurrentTapContext(),
		dispatchUpdate,
		changelog: [],
		rollbackCallbacks: []
	};
};
var commitRoot = (root) => {
	root.committedVersion = root.version;
	root.changelog.length = 0;
	root.rollbackCallbacks.length = 0;
};
var setRootVersion = (root, version) => {
	const rollback = root.version > version;
	root.version = version;
	if (rollback) {
		for (let i = 0; i < root.rollbackCallbacks.length; i++) root.rollbackCallbacks[i]();
		root.rollbackCallbacks.length = 0;
		if (version === root.committedVersion) root.changelog.length = 0;
		else {
			if (root.committedVersion > version) throw new Error("Version is less than committed version");
			while (root.committedVersion + root.changelog.length > version) root.changelog.pop();
			for (let i = 0; i < root.changelog.length; i++) applyChangelogRecord(root.changelog[i]);
			commitRoot(root);
		}
	}
};
var applyChangelogRecord = (record) => {
	markReducerDirty(record.fiber, record.cell);
	if (!record.queued) {
		record.queued = true;
		(record.cell.queue ??= []).push(record);
	}
};
var addCommit = (fiber, priority, callback) => {
	const callbacks = fiber.wipCommitCallbacks;
	(callbacks[priority] ??= []).push(callback);
};
var addRollback = (root, callback) => {
	root.rollbackCallbacks.push(callback);
};
var markReducerDirty = (fiber, cell) => {
	if (cell.isDirty) return;
	cell.isDirty = true;
	fiber.markDirty?.();
	addRollback(fiber.root, () => {
		if (cell.queue !== null) {
			for (const record of cell.queue) record.queued = false;
			cell.queue = null;
		}
		cell.workInProgress = cell.current;
		cell.isDirty = false;
	});
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/utils/hookErrors.js
var throwRenderedMoreHooks = () => {
	throw new Error("Rendered more hooks than during the previous render. Hooks must be called in the exact same order in every render.");
};
var throwHookOrderChanged = () => {
	throw new Error("Hook order changed between renders");
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useReducer.js
var dispatchOnFiber = (fiber, record, eagerReducer) => {
	if (fiber.isNeverMounted) throw new Error("Resource updated before mount");
	let evaluated = false;
	let hasWork = true;
	fiber.root.dispatchUpdate(() => {
		if (evaluated) return hasWork;
		evaluated = true;
		if (eagerReducer && fiber.root.changelog.length === 0 && !record.cell.isDirty && !record.hasEagerState) {
			record.eagerState = eagerReducer(record.cell.workInProgress, record.action);
			record.hasEagerState = true;
			hasWork = !Object.is(record.cell.current, record.eagerState);
		}
		return hasWork;
	}, () => {
		evaluated = true;
		hasWork = true;
		applyChangelogRecord(record);
		fiber.root.changelog.push(record);
		return true;
	});
};
var createReducerCell = (fiber, reducer, initialArg, initFn, eagerBailout) => {
	const initialState = initFn ? initFn(initialArg) : initialArg;
	if (isDevelopment && fiber.devStrictMode && initFn) initFn(initialArg);
	const cell = {
		type: "reducer",
		workInProgress: initialState,
		current: initialState,
		isDirty: false,
		queue: null,
		renderQueue: null,
		reducer,
		dispatch: (action) => {
			const currentFiber = peekResourceFiber();
			if (currentFiber !== null) {
				if (currentFiber !== fiber) throw new Error("Cannot update a resource while rendering a different resource.");
				(fiber.renderPendingCells ??= /* @__PURE__ */ new Set()).add(cell);
				(cell.renderQueue ??= []).push(action);
			} else dispatchOnFiber(fiber, {
				fiber,
				cell,
				action,
				hasEagerState: false,
				eagerState: void 0,
				queued: false
			}, eagerBailout ? reducer : void 0);
		}
	};
	return cell;
};
function useReducerImpl(reducer, initialArg, initFn, eagerBailout) {
	const fiber = getCurrentResourceFiber();
	const index = fiber.currentIndex++;
	const existing = fiber.cells[index];
	const cell = (() => {
		if (existing !== void 0) return existing.type === "reducer" ? existing : throwHookOrderChanged();
		if (!fiber.isFirstRender && index >= fiber.cells.length) throwRenderedMoreHooks();
		const cell = createReducerCell(fiber, reducer, initialArg, initFn, eagerBailout);
		fiber.cells[index] = cell;
		return cell;
	})();
	const queue = cell.queue;
	if (queue !== null) {
		const sameReducer = reducer === cell.reducer;
		for (let i = 0; i < queue.length; i++) {
			const item = queue[i];
			if (!item.hasEagerState || !sameReducer) {
				item.eagerState = reducer(cell.workInProgress, item.action);
				item.hasEagerState = true;
				if (isDevelopment && fiber.devStrictMode) item.eagerState = reducer(cell.workInProgress, item.action);
			} else if (isDevelopment && fiber.devStrictMode) reducer(cell.workInProgress, item.action);
			item.queued = false;
			cell.workInProgress = item.eagerState;
		}
		cell.queue = null;
	}
	cell.reducer = reducer;
	if (cell.renderQueue !== null) {
		let derived = cell.workInProgress;
		for (const action of cell.renderQueue) derived = reducer(derived, action);
		cell.renderQueue = null;
		fiber.renderPendingCells?.delete(cell);
		if (!Object.is(derived, cell.workInProgress)) {
			markReducerDirty(fiber, cell);
			cell.workInProgress = derived;
		}
	}
	if (cell.isDirty) addCommit(fiber, 0, () => {
		cell.current = cell.workInProgress;
		cell.isDirty = false;
	});
	return [cell.workInProgress, cell.dispatch];
}
function useReducer$1(reducer, initialArg, init) {
	return useReducerImpl(reducer, initialArg, init, false);
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useState.js
var stateReducer = (state, action) => typeof action === "function" ? action(state) : action;
var stateInit = (initial) => initial === void 0 ? void 0 : typeof initial === "function" ? initial() : initial;
function useState$1(initial) {
	return useReducerImpl(stateReducer, initial, stateInit, true);
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/utils/depsShallowEqual.js
var depsShallowEqual = (a, b) => {
	if (isDevelopment && a.length !== b.length) console.error(`The final argument passed to a hook changed size between renders. The order and size of this array must remain constant.

Previous: [${a.join(", ")}]\nIncoming: [${b.join(", ")}]`);
	for (let i = 0; i < a.length && i < b.length; i++) if (!Object.is(a[i], b[i])) return false;
	return true;
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useMemo.js
var addMemoCommit = (fiber, cell) => {
	addCommit(fiber, 0, () => {
		cell.current = cell.wip;
		cell.currentDeps = cell.wipDeps;
		cell.isDirty = false;
	});
};
var useMemo$1 = (fn, deps) => {
	const fiber = getCurrentResourceFiber();
	const index = fiber.currentIndex++;
	let cell = fiber.cells[index];
	if (cell === void 0) {
		if (!fiber.isFirstRender && index >= fiber.cells.length) throwRenderedMoreHooks();
		const value = fn();
		if (isDevelopment && fiber.devStrictMode) fn();
		cell = {
			type: "memo",
			current: value,
			currentDeps: deps,
			wip: value,
			wipDeps: deps,
			isDirty: false
		};
		fiber.cells[index] = cell;
		return value;
	}
	if (cell.type !== "memo") throwHookOrderChanged();
	const memoCell = cell;
	if (depsShallowEqual(memoCell.wipDeps, deps)) {
		if (memoCell.isDirty) addMemoCommit(fiber, memoCell);
		return memoCell.wip;
	}
	const value = fn();
	if (isDevelopment && fiber.devStrictMode) fn();
	memoCell.wip = value;
	memoCell.wipDeps = deps;
	if (!memoCell.isDirty) {
		memoCell.isDirty = true;
		addRollback(fiber.root, () => {
			memoCell.wip = memoCell.current;
			memoCell.wipDeps = memoCell.currentDeps;
			memoCell.isDirty = false;
		});
	}
	addMemoCommit(fiber, memoCell);
	return value;
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useRef.js
function useRef$2(initialValue) {
	return useMemo$1(() => ({ current: initialValue }), []);
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useCallback.js
var useCallback$2 = (fn, deps) => {
	return useMemo$1(() => fn, deps);
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useEffect.js
var newEffect = () => ({
	type: "effect",
	cleanup: void 0,
	deps: null
});
function useEffect$1(effect, deps) {
	const fiber = getCurrentResourceFiber();
	const index = fiber.currentIndex++;
	const existing = fiber.cells[index];
	const cell = existing === void 0 ? newEffect() : existing.type === "effect" ? existing : throwHookOrderChanged();
	if (existing === void 0) {
		if (!fiber.isFirstRender && index >= fiber.cells.length) throwRenderedMoreHooks();
		fiber.cells[index] = cell;
	}
	if (deps && cell.deps && depsShallowEqual(cell.deps, deps)) return;
	if (cell.deps !== null && !!deps !== !!cell.deps) throw new Error("useEffect called with and without dependencies across re-renders");
	addCommit(fiber, 2, () => {
		try {
			cell.cleanup?.();
		} finally {
			cell.cleanup = void 0;
		}
	});
	addCommit(fiber, 3, () => {
		try {
			const cleanup = effect();
			if (cleanup !== void 0 && typeof cleanup !== "function") throw new Error(`An effect function must either return a cleanup function or nothing. Received: ${typeof cleanup}`);
			cell.cleanup = cleanup;
		} finally {
			cell.deps = deps;
		}
	});
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useEffectEvent.js
/**
* Creates a stable function reference that always calls the most recent version of the callback.
* Similar to React's useEffectEvent hook.
*
* @param callback - The callback function to wrap
* @returns A stable function reference that always calls the latest callback
*
* @example
* ```typescript
* const handleClick = useEffectEvent((value: string) => {
*   console.log(value);
* });
* // handleClick reference is stable, but always calls the latest version
* ```
*/
function useEffectEvent$1(callback) {
	const fiber = getCurrentResourceFiber();
	const callbackRef = useRef$2(callback);
	if (callbackRef.current !== callback) addCommit(fiber, 1, () => {
		callbackRef.current = callback;
	});
	return useCallback$2(((...args) => {
		if (isDevelopment && peekResourceFiber()) throw new Error("useEffectEvent cannot be called during render");
		return callbackRef.current(...args);
	}), []);
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/use.js
/**
* Reads a context from inside a resource render, the tap equivalent of React's
* `use(Context)` / `useContext(Context)`. Accepts React contexts.
*/
var use$1 = (usable) => {
	if (!isReadableTapContext(usable)) throw new Error("A tap resource's `use()` only accepts a tap context.");
	return useTapContext(usable);
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useSyncExternalStore.js
var useSyncExternalStore$1 = (subscribe, getSnapshot, getServerSnapshot = getSnapshot) => {
	const isFirstRender = useRef$2(true);
	const value = isFirstRender.current ? getServerSnapshot() : getSnapshot();
	isFirstRender.current = false;
	const [, forceUpdate] = useState$1(0);
	const onStoreChange = useEffectEvent$1(() => {
		if (!Object.is(value, getSnapshot())) forceUpdate((c) => c + 1);
	});
	useEffect$1(() => {
		onStoreChange();
		return subscribe(onStoreChange);
	}, [subscribe]);
	return value;
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-hooks/useDebugValue.js
var useDebugValue$1 = (_value, _format) => {};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-shim/useReactEffectEvent.js
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var ReactRuntime$3 = import_react.default;
function useReactEffectEventShim(callback) {
	const callbackRef = (0, import_react.useRef)(callback);
	(0, import_react.useInsertionEffect)(() => {
		callbackRef.current = callback;
	});
	return (0, import_react.useCallback)(((...args) => callbackRef.current(...args)), []);
}
var useReactEffectEvent = ReactRuntime$3.useEffectEvent ?? useReactEffectEventShim;
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-shim/index.js
var react_shim_exports = /* @__PURE__ */ __exportAll({
	createContext: () => createContext,
	default: () => import_react.default,
	use: () => use,
	useCallback: () => useCallback,
	useContext: () => useContext,
	useDebugValue: () => useDebugValue,
	useEffect: () => useEffect,
	useEffectEvent: () => useEffectEvent,
	useLayoutEffect: () => useLayoutEffect,
	useMemo: () => useMemo,
	useReducer: () => useReducer,
	useRef: () => useRef,
	useState: () => useState,
	useSyncExternalStore: () => useSyncExternalStore
});
__reExport(react_shim_exports, /* @__PURE__ */ __toESM(require_react(), 1));
var inTap = () => peekResourceFiber() !== null;
var ReactRuntime$2 = import_react.default;
var useState = (initialState) => inTap() ? useState$1(initialState) : ReactRuntime$2.useState(initialState);
var useReducer = (reducer, initialArg, init) => inTap() ? useReducer$1(reducer, initialArg, init) : ReactRuntime$2.useReducer(reducer, initialArg, init);
var useRef = (initialValue) => inTap() ? useRef$2(initialValue) : ReactRuntime$2.useRef(initialValue);
var useMemo = (factory, deps) => inTap() ? useMemo$1(factory, deps) : ReactRuntime$2.useMemo(factory, deps);
var useCallback = (callback, deps) => inTap() ? useCallback$2(callback, deps) : ReactRuntime$2.useCallback(callback, deps);
var useEffect = (effect, deps) => inTap() ? useEffect$1(effect, deps) : ReactRuntime$2.useEffect(effect, deps);
var useLayoutEffect = (effect, deps) => inTap() ? useEffect$1(effect, deps) : ReactRuntime$2.useLayoutEffect(effect, deps);
var useEffectEvent = (callback) => inTap() ? useEffectEvent$1(callback) : useReactEffectEvent(callback);
var useSyncExternalStore = (subscribe, getSnapshot, getServerSnapshot) => inTap() ? useSyncExternalStore$1(subscribe, getSnapshot, getServerSnapshot) : ReactRuntime$2.useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
var useDebugValue = (value, format) => inTap() ? void 0 : ReactRuntime$2.useDebugValue(value, format);
var createContext = (defaultValue) => {
	const context = ReactRuntime$2.createContext(defaultValue);
	attachDefaultValueToContext(context, defaultValue);
	return context;
};
var use = (usable) => inTap() && isReadableTapContext(usable) ? use$1(usable) : ReactRuntime$2.use(usable);
var useContext = (context) => inTap() && isReadableTapContext(context) ? use$1(context) : ReactRuntime$2.useContext(context);
//#endregion
//#region node_modules/@assistant-ui/tap/dist/react-shim/compiler-runtime.js
var ReactRuntime$1 = import_react.default;
var MEMO_CACHE_SENTINEL = Symbol.for("react.memo_cache_sentinel");
var createMemoCache = (size) => new Array(size).fill(MEMO_CACHE_SENTINEL);
var cPolyfill = (size) => ReactRuntime$1.useMemo(() => {
	const $ = createMemoCache(size);
	$[MEMO_CACHE_SENTINEL] = true;
	return $;
}, []);
var c = (size) => {
	const fiber = peekResourceFiber();
	if (fiber === null) return (ReactRuntime$1.__COMPILER_RUNTIME?.c ?? cPolyfill)(size);
	const memoCache = fiber.memoCache;
	let data = memoCache.workInProgress;
	if (data === null) {
		const current = memoCache.current;
		data = current === null ? [] : current.map((array) => array.slice());
		memoCache.workInProgress = data;
	}
	const index = memoCache.index++;
	let cache = data[index];
	if (cache === void 0) {
		cache = createMemoCache(size);
		data[index] = cache;
	} else if (isDevelopment && cache.length !== size) console.error(`Expected a constant size argument for each invocation of c(). The previous cache was allocated with size ${cache.length} but size ${size} was requested.`);
	return cache;
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/resource.js
function resource(hook) {
	return (...args) => ({
		hook,
		args
	});
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/withKey.js
function withKey(key, element, deps) {
	return deps ? {
		...element,
		key,
		deps
	} : {
		...element,
		key
	};
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/scheduler.js
var MAX_FLUSH_LIMIT = 50;
var flushState = {
	schedulers: /* @__PURE__ */ new Set([]),
	isScheduled: false
};
var UpdateScheduler = class {
	_task;
	_isDirty = false;
	constructor(_task) {
		this._task = _task;
	}
	get isDirty() {
		return this._isDirty;
	}
	markDirty() {
		this._isDirty = true;
		flushState.schedulers.add(this);
		scheduleFlush();
	}
	runTask() {
		this._isDirty = false;
		this._task();
	}
};
var scheduleFlush = () => {
	if (flushState.isScheduled) return;
	flushState.isScheduled = true;
	scheduleMacrotask();
};
var flushScheduled = () => {
	try {
		const errors = [];
		let flushDepth = 0;
		for (const scheduler of flushState.schedulers) {
			flushState.schedulers.delete(scheduler);
			if (!scheduler.isDirty) continue;
			flushDepth++;
			if (flushDepth > MAX_FLUSH_LIMIT) throw new Error("Maximum update depth exceeded. This can happen when a resource repeatedly calls setState inside useEffect.");
			try {
				scheduler.runTask();
			} catch (error) {
				errors.push(error);
			}
		}
		if (errors.length > 0) if (errors.length === 1) throw errors[0];
		else {
			for (const error of errors) console.error(error);
			throw new AggregateError(errors, "Errors occurred during flushSync");
		}
	} finally {
		flushState.schedulers.clear();
		flushState.isScheduled = false;
	}
};
var scheduleMacrotask = (() => {
	if (typeof MessageChannel !== "undefined") {
		const channel = new MessageChannel();
		channel.port1.onmessage = flushScheduled;
		return () => channel.port2.postMessage(null);
	}
	return () => setTimeout(flushScheduled, 0);
})();
var flushTapSync = (callback) => {
	const prev = flushState;
	flushState = {
		schedulers: /* @__PURE__ */ new Set([]),
		isScheduled: true
	};
	try {
		const value = callback();
		flushScheduled();
		return value;
	} finally {
		flushState = prev;
	}
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/react-dispatcher.js
var tapDispatcher = {
	useState: useState$1,
	useReducer: useReducer$1,
	useRef: useRef$2,
	useMemo: useMemo$1,
	useCallback: useCallback$2,
	useEffect: useEffect$1,
	useLayoutEffect: useEffect$1,
	useInsertionEffect: useEffect$1,
	useEffectEvent: useEffectEvent$1,
	useContext: use$1,
	use: use$1,
	useSyncExternalStore: useSyncExternalStore$1,
	useDebugValue: useDebugValue$1
};
var ReactRuntime = import_react.default;
var internals = ReactRuntime.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE ?? ReactRuntime.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED;
var slot = internals == null ? null : "H" in internals ? {
	get current() {
		return internals.H;
	},
	set current(d) {
		internals.H = d;
	}
} : "ReactCurrentDispatcher" in internals ? {
	get current() {
		return internals.ReactCurrentDispatcher.current;
	},
	set current(d) {
		internals.ReactCurrentDispatcher.current = d;
	}
} : null;
/**
* Runs a resource body with tap's React dispatcher installed, so real React
* hooks called inside it (`import { useState } from "react"`) route to tap, then
* restores the previous dispatcher. If React's internal dispatcher slot can't be
* found (an unsupported React version), the body runs unchanged and `react`
* hooks inside it keep throwing React's "invalid hook call".
*/
function withReactDispatcher(render) {
	if (!slot) return render();
	const previous = slot.current;
	slot.current = tapDispatcher;
	try {
		return render();
	} finally {
		slot.current = previous;
	}
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/core/ResourceFiber.js
function createResourceFiber(hook, root, markDirty = void 0, strictMode) {
	return {
		hook,
		root,
		markDirty,
		devStrictMode: strictMode,
		cells: [],
		contextDeps: null,
		wipContextDeps: null,
		commitCallbacks: null,
		wipCommitCallbacks: null,
		memoCache: {
			current: null,
			workInProgress: null,
			index: 0
		},
		renderPendingCells: null,
		currentIndex: 0,
		isFirstRender: true,
		isMounted: false,
		isNeverMounted: true
	};
}
function unmountResourceFiber(fiber) {
	if (!fiber.isMounted) throw new Error("Tried to unmount a fiber that is already unmounted");
	fiber.isMounted = false;
	cleanupAllEffects(fiber);
}
function renderResourceFiber(fiber, args) {
	fiber.memoCache.workInProgress = null;
	if (fiber.renderPendingCells !== null) {
		for (const cell of fiber.renderPendingCells) cell.renderQueue = null;
		fiber.renderPendingCells.clear();
	}
	let passes = 0;
	let value;
	do {
		if (++passes > 25) throw new Error("Too many re-renders. tap limits the number of renders to prevent an infinite loop.");
		fiber.memoCache.index = 0;
		withResourceFiber(fiber, () => {
			value = withReactDispatcher(() => fiber.hook(...args));
		});
	} while ((fiber.renderPendingCells?.size ?? 0) > 0);
	bubbleContextDeps(fiber);
	return value;
}
function commitResourceFiber(fiber) {
	const commitCallbacks = fiber.wipCommitCallbacks ?? fiber.commitCallbacks ?? [];
	fiber.wipCommitCallbacks = null;
	fiber.commitCallbacks = commitCallbacks;
	fiber.isMounted = true;
	fiber.contextDeps = fiber.wipContextDeps;
	commitRoot(fiber.root);
	if (fiber.memoCache.workInProgress !== null) {
		fiber.memoCache.current = fiber.memoCache.workInProgress;
		fiber.memoCache.workInProgress = null;
	}
	if (isDevelopment && fiber.isNeverMounted && fiber.devStrictMode === "root") {
		fiber.isNeverMounted = false;
		commitAllCallbacks(commitCallbacks);
		cleanupAllEffects(fiber);
	}
	fiber.isNeverMounted = false;
	commitAllCallbacks(commitCallbacks);
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/utils/useDevStrictMode.js
var getTapDevMode = () => {
	const currentResourceFiber = getCurrentResourceFiber();
	if (currentResourceFiber.devStrictMode) return currentResourceFiber.isFirstRender ? "child" : "root";
	return null;
};
var child = () => "child";
var notDevMode = () => null;
var useDevStrictModeReact = () => {
	if (!isDevelopment) return notDevMode;
	const count = useRef(0);
	useState(() => count.current++);
	if (count.current !== 2) return notDevMode;
	return child;
};
var useDevStrictMode = () => {
	return peekResourceFiber() ? getTapDevMode : useDevStrictModeReact();
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/useTapRoot.js
var useHostRoot = (render) => render();
var useTapRoot = (render) => {
	const scheduler = useMemo(() => new UpdateScheduler(() => handleUpdate()), []);
	const queue = useMemo(() => [], []);
	const getDevStrictMode = useDevStrictMode();
	const fiber = useMemo(() => {
		const root = createResourceFiberRoot((evaluate, apply) => {
			if (!scheduler.isDirty) {
				if (!evaluate()) return;
				apply();
			}
			setRootVersion(root, root.committedVersion + root.changelog.length);
			queue.push(apply);
			scheduler.markDirty();
		});
		return createResourceFiber(useHostRoot, root, void 0, getDevStrictMode());
	}, [
		queue,
		scheduler,
		getDevStrictMode
	]);
	const context = cloneCurrentTapContext();
	const drainedCount = fiber.root.version - fiber.root.committedVersion;
	const render2 = withTapContextRoot(context, () => {
		return renderResourceFiber(fiber, [render]);
	});
	const isMountedRef = useRef(false);
	const committedArgsRef = useRef([render]);
	const valueRef = useRef(render2);
	const subscribers = useMemo(() => /* @__PURE__ */ new Set(), []);
	const publish = (output) => {
		if (scheduler.isDirty || valueRef.current === output) return;
		valueRef.current = output;
		subscribers.forEach((listener) => listener());
	};
	const handleUpdate = useEffectEvent(() => {
		setRootVersion(fiber.root, fiber.root.committedVersion);
		queue.forEach((callback) => {
			if (isDevelopment && fiber.devStrictMode) callback();
			callback();
		});
		setRootVersion(fiber.root, fiber.root.committedVersion + fiber.root.changelog.length);
		if (isDevelopment && fiber.devStrictMode) withTapContextRoot(fiber.root.context, () => {
			return renderResourceFiber(fiber, committedArgsRef.current);
		});
		const render = withTapContextRoot(fiber.root.context, () => {
			return renderResourceFiber(fiber, committedArgsRef.current);
		});
		if (scheduler.isDirty) throw new Error("Scheduler is dirty, this should never happen");
		commitRoot(fiber.root);
		queue.length = 0;
		if (isMountedRef.current) commitResourceFiber(fiber);
		publish(render);
	});
	useEffect(() => {
		isMountedRef.current = true;
		return () => {
			isMountedRef.current = false;
			unmountResourceFiber(fiber);
		};
	}, [fiber]);
	useEffect(() => {
		committedArgsRef.current = [render];
		commitRoot(fiber.root);
		queue.splice(0, drainedCount);
		fiber.root.context = context;
		commitResourceFiber(fiber);
		publish(render2);
	});
	return useMemo(() => ({
		getValue: () => valueRef.current,
		subscribe: (listener) => {
			subscribers.add(listener);
			return () => subscribers.delete(listener);
		}
	}), [subscribers]);
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/utils/useResourceFiberHostUtils.js
var useResourceFiberHostUtilsTap = () => {
	const versionRef = useRef(0);
	const version = versionRef.current;
	const parent = getCurrentResourceFiber();
	return {
		version,
		markDirty: useMemo(() => () => {
			versionRef.current++;
			parent?.markDirty?.();
		}, [parent]),
		root: parent.root
	};
};
var useResourceFiberHostUtilsReact = () => {
	const root = useMemo(() => {
		return createResourceFiberRoot((evaluateUpdate, applyUpdate) => {
			let eagerBail = false;
			evaluate((version) => {
				eagerBail = !evaluateUpdate();
				return eagerBail ? version : version + 1;
			});
			if (!eagerBail) apply(applyUpdate);
		});
	}, []);
	const [version, apply] = useReducer((v, applyUpdate) => {
		setRootVersion(root, v);
		return v + (applyUpdate() ? 1 : 0);
	}, 0);
	const [, evaluate] = useState(0);
	setRootVersion(root, version);
	return {
		root,
		version,
		markDirty: void 0
	};
};
var useResourceFiberHost = () => {
	const getDevMode = useDevStrictMode();
	const { root, version, markDirty } = peekResourceFiber() ? useResourceFiberHostUtilsTap() : useResourceFiberHostUtilsReact();
	return {
		version,
		createFiber: useCallback((hook, _key, onDirty) => {
			return createResourceFiber(hook, root, onDirty ? () => {
				onDirty();
				markDirty?.();
			} : markDirty, getDevMode());
		}, [])
	};
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/utils/useRenderMemo.js
var useRenderMemo = (callback, deps, disableMemo) => {
	const stateRef = useRef(null);
	const state = stateRef.current ?? (stateRef.current = {
		wipDeps: null,
		wip: null,
		currentDeps: null,
		current: null
	});
	state.wipDeps = state.currentDeps;
	state.wip = state.current;
	useEffect(() => {
		state.currentDeps = state.wipDeps;
		state.current = state.wip;
	});
	if (!disableMemo && state.currentDeps && depsShallowEqual(state.currentDeps, deps)) return state.current;
	state.wipDeps = deps;
	state.wip = callback();
	return state.wip;
};
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/useResource.js
function useResource(element) {
	const { version, createFiber } = useResourceFiberHost();
	const fiber = useMemo(() => {
		return createFiber(element.hook, element.key);
	}, [
		element.hook,
		element.key,
		createFiber
	]);
	const result = useRenderMemo(() => ({ value: renderResourceFiber(fiber, element.args) }), [
		fiber,
		version,
		element.args
	], hasContextDepsChanged(fiber));
	useEffect(() => () => unmountResourceFiber(fiber), [fiber]);
	useEffect(() => {
		commitResourceFiber(fiber);
	}, [fiber, result]);
	return result.value;
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/useResources.js
var markChildDirty = (fibers, key) => {
	const state = fibers.get(key);
	if (state) state.isDirty = true;
};
var canReuse = (state, deps) => !state.isDirty && !hasContextDepsChanged(state.fiber) && deps !== void 0 && state.committedDeps !== void 0 && depsShallowEqual(state.committedDeps, deps);
var hasAnyChildContextDepsChanged = (fibers) => {
	if (!hasChangedContexts()) return false;
	for (const { fiber } of fibers.values()) if (hasContextDepsChanged(fiber)) return true;
	return false;
};
function useResources(elements) {
	const fibers = useMemo(() => /* @__PURE__ */ new Map(), []);
	const { version, createFiber } = useResourceFiberHost();
	const hasAnyContextDepsChanged = hasAnyChildContextDepsChanged(fibers);
	const val = useRenderMemo(() => {
		const seenKeys = /* @__PURE__ */ new Set();
		const values = [];
		let newCount = 0;
		for (let i = 0; i < elements.length; i++) {
			const element = elements[i];
			const elementKey = element.key;
			if (elementKey === void 0) throw new Error(`useResources did not provide a key for array at index ${i}`);
			if (seenKeys.has(elementKey)) throw new Error(`Duplicate key ${elementKey} in useResources`);
			seenKeys.add(elementKey);
			let state = fibers.get(elementKey);
			if (!state) {
				const fiber = createFiber(element.hook, element.key, () => markChildDirty(fibers, elementKey));
				state = {
					fiber,
					next: {
						value: renderResourceFiber(fiber, element.args),
						deps: element.deps
					},
					isDirty: false,
					committedDeps: void 0,
					committedValue: void 0
				};
				newCount++;
				fibers.set(elementKey, state);
			} else if (state.fiber.hook !== element.hook) {
				const fiber = createFiber(element.hook, element.key, () => markChildDirty(fibers, elementKey));
				const value = renderResourceFiber(fiber, element.args);
				state.next = {
					value,
					deps: element.deps,
					remount: fiber
				};
			} else if (canReuse(state, element.deps)) {
				if (state.fiber.contextDeps) bubbleContextDeps(state.fiber, state.fiber.contextDeps);
				state.next = "skip";
			} else {
				const value = renderResourceFiber(state.fiber, element.args);
				state.next = {
					value,
					deps: element.deps
				};
			}
			values.push(typeof state.next === "object" ? state.next.value : state.committedValue);
		}
		if (fibers.size > values.length - newCount) {
			for (const key of fibers.keys()) if (!seenKeys.has(key)) fibers.get(key).next = "delete";
		}
		return values;
	}, [
		elements,
		fibers,
		createFiber,
		version
	], hasAnyContextDepsChanged);
	useEffect(() => {
		return () => {
			for (const key of fibers.keys()) {
				const fiber = fibers.get(key).fiber;
				unmountResourceFiber(fiber);
			}
		};
	}, [fibers]);
	useEffect(() => {
		for (const [key, state] of fibers.entries()) {
			const next = state.next;
			if (next === "delete") {
				if (state.fiber.isMounted) unmountResourceFiber(state.fiber);
				fibers.delete(key);
			} else if (next === "skip") {} else {
				if (next.remount) {
					unmountResourceFiber(state.fiber);
					state.fiber = next.remount;
				}
				commitResourceFiber(state.fiber);
				state.committedDeps = next.deps;
				state.committedValue = next.value;
				state.isDirty = false;
			}
		}
	}, [val, fibers]);
	return val;
}
//#endregion
//#region node_modules/@assistant-ui/tap/dist/hooks/useTapHost.js
var useHostRender = (render) => render();
var useTapHost = (callback) => {
	const { createFiber } = useResourceFiberHost();
	const fiber = useMemo(() => createFiber(useHostRender, void 0), [createFiber]);
	const render = renderResourceFiber(fiber, [callback]);
	useEffect(() => {
		return () => {
			unmountResourceFiber(fiber);
		};
	}, [fiber]);
	let renderCommitted = false;
	const effects = () => {
		if (renderCommitted && fiber.isMounted) return;
		renderCommitted = true;
		commitResourceFiber(fiber);
	};
	useEffect(effects);
	return {
		value: render,
		effects
	};
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/client/DataRenderers.js
/**
* Registers renderers for `data` message parts.
*
* Data renderers are looked up by the part's `name` field. Use this resource
* directly for a renderer scope, or prefer {@link useAssistantDataUI} /
* {@link makeAssistantDataUI} when registering from React components.
*/
var useDataRenderers = () => {
	const $ = c(4);
	const [state, setState] = useState(_temp$18);
	let t0;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t0 = (name, render) => {
			setState((prev) => ({
				...prev,
				renderers: {
					...prev.renderers,
					[name]: [...prev.renderers[name] ?? [], render]
				}
			}));
			return () => {
				setState((prev_0) => ({
					...prev_0,
					renderers: {
						...prev_0.renderers,
						[name]: prev_0.renderers[name]?.filter((r) => r !== render) ?? []
					}
				}));
			};
		};
		$[0] = t0;
	} else t0 = $[0];
	const setDataUI = t0;
	let t1;
	if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = (render_0) => {
			setState((prev_1) => ({
				...prev_1,
				fallbacks: [...prev_1.fallbacks, render_0]
			}));
			return () => {
				setState((prev_2) => ({
					...prev_2,
					fallbacks: prev_2.fallbacks.filter((r_0) => r_0 !== render_0)
				}));
			};
		};
		$[1] = t1;
	} else t1 = $[1];
	const setFallbackDataUI = t1;
	let t2;
	if ($[2] !== state) {
		t2 = {
			getState: () => state,
			setDataUI,
			setFallbackDataUI
		};
		$[2] = state;
		$[3] = t2;
	} else t2 = $[3];
	return t2;
};
var DataRenderers = resource(useDataRenderers);
function _temp$18() {
	return {
		renderers: {},
		fallbacks: []
	};
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/model-context/types.js
var mergeModelContexts = (configSet) => {
	const configs = Array.from(configSet).map((c) => c.getModelContext()).sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0));
	const toolPriorities = {};
	return configs.reduce((acc, config) => {
		const priority = config.priority ?? 0;
		if (config.system) if (acc.system) acc.system += `\n\n${config.system}`;
		else acc.system = config.system;
		if (config.tools) for (const [name, tool] of Object.entries(config.tools)) {
			const existing = acc.tools?.[name];
			if (existing && existing !== tool) {
				const existingPriority = toolPriorities[name];
				if (existingPriority === priority) throw new Error(`You tried to define a tool with the name ${name}, but it already exists.`);
				const higherPriorityTool = existingPriority > priority ? existing : tool;
				const lowerPriorityTool = existingPriority > priority ? tool : existing;
				acc.tools[name] = {
					...lowerPriorityTool,
					...higherPriorityTool
				};
				toolPriorities[name] = Math.max(existingPriority, priority);
				continue;
			}
			if (!acc.tools) acc.tools = {};
			acc.tools[name] = tool;
			toolPriorities[name] ??= priority;
		}
		if (config.config) acc.config = {
			...acc.config,
			...config.config
		};
		if (config.callSettings) acc.callSettings = {
			...acc.callSettings,
			...config.callSettings
		};
		return acc;
	}, {});
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/utils/composite-context-provider.js
var CompositeContextProvider = class {
	_providers = /* @__PURE__ */ new Set();
	getModelContext() {
		return mergeModelContexts(this._providers);
	}
	registerModelContextProvider(provider) {
		this._providers.add(provider);
		const unsubscribe = provider.subscribe?.(() => {
			this.notifySubscribers();
		});
		this.notifySubscribers();
		return () => {
			this._providers.delete(provider);
			unsubscribe?.();
			this.notifySubscribers();
		};
	}
	_subscribers = /* @__PURE__ */ new Set();
	notifySubscribers() {
		for (const callback of this._subscribers) callback();
	}
	subscribe(callback) {
		this._subscribers.add(callback);
		return () => {
			this._subscribers.delete(callback);
		};
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/clients/model-context-client.js
var EMPTY_TOOL_NAMES = [];
var INITIAL_STATE = {
	modelName: void 0,
	toolNames: EMPTY_TOOL_NAMES
};
var toolNamesEqual = (a, b) => a === b || a.length === b.length && a.every((v, i) => v === b[i]);
var deriveState = (composite, prev) => {
	const ctx = composite.getModelContext();
	const modelName = ctx.config?.modelName;
	const keys = ctx.tools ? Object.keys(ctx.tools).sort() : EMPTY_TOOL_NAMES;
	const toolNames = keys.length ? keys : EMPTY_TOOL_NAMES;
	if (modelName === prev.modelName && toolNamesEqual(toolNames, prev.toolNames)) return prev;
	return {
		modelName,
		toolNames
	};
};
var useModelContext = () => {
	const $ = c(11);
	let t0;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t0 = new CompositeContextProvider();
		$[0] = t0;
	} else t0 = $[0];
	const composite = t0;
	let t1;
	if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = () => deriveState(composite, INITIAL_STATE);
		$[1] = t1;
	} else t1 = $[1];
	const [state, setState] = useState(t1);
	let t2;
	let t3;
	if ($[2] === Symbol.for("react.memo_cache_sentinel")) {
		t2 = () => {
			setState((prev) => deriveState(composite, prev));
			return composite.subscribe(() => {
				setState((prev_0) => deriveState(composite, prev_0));
			});
		};
		t3 = [composite];
		$[2] = t2;
		$[3] = t3;
	} else {
		t2 = $[2];
		t3 = $[3];
	}
	useEffect(t2, t3);
	let t4;
	if ($[4] !== state) {
		t4 = () => deriveState(composite, state);
		$[4] = state;
		$[5] = t4;
	} else t4 = $[5];
	let t5;
	let t6;
	let t7;
	if ($[6] === Symbol.for("react.memo_cache_sentinel")) {
		t5 = () => composite.getModelContext();
		t6 = (callback) => composite.subscribe(callback);
		t7 = (provider) => composite.registerModelContextProvider(provider);
		$[6] = t5;
		$[7] = t6;
		$[8] = t7;
	} else {
		t5 = $[6];
		t6 = $[7];
		t7 = $[8];
	}
	let t8;
	if ($[9] !== t4) {
		t8 = {
			getState: t4,
			getModelContext: t5,
			subscribe: t6,
			register: t7
		};
		$[9] = t4;
		$[10] = t8;
	} else t8 = $[10];
	return t8;
};
var ModelContext = resource(useModelContext);
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/model-context/toolbox.js
/**
* Resolves whether a tool's UI should be presented standalone (outside the
* chain-of-thought grouping), applying the type-based defaults.
*
* An explicit `display` wins. Otherwise `human` tools default to standalone
* (they prompt the user), and every other tool defaults to inline (a trace of
* what the model is doing). MCP-app tool calls are detected separately from
* the part itself and are not resolved here.
*/
var isStandaloneToolDisplay = (tool) => {
	if (tool.display !== void 0) return tool.display === "standalone";
	return tool.type === "human";
};
var resolveToolCallText = (text, part) => {
	if (!(part.status?.type === "running" || part.status?.type === "requires-action")) {
		const value = text.complete;
		if (typeof value !== "function") return value ?? null;
		return value({
			args: part.args,
			result: part.result
		});
	}
	const value = text.running;
	if (typeof value !== "function") return value ?? null;
	return value({ args: part.args });
};
var makeToolCallTextComponent = (text) => {
	return function ToolCallTextComponent(part) {
		return resolveToolCallText(text, part);
	};
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/utils/tap-client-stack-context.js
/**
* Symbol used to get the client index from a ClientProxy.
*/
var SYMBOL_CLIENT_INDEX = Symbol("assistant-ui.store.clientIndex");
/**
* Get the index of a client (its position in the client stack when created).
*/
var getClientIndex = (client) => {
	return client[SYMBOL_CLIENT_INDEX];
};
var ClientStackContext = createContext([]);
/**
* Get the current client stack inside a tap resource.
*/
var useClientStack = () => {
	return use(ClientStackContext);
};
/**
* Execute a callback with a client pushed onto the stack.
* The stack is duplicated, not mutated.
*/
var useClientStackProvider = (client, callback) => {
	const $ = c(3);
	const currentStack = useClientStack();
	let t0;
	if ($[0] !== client || $[1] !== currentStack) {
		t0 = [...currentStack, client];
		$[0] = client;
		$[1] = currentStack;
		$[2] = t0;
	} else t0 = $[2];
	return useContextProvider(ClientStackContext, t0, callback);
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/utils/BaseProxyHandler.js
var INTROSPECTION_PROPS = new Set([
	"$$typeof",
	"nodeType",
	"then"
]);
/**
* Handles common proxy introspection properties.
* Returns the appropriate value for toStringTag, toJSON, and props that should return undefined.
* Returns `false` if the prop should be handled by the subclass.
*/
var handleIntrospectionProp = (prop, name) => {
	if (prop === Symbol.toStringTag) return name;
	if (typeof prop === "symbol") return void 0;
	if (prop === "toJSON") return () => name;
	if (INTROSPECTION_PROPS.has(prop)) return void 0;
	return false;
};
var BaseProxyHandler = class {
	getOwnPropertyDescriptor(_, prop) {
		const value = this.get(_, prop);
		if (value === void 0) return void 0;
		return {
			value,
			writable: false,
			enumerable: true,
			configurable: false
		};
	}
	set() {
		return false;
	}
	setPrototypeOf() {
		return false;
	}
	defineProperty() {
		return false;
	}
	deleteProperty() {
		return false;
	}
	preventExtensions() {
		return false;
	}
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/useClientResource.js
/**
* Symbol used internally to get state from ClientProxy.
* This allows getState() to be optional in the user-facing client.
*/
var SYMBOL_GET_OUTPUT = Symbol("assistant-ui.store.getValue");
var getClientState = (client) => {
	const output = client[SYMBOL_GET_OUTPUT];
	if (!output) throw new Error("Client scope contains a non-client resource. Ensure your Derived get() returns a client created with useClientResource(), not a plain resource.");
	return output.getState?.();
};
var fieldAccessFns = /* @__PURE__ */ new Map();
function getOrCreateProxyFn(prop) {
	let template = fieldAccessFns.get(prop);
	if (!template) {
		template = function(...args) {
			if (!this || typeof this !== "object") throw new Error(`Method "${String(prop)}" called without proper context. This may indicate the function was called incorrectly.`);
			const output = this[SYMBOL_GET_OUTPUT];
			if (!output) throw new Error(`Method "${String(prop)}" called on invalid client proxy. Ensure you are calling this method on a valid client instance.`);
			const method = output[prop];
			if (!method) throw new Error(`Method "${String(prop)}" is not implemented.`);
			if (typeof method !== "function") throw new Error(`"${String(prop)}" is not a function.`);
			return method(...args);
		};
		fieldAccessFns.set(prop, template);
	}
	return template;
}
var ClientProxyHandler = class extends BaseProxyHandler {
	outputRef;
	index;
	boundFns;
	cachedReceiver;
	constructor(outputRef, index) {
		super();
		this.outputRef = outputRef;
		this.index = index;
	}
	get(_, prop, receiver) {
		if (prop === SYMBOL_GET_OUTPUT) return this.outputRef.current;
		if (prop === SYMBOL_CLIENT_INDEX) return this.index;
		const introspection = handleIntrospectionProp(prop, "ClientProxy");
		if (introspection !== false) return introspection;
		const value = this.outputRef.current[prop];
		if (typeof value === "function") {
			if (this.cachedReceiver !== receiver) {
				this.boundFns = /* @__PURE__ */ new Map();
				this.cachedReceiver = receiver;
			}
			let bound = this.boundFns.get(prop);
			if (!bound) {
				bound = getOrCreateProxyFn(prop).bind(receiver);
				this.boundFns.set(prop, bound);
			}
			return bound;
		}
		return value;
	}
	ownKeys() {
		return Object.keys(this.outputRef.current);
	}
	has(_, prop) {
		if (prop === SYMBOL_GET_OUTPUT) return true;
		if (prop === SYMBOL_CLIENT_INDEX) return true;
		return prop in this.outputRef.current;
	}
};
var useClientResource = (element) => {
	const valueRef = useRef(null);
	const index = useClientStack().length;
	const methods = useMemo(() => new Proxy({}, new ClientProxyHandler(valueRef, index)), [index]);
	const value = useClientStackProvider(methods, function WithClientStack() {
		return useResource(element);
	});
	if (!valueRef.current) valueRef.current = value;
	useEffect(() => {
		valueRef.current = value;
	});
	return {
		methods,
		state: value.getState?.(),
		key: element.key
	};
};
var ClientResource = resource(useClientResource);
//#endregion
//#region node_modules/@assistant-ui/store/dist/utils/proxied-assistant-state.js
var PROXIED_ASSISTANT_STATE_SYMBOL = Symbol("assistant-ui.store.proxiedAssistantState");
var isIgnoredKey = (key) => {
	return key === "on" || key === "subscribe" || typeof key === "symbol";
};
/**
* Proxied state that lazily accesses scope states
*/
var createProxiedAssistantState = (client) => {
	class ProxiedAssistantStateProxyHandler extends BaseProxyHandler {
		get(_, prop) {
			const introspection = handleIntrospectionProp(prop, "AssistantState");
			if (introspection !== false) return introspection;
			const scope = prop;
			if (isIgnoredKey(scope)) return void 0;
			return getClientState(client[scope]());
		}
		ownKeys() {
			return Object.keys(client).filter((key) => !isIgnoredKey(key));
		}
		has(_, prop) {
			return !isIgnoredKey(prop) && prop in client;
		}
	}
	return new Proxy({}, new ProxiedAssistantStateProxyHandler());
};
var getProxiedAssistantState = (client) => {
	return client[PROXIED_ASSISTANT_STATE_SYMBOL];
};
//#endregion
//#region node_modules/react/cjs/react-jsx-runtime.production.min.js
/**
* @license React
* react-jsx-runtime.production.min.js
*
* Copyright (c) Facebook, Inc. and its affiliates.
*
* This source code is licensed under the MIT license found in the
* LICENSE file in the root directory of this source tree.
*/
var require_react_jsx_runtime_production_min = /* @__PURE__ */ __commonJSMin(((exports) => {
	var f = require_react(), k = Symbol.for("react.element"), l = Symbol.for("react.fragment"), m = Object.prototype.hasOwnProperty, n = f.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED.ReactCurrentOwner, p = {
		key: !0,
		ref: !0,
		__self: !0,
		__source: !0
	};
	function q(c, a, g) {
		var b, d = {}, e = null, h = null;
		void 0 !== g && (e = "" + g);
		void 0 !== a.key && (e = "" + a.key);
		void 0 !== a.ref && (h = a.ref);
		for (b in a) m.call(a, b) && !p.hasOwnProperty(b) && (d[b] = a[b]);
		if (c && c.defaultProps) for (b in a = c.defaultProps, a) void 0 === d[b] && (d[b] = a[b]);
		return {
			$$typeof: k,
			type: c,
			key: e,
			ref: h,
			props: d,
			_owner: n.current
		};
	}
	exports.Fragment = l;
	exports.jsx = q;
	exports.jsxs = q;
}));
//#endregion
//#region node_modules/react/jsx-runtime.js
var require_jsx_runtime = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	module.exports = require_react_jsx_runtime_production_min();
}));
//#endregion
//#region node_modules/@assistant-ui/store/dist/utils/react-assistant-context.js
var import_jsx_runtime = require_jsx_runtime();
var NO_OP_SUBSCRIBE = () => () => {};
var createErrorClientField = (message) => {
	const fn = (() => {
		throw new Error(message);
	});
	fn.source = null;
	fn.query = null;
	return fn;
};
var DefaultAssistantClientProxyHandler = class extends BaseProxyHandler {
	get(_, prop) {
		if (prop === "subscribe") return NO_OP_SUBSCRIBE;
		if (prop === "on") return NO_OP_SUBSCRIBE;
		if (prop === PROXIED_ASSISTANT_STATE_SYMBOL) return DefaultAssistantClientProxiedAssistantState;
		const introspection = handleIntrospectionProp(prop, "DefaultAssistantClient");
		if (introspection !== false) return introspection;
		return createErrorClientField("You are using a component or hook that requires an AuiProvider. Wrap your component in an <AuiProvider> component.");
	}
	ownKeys() {
		return [
			"subscribe",
			"on",
			PROXIED_ASSISTANT_STATE_SYMBOL
		];
	}
	has(_, prop) {
		return prop === "subscribe" || prop === "on" || prop === PROXIED_ASSISTANT_STATE_SYMBOL;
	}
};
/** Default context value - throws "wrap in AuiProvider" error */
var DefaultAssistantClient = new Proxy({}, new DefaultAssistantClientProxyHandler());
var DefaultAssistantClientProxiedAssistantState = createProxiedAssistantState(DefaultAssistantClient);
/** Root prototype for created clients - throws "scope not defined" error */
var createRootAssistantClient = () => new Proxy({}, { get(_, prop) {
	const introspection = handleIntrospectionProp(prop, "AssistantClient");
	if (introspection !== false) return introspection;
	return createErrorClientField(`The current scope does not have a "${String(prop)}" property.`);
} });
/**
* React Context for the AssistantClient
*/
var AssistantContext = createContext(DefaultAssistantClient);
/**
* Carries the tap host's effects callback on the client so AuiProvider can
* mount the host's commit ahead of its children's effects.
*/
var AUI_USE_EFFECTS_SYMBOL = Symbol("assistant-ui.store.useEffects");
var NOOP_EFFECT = () => {};
var getTapEffects = (client) => {
	return client[AUI_USE_EFFECTS_SYMBOL] ?? NOOP_EFFECT;
};
var UseTapEffects = () => {
	"use no memo";
	useEffect(getTapEffects(useAssistantContextValue()));
	return null;
};
var useAssistantContextValue = () => {
	return useContext(AssistantContext);
};
/**
* Supplies an `AssistantClient` to the React tree.
*
* Place near the root of any subtree that uses {@link useAui} or the
* primitives built on it. Components rendered outside an `AuiProvider`
* receive a default client whose scope accessors throw on use, so
* missing-provider mistakes surface at the point of use.
*
* When mounting a runtime built with one of the runtime hooks, use
* {@link AssistantRuntimeProvider} — it installs an `AuiProvider`
* internally — rather than wiring `AuiProvider` yourself.
*
* @example
* ```tsx
* function ScopedAssistant({ children, scopes }) {
*   const aui = useAui(scopes);
*
*   return <AuiProvider value={aui}>{children}</AuiProvider>;
* }
* ```
*/
var AuiProvider = ({ value, children }) => {
	"use no memo";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(AssistantContext.Provider, {
		value,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(UseTapEffects, {}), children]
	});
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/Derived.js
/**
* Creates a derived client field that references a client from a parent scope.
* The get callback always calls the most recent version (useEffectEvent pattern).
*
* IMPORTANT: The `get` callback must return a client that was created via
* `useClientResource` (or `useClientLookup`/`useClientList` which use it internally).
* This is required for event scoping to work correctly.
*
* @example
* ```typescript
* const aui = useAui({
*   message: Derived({
*     source: "thread",
*     query: { index: 0 },
*     get: (aui) => aui.thread().message({ index: 0 }),
*   }),
* });
* ```
*/
var useDerived = (_config) => {
	return null;
};
var Derived = resource(useDerived);
//#endregion
//#region node_modules/@assistant-ui/store/dist/attachTransformScopes.js
var TRANSFORM_SCOPES = Symbol("assistant-ui.transform-scopes");
function attachTransformScopes(hook, transform) {
	const h = hook;
	if (h[TRANSFORM_SCOPES]) throw new Error("transformScopes is already attached to this resource");
	h[TRANSFORM_SCOPES] = transform;
}
function getTransformScopes(hook) {
	return hook[TRANSFORM_SCOPES];
}
//#endregion
//#region node_modules/@assistant-ui/store/dist/types/events.js
var normalizeEventSelector = (selector) => {
	if (typeof selector === "string") return {
		scope: selector.split(".")[0],
		event: selector
	};
	return {
		scope: selector.scope,
		event: selector.event
	};
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/utils/tap-assistant-context.js
var AssistantTapContext = createContext(null);
var useAssistantTapContextProvider = (value, fn) => {
	return useContextProvider(AssistantTapContext, value, fn);
};
var useAssistantTapContext = () => {
	const ctx = use(AssistantTapContext);
	if (!ctx) throw new Error("AssistantTapContext is not available");
	return ctx;
};
var useAssistantClientRef = () => {
	return useAssistantTapContext().clientRef;
};
var useAssistantEmit = () => {
	const $ = c(3);
	const { emit } = useAssistantTapContext();
	const clientStack = useClientStack();
	let t0;
	if ($[0] !== clientStack || $[1] !== emit) {
		t0 = (event, payload) => {
			emit(event, payload, clientStack);
		};
		$[0] = clientStack;
		$[1] = emit;
		$[2] = t0;
	} else t0 = $[2];
	return useEffectEvent(t0);
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/utils/splitClients.js
/**
* Splits a clients object into root clients and derived clients,
* applying transformScopes from root client elements.
*/
function splitClients(clients, baseClient) {
	const scopes = { ...clients };
	const visited = /* @__PURE__ */ new Set();
	let changed = true;
	while (changed) {
		changed = false;
		for (const clientElement of Object.values(scopes)) {
			if (clientElement.hook === useDerived) continue;
			if (visited.has(clientElement.hook)) continue;
			visited.add(clientElement.hook);
			const transform = getTransformScopes(clientElement.hook);
			if (transform) {
				transform(scopes, baseClient);
				changed = true;
				break;
			}
		}
	}
	const rootClients = {};
	const derivedClients = {};
	for (const [key, clientElement] of Object.entries(scopes)) if (clientElement.hook === useDerived) derivedClients[key] = clientElement;
	else rootClients[key] = clientElement;
	return {
		rootClients,
		derivedClients
	};
}
var useShallowMemoObject = (object) => {
	return useMemo(() => object, [...Object.entries(object).flat()]);
};
var useSplitClients = (clients, baseClient) => {
	const $ = c(6);
	let t0;
	if ($[0] !== baseClient || $[1] !== clients) {
		t0 = splitClients(clients, baseClient);
		$[0] = baseClient;
		$[1] = clients;
		$[2] = t0;
	} else t0 = $[2];
	const { rootClients, derivedClients } = t0;
	const t1 = useShallowMemoObject(rootClients);
	const t2 = useShallowMemoObject(derivedClients);
	let t3;
	if ($[3] !== t1 || $[4] !== t2) {
		t3 = {
			rootClients: t1,
			derivedClients: t2
		};
		$[3] = t1;
		$[4] = t2;
		$[5] = t3;
	} else t3 = $[5];
	return t3;
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/utils/NotificationManager.js
var useNotificationManager = () => {
	const $ = c(3);
	let t0;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t0 = /* @__PURE__ */ new Map();
		$[0] = t0;
	} else t0 = $[0];
	const listeners = t0;
	let t1;
	if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = /* @__PURE__ */ new Set();
		$[1] = t1;
	} else t1 = $[1];
	const wildcardListeners = t1;
	let t2;
	if ($[2] === Symbol.for("react.memo_cache_sentinel")) {
		const subscribers = /* @__PURE__ */ new Set();
		t2 = {
			on(event, callback) {
				const cb = callback;
				if (event === "*") {
					wildcardListeners.add(cb);
					return () => wildcardListeners.delete(cb);
				}
				let set = listeners.get(event);
				if (!set) {
					set = /* @__PURE__ */ new Set();
					listeners.set(event, set);
				}
				set.add(cb);
				return () => {
					set.delete(cb);
					if (set.size === 0) listeners.delete(event);
				};
			},
			emit(event_0, payload, clientStack) {
				const eventListeners = listeners.get(event_0);
				if (!eventListeners && wildcardListeners.size === 0) return;
				queueMicrotask(() => {
					const errors = [];
					if (eventListeners) for (const cb_0 of eventListeners) try {
						cb_0(payload, clientStack);
					} catch (t3) {
						const e = t3;
						errors.push(e);
					}
					if (wildcardListeners.size > 0) {
						const wrapped = {
							event: event_0,
							payload
						};
						for (const cb_1 of wildcardListeners) try {
							cb_1(wrapped, clientStack);
						} catch (t4) {
							const e_0 = t4;
							errors.push(e_0);
						}
					}
					if (errors.length > 0) if (errors.length === 1) throw errors[0];
					else {
						for (const error of errors) console.error(error);
						throw new AggregateError(errors, "Errors occurred during event emission");
					}
				});
			},
			subscribe(callback_0) {
				subscribers.add(callback_0);
				return () => subscribers.delete(callback_0);
			},
			notifySubscribers() {
				for (const cb_2 of subscribers) try {
					cb_2();
				} catch (t5) {
					console.error("NotificationManager: subscriber callback error", t5);
				}
			}
		};
		$[2] = t2;
	} else t2 = $[2];
	return t2;
};
var NotificationManager = resource(useNotificationManager);
//#endregion
//#region node_modules/@assistant-ui/store/dist/useAui.js
var useShallowMemoArray = (array) => {
	return useMemo(() => array, array);
};
var useRootClientResource = ({ element, emit, clientRef }) => {
	const { methods, state } = useAssistantTapContextProvider({
		clientRef,
		emit
	}, function WithTapContext() {
		return useClientResource(element);
	});
	return useMemo(() => ({
		state,
		methods
	}), [methods, state]);
};
var useRootClientAccessorResource = ({ element, notifications, clientRef, name }) => {
	const store = useTapRoot(function RootClient() {
		return useRootClientResource({
			element,
			emit: notifications.emit,
			clientRef
		});
	});
	useEffect(() => {
		return store.subscribe(notifications.notifySubscribers);
	}, [store, notifications]);
	return useMemo(() => {
		const clientFunction = () => store.getValue().methods;
		Object.defineProperties(clientFunction, {
			source: {
				value: "root",
				writable: false
			},
			query: {
				value: {},
				writable: false
			},
			name: {
				value: name,
				configurable: true
			}
		});
		return clientFunction;
	}, [store, name]);
};
var RootClientAccessorResource = resource(useRootClientAccessorResource);
var useNoOpRootClientsAccessorsResource = () => {
	const $ = c(2);
	let t0;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t0 = [];
		$[0] = t0;
	} else t0 = $[0];
	let t1;
	if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = {
			clients: t0,
			subscribe: void 0,
			on: void 0
		};
		$[1] = t1;
	} else t1 = $[1];
	return t1;
};
var NoOpRootClientsAccessorsResource = resource(useNoOpRootClientsAccessorsResource);
var useRootClientsAccessors = (t0) => {
	const $ = c(14);
	const { clients: inputClients, clientRef } = t0;
	let t1;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = NotificationManager();
		$[0] = t1;
	} else t1 = $[0];
	const notifications = useResource(t1);
	let t2;
	if ($[1] !== clientRef.parent || $[2] !== notifications.notifySubscribers) {
		t2 = () => clientRef.parent.subscribe(notifications.notifySubscribers);
		$[1] = clientRef.parent;
		$[2] = notifications.notifySubscribers;
		$[3] = t2;
	} else t2 = $[3];
	let t3;
	if ($[4] !== clientRef || $[5] !== notifications) {
		t3 = [clientRef, notifications];
		$[4] = clientRef;
		$[5] = notifications;
		$[6] = t3;
	} else t3 = $[6];
	useEffect(t2, t3);
	let t4;
	if ($[7] !== clientRef || $[8] !== inputClients || $[9] !== notifications) {
		t4 = Object.keys(inputClients).map((key) => withKey(key, RootClientAccessorResource({
			element: inputClients[key],
			notifications,
			clientRef,
			name: key
		})));
		$[7] = clientRef;
		$[8] = inputClients;
		$[9] = notifications;
		$[10] = t4;
	} else t4 = $[10];
	const results = useShallowMemoArray(useResources(t4));
	let t5;
	if ($[11] !== notifications || $[12] !== results) {
		t5 = {
			notifications,
			results
		};
		$[11] = notifications;
		$[12] = results;
		$[13] = t5;
	} else t5 = $[13];
	return t5;
};
var useRootClientsAccessorsResource = (props) => {
	const { clientRef } = props;
	const { notifications, results } = useRootClientsAccessors(props);
	return useMemo(() => {
		return {
			clients: results,
			subscribe: notifications.subscribe,
			on: function(selector, callback) {
				if (!this) throw new Error("const { on } = useAui() is not supported. Use aui.on() instead.");
				const { scope, event } = normalizeEventSelector(selector);
				if (scope !== "*") {
					if (this[scope].source === null) throw new Error(`Scope "${scope}" is not available. Use { scope: "*", event: "${event}" } to listen globally.`);
				}
				const localUnsub = notifications.on(event, (payload, clientStack) => {
					if (scope === "*") {
						callback(payload);
						return;
					}
					const scopeClient = this[scope]();
					if (scopeClient === clientStack[getClientIndex(scopeClient)]) callback(payload);
				});
				if (scope !== "*" && clientRef.parent[scope].source === null) return localUnsub;
				const parentUnsub = clientRef.parent.on(selector, callback);
				return () => {
					localUnsub();
					parentUnsub();
				};
			}
		};
	}, [
		results,
		notifications,
		clientRef
	]);
};
var RootClientsAccessorsResource = resource(useRootClientsAccessorsResource);
var useDerivedClientAccessorResource = ({ element, clientRef, name }) => {
	const propsRef = useRef(element.args[0]);
	propsRef.current = element.args[0];
	return useMemo(() => {
		const clientFunction = () => propsRef.current.get(clientRef.current);
		Object.defineProperties(clientFunction, {
			source: { value: propsRef.current.source },
			query: { value: propsRef.current.query },
			name: {
				value: name,
				configurable: true
			}
		});
		return clientFunction;
	}, [clientRef, name]);
};
var DerivedClientAccessorResource = resource(useDerivedClientAccessorResource);
var serializeMeta = (name, meta) => {
	let queryKey;
	try {
		const sorted = {};
		for (const k of Object.keys(meta.query).sort()) sorted[k] = meta.query[k];
		queryKey = JSON.stringify(sorted);
	} catch {
		queryKey = String(meta.query);
	}
	return `${name}::${meta.source}::${queryKey}`;
};
var useDerivedClientsAccessorsResource = (t0) => {
	const $ = c(3);
	const { clients, clientRef } = t0;
	let t1;
	if ($[0] !== clientRef || $[1] !== clients) {
		t1 = Object.keys(clients).map((key) => {
			const name = key;
			const element = clients[name];
			return withKey(serializeMeta(name, element.args[0]), DerivedClientAccessorResource({
				element,
				clientRef,
				name
			}));
		});
		$[0] = clientRef;
		$[1] = clients;
		$[2] = t1;
	} else t1 = $[2];
	return useShallowMemoArray(useResources(t1));
};
/**
* Resource that creates an extended AssistantClient.
*/
var useRootFields = (t0) => {
	const $ = c(3);
	const { rootClients, clientRef } = t0;
	let t1;
	if ($[0] !== clientRef || $[1] !== rootClients) {
		t1 = Object.keys(rootClients).length > 0 ? RootClientsAccessorsResource({
			clients: rootClients,
			clientRef
		}) : NoOpRootClientsAccessorsResource();
		$[0] = clientRef;
		$[1] = rootClients;
		$[2] = t1;
	} else t1 = $[2];
	return useResource(t1);
};
var useAssistantClient = ({ parent, clients }) => {
	const { rootClients, derivedClients } = useSplitClients(clients, parent);
	const clientRef = useRef({
		parent,
		current: null
	}).current;
	useEffect(() => {
		clientRef.current = client;
	});
	const rootFields = useRootFields({
		rootClients,
		clientRef
	});
	const derivedFields = useDerivedClientsAccessorsResource({
		clients: derivedClients,
		clientRef
	});
	const client = useMemo(() => {
		const proto = parent === DefaultAssistantClient ? createRootAssistantClient() : parent;
		const client_0 = Object.create(proto);
		Object.assign(client_0, {
			subscribe: rootFields.subscribe ?? parent.subscribe,
			on: rootFields.on ?? parent.on,
			[PROXIED_ASSISTANT_STATE_SYMBOL]: createProxiedAssistantState(client_0)
		});
		for (const field of rootFields.clients) client_0[field.name] = field;
		for (const field_0 of derivedFields) client_0[field_0.name] = field_0;
		return client_0;
	}, [
		parent,
		rootFields,
		derivedFields
	]);
	if (clientRef.current === null) clientRef.current = client;
	return client;
};
var useHostedAssistantClient = (props) => {
	const { value: client, effects } = useTapHost(function AssistantClientHost() {
		return useAssistantClient(props);
	});
	client[AUI_USE_EFFECTS_SYMBOL] = effects;
	return client;
};
/** @deprecated This API is highly experimental and may be changed in a minor release */
function useAui(clients, { parent } = { parent: useAssistantContextValue() }) {
	if (clients) return useHostedAssistantClient({
		parent: parent ?? DefaultAssistantClient,
		clients
	});
	if (parent === null) throw new Error("received null parent, this usage is not allowed");
	return parent;
}
//#endregion
//#region node_modules/@assistant-ui/store/dist/useAuiState.js
/**
* Subscribes to a slice of {@link AssistantState} and re-renders the
* component whenever that slice changes.
*
* The `selector` is called on every store update; its return value is
* compared by `Object.is`, and the component re-renders only when the
* selected slice changes. Returning the entire state object is not
* supported and throws at runtime — select a specific field instead, or
* compose multiple `useAuiState` calls. Returning a new object or array
* literal, including spreading `s.thread` into a new object, causes a
* re-render on every store update; either select primitives or return a
* memoized reference.
*
* @param selector - Pure function that derives a value from the current
*   assistant state. Should be cheap and referentially stable for equal
*   inputs (plain field reads, primitives, or memoized values).
* @returns The currently selected slice.
*
* @example
* ```tsx
* // Disable a button while a run is in flight.
* const isRunning = useAuiState((s) => s.thread.isRunning);
* ```
*
* @example
* ```tsx
* // Prefer multiple selectors over an inline object literal, which would
* // create a new reference on every render.
* const text = useAuiState((s) => s.composer.text);
* const canSend = useAuiState((s) => s.composer.canSend);
* ```
*/
var useAuiState = (selector) => {
	const $ = c(6);
	const aui = useAui();
	let t0;
	if ($[0] !== aui) {
		t0 = getProxiedAssistantState(aui);
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const proxiedState = t0;
	let t1;
	let t2;
	if ($[2] !== proxiedState || $[3] !== selector) {
		t1 = () => selector(proxiedState);
		t2 = () => selector(proxiedState);
		$[2] = proxiedState;
		$[3] = selector;
		$[4] = t1;
		$[5] = t2;
	} else {
		t1 = $[4];
		t2 = $[5];
	}
	const slice = useSyncExternalStore(aui.subscribe, t1, t2);
	if (slice === proxiedState) throw new Error("You tried to return the entire AssistantState. This is not supported due to technical limitations.");
	useDebugValue(slice);
	return slice;
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/RenderChildrenWithAccessor.js
var useGetItemAccessor = (getItemState) => {
	const aui = useAui();
	const accessedRef = useRef(false);
	const currentValue = accessedRef.current ? null : getItemState(aui);
	useAuiState(() => {
		if (!accessedRef.current) return currentValue;
		return getItemState(aui);
	});
	return () => {
		accessedRef.current = true;
		return getItemState(aui);
	};
};
var EMPTY_OBJECT$1 = Object.freeze({});
/**
* Component that sets up a lazy item accessor and memoizes propless children.
*
* For the common pattern where children returns a component without props
* (e.g. `<Foo />`), the output is memoized and not re-created on parent re-renders.
*
* @example
* ```tsx
* <RenderChildrenWithAccessor
*   getItemState={(aui) => aui.fooList().foo({ index }).getState()}
* >
*   {() => <Foo />}
* </RenderChildrenWithAccessor>
* ```
*/
function RenderChildrenWithAccessor(t0) {
	const $ = c(3);
	const { getItemState, children } = t0;
	const getItem = useGetItemAccessor(getItemState);
	let t1;
	if ($[0] !== children || $[1] !== getItem) {
		t1 = children(getItem);
		$[0] = children;
		$[1] = getItem;
		$[2] = t1;
	} else t1 = $[2];
	return useMemoizedProplessComponent(t1);
}
var useMemoizedProplessComponent = (node) => {
	const el = typeof node === "object" && node != null && "type" in node ? node : null;
	const resultType = el?.type;
	const resultKey = el?.key;
	return useMemo(() => el, [
		resultType,
		resultKey,
		typeof el?.props === "object" && el.props != null && Object.entries(el.props).length === 0 ? EMPTY_OBJECT$1 : el?.props
	]) ?? node;
};
//#endregion
//#region node_modules/@assistant-ui/store/dist/useClientLookup.js
var getElementKey = (el) => {
	if (el.key === void 0) throw new Error("useClientLookup: Element has no key");
	return el.key;
};
function useClientLookup(elements) {
	const $ = c(15);
	let t0;
	if ($[0] !== elements) {
		t0 = elements.map(_temp$17);
		$[0] = elements;
		$[1] = t0;
	} else t0 = $[1];
	const resources = useResources(t0);
	let t1;
	if ($[2] !== resources) {
		t1 = Object.keys(resources);
		$[2] = resources;
		$[3] = t1;
	} else t1 = $[3];
	const keys = t1;
	let t2;
	if ($[4] !== resources) {
		t2 = resources.reduce(_temp2$5, {});
		$[4] = resources;
		$[5] = t2;
	} else t2 = $[5];
	const keyToIndex = t2;
	let t3;
	if ($[6] !== resources) {
		t3 = resources.map(_temp3$3);
		$[6] = resources;
		$[7] = t3;
	} else t3 = $[7];
	const state = t3;
	let t4;
	if ($[8] !== keyToIndex || $[9] !== keys || $[10] !== resources) {
		t4 = (lookup) => {
			if ("index" in lookup) {
				if (lookup.index < 0 || lookup.index >= keys.length) throw new Error(`useClientLookup: Index ${lookup.index} out of bounds (length: ${keys.length})`);
				return resources[lookup.index].methods;
			}
			const index_0 = keyToIndex[lookup.key];
			if (index_0 === void 0) throw new Error(`useClientLookup: Key "${lookup.key}" not found`);
			return resources[index_0].methods;
		};
		$[8] = keyToIndex;
		$[9] = keys;
		$[10] = resources;
		$[11] = t4;
	} else t4 = $[11];
	let t5;
	if ($[12] !== state || $[13] !== t4) {
		t5 = {
			state,
			get: t4
		};
		$[12] = state;
		$[13] = t4;
		$[14] = t5;
	} else t5 = $[14];
	return t5;
}
function _temp3$3(r) {
	return r.state;
}
function _temp2$5(acc, resource, index) {
	acc[resource.key] = index;
	return acc;
}
function _temp$17(el) {
	return withKey(getElementKey(el), ClientResource(el), el.deps);
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/client/Tools.js
/**
* Registers tools with model context and installs tool-call renderers.
*
* Mount this resource near an assistant subtree when you want to expose a
* group of tools declaratively. Tool definitions are registered with model
* context, while each tool renderer is registered with the tools scope for
* message rendering.
*/
var useTools = (t0) => {
	const $ = c(15);
	const { toolkit, mcpApp } = t0;
	let t1;
	if ($[0] !== mcpApp) {
		t1 = mcpApp ? [withKey("mcpApp", mcpApp)] : [];
		$[0] = mcpApp;
		$[1] = t1;
	} else t1 = $[1];
	const mcpAppOutput = useResources(t1)[0];
	const [toolUIs, setToolUIs] = useState(_temp$16);
	let t2;
	if ($[2] !== toolUIs) {
		t2 = Object.fromEntries(Object.entries(toolUIs).map(_temp3$2));
		$[2] = toolUIs;
		$[3] = t2;
	} else t2 = $[3];
	let t3;
	if ($[4] !== mcpAppOutput || $[5] !== t2 || $[6] !== toolUIs) {
		t3 = {
			toolUIs,
			mcpApp: mcpAppOutput,
			tools: t2
		};
		$[4] = mcpAppOutput;
		$[5] = t2;
		$[6] = toolUIs;
		$[7] = t3;
	} else t3 = $[7];
	const state = t3;
	const clientRef = useAssistantClientRef();
	let t4;
	if ($[8] === Symbol.for("react.memo_cache_sentinel")) {
		t4 = (toolName, render, options) => {
			const registration = {
				render,
				standalone: options?.standalone ?? false
			};
			setToolUIs((prev) => ({
				...prev,
				[toolName]: [...prev[toolName] ?? [], registration]
			}));
			return () => {
				setToolUIs((prev_0) => {
					const next = prev_0[toolName]?.filter((r_0) => r_0 !== registration) ?? [];
					if (next.length > 0) return {
						...prev_0,
						[toolName]: next
					};
					const rest = { ...prev_0 };
					delete rest[toolName];
					return rest;
				});
			};
		};
		$[8] = t4;
	} else t4 = $[8];
	const setToolUI = t4;
	let t5;
	let t6;
	if ($[9] !== clientRef || $[10] !== toolkit) {
		t5 = () => {
			if (!toolkit) return;
			const unsubscribes = [];
			for (const [toolName_0, tool] of Object.entries(toolkit)) {
				const toolRender = "render" in tool ? tool.render : void 0;
				const toolRenderText = "renderText" in tool ? tool.renderText : void 0;
				const render_0 = toolRender ?? (toolRenderText ? makeToolCallTextComponent(toolRenderText) : void 0);
				if (render_0) unsubscribes.push(setToolUI(toolName_0, render_0, { standalone: isStandaloneToolDisplay(tool) }));
			}
			const toolsWithoutRender = Object.entries(toolkit).reduce(_temp4$1, {});
			unsubscribes.push(clientRef.current.modelContext().register({ getModelContext: () => ({ tools: toolsWithoutRender }) }));
			return () => {
				unsubscribes.forEach(_temp5$1);
			};
		};
		t6 = [
			toolkit,
			setToolUI,
			clientRef
		];
		$[9] = clientRef;
		$[10] = toolkit;
		$[11] = t5;
		$[12] = t6;
	} else {
		t5 = $[11];
		t6 = $[12];
	}
	useEffect(t5, t6);
	let t7;
	if ($[13] !== state) {
		t7 = {
			getState: () => state,
			setToolUI
		};
		$[13] = state;
		$[14] = t7;
	} else t7 = $[14];
	return t7;
};
var Tools = resource(useTools);
attachTransformScopes(useTools, (scopes, parent) => {
	if (!scopes.modelContext && parent.modelContext.source === null) scopes.modelContext = ModelContext();
});
function _temp$16() {
	return {};
}
function _temp2$4(r) {
	return r.render;
}
function _temp3$2(t0) {
	const [name, regs] = t0;
	return [name, regs.map(_temp2$4)];
}
function _temp4$1(acc, t0) {
	const [name_0, tool_0] = t0;
	if (tool_0.type === "mcp") return acc;
	const { display: _display, render: _render, renderText: _renderText, ...rest_0 } = tool_0;
	acc[name_0] = rest_0;
	return acc;
}
function _temp5$1(fn) {
	return fn();
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/useSubscribable.js
var useSubscribable = (subscribable) => {
	return useSyncExternalStore(subscribable.subscribe, subscribable.getState);
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/attachment-runtime-client.js
var useAttachmentRuntimeClient = (t0) => {
	const $ = c(8);
	const { runtime } = t0;
	const state = useSubscribable(runtime);
	let t1;
	if ($[0] !== state) {
		t1 = () => state;
		$[0] = state;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== runtime) {
		t2 = () => runtime;
		$[2] = runtime;
		$[3] = t2;
	} else t2 = $[3];
	let t3;
	if ($[4] !== runtime.remove || $[5] !== t1 || $[6] !== t2) {
		t3 = {
			getState: t1,
			remove: runtime.remove,
			__internal_getRuntime: t2
		};
		$[4] = runtime.remove;
		$[5] = t1;
		$[6] = t2;
		$[7] = t3;
	} else t3 = $[7];
	return t3;
};
var AttachmentRuntimeClient = resource(useAttachmentRuntimeClient);
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/composer-runtime-client.js
var useComposerAttachmentClientByIndex = (t0) => {
	const $ = c(5);
	const { runtime, index } = t0;
	let t1;
	if ($[0] !== index || $[1] !== runtime) {
		t1 = runtime.getAttachmentByIndex(index);
		$[0] = index;
		$[1] = runtime;
		$[2] = t1;
	} else t1 = $[2];
	const attachmentRuntime = t1;
	let t2;
	if ($[3] !== attachmentRuntime) {
		t2 = AttachmentRuntimeClient({ runtime: attachmentRuntime });
		$[3] = attachmentRuntime;
		$[4] = t2;
	} else t2 = $[4];
	return useResource(t2);
};
var ComposerAttachmentClientByIndex = resource(useComposerAttachmentClientByIndex);
var useQueueItemClient = ({ item, onSteer, onRemove }) => {
	return {
		getState: () => item,
		steer: onSteer,
		remove: onRemove
	};
};
var QueueItemClient = resource(useQueueItemClient);
var useComposerClient = (t0) => {
	const $ = c(55);
	const { threadIdRef, messageIdRef, runtime } = t0;
	const runtimeState = useSubscribable(runtime);
	const emit = useAssistantEmit();
	let t1;
	let t2;
	if ($[0] !== emit || $[1] !== messageIdRef || $[2] !== runtime || $[3] !== threadIdRef) {
		t1 = () => {
			const unsubscribers = [];
			for (const event of ["send", "attachmentAdd"]) {
				const unsubscribe = runtime.unstable_on(event, () => {
					emit(`composer.${event}`, {
						threadId: threadIdRef.current,
						...messageIdRef && { messageId: messageIdRef.current }
					});
				});
				unsubscribers.push(unsubscribe);
			}
			unsubscribers.push(runtime.unstable_on("attachmentAddError", (payload) => {
				emit("composer.attachmentAddError", {
					threadId: threadIdRef.current,
					...messageIdRef && { messageId: messageIdRef.current },
					...payload.attachmentId && { attachmentId: payload.attachmentId },
					reason: payload.reason,
					message: payload.message
				});
			}));
			return () => {
				for (const unsub of unsubscribers) unsub();
			};
		};
		t2 = [
			runtime,
			emit,
			threadIdRef,
			messageIdRef
		];
		$[0] = emit;
		$[1] = messageIdRef;
		$[2] = runtime;
		$[3] = threadIdRef;
		$[4] = t1;
		$[5] = t2;
	} else {
		t1 = $[4];
		t2 = $[5];
	}
	useEffect(t1, t2);
	let t3;
	if ($[6] !== runtime || $[7] !== runtimeState.attachments) {
		let t4;
		if ($[9] !== runtime) {
			t4 = (attachment, idx) => withKey(attachment.id, ComposerAttachmentClientByIndex({
				runtime,
				index: idx
			}), [runtime, idx]);
			$[9] = runtime;
			$[10] = t4;
		} else t4 = $[10];
		t3 = runtimeState.attachments.map(t4);
		$[6] = runtime;
		$[7] = runtimeState.attachments;
		$[8] = t3;
	} else t3 = $[8];
	const attachments = useClientLookup(t3);
	const queue = runtimeState.queue;
	let t4;
	if ($[11] !== queue || $[12] !== runtime) {
		let t5;
		if ($[14] !== runtime) {
			t5 = (item) => withKey(item.id, QueueItemClient({
				item,
				onSteer: () => runtime.steerQueueItem(item.id),
				onRemove: () => runtime.removeQueueItem(item.id)
			}));
			$[14] = runtime;
			$[15] = t5;
		} else t5 = $[15];
		t4 = queue.map(t5);
		$[11] = queue;
		$[12] = runtime;
		$[13] = t4;
	} else t4 = $[13];
	const queueItems = useClientLookup(t4);
	const t5 = runtimeState.type ?? "thread";
	let t6;
	if ($[16] !== attachments.state || $[17] !== queue || $[18] !== runtimeState.attachmentAccept || $[19] !== runtimeState.canCancel || $[20] !== runtimeState.canSend || $[21] !== runtimeState.dictation || $[22] !== runtimeState.isEditing || $[23] !== runtimeState.isEmpty || $[24] !== runtimeState.quote || $[25] !== runtimeState.role || $[26] !== runtimeState.runConfig || $[27] !== runtimeState.text || $[28] !== t5) {
		t6 = {
			text: runtimeState.text,
			role: runtimeState.role,
			attachments: attachments.state,
			runConfig: runtimeState.runConfig,
			isEditing: runtimeState.isEditing,
			canCancel: runtimeState.canCancel,
			canSend: runtimeState.canSend,
			attachmentAccept: runtimeState.attachmentAccept,
			isEmpty: runtimeState.isEmpty,
			type: t5,
			dictation: runtimeState.dictation,
			quote: runtimeState.quote,
			queue
		};
		$[16] = attachments.state;
		$[17] = queue;
		$[18] = runtimeState.attachmentAccept;
		$[19] = runtimeState.canCancel;
		$[20] = runtimeState.canSend;
		$[21] = runtimeState.dictation;
		$[22] = runtimeState.isEditing;
		$[23] = runtimeState.isEmpty;
		$[24] = runtimeState.quote;
		$[25] = runtimeState.role;
		$[26] = runtimeState.runConfig;
		$[27] = runtimeState.text;
		$[28] = t5;
		$[29] = t6;
	} else t6 = $[29];
	const state = t6;
	let t7;
	if ($[30] !== state) {
		t7 = () => state;
		$[30] = state;
		$[31] = t7;
	} else t7 = $[31];
	const t8 = runtime.beginEdit ?? _temp$15;
	let t9;
	if ($[32] !== attachments) {
		t9 = (selector) => {
			if ("id" in selector) return attachments.get({ key: selector.id });
			else return attachments.get(selector);
		};
		$[32] = attachments;
		$[33] = t9;
	} else t9 = $[33];
	let t10;
	if ($[34] !== queueItems) {
		t10 = (selector_0) => queueItems.get(selector_0);
		$[34] = queueItems;
		$[35] = t10;
	} else t10 = $[35];
	let t11;
	if ($[36] !== runtime) {
		t11 = () => runtime;
		$[36] = runtime;
		$[37] = t11;
	} else t11 = $[37];
	let t12;
	if ($[38] !== runtime.addAttachment || $[39] !== runtime.cancel || $[40] !== runtime.clearAttachments || $[41] !== runtime.reset || $[42] !== runtime.send || $[43] !== runtime.setQuote || $[44] !== runtime.setRole || $[45] !== runtime.setRunConfig || $[46] !== runtime.setText || $[47] !== runtime.startDictation || $[48] !== runtime.stopDictation || $[49] !== t10 || $[50] !== t11 || $[51] !== t7 || $[52] !== t8 || $[53] !== t9) {
		t12 = {
			getState: t7,
			setText: runtime.setText,
			setRole: runtime.setRole,
			setRunConfig: runtime.setRunConfig,
			addAttachment: runtime.addAttachment,
			reset: runtime.reset,
			clearAttachments: runtime.clearAttachments,
			send: runtime.send,
			cancel: runtime.cancel,
			beginEdit: t8,
			startDictation: runtime.startDictation,
			stopDictation: runtime.stopDictation,
			setQuote: runtime.setQuote,
			attachment: t9,
			queueItem: t10,
			__internal_getRuntime: t11
		};
		$[38] = runtime.addAttachment;
		$[39] = runtime.cancel;
		$[40] = runtime.clearAttachments;
		$[41] = runtime.reset;
		$[42] = runtime.send;
		$[43] = runtime.setQuote;
		$[44] = runtime.setRole;
		$[45] = runtime.setRunConfig;
		$[46] = runtime.setText;
		$[47] = runtime.startDictation;
		$[48] = runtime.stopDictation;
		$[49] = t10;
		$[50] = t11;
		$[51] = t7;
		$[52] = t8;
		$[53] = t9;
		$[54] = t12;
	} else t12 = $[54];
	return t12;
};
var ComposerClient = resource(useComposerClient);
function _temp$15() {
	throw new Error("beginEdit is not supported in this runtime");
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/liveRef.js
var liveRef = (get) => ({ get current() {
	return get();
} });
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/message-part-runtime-client.js
var useMessagePartClient = (t0) => {
	const $ = c(13);
	const { runtime } = t0;
	const state = useSubscribable(runtime);
	let t1;
	if ($[0] !== state) {
		t1 = () => state;
		$[0] = state;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	let t3;
	let t4;
	let t5;
	if ($[2] !== runtime) {
		t2 = (result) => runtime.addToolResult(result);
		t3 = (payload) => runtime.resumeToolCall(payload);
		t4 = (response) => runtime.respondToToolApproval(response);
		t5 = () => runtime;
		$[2] = runtime;
		$[3] = t2;
		$[4] = t3;
		$[5] = t4;
		$[6] = t5;
	} else {
		t2 = $[3];
		t3 = $[4];
		t4 = $[5];
		t5 = $[6];
	}
	let t6;
	if ($[7] !== t1 || $[8] !== t2 || $[9] !== t3 || $[10] !== t4 || $[11] !== t5) {
		t6 = {
			getState: t1,
			addToolResult: t2,
			resumeToolCall: t3,
			respondToToolApproval: t4,
			__internal_getRuntime: t5
		};
		$[7] = t1;
		$[8] = t2;
		$[9] = t3;
		$[10] = t4;
		$[11] = t5;
		$[12] = t6;
	} else t6 = $[12];
	return t6;
};
var MessagePartClient = resource(useMessagePartClient);
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/message-runtime-client.js
var useMessageAttachmentClientByIndex = (t0) => {
	const $ = c(5);
	const { runtime, index } = t0;
	let t1;
	if ($[0] !== index || $[1] !== runtime) {
		t1 = runtime.getAttachmentByIndex(index);
		$[0] = index;
		$[1] = runtime;
		$[2] = t1;
	} else t1 = $[2];
	const attachmentRuntime = t1;
	let t2;
	if ($[3] !== attachmentRuntime) {
		t2 = AttachmentRuntimeClient({ runtime: attachmentRuntime });
		$[3] = attachmentRuntime;
		$[4] = t2;
	} else t2 = $[4];
	return useResource(t2);
};
var MessageAttachmentClientByIndex = resource(useMessageAttachmentClientByIndex);
var useMessagePartByIndex = (t0) => {
	const $ = c(5);
	const { runtime, index } = t0;
	let t1;
	if ($[0] !== index || $[1] !== runtime) {
		t1 = runtime.getMessagePartByIndex(index);
		$[0] = index;
		$[1] = runtime;
		$[2] = t1;
	} else t1 = $[2];
	const partRuntime = t1;
	let t2;
	if ($[3] !== partRuntime) {
		t2 = MessagePartClient({ runtime: partRuntime });
		$[3] = partRuntime;
		$[4] = t2;
	} else t2 = $[4];
	return useResource(t2);
};
var MessagePartByIndex = resource(useMessagePartByIndex);
var useMessageClient = (t0) => {
	const $ = c(55);
	const { runtime, threadIdRef } = t0;
	const runtimeState = useSubscribable(runtime);
	const [isCopiedState, setIsCopied] = useState(false);
	const [isHoveringState, setIsHovering] = useState(false);
	let t1;
	if ($[0] !== runtime) {
		t1 = liveRef(() => runtime.getState().id);
		$[0] = runtime;
		$[1] = t1;
	} else t1 = $[1];
	const messageIdRef = t1;
	let t2;
	if ($[2] !== messageIdRef || $[3] !== runtime.composer || $[4] !== threadIdRef) {
		t2 = ComposerClient({
			runtime: runtime.composer,
			threadIdRef,
			messageIdRef
		});
		$[2] = messageIdRef;
		$[3] = runtime.composer;
		$[4] = threadIdRef;
		$[5] = t2;
	} else t2 = $[5];
	const composer = useClientResource(t2);
	let t3;
	if ($[6] !== runtime || $[7] !== runtimeState.content) {
		let t4;
		if ($[9] !== runtime) {
			t4 = (part, idx) => withKey("toolCallId" in part && part.toolCallId != null ? `toolCallId-${part.toolCallId}` : `index-${idx}`, MessagePartByIndex({
				runtime,
				index: idx
			}), [runtime, idx]);
			$[9] = runtime;
			$[10] = t4;
		} else t4 = $[10];
		t3 = runtimeState.content.map(t4);
		$[6] = runtime;
		$[7] = runtimeState.content;
		$[8] = t3;
	} else t3 = $[8];
	const parts = useClientLookup(t3);
	let t4;
	if ($[11] !== runtimeState.attachments) {
		t4 = runtimeState.attachments ?? [];
		$[11] = runtimeState.attachments;
		$[12] = t4;
	} else t4 = $[12];
	let t5;
	if ($[13] !== runtime || $[14] !== t4) {
		let t6;
		if ($[16] !== runtime) {
			t6 = (attachment, idx_0) => withKey(attachment.id, MessageAttachmentClientByIndex({
				runtime,
				index: idx_0
			}), [runtime, idx_0]);
			$[16] = runtime;
			$[17] = t6;
		} else t6 = $[17];
		t5 = t4.map(t6);
		$[13] = runtime;
		$[14] = t4;
		$[15] = t5;
	} else t5 = $[15];
	const attachments = useClientLookup(t5);
	const t6 = runtimeState;
	let t7;
	if ($[18] !== composer.state || $[19] !== isCopiedState || $[20] !== isHoveringState || $[21] !== parts.state || $[22] !== t6) {
		t7 = {
			...t6,
			parts: parts.state,
			composer: composer.state,
			isCopied: isCopiedState,
			isHovering: isHoveringState
		};
		$[18] = composer.state;
		$[19] = isCopiedState;
		$[20] = isHoveringState;
		$[21] = parts.state;
		$[22] = t6;
		$[23] = t7;
	} else t7 = $[23];
	const state = t7;
	let t8;
	if ($[24] !== state) {
		t8 = () => state;
		$[24] = state;
		$[25] = t8;
	} else t8 = $[25];
	let t9;
	if ($[26] !== composer.methods) {
		t9 = () => composer.methods;
		$[26] = composer.methods;
		$[27] = t9;
	} else t9 = $[27];
	let t10;
	let t11;
	let t12;
	let t13;
	let t14;
	let t15;
	let t16;
	if ($[28] !== runtime) {
		t10 = () => runtime.delete();
		t11 = (config) => runtime.reload(config);
		t12 = () => runtime.speak();
		t13 = () => runtime.stopSpeaking();
		t14 = (feedback) => runtime.submitFeedback(feedback);
		t15 = (options) => runtime.switchToBranch(options);
		t16 = () => runtime.unstable_getCopyText();
		$[28] = runtime;
		$[29] = t10;
		$[30] = t11;
		$[31] = t12;
		$[32] = t13;
		$[33] = t14;
		$[34] = t15;
		$[35] = t16;
	} else {
		t10 = $[29];
		t11 = $[30];
		t12 = $[31];
		t13 = $[32];
		t14 = $[33];
		t15 = $[34];
		t16 = $[35];
	}
	let t17;
	if ($[36] !== parts) {
		t17 = (selector) => {
			if ("index" in selector) return parts.get({ index: selector.index });
			else return parts.get({ key: `toolCallId-${selector.toolCallId}` });
		};
		$[36] = parts;
		$[37] = t17;
	} else t17 = $[37];
	let t18;
	if ($[38] !== attachments) {
		t18 = (selector_0) => {
			if ("id" in selector_0) return attachments.get({ key: selector_0.id });
			else return attachments.get(selector_0);
		};
		$[38] = attachments;
		$[39] = t18;
	} else t18 = $[39];
	let t19;
	if ($[40] !== runtime) {
		t19 = () => runtime;
		$[40] = runtime;
		$[41] = t19;
	} else t19 = $[41];
	let t20;
	if ($[42] !== t10 || $[43] !== t11 || $[44] !== t12 || $[45] !== t13 || $[46] !== t14 || $[47] !== t15 || $[48] !== t16 || $[49] !== t17 || $[50] !== t18 || $[51] !== t19 || $[52] !== t8 || $[53] !== t9) {
		t20 = {
			getState: t8,
			composer: t9,
			delete: t10,
			reload: t11,
			speak: t12,
			stopSpeaking: t13,
			submitFeedback: t14,
			switchToBranch: t15,
			getCopyText: t16,
			part: t17,
			attachment: t18,
			setIsCopied,
			setIsHovering,
			__internal_getRuntime: t19
		};
		$[42] = t10;
		$[43] = t11;
		$[44] = t12;
		$[45] = t13;
		$[46] = t14;
		$[47] = t15;
		$[48] = t16;
		$[49] = t17;
		$[50] = t18;
		$[51] = t19;
		$[52] = t8;
		$[53] = t9;
		$[54] = t20;
	} else t20 = $[54];
	return t20;
};
var MessageClient = resource(useMessageClient);
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/thread-runtime-client.js
var useMessageClientById = (t0) => {
	const $ = c(6);
	const { runtime, id, threadIdRef } = t0;
	let t1;
	if ($[0] !== id || $[1] !== runtime) {
		t1 = runtime.getMessageById(id);
		$[0] = id;
		$[1] = runtime;
		$[2] = t1;
	} else t1 = $[2];
	const messageRuntime = t1;
	let t2;
	if ($[3] !== messageRuntime || $[4] !== threadIdRef) {
		t2 = MessageClient({
			runtime: messageRuntime,
			threadIdRef
		});
		$[3] = messageRuntime;
		$[4] = threadIdRef;
		$[5] = t2;
	} else t2 = $[5];
	return useResource(t2);
};
var MessageClientById = resource(useMessageClientById);
var useThreadClient = (t0) => {
	const $ = c(58);
	const { runtime } = t0;
	const runtimeState = useSubscribable(runtime);
	const emit = useAssistantEmit();
	let t1;
	let t2;
	if ($[0] !== emit || $[1] !== runtime) {
		t1 = () => {
			const unsubscribers = [];
			for (const event of [
				"runStart",
				"runEnd",
				"initialize",
				"modelContextUpdate"
			]) {
				const unsubscribe = runtime.unstable_on(event, () => {
					const threadId = runtime.getState()?.threadId || "unknown";
					emit(`thread.${event}`, { threadId });
				});
				unsubscribers.push(unsubscribe);
			}
			return () => {
				for (const unsub of unsubscribers) unsub();
			};
		};
		t2 = [runtime, emit];
		$[0] = emit;
		$[1] = runtime;
		$[2] = t1;
		$[3] = t2;
	} else {
		t1 = $[2];
		t2 = $[3];
	}
	useEffect(t1, t2);
	let t3;
	if ($[4] !== runtime) {
		t3 = liveRef(() => runtime.getState().threadId);
		$[4] = runtime;
		$[5] = t3;
	} else t3 = $[5];
	const threadIdRef = t3;
	let t4;
	if ($[6] !== runtime.composer || $[7] !== threadIdRef) {
		t4 = ComposerClient({
			runtime: runtime.composer,
			threadIdRef
		});
		$[6] = runtime.composer;
		$[7] = threadIdRef;
		$[8] = t4;
	} else t4 = $[8];
	const composer = useClientResource(t4);
	let t5;
	if ($[9] !== runtime || $[10] !== runtimeState.messages || $[11] !== threadIdRef) {
		let t6;
		if ($[13] !== runtime || $[14] !== threadIdRef) {
			t6 = (m) => withKey(m.id, MessageClientById({
				runtime,
				id: m.id,
				threadIdRef
			}), [
				runtime,
				m.id,
				threadIdRef
			]);
			$[13] = runtime;
			$[14] = threadIdRef;
			$[15] = t6;
		} else t6 = $[15];
		t5 = runtimeState.messages.map(t6);
		$[9] = runtime;
		$[10] = runtimeState.messages;
		$[11] = threadIdRef;
		$[12] = t5;
	} else t5 = $[12];
	const messages = useClientLookup(t5);
	const t6 = messages.state.length === 0 && !runtimeState.isLoading;
	let t7;
	if ($[16] !== composer.state || $[17] !== messages.state || $[18] !== runtimeState.capabilities || $[19] !== runtimeState.extras || $[20] !== runtimeState.isDisabled || $[21] !== runtimeState.isLoading || $[22] !== runtimeState.isRunning || $[23] !== runtimeState.speech || $[24] !== runtimeState.state || $[25] !== runtimeState.suggestions || $[26] !== runtimeState.voice || $[27] !== t6) {
		t7 = {
			isEmpty: t6,
			isDisabled: runtimeState.isDisabled,
			isLoading: runtimeState.isLoading,
			isRunning: runtimeState.isRunning,
			capabilities: runtimeState.capabilities,
			state: runtimeState.state,
			suggestions: runtimeState.suggestions,
			extras: runtimeState.extras,
			speech: runtimeState.speech,
			voice: runtimeState.voice,
			composer: composer.state,
			messages: messages.state
		};
		$[16] = composer.state;
		$[17] = messages.state;
		$[18] = runtimeState.capabilities;
		$[19] = runtimeState.extras;
		$[20] = runtimeState.isDisabled;
		$[21] = runtimeState.isLoading;
		$[22] = runtimeState.isRunning;
		$[23] = runtimeState.speech;
		$[24] = runtimeState.state;
		$[25] = runtimeState.suggestions;
		$[26] = runtimeState.voice;
		$[27] = t6;
		$[28] = t7;
	} else t7 = $[28];
	const state = t7;
	let t8;
	if ($[29] !== state) {
		t8 = () => state;
		$[29] = state;
		$[30] = t8;
	} else t8 = $[30];
	let t9;
	if ($[31] !== composer.methods) {
		t9 = () => composer.methods;
		$[31] = composer.methods;
		$[32] = t9;
	} else t9 = $[32];
	let t10;
	if ($[33] !== messages) {
		t10 = (selector) => {
			if ("id" in selector) return messages.get({ key: selector.id });
			else return messages.get(selector);
		};
		$[33] = messages;
		$[34] = t10;
	} else t10 = $[34];
	let t11;
	if ($[35] !== runtime) {
		t11 = () => runtime;
		$[35] = runtime;
		$[36] = t11;
	} else t11 = $[36];
	let t12;
	if ($[37] !== runtime.append || $[38] !== runtime.cancelRun || $[39] !== runtime.connectVoice || $[40] !== runtime.deleteMessage || $[41] !== runtime.disconnectVoice || $[42] !== runtime.export || $[43] !== runtime.getModelContext || $[44] !== runtime.getVoiceVolume || $[45] !== runtime.import || $[46] !== runtime.muteVoice || $[47] !== runtime.reset || $[48] !== runtime.resumeRun || $[49] !== runtime.startRun || $[50] !== runtime.stopSpeaking || $[51] !== runtime.subscribeVoiceVolume || $[52] !== runtime.unmuteVoice || $[53] !== t10 || $[54] !== t11 || $[55] !== t8 || $[56] !== t9) {
		t12 = {
			getState: t8,
			composer: t9,
			append: runtime.append,
			deleteMessage: runtime.deleteMessage,
			startRun: runtime.startRun,
			resumeRun: runtime.resumeRun,
			cancelRun: runtime.cancelRun,
			getModelContext: runtime.getModelContext,
			export: runtime.export,
			import: runtime.import,
			reset: runtime.reset,
			stopSpeaking: runtime.stopSpeaking,
			connectVoice: runtime.connectVoice,
			disconnectVoice: runtime.disconnectVoice,
			getVoiceVolume: runtime.getVoiceVolume,
			subscribeVoiceVolume: runtime.subscribeVoiceVolume,
			muteVoice: runtime.muteVoice,
			unmuteVoice: runtime.unmuteVoice,
			message: t10,
			__internal_getRuntime: t11
		};
		$[37] = runtime.append;
		$[38] = runtime.cancelRun;
		$[39] = runtime.connectVoice;
		$[40] = runtime.deleteMessage;
		$[41] = runtime.disconnectVoice;
		$[42] = runtime.export;
		$[43] = runtime.getModelContext;
		$[44] = runtime.getVoiceVolume;
		$[45] = runtime.import;
		$[46] = runtime.muteVoice;
		$[47] = runtime.reset;
		$[48] = runtime.resumeRun;
		$[49] = runtime.startRun;
		$[50] = runtime.stopSpeaking;
		$[51] = runtime.subscribeVoiceVolume;
		$[52] = runtime.unmuteVoice;
		$[53] = t10;
		$[54] = t11;
		$[55] = t8;
		$[56] = t9;
		$[57] = t12;
	} else t12 = $[57];
	return t12;
};
var ThreadClient = resource(useThreadClient);
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/thread-list-item-runtime-client.js
var useThreadListItemClient = (t0) => {
	const $ = c(20);
	const { runtime } = t0;
	const state = useSubscribable(runtime);
	const emit = useAssistantEmit();
	let t1;
	let t2;
	if ($[0] !== emit || $[1] !== runtime) {
		t1 = () => {
			const unsubscribers = [];
			for (const event of ["switchedTo", "switchedAway"]) {
				const unsubscribe = runtime.unstable_on(event, () => {
					emit(`threadListItem.${event}`, { threadId: runtime.getState().id });
				});
				unsubscribers.push(unsubscribe);
			}
			return () => {
				for (const unsub of unsubscribers) unsub();
			};
		};
		t2 = [runtime, emit];
		$[0] = emit;
		$[1] = runtime;
		$[2] = t1;
		$[3] = t2;
	} else {
		t1 = $[2];
		t2 = $[3];
	}
	useEffect(t1, t2);
	let t3;
	if ($[4] !== state) {
		t3 = () => state;
		$[4] = state;
		$[5] = t3;
	} else t3 = $[5];
	let t4;
	if ($[6] !== runtime) {
		t4 = () => runtime;
		$[6] = runtime;
		$[7] = t4;
	} else t4 = $[7];
	let t5;
	if ($[8] !== runtime.archive || $[9] !== runtime.delete || $[10] !== runtime.detach || $[11] !== runtime.generateTitle || $[12] !== runtime.initialize || $[13] !== runtime.rename || $[14] !== runtime.switchTo || $[15] !== runtime.unarchive || $[16] !== runtime.updateCustom || $[17] !== t3 || $[18] !== t4) {
		t5 = {
			getState: t3,
			switchTo: runtime.switchTo,
			rename: runtime.rename,
			updateCustom: runtime.updateCustom,
			archive: runtime.archive,
			unarchive: runtime.unarchive,
			delete: runtime.delete,
			generateTitle: runtime.generateTitle,
			initialize: runtime.initialize,
			detach: runtime.detach,
			__internal_getRuntime: t4
		};
		$[8] = runtime.archive;
		$[9] = runtime.delete;
		$[10] = runtime.detach;
		$[11] = runtime.generateTitle;
		$[12] = runtime.initialize;
		$[13] = runtime.rename;
		$[14] = runtime.switchTo;
		$[15] = runtime.unarchive;
		$[16] = runtime.updateCustom;
		$[17] = t3;
		$[18] = t4;
		$[19] = t5;
	} else t5 = $[19];
	return t5;
};
var ThreadListItemClient = resource(useThreadListItemClient);
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/runtime-clients/thread-list-runtime-client.js
var useThreadListItemClientById = (t0) => {
	const $ = c(5);
	const { runtime, id } = t0;
	let t1;
	if ($[0] !== id || $[1] !== runtime) {
		t1 = runtime.getItemById(id);
		$[0] = id;
		$[1] = runtime;
		$[2] = t1;
	} else t1 = $[2];
	const threadListItemRuntime = t1;
	let t2;
	if ($[3] !== threadListItemRuntime) {
		t2 = ThreadListItemClient({ runtime: threadListItemRuntime });
		$[3] = threadListItemRuntime;
		$[4] = t2;
	} else t2 = $[4];
	return useResource(t2);
};
var ThreadListItemClientById = resource(useThreadListItemClientById);
var useThreadListClient = (t0) => {
	const $ = c(40);
	const { runtime, __internal_assistantRuntime } = t0;
	const runtimeState = useSubscribable(runtime);
	let t1;
	if ($[0] !== runtime.main) {
		t1 = ThreadClient({ runtime: runtime.main });
		$[0] = runtime.main;
		$[1] = t1;
	} else t1 = $[1];
	const main = useClientResource(t1);
	let t2;
	if ($[2] !== runtime || $[3] !== runtimeState.threadItems) {
		t2 = Object.keys(runtimeState.threadItems).map((id) => withKey(id, ThreadListItemClientById({
			runtime,
			id
		}), [runtime, id]));
		$[2] = runtime;
		$[3] = runtimeState.threadItems;
		$[4] = t2;
	} else t2 = $[4];
	const threadItems = useClientLookup(t2);
	const t3 = runtimeState.newThreadId ?? null;
	let t4;
	if ($[5] !== main.state || $[6] !== runtimeState.archivedThreadIds || $[7] !== runtimeState.hasMore || $[8] !== runtimeState.isLoading || $[9] !== runtimeState.isLoadingMore || $[10] !== runtimeState.mainThreadId || $[11] !== runtimeState.threadIds || $[12] !== t3 || $[13] !== threadItems.state) {
		t4 = {
			mainThreadId: runtimeState.mainThreadId,
			newThreadId: t3,
			isLoading: runtimeState.isLoading,
			isLoadingMore: runtimeState.isLoadingMore,
			hasMore: runtimeState.hasMore,
			threadIds: runtimeState.threadIds,
			archivedThreadIds: runtimeState.archivedThreadIds,
			threadItems: threadItems.state,
			main: main.state
		};
		$[5] = main.state;
		$[6] = runtimeState.archivedThreadIds;
		$[7] = runtimeState.hasMore;
		$[8] = runtimeState.isLoading;
		$[9] = runtimeState.isLoadingMore;
		$[10] = runtimeState.mainThreadId;
		$[11] = runtimeState.threadIds;
		$[12] = t3;
		$[13] = threadItems.state;
		$[14] = t4;
	} else t4 = $[14];
	const state = t4;
	let t5;
	if ($[15] !== state) {
		t5 = () => state;
		$[15] = state;
		$[16] = t5;
	} else t5 = $[16];
	let t6;
	if ($[17] !== main.methods) {
		t6 = () => main.methods;
		$[17] = main.methods;
		$[18] = t6;
	} else t6 = $[18];
	let t7;
	if ($[19] !== state || $[20] !== threadItems) {
		t7 = (threadIdOrOptions) => {
			if (threadIdOrOptions === "main") return threadItems.get({ key: state.mainThreadId });
			if ("id" in threadIdOrOptions) return threadItems.get({ key: threadIdOrOptions.id });
			const { index, archived: t8 } = threadIdOrOptions;
			const id_0 = (t8 === void 0 ? false : t8) ? state.archivedThreadIds[index] : state.threadIds[index];
			return threadItems.get({ key: id_0 });
		};
		$[19] = state;
		$[20] = threadItems;
		$[21] = t7;
	} else t7 = $[21];
	let t10;
	let t11;
	let t12;
	let t8;
	let t9;
	if ($[22] !== runtime) {
		t8 = async (threadId, options) => {
			await runtime.switchToThread(threadId, options);
		};
		t9 = async () => {
			await runtime.switchToNewThread();
		};
		t10 = () => runtime.getLoadThreadsPromise();
		t11 = () => runtime.reload();
		t12 = () => runtime.loadMore();
		$[22] = runtime;
		$[23] = t10;
		$[24] = t11;
		$[25] = t12;
		$[26] = t8;
		$[27] = t9;
	} else {
		t10 = $[23];
		t11 = $[24];
		t12 = $[25];
		t8 = $[26];
		t9 = $[27];
	}
	let t13;
	if ($[28] !== __internal_assistantRuntime) {
		t13 = () => __internal_assistantRuntime;
		$[28] = __internal_assistantRuntime;
		$[29] = t13;
	} else t13 = $[29];
	let t14;
	if ($[30] !== t10 || $[31] !== t11 || $[32] !== t12 || $[33] !== t13 || $[34] !== t5 || $[35] !== t6 || $[36] !== t7 || $[37] !== t8 || $[38] !== t9) {
		t14 = {
			getState: t5,
			thread: t6,
			item: t7,
			switchToThread: t8,
			switchToNewThread: t9,
			getLoadThreadsPromise: t10,
			reload: t11,
			loadMore: t12,
			__internal_getAssistantRuntime: t13
		};
		$[30] = t10;
		$[31] = t11;
		$[32] = t12;
		$[33] = t13;
		$[34] = t5;
		$[35] = t6;
		$[36] = t7;
		$[37] = t8;
		$[38] = t9;
		$[39] = t14;
	} else t14 = $[39];
	return t14;
};
var ThreadListClient = resource(useThreadListClient);
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/clients/suggestions.js
var useSuggestionClient = (state) => {
	return { getState: () => state };
};
var SuggestionClient = resource(useSuggestionClient);
var useSuggestionsResource = (suggestions) => {
	const $ = c(11);
	let t0;
	if ($[0] !== suggestions) {
		t0 = () => {
			return { suggestions: (suggestions ?? []).map(_temp$14) };
		};
		$[0] = suggestions;
		$[1] = t0;
	} else t0 = $[1];
	const [state] = useState(t0);
	let t1;
	if ($[2] !== state.suggestions) {
		t1 = state.suggestions.map(_temp2$3);
		$[2] = state.suggestions;
		$[3] = t1;
	} else t1 = $[3];
	const suggestionClients = useClientLookup(t1);
	let t2;
	if ($[4] !== state) {
		t2 = () => state;
		$[4] = state;
		$[5] = t2;
	} else t2 = $[5];
	let t3;
	if ($[6] !== suggestionClients) {
		t3 = (t4) => {
			const { index: index_0 } = t4;
			return suggestionClients.get({ index: index_0 });
		};
		$[6] = suggestionClients;
		$[7] = t3;
	} else t3 = $[7];
	let t4;
	if ($[8] !== t2 || $[9] !== t3) {
		t4 = {
			getState: t2,
			suggestion: t3
		};
		$[8] = t2;
		$[9] = t3;
		$[10] = t4;
	} else t4 = $[10];
	return t4;
};
var Suggestions = resource(useSuggestionsResource);
function _temp$14(s) {
	if (typeof s === "string") return {
		title: s,
		label: "",
		prompt: s
	};
	return {
		title: s.title,
		label: s.label,
		prompt: s.prompt
	};
}
function _temp2$3(suggestion, index) {
	return withKey(index, SuggestionClient(suggestion), [suggestion]);
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/clients/runtime-adapter.js
var baseRuntimeAdapterTransformScopes = (scopes, parent) => {
	scopes.thread ??= Derived({
		source: "threads",
		query: { type: "main" },
		get: (aui) => aui.threads().thread("main")
	});
	scopes.threadListItem ??= Derived({
		source: "threads",
		query: { type: "main" },
		get: (aui) => aui.threads().item("main")
	});
	scopes.composer ??= Derived({
		source: "thread",
		query: {},
		get: (aui) => aui.threads().thread("main").composer()
	});
	if (!scopes.modelContext && parent.modelContext.source === null) scopes.modelContext = ModelContext();
	if (!scopes.suggestions && parent.suggestions.source === null) scopes.suggestions = Suggestions();
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/RuntimeAdapter.js
var useRuntimeAdapter = (runtime) => {
	const $ = c(6);
	const clientRef = useAssistantClientRef();
	let t0;
	let t1;
	if ($[0] !== clientRef || $[1] !== runtime) {
		t0 = () => runtime.registerModelContextProvider(clientRef.current.modelContext());
		t1 = [runtime, clientRef];
		$[0] = clientRef;
		$[1] = runtime;
		$[2] = t0;
		$[3] = t1;
	} else {
		t0 = $[2];
		t1 = $[3];
	}
	useEffect(t0, t1);
	let t2;
	if ($[4] !== runtime) {
		t2 = ThreadListClient({
			runtime: runtime.threads,
			__internal_assistantRuntime: runtime
		});
		$[4] = runtime;
		$[5] = t2;
	} else t2 = $[5];
	return useResource(t2);
};
var RuntimeAdapter = resource(useRuntimeAdapter);
attachTransformScopes(useRuntimeAdapter, (scopes, parent) => {
	baseRuntimeAdapterTransformScopes(scopes, parent);
	if (!scopes.tools && parent.tools.source === null) scopes.tools = Tools({});
	if (!scopes.dataRenderers && parent.dataRenderers.source === null) scopes.dataRenderers = DataRenderers();
});
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/AssistantProvider.js
var getRenderComponent = (runtime) => {
	return runtime._core?.RenderComponent;
};
var AssistantProviderBase = (0, react_shim_exports.memo)(({ runtime, aui: parent = null, children }) => {
	"use no memo";
	const aui = useAui({ threads: RuntimeAdapter(runtime) }, { parent });
	const RenderComponent = getRenderComponent(runtime);
	const inner = /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(AuiProvider, {
		value: aui,
		children: [RenderComponent && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderComponent, {}), children]
	});
	if (!parent) return inner;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
		value: parent,
		children: inner
	});
});
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/providers/AttachmentByIndexProvider.js
var MessageAttachmentByIndexProvider = (t0) => {
	const $ = c(7);
	const { index, children } = t0;
	let t1;
	if ($[0] !== index) {
		t1 = Derived({
			source: "message",
			query: {
				type: "index",
				index
			},
			get: (aui) => aui.message().attachment({ index })
		});
		$[0] = index;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== t1) {
		t2 = { attachment: t1 };
		$[2] = t1;
		$[3] = t2;
	} else t2 = $[3];
	const aui_0 = useAui(t2);
	let t3;
	if ($[4] !== aui_0 || $[5] !== children) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui_0,
			children
		});
		$[4] = aui_0;
		$[5] = children;
		$[6] = t3;
	} else t3 = $[6];
	return t3;
};
var ComposerAttachmentByIndexProvider = (t0) => {
	const $ = c(7);
	const { index, children } = t0;
	let t1;
	if ($[0] !== index) {
		t1 = Derived({
			source: "composer",
			query: {
				type: "index",
				index
			},
			get: (aui) => aui.composer().attachment({ index })
		});
		$[0] = index;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== t1) {
		t2 = { attachment: t1 };
		$[2] = t1;
		$[3] = t2;
	} else t2 = $[3];
	const aui_0 = useAui(t2);
	let t3;
	if ($[4] !== aui_0 || $[5] !== children) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui_0,
			children
		});
		$[4] = aui_0;
		$[5] = children;
		$[6] = t3;
	} else t3 = $[6];
	return t3;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/providers/MessageByIndexProvider.js
var MessageByIndexProvider = (t0) => {
	const $ = c(10);
	const { index, children } = t0;
	let t1;
	if ($[0] !== index) {
		t1 = Derived({
			source: "thread",
			query: {
				type: "index",
				index
			},
			get: (aui) => aui.thread().message({ index })
		});
		$[0] = index;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== index) {
		t2 = Derived({
			source: "message",
			query: {},
			get: (aui_0) => aui_0.thread().message({ index }).composer()
		});
		$[2] = index;
		$[3] = t2;
	} else t2 = $[3];
	let t3;
	if ($[4] !== t1 || $[5] !== t2) {
		t3 = {
			message: t1,
			composer: t2
		};
		$[4] = t1;
		$[5] = t2;
		$[6] = t3;
	} else t3 = $[6];
	const aui_1 = useAui(t3);
	let t4;
	if ($[7] !== aui_1 || $[8] !== children) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui_1,
			children
		});
		$[7] = aui_1;
		$[8] = children;
		$[9] = t4;
	} else t4 = $[9];
	return t4;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/providers/PartByIndexProvider.js
var PartByIndexProvider = (t0) => {
	const $ = c(7);
	const { index, children } = t0;
	let t1;
	if ($[0] !== index) {
		t1 = Derived({
			source: "message",
			query: {
				type: "index",
				index
			},
			get: (aui) => aui.message().part({ index })
		});
		$[0] = index;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== t1) {
		t2 = { part: t1 };
		$[2] = t1;
		$[3] = t2;
	} else t2 = $[3];
	const aui_0 = useAui(t2);
	let t3;
	if ($[4] !== aui_0 || $[5] !== children) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui_0,
			children
		});
		$[4] = aui_0;
		$[5] = children;
		$[6] = t3;
	} else t3 = $[6];
	return t3;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/providers/TextMessagePartProvider.js
var useTextMessagePartClient = (t0) => {
	const $ = c(7);
	const { text, isRunning } = t0;
	let t1;
	if ($[0] !== isRunning) {
		t1 = isRunning ? { type: "running" } : { type: "complete" };
		$[0] = isRunning;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== t1 || $[3] !== text) {
		t2 = {
			type: "text",
			text,
			status: t1
		};
		$[2] = t1;
		$[3] = text;
		$[4] = t2;
	} else t2 = $[4];
	const state = t2;
	let t3;
	if ($[5] !== state) {
		t3 = {
			getState: () => state,
			addToolResult: _temp$13,
			resumeToolCall: _temp2$2,
			respondToToolApproval: _temp3$1
		};
		$[5] = state;
		$[6] = t3;
	} else t3 = $[6];
	return t3;
};
var TextMessagePartClient = resource(useTextMessagePartClient);
var TextMessagePartProvider = (t0) => {
	const $ = c(8);
	const { text, isRunning: t1, children } = t0;
	const isRunning = t1 === void 0 ? false : t1;
	let t2;
	if ($[0] !== isRunning || $[1] !== text) {
		t2 = TextMessagePartClient({
			text,
			isRunning
		});
		$[0] = isRunning;
		$[1] = text;
		$[2] = t2;
	} else t2 = $[2];
	let t3;
	if ($[3] !== t2) {
		t3 = { part: t2 };
		$[3] = t2;
		$[4] = t3;
	} else t3 = $[4];
	const aui = useAui(t3);
	let t4;
	if ($[5] !== aui || $[6] !== children) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui,
			children
		});
		$[5] = aui;
		$[6] = children;
		$[7] = t4;
	} else t4 = $[7];
	return t4;
};
function _temp$13() {
	throw new Error("Not supported");
}
function _temp2$2() {
	throw new Error("Not supported");
}
function _temp3$1() {
	throw new Error("Not supported");
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/clients/chain-of-thought-client.js
var COMPLETE_STATUS$3 = Object.freeze({ type: "complete" });
var useChainOfThoughtClient = (t0) => {
	const $ = c(9);
	const { parts, getMessagePart } = t0;
	const [collapsed, setCollapsed] = useState(true);
	const status = parts[parts.length - 1]?.status ?? COMPLETE_STATUS$3;
	let t1;
	if ($[0] !== collapsed || $[1] !== parts || $[2] !== status) {
		t1 = {
			parts,
			collapsed,
			status
		};
		$[0] = collapsed;
		$[1] = parts;
		$[2] = status;
		$[3] = t1;
	} else t1 = $[3];
	const state = t1;
	let t2;
	if ($[4] !== state) {
		t2 = () => state;
		$[4] = state;
		$[5] = t2;
	} else t2 = $[5];
	let t3;
	if ($[6] !== getMessagePart || $[7] !== t2) {
		t3 = {
			getState: t2,
			setCollapsed,
			part: getMessagePart
		};
		$[6] = getMessagePart;
		$[7] = t2;
		$[8] = t3;
	} else t3 = $[8];
	return t3;
};
var ChainOfThoughtClient = resource(useChainOfThoughtClient);
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/providers/ChainOfThoughtByIndicesProvider.js
var ChainOfThoughtByIndicesProvider = (t0) => {
	const $ = c(5);
	const { startIndex, endIndex, children } = t0;
	const parts = useAuiState(_temp$12).slice(startIndex, endIndex + 1);
	const parentAui = useAui();
	const t2 = ChainOfThoughtClient({
		parts,
		getMessagePart: (t1) => {
			const { index } = t1;
			if (index < 0 || index >= parts.length) throw new Error(`ChainOfThought part index ${index} is out of bounds (0..${parts.length - 1})`);
			return parentAui.message().part({ index: startIndex + index });
		}
	});
	let t3;
	if ($[0] !== t2) {
		t3 = { chainOfThought: t2 };
		$[0] = t2;
		$[1] = t3;
	} else t3 = $[1];
	const aui = useAui(t3);
	let t4;
	if ($[2] !== aui || $[3] !== children) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui,
			children
		});
		$[2] = aui;
		$[3] = children;
		$[4] = t4;
	} else t4 = $[4];
	return t4;
};
function _temp$12(s) {
	return s.message.parts;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/providers/SuggestionByIndexProvider.js
var SuggestionByIndexProvider = (t0) => {
	const $ = c(7);
	const { index, children } = t0;
	let t1;
	if ($[0] !== index) {
		t1 = Derived({
			source: "suggestions",
			query: { index },
			get: (aui) => aui.suggestions().suggestion({ index })
		});
		$[0] = index;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== t1) {
		t2 = { suggestion: t1 };
		$[2] = t1;
		$[3] = t2;
	} else t2 = $[3];
	const aui_0 = useAui(t2);
	let t3;
	if ($[4] !== aui_0 || $[5] !== children) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui_0,
			children
		});
		$[4] = aui_0;
		$[5] = children;
		$[6] = t3;
	} else t3 = $[6];
	return t3;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/providers/QueueItemByIndexProvider.js
var QueueItemByIndexProvider = (t0) => {
	const $ = c(7);
	const { index, children } = t0;
	let t1;
	if ($[0] !== index) {
		t1 = Derived({
			source: "composer",
			query: { index },
			get: (aui) => aui.composer().queueItem({ index })
		});
		$[0] = index;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== t1) {
		t2 = { queueItem: t1 };
		$[2] = t1;
		$[3] = t2;
	} else t2 = $[3];
	const aui_0 = useAui(t2);
	let t3;
	if ($[4] !== aui_0 || $[5] !== children) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AuiProvider, {
			value: aui_0,
			children
		});
		$[4] = aui_0;
		$[5] = children;
		$[6] = t3;
	} else t3 = $[6];
	return t3;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/utils/external-store-message.js
var symbolInnerMessage = Symbol("innerMessage");
var symbolInnerMessages = Symbol("innerMessages");
var EMPTY_ARRAY$3 = [];
/**
* Attach the original external store message(s) to a ThreadMessage or message part.
* This is a no-op if the target already has a bound message.
* Use `getExternalStoreMessages` to retrieve the bound messages later.
*
* @deprecated This API is experimental and may change without notice.
*/
var bindExternalStoreMessage = (target, message) => {
	if (symbolInnerMessage in target) return;
	target[symbolInnerMessage] = message;
};
var getExternalStoreMessages = (input) => {
	const container = "messages" in input ? input.messages : input;
	const value = container[symbolInnerMessages] || container[symbolInnerMessage];
	if (!value) return EMPTY_ARRAY$3;
	if (Array.isArray(value)) return value;
	container[symbolInnerMessages] = [value];
	return container[symbolInnerMessages];
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/subscribable/subscribable.js
var SKIP_UPDATE = Symbol("skip-update");
function shallowEqual$1(objA, objB) {
	if (objA === void 0 && objB === void 0) return true;
	if (objA === void 0) return false;
	if (objB === void 0) return false;
	for (const key of Object.keys(objA)) {
		const valueA = objA[key];
		const valueB = objB[key];
		if (!Object.is(valueA, valueB)) return false;
	}
	return true;
}
var BaseSubscribable = class {
	_subscribers = /* @__PURE__ */ new Set();
	subscribe(callback) {
		this._subscribers.add(callback);
		return () => this._subscribers.delete(callback);
	}
	waitForUpdate() {
		return new Promise((resolve) => {
			const unsubscribe = this.subscribe(() => {
				unsubscribe();
				resolve();
			});
		});
	}
	_notifySubscribers() {
		const errors = [];
		for (const callback of this._subscribers) try {
			callback();
		} catch (error) {
			errors.push(error);
		}
		if (errors.length > 0) if (errors.length === 1) throw errors[0];
		else {
			for (const error of errors) console.error(error);
			throw new AggregateError(errors);
		}
	}
};
var BaseSubject = class {
	_subscriptions = /* @__PURE__ */ new Set();
	_connection;
	get isConnected() {
		return !!this._connection;
	}
	notifySubscribers(payload) {
		for (const callback of this._subscriptions) callback(payload);
	}
	_updateConnection() {
		if (this._subscriptions.size > 0) {
			if (this._connection) return;
			this._connection = this._connect();
		} else {
			this._connection?.();
			this._connection = void 0;
		}
	}
	subscribe(callback) {
		this._subscriptions.add(callback);
		this._updateConnection();
		return () => {
			this._subscriptions.delete(callback);
			this._updateConnection();
		};
	}
};
var ShallowMemoizeSubject = class extends BaseSubject {
	binding;
	get path() {
		return this.binding.path;
	}
	constructor(binding) {
		super();
		this.binding = binding;
		const state = binding.getState();
		if (state === SKIP_UPDATE) throw new Error("Entry not available in the store");
		this._previousState = state;
	}
	_previousState;
	getState = () => {
		if (!this.isConnected) this._syncState();
		return this._previousState;
	};
	_syncState() {
		const state = this.binding.getState();
		if (state === SKIP_UPDATE) return false;
		if (shallowEqual$1(state, this._previousState)) return false;
		this._previousState = state;
		return true;
	}
	_connect() {
		const callback = () => {
			if (this._syncState()) this.notifySubscribers();
		};
		return this.binding.subscribe(callback);
	}
};
var LazyMemoizeSubject = class extends BaseSubject {
	binding;
	get path() {
		return this.binding.path;
	}
	constructor(binding) {
		super();
		this.binding = binding;
	}
	_previousStateDirty = true;
	_previousState;
	getState = () => {
		if (!this.isConnected || this._previousStateDirty) {
			const newState = this.binding.getState();
			if (newState !== SKIP_UPDATE) this._previousState = newState;
			this._previousStateDirty = false;
		}
		if (this._previousState === void 0) throw new Error("Entry not available in the store");
		return this._previousState;
	};
	_connect() {
		const callback = () => {
			this._previousStateDirty = true;
			this.notifySubscribers();
		};
		return this.binding.subscribe(callback);
	}
};
var NestedSubscriptionSubject = class extends BaseSubject {
	binding;
	get path() {
		return this.binding.path;
	}
	constructor(binding) {
		super();
		this.binding = binding;
	}
	getState() {
		return this.binding.getState();
	}
	outerSubscribe(callback) {
		return this.binding.subscribe(callback);
	}
	_connect() {
		const callback = () => {
			this.notifySubscribers();
		};
		let lastState = this.binding.getState();
		let innerUnsubscribe = lastState?.subscribe(callback);
		const onRuntimeUpdate = () => {
			const newState = this.binding.getState();
			if (newState === lastState) return;
			lastState = newState;
			innerUnsubscribe?.();
			innerUnsubscribe = newState?.subscribe(callback);
			callback();
		};
		const outerUnsubscribe = this.outerSubscribe(onRuntimeUpdate);
		return () => {
			outerUnsubscribe?.();
			innerUnsubscribe?.();
		};
	}
};
var EventSubscriptionSubject = class extends BaseSubject {
	config;
	constructor(config) {
		super();
		this.config = config;
	}
	getState() {
		return this.config.binding.getState();
	}
	outerSubscribe(callback) {
		return this.config.binding.subscribe(callback);
	}
	_connect() {
		const callback = (payload) => {
			this.notifySubscribers(payload);
		};
		let lastState = this.config.binding.getState();
		let innerUnsubscribe = lastState?.unstable_on(this.config.event, callback);
		const onRuntimeUpdate = () => {
			const newState = this.config.binding.getState();
			if (newState === lastState) return;
			lastState = newState;
			innerUnsubscribe?.();
			innerUnsubscribe = newState?.unstable_on(this.config.event, callback);
		};
		const outerUnsubscribe = this.outerSubscribe(onRuntimeUpdate);
		return () => {
			outerUnsubscribe?.();
			innerUnsubscribe?.();
		};
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/attachment-runtime.js
var AttachmentRuntimeImpl = class {
	_core;
	get path() {
		return this._core.path;
	}
	constructor(_core) {
		this._core = _core;
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		this.getState = this.getState.bind(this);
		this.remove = this.remove.bind(this);
		this.subscribe = this.subscribe.bind(this);
	}
	getState() {
		return this._core.getState();
	}
	subscribe(callback) {
		return this._core.subscribe(callback);
	}
};
var ComposerAttachmentRuntime = class extends AttachmentRuntimeImpl {
	_composerApi;
	constructor(core, _composerApi) {
		super(core);
		this._composerApi = _composerApi;
	}
	remove() {
		const core = this._composerApi.getState();
		if (!core) throw new Error("Composer is not available");
		return core.removeAttachment(this.getState().id);
	}
};
var ThreadComposerAttachmentRuntimeImpl = class extends ComposerAttachmentRuntime {
	get source() {
		return "thread-composer";
	}
};
var EditComposerAttachmentRuntimeImpl = class extends ComposerAttachmentRuntime {
	get source() {
		return "edit-composer";
	}
};
var MessageAttachmentRuntimeImpl = class extends AttachmentRuntimeImpl {
	get source() {
		return "message";
	}
	remove() {
		throw new Error("Message attachments cannot be removed");
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/composer-runtime.js
var EMPTY_ARRAY$2 = Object.freeze([]);
var EMPTY_OBJECT = Object.freeze({});
var getThreadComposerState = (runtime) => {
	return Object.freeze({
		type: "thread",
		isEditing: runtime?.isEditing ?? false,
		canCancel: runtime?.canCancel ?? false,
		canSend: runtime?.canSend ?? false,
		isEmpty: runtime?.isEmpty ?? true,
		attachments: runtime?.attachments ?? EMPTY_ARRAY$2,
		text: runtime?.text ?? "",
		role: runtime?.role ?? "user",
		runConfig: runtime?.runConfig ?? EMPTY_OBJECT,
		attachmentAccept: runtime?.attachmentAccept ?? "",
		dictation: runtime?.dictation,
		quote: runtime?.quote,
		queue: runtime?.queue ?? EMPTY_ARRAY$2,
		value: runtime?.text ?? ""
	});
};
var getEditComposerState = (runtime) => {
	return Object.freeze({
		type: "edit",
		isEditing: runtime?.isEditing ?? false,
		canCancel: runtime?.canCancel ?? false,
		canSend: runtime?.canSend ?? false,
		isEmpty: runtime?.isEmpty ?? true,
		text: runtime?.text ?? "",
		role: runtime?.role ?? "user",
		attachments: runtime?.attachments ?? EMPTY_ARRAY$2,
		runConfig: runtime?.runConfig ?? EMPTY_OBJECT,
		attachmentAccept: runtime?.attachmentAccept ?? "",
		dictation: runtime?.dictation,
		quote: runtime?.quote,
		queue: runtime?.queue ?? EMPTY_ARRAY$2,
		parentId: runtime?.parentId ?? null,
		sourceId: runtime?.sourceId ?? null,
		value: runtime?.text ?? ""
	});
};
var ComposerRuntimeImpl = class {
	_core;
	get path() {
		return this._core.path;
	}
	constructor(_core) {
		this._core = _core;
	}
	__internal_bindMethods() {
		this.setText = this.setText.bind(this);
		this.setRunConfig = this.setRunConfig.bind(this);
		this.getState = this.getState.bind(this);
		this.subscribe = this.subscribe.bind(this);
		this.addAttachment = this.addAttachment.bind(this);
		this.reset = this.reset.bind(this);
		this.clearAttachments = this.clearAttachments.bind(this);
		this.send = this.send.bind(this);
		this.cancel = this.cancel.bind(this);
		this.steerQueueItem = this.steerQueueItem.bind(this);
		this.removeQueueItem = this.removeQueueItem.bind(this);
		this.setRole = this.setRole.bind(this);
		this.getAttachmentByIndex = this.getAttachmentByIndex.bind(this);
		this.startDictation = this.startDictation.bind(this);
		this.stopDictation = this.stopDictation.bind(this);
		this.setQuote = this.setQuote.bind(this);
		this.unstable_on = this.unstable_on.bind(this);
	}
	setText(text) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.setText(text);
	}
	setRunConfig(runConfig) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.setRunConfig(runConfig);
	}
	addAttachment(fileOrAttachment) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		return core.addAttachment(fileOrAttachment);
	}
	reset() {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		return core.reset();
	}
	clearAttachments() {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		return core.clearAttachments();
	}
	send(options) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.send(options);
	}
	cancel() {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.cancel();
	}
	steerQueueItem(queueItemId) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.steerQueueItem(queueItemId);
	}
	removeQueueItem(queueItemId) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.removeQueueItem(queueItemId);
	}
	setRole(role) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.setRole(role);
	}
	startDictation() {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.startDictation();
	}
	stopDictation() {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.stopDictation();
	}
	setQuote(quote) {
		const core = this._core.getState();
		if (!core) throw new Error("Composer is not available");
		core.setQuote(quote);
	}
	subscribe(callback) {
		return this._core.subscribe(callback);
	}
	_eventSubscriptionSubjects = /* @__PURE__ */ new Map();
	unstable_on(event, callback) {
		let subject = this._eventSubscriptionSubjects.get(event);
		if (!subject) {
			subject = new EventSubscriptionSubject({
				event,
				binding: this._core
			});
			this._eventSubscriptionSubjects.set(event, subject);
		}
		return subject.subscribe(callback);
	}
};
var ThreadComposerRuntimeImpl = class extends ComposerRuntimeImpl {
	get path() {
		return this._core.path;
	}
	get type() {
		return "thread";
	}
	_getState;
	constructor(core) {
		const stateBinding = new LazyMemoizeSubject({
			path: core.path,
			getState: () => getThreadComposerState(core.getState()),
			subscribe: (callback) => core.subscribe(callback)
		});
		super({
			path: core.path,
			getState: () => core.getState(),
			subscribe: (callback) => stateBinding.subscribe(callback)
		});
		this._getState = stateBinding.getState.bind(stateBinding);
		this.__internal_bindMethods();
	}
	getState() {
		return this._getState();
	}
	getAttachmentByIndex(idx) {
		return new ThreadComposerAttachmentRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				...this.path,
				attachmentSource: "thread-composer",
				attachmentSelector: {
					type: "index",
					index: idx
				},
				ref: `${this.path.ref}.attachments[${idx}]`
			},
			getState: () => {
				const attachment = this.getState().attachments[idx];
				if (!attachment) return SKIP_UPDATE;
				return {
					...attachment,
					source: "thread-composer"
				};
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core);
	}
};
var EditComposerRuntimeImpl = class extends ComposerRuntimeImpl {
	_beginEdit;
	get path() {
		return this._core.path;
	}
	get type() {
		return "edit";
	}
	_getState;
	constructor(core, _beginEdit) {
		const stateBinding = new LazyMemoizeSubject({
			path: core.path,
			getState: () => getEditComposerState(core.getState()),
			subscribe: (callback) => core.subscribe(callback)
		});
		super({
			path: core.path,
			getState: () => core.getState(),
			subscribe: (callback) => stateBinding.subscribe(callback)
		});
		this._beginEdit = _beginEdit;
		this._getState = stateBinding.getState.bind(stateBinding);
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		super.__internal_bindMethods();
		this.beginEdit = this.beginEdit.bind(this);
	}
	getState() {
		return this._getState();
	}
	beginEdit() {
		this._beginEdit();
	}
	getAttachmentByIndex(idx) {
		return new EditComposerAttachmentRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				...this.path,
				attachmentSource: "edit-composer",
				attachmentSelector: {
					type: "index",
					index: idx
				},
				ref: `${this.path.ref}.attachments[${idx}]`
			},
			getState: () => {
				const attachment = this.getState().attachments[idx];
				if (!attachment) return SKIP_UPDATE;
				return {
					...attachment,
					source: "edit-composer"
				};
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/utils/text.js
var getThreadMessageText = (message) => {
	return message.content.filter((part) => part.type === "text").map((part) => part.text).join("\n\n");
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/utils/resolveToolApprovalResponse.js
var APPROVED_BY_KIND = {
	"allow-once": true,
	"allow-always": true,
	"reject-once": false,
	"reject-always": false
};
/**
* Resolves a renderer-facing approval response (boolean or optionId form)
* against the approval's option list into the runtime-facing decision shape.
*/
var resolveToolApprovalResponse = (approval, response) => {
	let approved;
	let optionId;
	if ("optionId" in response) {
		const option = approval.options?.find((o) => o.id === response.optionId);
		if (!option) throw new Error(`Tool approval has no option with id "${response.optionId}"`);
		if ("approved" in response) approved = response.approved;
		else {
			if (!Object.hasOwn(APPROVED_BY_KIND, option.kind)) throw new Error(`Tool approval option "${option.id}" has a custom kind "${option.kind}"; respond with an explicit approved value instead`);
			approved = APPROVED_BY_KIND[option.kind];
		}
		optionId = option.id;
	} else approved = response.approved;
	return {
		approvalId: approval.id,
		approved,
		...optionId !== void 0 && { optionId },
		...response.reason != null && { reason: response.reason }
	};
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/message-part-runtime.js
var MessagePartRuntimeImpl = class {
	contentBinding;
	messageApi;
	threadApi;
	get path() {
		return this.contentBinding.path;
	}
	constructor(contentBinding, messageApi, threadApi) {
		this.contentBinding = contentBinding;
		this.messageApi = messageApi;
		this.threadApi = threadApi;
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		this.addToolResult = this.addToolResult.bind(this);
		this.resumeToolCall = this.resumeToolCall.bind(this);
		this.respondToToolApproval = this.respondToToolApproval.bind(this);
		this.getState = this.getState.bind(this);
		this.subscribe = this.subscribe.bind(this);
	}
	getState() {
		return this.contentBinding.getState();
	}
	addToolResult(result) {
		const state = this.contentBinding.getState();
		if (!state) throw new Error("Message part is not available");
		if (state.type !== "tool-call") throw new Error("Tried to add tool result to non-tool message part");
		if (!this.messageApi) throw new Error("Message API is not available. This is likely a bug in assistant-ui.");
		if (!this.threadApi) throw new Error("Thread API is not available");
		const message = this.messageApi.getState();
		if (!message) throw new Error("Message is not available");
		const toolName = state.toolName;
		const toolCallId = state.toolCallId;
		const response = ToolResponse.toResponse(result);
		this.threadApi.getState().addToolResult({
			messageId: message.id,
			toolName,
			toolCallId,
			result: response.result,
			artifact: response.artifact,
			isError: response.isError
		});
	}
	resumeToolCall(payload) {
		const state = this.contentBinding.getState();
		if (!state) throw new Error("Message part is not available");
		if (state.type !== "tool-call") throw new Error("Tried to resume tool call on non-tool message part");
		if (!this.threadApi) throw new Error("Thread API is not available");
		const toolCallId = state.toolCallId;
		this.threadApi.getState().resumeToolCall({
			toolCallId,
			payload
		});
	}
	respondToToolApproval(response) {
		const state = this.contentBinding.getState();
		if (!state) throw new Error("Message part is not available");
		if (state.type !== "tool-call") throw new Error("Tried to respond to tool approval on non-tool message part");
		if (!state.approval || state.approval.approved !== void 0 || state.approval.resolution !== void 0) throw new Error("Tool call has no pending approval");
		if (!this.threadApi) throw new Error("Thread API is not available");
		this.threadApi.getState().respondToToolApproval(resolveToolApprovalResponse(state.approval, response));
	}
	subscribe(callback) {
		return this.contentBinding.subscribe(callback);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/message-runtime.js
var COMPLETE_STATUS$2 = Object.freeze({ type: "complete" });
var toMessagePartStatus = (message, partIndex, part) => {
	if (message.role !== "assistant") return COMPLETE_STATUS$2;
	if (part.type === "tool-call") if (!part.result) return message.status;
	else return COMPLETE_STATUS$2;
	const isLastPart = partIndex === Math.max(0, message.content.length - 1);
	if (message.status.type === "requires-action") return COMPLETE_STATUS$2;
	return isLastPart ? message.status : COMPLETE_STATUS$2;
};
var getMessagePartState = (message, partIndex) => {
	const part = message.content[partIndex];
	if (!part) return SKIP_UPDATE;
	const status = toMessagePartStatus(message, partIndex, part);
	return Object.freeze({
		...part,
		[symbolInnerMessage]: part[symbolInnerMessage],
		status
	});
};
var MessageRuntimeImpl = class {
	_core;
	_threadBinding;
	get path() {
		return this._core.path;
	}
	constructor(_core, _threadBinding) {
		this._core = _core;
		this._threadBinding = _threadBinding;
		this.composer = new EditComposerRuntimeImpl(new NestedSubscriptionSubject({
			path: {
				...this.path,
				ref: `${this.path.ref}${this.path.ref}.composer`,
				composerSource: "edit"
			},
			getState: this._getEditComposerRuntimeCore,
			subscribe: (callback) => this._threadBinding.subscribe(callback)
		}), () => this._threadBinding.getState().beginEdit(this._core.getState().id));
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		this.reload = this.reload.bind(this);
		this.delete = this.delete.bind(this);
		this.getState = this.getState.bind(this);
		this.subscribe = this.subscribe.bind(this);
		this.getMessagePartByIndex = this.getMessagePartByIndex.bind(this);
		this.getMessagePartByToolCallId = this.getMessagePartByToolCallId.bind(this);
		this.getAttachmentByIndex = this.getAttachmentByIndex.bind(this);
		this.unstable_getCopyText = this.unstable_getCopyText.bind(this);
		this.speak = this.speak.bind(this);
		this.stopSpeaking = this.stopSpeaking.bind(this);
		this.submitFeedback = this.submitFeedback.bind(this);
		this.switchToBranch = this.switchToBranch.bind(this);
	}
	composer;
	_getEditComposerRuntimeCore = () => {
		return this._threadBinding.getState().getEditComposer(this._core.getState().id);
	};
	getState() {
		return this._core.getState();
	}
	delete() {
		const state = this._core.getState();
		return this._threadBinding.getState().deleteMessage(state.id);
	}
	reload(reloadConfig = {}) {
		const editComposerRuntimeCore = this._getEditComposerRuntimeCore();
		const composerRuntimeCore = editComposerRuntimeCore ?? this._threadBinding.getState().composer;
		const composer = editComposerRuntimeCore ?? composerRuntimeCore;
		const { runConfig = composer.runConfig } = reloadConfig;
		const state = this._core.getState();
		if (state.role !== "assistant") throw new Error("Can only reload assistant messages");
		this._threadBinding.getState().startRun({
			parentId: state.parentId,
			sourceId: state.id,
			runConfig
		});
	}
	speak() {
		const state = this._core.getState();
		return this._threadBinding.getState().speak(state.id);
	}
	stopSpeaking() {
		const state = this._core.getState();
		if (this._threadBinding.getState().speech?.messageId === state.id) this._threadBinding.getState().stopSpeaking();
		else throw new Error("Message is not being spoken");
	}
	submitFeedback({ type }) {
		const state = this._core.getState();
		this._threadBinding.getState().submitFeedback({
			messageId: state.id,
			type
		});
	}
	switchToBranch({ position, branchId }) {
		const state = this._core.getState();
		if (branchId && position) throw new Error("May not specify both branchId and position");
		else if (!branchId && !position) throw new Error("Must specify either branchId or position");
		const branches = this._threadBinding.getState().getBranches(state.id);
		let targetBranch = branchId;
		if (position === "previous") targetBranch = branches[state.branchNumber - 2];
		else if (position === "next") targetBranch = branches[state.branchNumber];
		if (!targetBranch) throw new Error("Branch not found");
		this._threadBinding.getState().switchToBranch(targetBranch);
	}
	unstable_getCopyText() {
		return getThreadMessageText(this.getState());
	}
	subscribe(callback) {
		return this._core.subscribe(callback);
	}
	getMessagePartByIndex(idx) {
		if (idx < 0) throw new Error("Message part index must be >= 0");
		return new MessagePartRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				...this.path,
				ref: `${this.path.ref}${this.path.ref}.content[${idx}]`,
				messagePartSelector: {
					type: "index",
					index: idx
				}
			},
			getState: () => {
				return getMessagePartState(this.getState(), idx);
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core, this._threadBinding);
	}
	getMessagePartByToolCallId(toolCallId) {
		return new MessagePartRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				...this.path,
				ref: this.path.ref + `${this.path.ref}.content[toolCallId=${JSON.stringify(toolCallId)}]`,
				messagePartSelector: {
					type: "toolCallId",
					toolCallId
				}
			},
			getState: () => {
				const state = this._core.getState();
				const idx = state.content.findIndex((part) => part.type === "tool-call" && part.toolCallId === toolCallId);
				if (idx === -1) return SKIP_UPDATE;
				return getMessagePartState(state, idx);
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core, this._threadBinding);
	}
	getAttachmentByIndex(idx) {
		return new MessageAttachmentRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				...this.path,
				ref: `${this.path.ref}${this.path.ref}.attachments[${idx}]`,
				attachmentSource: "message",
				attachmentSelector: {
					type: "index",
					index: idx
				}
			},
			getState: () => {
				const attachment = this.getState().attachments?.[idx];
				if (!attachment) return SKIP_UPDATE;
				return {
					...attachment,
					source: "message"
				};
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}));
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/thread-runtime.js
var toResumeRunConfig = (message) => {
	return {
		parentId: message.parentId ?? null,
		sourceId: message.sourceId ?? null,
		runConfig: message.runConfig ?? {},
		...message.stream ? { stream: message.stream } : {}
	};
};
var toStartRunConfig = (message) => {
	return {
		parentId: message.parentId ?? null,
		sourceId: message.sourceId ?? null,
		runConfig: message.runConfig ?? {}
	};
};
var toAppendMessage = (messages, message) => {
	if (typeof message === "string") return {
		createdAt: /* @__PURE__ */ new Date(),
		parentId: messages.at(-1)?.id ?? null,
		sourceId: null,
		runConfig: {},
		role: "user",
		content: [{
			type: "text",
			text: message
		}],
		attachments: [],
		metadata: { custom: {} }
	};
	return {
		createdAt: message.createdAt ?? /* @__PURE__ */ new Date(),
		parentId: message.parentId ?? messages.at(-1)?.id ?? null,
		sourceId: message.sourceId ?? null,
		role: message.role ?? "user",
		content: message.content,
		attachments: message.attachments ?? [],
		metadata: message.metadata ?? { custom: {} },
		runConfig: message.runConfig ?? {},
		startRun: message.startRun
	};
};
var getThreadState = (runtime, threadListItemState) => {
	const lastMessage = runtime.messages.at(-1);
	return Object.freeze({
		threadId: threadListItemState.id,
		metadata: threadListItemState,
		capabilities: runtime.capabilities,
		isDisabled: runtime.isDisabled,
		isLoading: runtime.isLoading,
		isRunning: runtime.isRunning ?? (lastMessage?.role !== "assistant" ? false : lastMessage.status.type === "running"),
		messages: runtime.messages,
		state: runtime.state,
		suggestions: runtime.suggestions,
		extras: runtime.extras,
		speech: runtime.speech,
		voice: runtime.voice
	});
};
var ThreadRuntimeImpl = class {
	get path() {
		return this._threadBinding.path;
	}
	get __internal_threadBinding() {
		return this._threadBinding;
	}
	_threadBinding;
	constructor(threadBinding, threadListItemBinding) {
		const stateBinding = new ShallowMemoizeSubject({
			path: threadBinding.path,
			getState: () => getThreadState(threadBinding.getState(), threadListItemBinding.getState()),
			subscribe: (callback) => {
				const sub1 = threadBinding.subscribe(callback);
				const sub2 = threadListItemBinding.subscribe(callback);
				return () => {
					sub1();
					sub2();
				};
			}
		});
		this._threadBinding = {
			path: threadBinding.path,
			getState: () => threadBinding.getState(),
			getStateState: () => stateBinding.getState(),
			outerSubscribe: (callback) => threadBinding.outerSubscribe(callback),
			subscribe: (callback) => threadBinding.subscribe(callback)
		};
		this.composer = new ThreadComposerRuntimeImpl(new NestedSubscriptionSubject({
			path: {
				...this.path,
				ref: `${this.path.ref}.composer`,
				composerSource: "thread"
			},
			getState: () => this._threadBinding.getState().composer,
			subscribe: (callback) => this._threadBinding.subscribe(callback)
		}));
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		this.append = this.append.bind(this);
		this.deleteMessage = this.deleteMessage.bind(this);
		this.resumeRun = this.resumeRun.bind(this);
		this.importExternalState = this.importExternalState.bind(this);
		this.exportExternalState = this.exportExternalState.bind(this);
		this.startRun = this.startRun.bind(this);
		this.cancelRun = this.cancelRun.bind(this);
		this.stopSpeaking = this.stopSpeaking.bind(this);
		this.connectVoice = this.connectVoice.bind(this);
		this.disconnectVoice = this.disconnectVoice.bind(this);
		this.muteVoice = this.muteVoice.bind(this);
		this.unmuteVoice = this.unmuteVoice.bind(this);
		this.getVoiceVolume = this.getVoiceVolume.bind(this);
		this.subscribeVoiceVolume = this.subscribeVoiceVolume.bind(this);
		this.export = this.export.bind(this);
		this.import = this.import.bind(this);
		this.reset = this.reset.bind(this);
		this.getMessageByIndex = this.getMessageByIndex.bind(this);
		this.getMessageById = this.getMessageById.bind(this);
		this.subscribe = this.subscribe.bind(this);
		this.unstable_on = this.unstable_on.bind(this);
		this.getModelContext = this.getModelContext.bind(this);
		this.getState = this.getState.bind(this);
	}
	composer;
	getState() {
		return this._threadBinding.getStateState();
	}
	append(message) {
		this._threadBinding.getState().append(toAppendMessage(this._threadBinding.getState().messages, message));
	}
	deleteMessage(messageId) {
		return this._threadBinding.getState().deleteMessage(messageId);
	}
	subscribe(callback) {
		return this._threadBinding.subscribe(callback);
	}
	getModelContext() {
		return this._threadBinding.getState().getModelContext();
	}
	startRun(config) {
		return this._threadBinding.getState().startRun(toStartRunConfig(config));
	}
	resumeRun(config) {
		return this._threadBinding.getState().resumeRun(toResumeRunConfig(config));
	}
	exportExternalState() {
		return this._threadBinding.getState().exportExternalState();
	}
	importExternalState(state) {
		this._threadBinding.getState().importExternalState(state);
	}
	cancelRun() {
		this._threadBinding.getState().cancelRun();
	}
	stopSpeaking() {
		return this._threadBinding.getState().stopSpeaking();
	}
	connectVoice() {
		this._threadBinding.getState().connectVoice();
	}
	disconnectVoice() {
		this._threadBinding.getState().disconnectVoice();
	}
	getVoiceVolume() {
		return this._threadBinding.getState().getVoiceVolume();
	}
	subscribeVoiceVolume(callback) {
		return this._threadBinding.getState().subscribeVoiceVolume(callback);
	}
	muteVoice() {
		this._threadBinding.getState().muteVoice();
	}
	unmuteVoice() {
		this._threadBinding.getState().unmuteVoice();
	}
	export() {
		return this._threadBinding.getState().export();
	}
	import(data) {
		this._threadBinding.getState().import(data);
	}
	reset(initialMessages) {
		this._threadBinding.getState().reset(initialMessages);
	}
	getMessageByIndex(idx) {
		if (idx < 0) throw new Error("Message index must be >= 0");
		return this._getMessageRuntime({
			...this.path,
			ref: `${this.path.ref}.messages[${idx}]`,
			messageSelector: {
				type: "index",
				index: idx
			}
		}, () => {
			const messages = this._threadBinding.getState().messages;
			const message = messages[idx];
			if (!message) return void 0;
			return {
				message,
				parentId: messages[idx - 1]?.id ?? null,
				index: idx
			};
		});
	}
	getMessageById(messageId) {
		return this._getMessageRuntime({
			...this.path,
			ref: `${this.path.ref}.messages[messageId=${JSON.stringify(messageId)}]`,
			messageSelector: {
				type: "messageId",
				messageId
			}
		}, () => this._threadBinding.getState().getMessageById(messageId));
	}
	_getMessageRuntime(path, callback) {
		return new MessageRuntimeImpl(new ShallowMemoizeSubject({
			path,
			getState: () => {
				const { message, parentId, index } = callback() ?? {};
				const { messages, speech: speechState } = this._threadBinding.getState();
				if (!message || parentId === void 0 || index === void 0) return SKIP_UPDATE;
				const branches = this._threadBinding.getState().getBranches(message.id);
				return {
					...message,
					[symbolInnerMessage]: message[symbolInnerMessage],
					index,
					isLast: messages.at(-1)?.id === message.id,
					parentId,
					branchNumber: branches.indexOf(message.id) + 1,
					branchCount: branches.length,
					speech: speechState?.messageId === message.id ? speechState : void 0
				};
			},
			subscribe: (callback) => this._threadBinding.subscribe(callback)
		}), this._threadBinding);
	}
	_eventSubscriptionSubjects = /* @__PURE__ */ new Map();
	unstable_on(event, callback) {
		let subject = this._eventSubscriptionSubjects.get(event);
		if (!subject) {
			subject = new EventSubscriptionSubject({
				event,
				binding: this._threadBinding
			});
			this._eventSubscriptionSubjects.set(event, subject);
		}
		return subject.subscribe(callback);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/runtimes/RuntimeAdapterProvider.js
var RuntimeAdaptersContext = createContext(null);
var useRuntimeAdapters = () => {
	return useContext(RuntimeAdaptersContext);
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/thread-list-item-runtime.js
var ThreadListItemRuntimeImpl = class {
	_core;
	_threadListBinding;
	get path() {
		return this._core.path;
	}
	constructor(_core, _threadListBinding) {
		this._core = _core;
		this._threadListBinding = _threadListBinding;
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		this.switchTo = this.switchTo.bind(this);
		this.rename = this.rename.bind(this);
		this.updateCustom = this.updateCustom.bind(this);
		this.archive = this.archive.bind(this);
		this.unarchive = this.unarchive.bind(this);
		this.delete = this.delete.bind(this);
		this.initialize = this.initialize.bind(this);
		this.generateTitle = this.generateTitle.bind(this);
		this.subscribe = this.subscribe.bind(this);
		this.unstable_on = this.unstable_on.bind(this);
		this.getState = this.getState.bind(this);
		this.detach = this.detach.bind(this);
	}
	getState() {
		return this._core.getState();
	}
	switchTo(options) {
		const state = this._core.getState();
		return this._threadListBinding.switchToThread(state.id, options);
	}
	rename(newTitle) {
		const state = this._core.getState();
		return this._threadListBinding.rename(state.id, newTitle);
	}
	updateCustom(custom) {
		const state = this._core.getState();
		if (!this._threadListBinding.updateCustom) throw new Error("Thread list runtime does not support updating custom metadata");
		return this._threadListBinding.updateCustom(state.id, custom);
	}
	archive() {
		const state = this._core.getState();
		return this._threadListBinding.archive(state.id);
	}
	unarchive() {
		const state = this._core.getState();
		return this._threadListBinding.unarchive(state.id);
	}
	delete() {
		const state = this._core.getState();
		return this._threadListBinding.delete(state.id);
	}
	initialize() {
		const state = this._core.getState();
		return this._threadListBinding.initialize(state.id);
	}
	generateTitle() {
		const state = this._core.getState();
		return this._threadListBinding.generateTitle(state.id);
	}
	unstable_on(event, callback) {
		let prevIsMain = this._core.getState().isMain;
		let prevThreadId = this._core.getState().id;
		return this.subscribe(() => {
			const currentState = this._core.getState();
			const newIsMain = currentState.isMain;
			const newThreadId = currentState.id;
			if (prevIsMain === newIsMain && prevThreadId === newThreadId) return;
			prevIsMain = newIsMain;
			prevThreadId = newThreadId;
			if (event === "switchedTo" && !newIsMain) return;
			if (event === "switchedAway" && newIsMain) return;
			callback({});
		});
	}
	subscribe(callback) {
		return this._core.subscribe(callback);
	}
	detach() {
		const state = this._core.getState();
		this._threadListBinding.detach(state.id);
	}
	__internal_getRuntime() {
		return this;
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/thread-list-runtime.js
var RESOLVED_PROMISE$1 = Promise.resolve();
var getThreadListState = (threadList) => {
	return {
		mainThreadId: threadList.mainThreadId,
		newThreadId: threadList.newThreadId,
		threadIds: threadList.threadIds,
		archivedThreadIds: threadList.archivedThreadIds,
		isLoading: threadList.isLoading,
		isLoadingMore: threadList.isLoadingMore ?? false,
		hasMore: threadList.hasMore ?? false,
		threadItems: threadList.threadItems
	};
};
var getThreadListItemState = (threadList, threadId) => {
	if (threadId === void 0) return SKIP_UPDATE;
	const threadData = threadList.getItemById(threadId);
	if (!threadData) return SKIP_UPDATE;
	return {
		id: threadData.id,
		remoteId: threadData.remoteId,
		externalId: threadData.externalId,
		title: threadData.title,
		status: threadData.status,
		lastMessageAt: threadData.lastMessageAt,
		custom: threadData.custom,
		isMain: threadData.id === threadList.mainThreadId
	};
};
var ThreadListRuntimeImpl = class {
	_core;
	_runtimeFactory;
	_getState;
	constructor(_core, _runtimeFactory = ThreadRuntimeImpl) {
		this._core = _core;
		this._runtimeFactory = _runtimeFactory;
		const stateBinding = new LazyMemoizeSubject({
			path: {},
			getState: () => getThreadListState(_core),
			subscribe: (callback) => _core.subscribe(callback)
		});
		this._getState = stateBinding.getState.bind(stateBinding);
		this._mainThreadListItemRuntime = new ThreadListItemRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				ref: `threadItems[main]`,
				threadSelector: { type: "main" }
			},
			getState: () => {
				return getThreadListItemState(this._core, this._core.mainThreadId);
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core);
		this.main = new _runtimeFactory(new NestedSubscriptionSubject({
			path: {
				ref: "threads.main",
				threadSelector: { type: "main" }
			},
			getState: () => _core.getMainThreadRuntimeCore(),
			subscribe: (callback) => _core.subscribe(callback)
		}), this._mainThreadListItemRuntime);
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		this.switchToThread = this.switchToThread.bind(this);
		this.switchToNewThread = this.switchToNewThread.bind(this);
		this.getLoadThreadsPromise = this.getLoadThreadsPromise.bind(this);
		this.reload = this.reload.bind(this);
		this.loadMore = this.loadMore.bind(this);
		this.getState = this.getState.bind(this);
		this.subscribe = this.subscribe.bind(this);
		this.getById = this.getById.bind(this);
		this.getItemById = this.getItemById.bind(this);
		this.getItemByIndex = this.getItemByIndex.bind(this);
		this.getArchivedItemByIndex = this.getArchivedItemByIndex.bind(this);
	}
	switchToThread(threadId, options) {
		return this._core.switchToThread(threadId, options);
	}
	switchToNewThread() {
		return this._core.switchToNewThread();
	}
	getLoadThreadsPromise() {
		return this._core.getLoadThreadsPromise();
	}
	reload() {
		return this._core.reload?.() ?? RESOLVED_PROMISE$1;
	}
	loadMore() {
		return this._core.loadMore?.() ?? RESOLVED_PROMISE$1;
	}
	getState() {
		return this._getState();
	}
	subscribe(callback) {
		return this._core.subscribe(callback);
	}
	_mainThreadListItemRuntime;
	main;
	get mainItem() {
		return this._mainThreadListItemRuntime;
	}
	getById(threadId) {
		return new this._runtimeFactory(new NestedSubscriptionSubject({
			path: {
				ref: `threads[threadId=${JSON.stringify(threadId)}]`,
				threadSelector: {
					type: "threadId",
					threadId
				}
			},
			getState: () => this._core.getThreadRuntimeCore(threadId),
			subscribe: (callback) => this._core.subscribe(callback)
		}), this.mainItem);
	}
	getItemByIndex(idx) {
		return new ThreadListItemRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				ref: `threadItems[${idx}]`,
				threadSelector: {
					type: "index",
					index: idx
				}
			},
			getState: () => {
				return getThreadListItemState(this._core, this._core.threadIds[idx]);
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core);
	}
	getArchivedItemByIndex(idx) {
		return new ThreadListItemRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				ref: `archivedThreadItems[${idx}]`,
				threadSelector: {
					type: "archiveIndex",
					index: idx
				}
			},
			getState: () => {
				return getThreadListItemState(this._core, this._core.archivedThreadIds[idx]);
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core);
	}
	getItemById(threadId) {
		return new ThreadListItemRuntimeImpl(new ShallowMemoizeSubject({
			path: {
				ref: `threadItems[threadId=${threadId}]`,
				threadSelector: {
					type: "threadId",
					threadId
				}
			},
			getState: () => {
				return getThreadListItemState(this._core, threadId);
			},
			subscribe: (callback) => this._core.subscribe(callback)
		}), this._core);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/api/assistant-runtime.js
var AssistantRuntimeImpl = class {
	_core;
	threads;
	_thread;
	constructor(_core) {
		this._core = _core;
		this.threads = new ThreadListRuntimeImpl(_core.threads);
		this._thread = this.threads.main;
		this.__internal_bindMethods();
	}
	__internal_bindMethods() {
		this.registerModelContextProvider = this.registerModelContextProvider.bind(this);
	}
	get thread() {
		return this._thread;
	}
	registerModelContextProvider(provider) {
		return this._core.registerModelContextProvider(provider);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/base/base-assistant-runtime-core.js
var BaseAssistantRuntimeCore = class {
	_contextProvider = new CompositeContextProvider();
	registerModelContextProvider(provider) {
		return this._contextProvider.registerModelContextProvider(provider);
	}
	getModelContextProvider() {
		return this._contextProvider;
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtimes/external-store/external-store-thread-list-runtime-core.js
var EMPTY_ARRAY$1 = Object.freeze([]);
var DEFAULT_THREAD_ID = "DEFAULT_THREAD_ID";
var DEFAULT_THREADS = Object.freeze([DEFAULT_THREAD_ID]);
var DEFAULT_THREAD = Object.freeze({
	id: DEFAULT_THREAD_ID,
	remoteId: void 0,
	externalId: void 0,
	status: "regular"
});
var RESOLVED_PROMISE = Promise.resolve();
var DEFAULT_THREAD_DATA = Object.freeze({ [DEFAULT_THREAD_ID]: DEFAULT_THREAD });
var ExternalStoreThreadListRuntimeCore = class {
	threadFactory;
	_mainThreadId = DEFAULT_THREAD_ID;
	_threads = DEFAULT_THREADS;
	_archivedThreads = EMPTY_ARRAY$1;
	_threadData = DEFAULT_THREAD_DATA;
	adapter = {};
	get isLoading() {
		return this.adapter.isLoading ?? false;
	}
	get newThreadId() {}
	get threadIds() {
		return this._threads;
	}
	get archivedThreadIds() {
		return this._archivedThreads;
	}
	get threadItems() {
		return this._threadData;
	}
	getLoadThreadsPromise() {
		return RESOLVED_PROMISE;
	}
	_mainThread;
	get mainThreadId() {
		return this._mainThreadId;
	}
	constructor(adapter = {}, threadFactory) {
		this.threadFactory = threadFactory;
		this.__internal_setAdapter(adapter, true);
	}
	getMainThreadRuntimeCore() {
		return this._mainThread;
	}
	getThreadRuntimeCore() {
		throw new Error("Method not implemented.");
	}
	getItemById(threadId) {
		return this._threadData[threadId];
	}
	__internal_setAdapter(adapter, initialLoad = false) {
		const previousAdapter = this.adapter;
		this.adapter = adapter;
		const newThreadId = adapter.threadId ?? DEFAULT_THREAD_ID;
		const newThreads = adapter.threads ?? EMPTY_ARRAY$1;
		const newArchivedThreads = adapter.archivedThreads ?? EMPTY_ARRAY$1;
		const previousThreadId = previousAdapter.threadId ?? DEFAULT_THREAD_ID;
		const previousThreads = previousAdapter.threads ?? EMPTY_ARRAY$1;
		const previousArchivedThreads = previousAdapter.archivedThreads ?? EMPTY_ARRAY$1;
		if (!initialLoad && previousThreadId === newThreadId && previousThreads === newThreads && previousArchivedThreads === newArchivedThreads) return;
		if (previousThreads !== newThreads || previousArchivedThreads !== newArchivedThreads || previousThreadId !== newThreadId) this._threadData = {
			...DEFAULT_THREAD_DATA,
			...Object.fromEntries(adapter.threads?.map((t) => [t.id, {
				...t,
				remoteId: t.remoteId,
				externalId: t.externalId,
				status: "regular"
			}]) ?? []),
			...Object.fromEntries(adapter.archivedThreads?.map((t) => [t.id, {
				...t,
				remoteId: t.remoteId,
				externalId: t.externalId,
				status: "archived"
			}]) ?? [])
		};
		if (previousThreads !== newThreads) this._threads = this.adapter.threads?.map((t) => t.id) ?? EMPTY_ARRAY$1;
		if (previousArchivedThreads !== newArchivedThreads) this._archivedThreads = this.adapter.archivedThreads?.map((t) => t.id) ?? EMPTY_ARRAY$1;
		if (initialLoad || previousThreadId !== newThreadId) {
			this._mainThreadId = newThreadId;
			this._mainThread = this.threadFactory();
		}
		if (!this._threadData[this._mainThreadId]) this._threadData = {
			...this._threadData,
			[this._mainThreadId]: {
				id: this._mainThreadId,
				remoteId: void 0,
				externalId: void 0,
				status: "regular"
			}
		};
		this._notifySubscribers();
	}
	async switchToThread(threadId, _options) {
		if (this._mainThreadId === threadId) return;
		const onSwitchToThread = this.adapter.onSwitchToThread;
		if (!onSwitchToThread) throw new Error("External store adapter does not support switching to thread");
		await onSwitchToThread(threadId);
	}
	async switchToNewThread() {
		const onSwitchToNewThread = this.adapter.onSwitchToNewThread;
		if (!onSwitchToNewThread) throw new Error("External store adapter does not support switching to new thread");
		await onSwitchToNewThread();
	}
	async rename(threadId, newTitle) {
		const onRename = this.adapter.onRename;
		if (!onRename) throw new Error("External store adapter does not support renaming");
		await onRename(threadId, newTitle);
	}
	async updateCustom(threadId, custom) {
		const onUpdateCustom = this.adapter.onUpdateCustom;
		if (!onUpdateCustom) throw new Error("External store adapter does not support updating custom metadata");
		await onUpdateCustom(threadId, custom);
	}
	async detach() {}
	async archive(threadId) {
		const onArchive = this.adapter.onArchive;
		if (!onArchive) throw new Error("External store adapter does not support archiving");
		await onArchive(threadId);
	}
	async unarchive(threadId) {
		const onUnarchive = this.adapter.onUnarchive;
		if (!onUnarchive) throw new Error("External store adapter does not support unarchiving");
		await onUnarchive(threadId);
	}
	async delete(threadId) {
		const onDelete = this.adapter.onDelete;
		if (!onDelete) throw new Error("External store adapter does not support deleting");
		await onDelete(threadId);
	}
	initialize(threadId) {
		return Promise.resolve({
			remoteId: threadId,
			externalId: void 0
		});
	}
	generateTitle() {
		throw new Error("Method not implemented.");
	}
	_subscriptions = /* @__PURE__ */ new Set();
	subscribe(callback) {
		this._subscriptions.add(callback);
		return () => this._subscriptions.delete(callback);
	}
	_notifySubscribers() {
		for (const callback of this._subscriptions) callback();
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/utils/id.js
/**
* @deprecated This API is experimental and may change without notice.
*/
var generateId = customAlphabet("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", 7);
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/utils/thread-message-like.js
var convertDataPrefixedPart = (type, data) => {
	if (!type.startsWith("data-")) return void 0;
	return {
		type: "data",
		name: type.substring(5),
		data
	};
};
/**
* @deprecated This API is experimental and may change without notice.
*/
var fromThreadMessageLike = (like, fallbackId, fallbackStatus) => {
	const { role, id, createdAt, attachments, status, metadata } = like;
	const common = {
		id: id ?? fallbackId,
		createdAt: createdAt ?? /* @__PURE__ */ new Date()
	};
	const content = typeof like.content === "string" ? [{
		type: "text",
		text: like.content
	}] : like.content;
	const sanitizeImageContent = ({ image, ...rest }) => {
		if (image.match(/^data:image\/(png|jpeg|jpg|gif|webp|svg\+xml);base64,(.*)$/)) return {
			...rest,
			image
		};
		if (/^(https:\/\/|blob:)/.test(image)) return {
			...rest,
			image
		};
		console.warn(`Invalid image data format detected`);
		return null;
	};
	if (role !== "user" && attachments?.length) throw new Error("attachments are only supported for user messages");
	if (role !== "assistant" && status) throw new Error("status is only supported for assistant messages");
	if (role !== "assistant" && metadata?.steps) throw new Error("metadata.steps is only supported for assistant messages");
	switch (role) {
		case "assistant": return {
			...common,
			role,
			content: content.map((part) => {
				const type = part.type;
				switch (type) {
					case "text":
					case "reasoning":
						if (part.text.trim().length === 0) return null;
						return part;
					case "file":
					case "source": return part;
					case "image": return sanitizeImageContent(part);
					case "data": return part;
					case "generative-ui": return part;
					case "tool-call": {
						const { parentId, messages, ...basePart } = part;
						const commonProps = {
							...basePart,
							toolCallId: part.toolCallId ?? `tool-${generateId()}`,
							...parentId !== void 0 && { parentId },
							...messages !== void 0 && { messages }
						};
						if (part.args) return {
							...commonProps,
							args: part.args,
							argsText: part.argsText ?? JSON.stringify(part.args)
						};
						return {
							...commonProps,
							args: parsePartialJsonObject(part.argsText ?? "") ?? {},
							argsText: part.argsText ?? ""
						};
					}
					default: {
						const converted = convertDataPrefixedPart(type, part.data);
						if (converted) return converted;
						throw new Error(`Unsupported assistant message part type: ${type}`);
					}
				}
			}).filter((c) => !!c),
			status: status ?? fallbackStatus,
			metadata: {
				unstable_state: metadata?.unstable_state ?? null,
				unstable_annotations: metadata?.unstable_annotations ?? [],
				unstable_data: metadata?.unstable_data ?? [],
				custom: metadata?.custom ?? {},
				steps: metadata?.steps ?? [],
				...metadata?.timing && { timing: metadata.timing },
				...metadata?.submittedFeedback && { submittedFeedback: metadata.submittedFeedback },
				...metadata?.isOptimistic && { isOptimistic: true }
			}
		};
		case "user": return {
			...common,
			role,
			content: content.map((part) => {
				const type = part.type;
				switch (type) {
					case "text":
					case "image":
					case "audio":
					case "file":
					case "data": return part;
					default: {
						const converted = convertDataPrefixedPart(type, part.data);
						if (converted) return converted;
						throw new Error(`Unsupported user message part type: ${type}`);
					}
				}
			}),
			attachments: (attachments ?? []).map((att) => ({
				...att,
				content: att.content.map((part) => {
					return convertDataPrefixedPart(part.type, part.data) ?? part;
				})
			})),
			metadata: { custom: metadata?.custom ?? {} }
		};
		case "system":
			if (content.length !== 1 || content[0].type !== "text") throw new Error("System messages must have exactly one text message part.");
			return {
				...common,
				role,
				content,
				metadata: { custom: metadata?.custom ?? {} }
			};
		default: throw new Error(`Unknown message role: ${role}`);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/utils/auto-status.js
var symbolAutoStatus = Symbol("autoStatus");
var AUTO_STATUS_RUNNING = Object.freeze(Object.assign({ type: "running" }, { [symbolAutoStatus]: true }));
var AUTO_STATUS_COMPLETE = Object.freeze(Object.assign({
	type: "complete",
	reason: "unknown"
}, { [symbolAutoStatus]: true }));
var AUTO_STATUS_PENDING = Object.freeze(Object.assign({
	type: "requires-action",
	reason: "tool-calls"
}, { [symbolAutoStatus]: true }));
var AUTO_STATUS_INTERRUPT = Object.freeze(Object.assign({
	type: "requires-action",
	reason: "interrupt"
}, { [symbolAutoStatus]: true }));
var isAutoStatus = (status) => status[symbolAutoStatus] === true;
var getAutoStatus = (isLast, isRunning, hasInterruptedToolCalls, hasPendingToolCalls, error) => {
	if (isLast && error) return Object.assign({
		type: "incomplete",
		reason: "error",
		error
	}, { [symbolAutoStatus]: true });
	return isLast && isRunning ? AUTO_STATUS_RUNNING : hasInterruptedToolCalls ? AUTO_STATUS_INTERRUPT : hasPendingToolCalls ? AUTO_STATUS_PENDING : AUTO_STATUS_COMPLETE;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/utils/message-repository.js
var ExportedMessageRepository = {
	fromArray: (messages) => {
		const conv = messages.map((m) => fromThreadMessageLike(m, generateId(), getAutoStatus(false, false, false, false, void 0)));
		return { messages: conv.map((m, idx) => ({
			parentId: idx > 0 ? conv[idx - 1].id : null,
			message: m
		})) };
	},
	fromBranchableArray: (items, options) => {
		const fallbackStatus = getAutoStatus(false, false, false, false, void 0);
		return {
			...options?.headId !== void 0 ? { headId: options.headId } : void 0,
			messages: items.map(({ message, parentId }) => {
				if (!message.id) throw new Error("ExportedMessageRepository.fromBranchableArray: Each message must have an 'id' field set.");
				return {
					parentId,
					message: fromThreadMessageLike(message, message.id, fallbackStatus)
				};
			})
		};
	}
};
var findHead = (message) => {
	if (message.next) return findHead(message.next);
	if ("current" in message) return message;
	return null;
};
var CachedValue = class {
	func;
	_value = null;
	constructor(func) {
		this.func = func;
	}
	get value() {
		if (this._value === null) this._value = this.func();
		return this._value;
	}
	dirty() {
		this._value = null;
	}
};
var MessageRepository = class {
	messages = /* @__PURE__ */ new Map();
	head = null;
	root = {
		children: [],
		next: null
	};
	updateLevels(message, newLevel) {
		message.level = newLevel;
		for (const childId of message.children) {
			const childMessage = this.messages.get(childId);
			if (childMessage) this.updateLevels(childMessage, newLevel + 1);
		}
	}
	performOp(newParent, child, operation) {
		const parentOrRoot = child.prev ?? this.root;
		const newParentOrRoot = newParent ?? this.root;
		if (operation === "relink" && parentOrRoot === newParentOrRoot) return;
		if (operation !== "link") {
			parentOrRoot.children = parentOrRoot.children.filter((m) => m !== child.current.id);
			if (parentOrRoot.next === child) {
				const fallbackId = parentOrRoot.children.at(-1);
				const fallback = fallbackId ? this.messages.get(fallbackId) : null;
				if (fallback === void 0) throw new Error("MessageRepository(performOp/cut): Fallback sibling message not found. This is likely an internal bug in assistant-ui.");
				parentOrRoot.next = fallback;
			}
		}
		if (operation !== "cut") {
			for (let current = newParent; current; current = current.prev) if (current.current.id === child.current.id) throw new Error("MessageRepository(performOp/link): A message with the same id already exists in the parent tree. This error occurs if the same message id is found multiple times. This is likely an internal bug in assistant-ui.");
			newParentOrRoot.children = [...newParentOrRoot.children, child.current.id];
			if (findHead(child) === this.head || newParentOrRoot.next === null) newParentOrRoot.next = child;
			child.prev = newParent;
			const newLevel = newParent ? newParent.level + 1 : 0;
			this.updateLevels(child, newLevel);
		}
	}
	_messages = new CachedValue(() => {
		const messages = new Array((this.head?.level ?? -1) + 1);
		for (let current = this.head; current; current = current.prev) messages[current.level] = current.current;
		return messages;
	});
	get headId() {
		return this.head?.current.id ?? null;
	}
	getMessages(headId) {
		if (headId === void 0 || headId === this.head?.current.id) return this._messages.value;
		const headMessage = this.messages.get(headId);
		if (!headMessage) throw new Error("MessageRepository(getMessages): Head message not found. This is likely an internal bug in assistant-ui.");
		const messages = new Array(headMessage.level + 1);
		for (let current = headMessage; current; current = current.prev) messages[current.level] = current.current;
		return messages;
	}
	addOrUpdateMessage(parentId, message) {
		const existingItem = this.messages.get(message.id);
		const prev = parentId ? this.messages.get(parentId) : null;
		if (prev === void 0) throw new Error("MessageRepository(addOrUpdateMessage): Parent message not found. This is likely an internal bug in assistant-ui.");
		if (existingItem) {
			existingItem.current = message;
			this.performOp(prev, existingItem, "relink");
			this._messages.dirty();
			return;
		}
		const newItem = {
			prev,
			current: message,
			next: null,
			children: [],
			level: prev ? prev.level + 1 : 0
		};
		this.messages.set(message.id, newItem);
		this.performOp(prev, newItem, "link");
		if (this.head === prev) this.head = newItem;
		this._messages.dirty();
	}
	getMessage(messageId) {
		const message = this.messages.get(messageId);
		if (!message) throw new Error("MessageRepository(updateMessage): Message not found. This is likely an internal bug in assistant-ui.");
		return {
			parentId: message.prev?.current.id ?? null,
			message: message.current,
			index: message.level
		};
	}
	deleteMessage(messageId, replacementId) {
		const message = this.messages.get(messageId);
		if (!message) throw new Error("MessageRepository(deleteMessage): Message not found. This is likely an internal bug in assistant-ui.");
		const replacement = replacementId === void 0 ? message.prev : replacementId === null ? null : this.messages.get(replacementId);
		if (replacement === void 0) throw new Error("MessageRepository(deleteMessage): Replacement not found. This is likely an internal bug in assistant-ui.");
		for (const child of message.children) {
			const childMessage = this.messages.get(child);
			if (!childMessage) throw new Error("MessageRepository(deleteMessage): Child message not found. This is likely an internal bug in assistant-ui.");
			this.performOp(replacement, childMessage, "relink");
		}
		this.performOp(null, message, "cut");
		this.messages.delete(messageId);
		if (this.head === message) this.head = findHead(replacement ?? this.root);
		this._messages.dirty();
	}
	getBranches(messageId) {
		const message = this.messages.get(messageId);
		if (!message) throw new Error("MessageRepository(getBranches): Message not found. This is likely an internal bug in assistant-ui.");
		const { children } = message.prev ?? this.root;
		return children;
	}
	/**
	* Evicts optimistic messages (`metadata.isOptimistic`) the head just moved
	* away from. Since eviction runs on every head move, the only optimistic
	* messages in the repository live on the branch the head previously pointed
	* at — so we walk just that branch rather than the whole repository. Keeps a
	* client→server id swap from leaving a phantom sibling, and drops off-branch
	* placeholders.
	*/
	evictOffBranchOptimisticMessages(previousHead, currentHead) {
		if (!previousHead) return;
		const onHeadBranch = /* @__PURE__ */ new Set();
		for (let current = currentHead; current; current = current.prev) onHeadBranch.add(current.current.id);
		const stale = [];
		for (let current = previousHead; current; current = current.prev) {
			if (onHeadBranch.has(current.current.id)) break;
			if (current.current.metadata?.isOptimistic) stale.push(current.current.id);
		}
		for (const id of stale) if (this.messages.has(id)) this.deleteMessage(id);
	}
	switchToBranch(messageId) {
		const message = this.messages.get(messageId);
		if (!message) throw new Error("MessageRepository(switchToBranch): Branch not found. This is likely an internal bug in assistant-ui.");
		const previousHead = this.head;
		const prevOrRoot = message.prev ?? this.root;
		prevOrRoot.next = message;
		this.head = findHead(message);
		this.evictOffBranchOptimisticMessages(previousHead, this.head);
		this._messages.dirty();
	}
	resetHead(messageId) {
		if (messageId === null) {
			this.clear();
			return;
		}
		const message = this.messages.get(messageId);
		if (!message) throw new Error("MessageRepository(resetHead): Branch not found. This is likely an internal bug in assistant-ui.");
		const previousHead = this.head;
		if (message.children.length > 0) {
			const deleteDescendants = (msg) => {
				for (const childId of msg.children) {
					const childMessage = this.messages.get(childId);
					if (childMessage) {
						deleteDescendants(childMessage);
						this.messages.delete(childId);
					}
				}
			};
			deleteDescendants(message);
			message.children = [];
			message.next = null;
		}
		this.head = message;
		for (let current = message; current; current = current.prev) if (current.prev) current.prev.next = current;
		else this.root.next = current;
		this.evictOffBranchOptimisticMessages(previousHead, this.head);
		this._messages.dirty();
	}
	clear() {
		this.messages.clear();
		this.head = null;
		this.root = {
			children: [],
			next: null
		};
		this._messages.dirty();
	}
	export() {
		const exportItems = [];
		for (const [, message] of this.messages) {
			if (message.current.metadata?.isOptimistic) continue;
			exportItems.push({
				message: message.current,
				parentId: message.prev?.current.id ?? null
			});
		}
		let head = this.head;
		while (head?.current.metadata?.isOptimistic) head = head.prev;
		return {
			headId: head?.current.id ?? null,
			messages: exportItems
		};
	}
	import({ headId, messages }) {
		for (const { message, parentId } of messages) this.addOrUpdateMessage(parentId, message);
		this.resetHead(headId ?? messages.at(-1)?.message.id ?? null);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/store/scopes/queue-item.js
var EMPTY_QUEUE_ITEMS = Object.freeze([]);
//#endregion
//#region node_modules/@assistant-ui/core/dist/adapters/attachment.js
function fileMatchesAccept(file, acceptString) {
	if (acceptString === "*") return true;
	const allowedTypes = acceptString.split(",").map((type) => type.trim().toLowerCase());
	const fileExtension = `.${file.name.split(".").pop().toLowerCase()}`;
	const fileMimeType = file.type.toLowerCase();
	for (const type of allowedTypes) {
		if (type.startsWith(".") && type === fileExtension) return true;
		if (type.includes("/") && type === fileMimeType) return true;
		if (type.endsWith("/*")) {
			const generalType = type.split("/")[0];
			if (fileMimeType.startsWith(`${generalType}/`)) return true;
		}
	}
	return false;
}
function attachmentsEqual(a, b) {
	if (a.length !== b.length) return false;
	return a.every((att, i) => att.id === b[i].id);
}
function partToCompleteAttachment(part) {
	const id = generateId();
	if (part.type === "image") return {
		id,
		type: "image",
		name: part.filename ?? "image",
		content: [part],
		status: { type: "complete" }
	};
	if (part.type === "file") return {
		id,
		type: "document",
		name: part.filename ?? "document",
		contentType: part.mimeType,
		content: [part],
		status: { type: "complete" }
	};
	if (part.type === "audio") return {
		id,
		type: "audio",
		name: `audio.${part.audio.format}`,
		contentType: `audio/${part.audio.format}`,
		content: [part],
		status: { type: "complete" }
	};
	return {
		id,
		type: "data",
		name: part.name,
		content: [part],
		status: { type: "complete" }
	};
}
function liftNonTextParts(content) {
	const result = [];
	for (const part of content) if (part.type !== "text") result.push(partToCompleteAttachment(part));
	return result;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/base/base-composer-runtime-core.js
var isAttachmentComplete = (a) => a.status.type === "complete";
var BaseComposerRuntimeCore = class extends BaseSubscribable {
	isEditing = true;
	get attachmentAccept() {
		return this.getAttachmentAdapter()?.accept ?? "*";
	}
	_attachments = [];
	get attachments() {
		return this._attachments;
	}
	setAttachments(value) {
		this._attachments = value;
		this._notifySubscribers();
	}
	get isEmpty() {
		return !this.text.trim() && !this.attachments.length;
	}
	_text = "";
	get text() {
		return this._text;
	}
	_role = "user";
	get role() {
		return this._role;
	}
	_runConfig = {};
	get runConfig() {
		return this._runConfig;
	}
	_quote = void 0;
	get quote() {
		return this._quote;
	}
	setQuote(quote) {
		if (this._quote === quote) return;
		this._quote = quote;
		this._notifySubscribers();
	}
	setText(value) {
		if (this._text === value) return;
		this._text = value;
		if (this._dictation) {
			this._dictationBaseText = value;
			this._currentInterimText = "";
			const { status, inputDisabled } = this._dictation;
			this._dictation = inputDisabled ? {
				status,
				inputDisabled
			} : { status };
		}
		this._notifySubscribers();
	}
	setRole(role) {
		if (this._role === role) return;
		this._role = role;
		this._notifySubscribers();
	}
	setRunConfig(runConfig) {
		if (this._runConfig === runConfig) return;
		this._runConfig = runConfig;
		this._notifySubscribers();
	}
	_emptyTextAndAttachments() {
		this._attachments = [];
		this._text = "";
		this._notifySubscribers();
	}
	async _onClearAttachments() {
		const adapter = this.getAttachmentAdapter();
		if (adapter) {
			const pending = this._attachments.filter((a) => !isAttachmentComplete(a));
			await Promise.all(pending.map((a) => adapter.remove(a)));
		}
	}
	async reset() {
		if (this._attachments.length === 0 && this._text === "" && this._role === "user" && Object.keys(this._runConfig).length === 0 && this._quote === void 0) return;
		this._role = "user";
		this._runConfig = {};
		this._quote = void 0;
		const task = this._onClearAttachments();
		this._emptyTextAndAttachments();
		await task;
	}
	async clearAttachments() {
		const task = this._onClearAttachments();
		this.setAttachments([]);
		await task;
	}
	async send(options) {
		if (!this.canSend) return;
		if (this._dictationSession) {
			this._dictationSession.cancel();
			this._cleanupDictation();
		}
		const adapter = this.getAttachmentAdapter();
		const attachments = this.attachments.length > 0 ? Promise.all(this.attachments.map(async (a) => {
			if (isAttachmentComplete(a)) return a;
			if (!adapter) throw new Error("Attachments are not supported");
			return await adapter.send(a);
		})) : [];
		const text = this.text;
		const quote = this._quote;
		this._quote = void 0;
		this._emptyTextAndAttachments();
		const message = {
			createdAt: /* @__PURE__ */ new Date(),
			role: this.role,
			content: text ? [{
				type: "text",
				text
			}] : [],
			attachments: await attachments,
			runConfig: this.runConfig,
			metadata: { custom: { ...quote ? { quote } : {} } }
		};
		this.handleSend(message, options);
		this._notifyEventSubscribers("send", {});
	}
	cancel() {
		this.handleCancel();
	}
	get queue() {
		return EMPTY_QUEUE_ITEMS;
	}
	steerQueueItem(_queueItemId) {}
	removeQueueItem(_queueItemId) {}
	async addAttachment(fileOrAttachment) {
		if (!(fileOrAttachment instanceof File)) {
			const adapter = this.getAttachmentAdapter();
			if (adapter && !fileMatchesAccept({
				name: fileOrAttachment.name,
				type: fileOrAttachment.contentType ?? ""
			}, adapter.accept)) {
				const message = `File type ${fileOrAttachment.contentType || "unknown"} is not accepted. Accepted types: ${adapter.accept}`;
				const err = new Error(message);
				this._safeEmitAttachmentAddError("not-accepted", message, void 0, err);
				throw err;
			}
			const a = {
				id: fileOrAttachment.id ?? generateId(),
				type: fileOrAttachment.type ?? "document",
				name: fileOrAttachment.name,
				contentType: fileOrAttachment.contentType,
				content: fileOrAttachment.content,
				status: { type: "complete" }
			};
			this._attachments = [...this._attachments, a];
			this._notifySubscribers();
			this._notifyEventSubscribers("attachmentAdd", {});
			return;
		}
		const upsertAttachment = (a) => {
			const idx = this._attachments.findIndex((attachment) => attachment.id === a.id);
			if (idx !== -1) this._attachments = [
				...this._attachments.slice(0, idx),
				a,
				...this._attachments.slice(idx + 1)
			];
			else this._attachments = [...this._attachments, a];
			this._notifySubscribers();
		};
		const adapter = this.getAttachmentAdapter();
		if (!adapter) {
			const message = "Attachments are not supported";
			const err = /* @__PURE__ */ new Error(message);
			this._safeEmitAttachmentAddError("no-adapter", message, void 0, err);
			throw err;
		}
		if (!fileMatchesAccept({
			name: fileOrAttachment.name,
			type: fileOrAttachment.type
		}, adapter.accept)) {
			const message = `File type ${fileOrAttachment.type || "unknown"} is not accepted. Accepted types: ${adapter.accept}`;
			const err = new Error(message);
			this._safeEmitAttachmentAddError("not-accepted", message, void 0, err);
			throw err;
		}
		let lastAttachment;
		try {
			const promiseOrGenerator = adapter.add({ file: fileOrAttachment });
			if (Symbol.asyncIterator in promiseOrGenerator) for await (const r of promiseOrGenerator) {
				lastAttachment = r;
				upsertAttachment(r);
			}
			else {
				lastAttachment = await promiseOrGenerator;
				upsertAttachment(lastAttachment);
			}
		} catch (e) {
			if (lastAttachment) upsertAttachment({
				...lastAttachment,
				status: {
					type: "incomplete",
					reason: "error"
				}
			});
			this._safeEmitAttachmentAddError("adapter-error", e instanceof Error ? e.message : String(e), lastAttachment?.id, e instanceof Error ? e : void 0);
			throw e;
		}
		if (lastAttachment?.status.type === "incomplete" && lastAttachment.status.reason === "error") this._safeEmitAttachmentAddError("adapter-error", "Attachment upload did not complete successfully.", lastAttachment?.id);
		else this._notifyEventSubscribers("attachmentAdd", {});
	}
	_safeEmitAttachmentAddError(reason, message, attachmentId, error) {
		try {
			this._notifyEventSubscribers("attachmentAddError", {
				reason,
				message,
				...attachmentId !== void 0 && { attachmentId },
				...error !== void 0 && { error }
			});
		} catch (subscriberError) {
			console.error("[assistant-ui] attachmentAddError subscriber threw:", subscriberError);
		}
	}
	async removeAttachment(attachmentId) {
		const index = this._attachments.findIndex((a) => a.id === attachmentId);
		if (index === -1) throw new Error("Attachment not found");
		const attachment = this._attachments[index];
		if (!isAttachmentComplete(attachment)) {
			const adapter = this.getAttachmentAdapter();
			if (!adapter) throw new Error("Attachments are not supported");
			await adapter.remove(attachment);
		}
		this._attachments = this._attachments.filter((a) => a.id !== attachmentId);
		this._notifySubscribers();
	}
	_dictation;
	_dictationSession;
	_dictationUnsubscribes = [];
	_dictationBaseText = "";
	_currentInterimText = "";
	_dictationSessionIdCounter = 0;
	_activeDictationSessionId;
	_isCleaningDictation = false;
	get dictation() {
		return this._dictation;
	}
	_isActiveSession(sessionId, session) {
		return this._activeDictationSessionId === sessionId && this._dictationSession === session;
	}
	startDictation() {
		const adapter = this.getDictationAdapter();
		if (!adapter) throw new Error("Dictation adapter not configured");
		if (this._dictationSession) {
			for (const unsub of this._dictationUnsubscribes) unsub();
			this._dictationUnsubscribes = [];
			this._dictationSession.stop().catch(() => {});
			this._dictationSession = void 0;
		}
		const inputDisabled = adapter.disableInputDuringDictation ?? false;
		this._dictationBaseText = this._text;
		this._currentInterimText = "";
		const session = adapter.listen();
		this._dictationSession = session;
		const sessionId = ++this._dictationSessionIdCounter;
		this._activeDictationSessionId = sessionId;
		this._dictation = {
			status: session.status,
			inputDisabled
		};
		this._notifySubscribers();
		const unsubSpeech = session.onSpeech((result) => {
			if (!this._isActiveSession(sessionId, session)) return;
			const isFinal = result.isFinal !== false;
			const separator = this._dictationBaseText && !this._dictationBaseText.endsWith(" ") && result.transcript ? " " : "";
			if (isFinal) {
				this._dictationBaseText = this._dictationBaseText + separator + result.transcript;
				this._currentInterimText = "";
				this._text = this._dictationBaseText;
				if (this._dictation) {
					const { transcript: _, ...rest } = this._dictation;
					this._dictation = rest;
				}
				this._notifySubscribers();
			} else {
				this._currentInterimText = separator + result.transcript;
				this._text = this._dictationBaseText + this._currentInterimText;
				if (this._dictation) this._dictation = {
					...this._dictation,
					transcript: result.transcript
				};
				this._notifySubscribers();
			}
		});
		this._dictationUnsubscribes.push(unsubSpeech);
		const unsubStart = session.onSpeechStart(() => {
			if (!this._isActiveSession(sessionId, session)) return;
			this._dictation = {
				status: { type: "running" },
				inputDisabled,
				...this._dictation?.transcript && { transcript: this._dictation.transcript }
			};
			this._notifySubscribers();
		});
		this._dictationUnsubscribes.push(unsubStart);
		const unsubEnd = session.onSpeechEnd(() => {
			this._cleanupDictation({ sessionId });
		});
		this._dictationUnsubscribes.push(unsubEnd);
		const statusInterval = setInterval(() => {
			if (!this._isActiveSession(sessionId, session)) return;
			if (session.status.type === "ended") this._cleanupDictation({ sessionId });
		}, 100);
		this._dictationUnsubscribes.push(() => clearInterval(statusInterval));
	}
	stopDictation() {
		if (!this._dictationSession) return;
		const session = this._dictationSession;
		const sessionId = this._activeDictationSessionId;
		session.stop().finally(() => {
			this._cleanupDictation({ sessionId });
		});
	}
	_cleanupDictation(options) {
		if (options?.sessionId !== void 0 && options.sessionId !== this._activeDictationSessionId || this._isCleaningDictation) return;
		this._isCleaningDictation = true;
		try {
			for (const unsub of this._dictationUnsubscribes) unsub();
			this._dictationUnsubscribes = [];
			this._dictationSession = void 0;
			this._activeDictationSessionId = void 0;
			this._dictation = void 0;
			this._dictationBaseText = "";
			this._currentInterimText = "";
			this._notifySubscribers();
		} finally {
			this._isCleaningDictation = false;
		}
	}
	_eventSubscribers = /* @__PURE__ */ new Map();
	_notifyEventSubscribers(event, payload) {
		const subscribers = this._eventSubscribers.get(event);
		if (!subscribers) return;
		for (const callback of subscribers) callback(payload);
	}
	unstable_on(event, callback) {
		const wrapped = callback;
		let subscribers = this._eventSubscribers.get(event);
		if (!subscribers) {
			subscribers = /* @__PURE__ */ new Set();
			this._eventSubscribers.set(event, subscribers);
		}
		subscribers.add(wrapped);
		return () => {
			this._eventSubscribers.get(event)?.delete(wrapped);
		};
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/base/default-thread-composer-runtime-core.js
var DefaultThreadComposerRuntimeCore = class extends BaseComposerRuntimeCore {
	runtime;
	_canCancel = false;
	get canCancel() {
		return this._canCancel;
	}
	get canSend() {
		return !this.isEmpty && !this.runtime.isSendDisabled;
	}
	get queue() {
		return this.runtime.getQueueItems?.() ?? EMPTY_QUEUE_ITEMS;
	}
	steerQueueItem(queueItemId) {
		this.runtime.steerQueueItem?.(queueItemId);
	}
	removeQueueItem(queueItemId) {
		this.runtime.removeQueueItem?.(queueItemId);
	}
	getAttachmentAdapter() {
		return this.runtime.adapters?.attachments;
	}
	getDictationAdapter() {
		return this.runtime.adapters?.dictation;
	}
	constructor(runtime) {
		super();
		this.runtime = runtime;
		this.connect();
	}
	connect() {
		let lastIsSendDisabled = this.runtime.isSendDisabled;
		let lastQueue = this.queue;
		return this.runtime.subscribe(() => {
			let changed = false;
			if (this.canCancel !== this.runtime.capabilities.cancel) {
				this._canCancel = this.runtime.capabilities.cancel;
				changed = true;
			}
			if (lastIsSendDisabled !== this.runtime.isSendDisabled) {
				lastIsSendDisabled = this.runtime.isSendDisabled;
				changed = true;
			}
			if (lastQueue !== this.queue) {
				lastQueue = this.queue;
				changed = true;
			}
			if (changed) this._notifySubscribers();
		});
	}
	async handleSend(message, options) {
		this.runtime.append({
			...message,
			parentId: this.runtime.messages.at(-1)?.id ?? null,
			sourceId: null,
			startRun: options?.startRun,
			steer: options?.steer
		});
	}
	async handleCancel() {
		this.runtime.cancelRun();
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/base/default-edit-composer-runtime-core.js
var DefaultEditComposerRuntimeCore = class extends BaseComposerRuntimeCore {
	runtime;
	endEditCallback;
	get canCancel() {
		return true;
	}
	get canSend() {
		return !this.isEmpty;
	}
	getAttachmentAdapter() {
		return this.runtime.adapters?.attachments;
	}
	getDictationAdapter() {
		return this.runtime.adapters?.dictation;
	}
	_previousText;
	_previousAttachments;
	_nonTextPassthrough;
	_parentId;
	_sourceId;
	constructor(runtime, endEditCallback, { parentId, message }) {
		super();
		this.runtime = runtime;
		this.endEditCallback = endEditCallback;
		this._parentId = parentId;
		this._sourceId = message.id;
		this._previousText = getThreadMessageText(message);
		this.setText(this._previousText);
		this.setRole(message.role);
		if (message.role === "user") {
			this._previousAttachments = [...message.attachments ?? [], ...liftNonTextParts(message.content)];
			this._nonTextPassthrough = [];
		} else {
			this._previousAttachments = message.attachments ?? [];
			this._nonTextPassthrough = message.content.filter((p) => p.type !== "text");
		}
		this.setAttachments(this._previousAttachments);
		this.setRunConfig({ ...runtime.composer.runConfig });
	}
	get parentId() {
		return this._parentId;
	}
	get sourceId() {
		return this._sourceId;
	}
	async handleSend(message, options) {
		const text = getThreadMessageText(message);
		const attachmentsChanged = !attachmentsEqual(message.attachments ?? [], this._previousAttachments);
		if (text !== this._previousText || attachmentsChanged || options?.startRun) {
			const content = this._nonTextPassthrough.length > 0 ? [...message.content, ...this._nonTextPassthrough] : message.content;
			this.runtime.append({
				...message,
				content,
				parentId: this._parentId,
				sourceId: this._sourceId,
				startRun: options?.startRun
			});
		}
		this.handleCancel();
	}
	handleCancel() {
		this.endEditCallback();
		this._notifySubscribers();
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtime/base/base-thread-runtime-core.js
var BaseThreadRuntimeCore = class {
	_contextProvider;
	_subscriptions = /* @__PURE__ */ new Set();
	_isInitialized = false;
	repository = new MessageRepository();
	_voiceMessages = [];
	_voiceGeneration = 0;
	_cachedMergedMessages = null;
	_cachedVoiceGeneration = -1;
	_cachedMergedBase = null;
	_markVoiceMessagesDirty() {
		this._voiceGeneration++;
		this._cachedMergedMessages = null;
	}
	_getBaseMessages() {
		return this.repository.getMessages();
	}
	get messages() {
		if (this._voiceMessages.length === 0) return this._getBaseMessages();
		const base = this._getBaseMessages();
		if (this._cachedVoiceGeneration !== this._voiceGeneration || this._cachedMergedBase !== base) {
			this._cachedMergedMessages = [...base, ...this._voiceMessages];
			this._cachedVoiceGeneration = this._voiceGeneration;
			this._cachedMergedBase = base;
		}
		return this._cachedMergedMessages;
	}
	get state() {
		let mostRecentAssistantMessage;
		for (const message of this.messages) if (message.role === "assistant") mostRecentAssistantMessage = message;
		return mostRecentAssistantMessage?.metadata.unstable_state ?? null;
	}
	composer = new DefaultThreadComposerRuntimeCore(this);
	constructor(_contextProvider) {
		this._contextProvider = _contextProvider;
	}
	getModelContext() {
		return this._contextProvider.getModelContext();
	}
	_editComposers = /* @__PURE__ */ new Map();
	getEditComposer(messageId) {
		return this._editComposers.get(messageId);
	}
	beginEdit(messageId) {
		if (this._editComposers.has(messageId)) throw new Error("Edit already in progress");
		this._editComposers.set(messageId, new DefaultEditComposerRuntimeCore(this, () => this._editComposers.delete(messageId), this.repository.getMessage(messageId)));
		this._notifySubscribers();
	}
	getMessageById(messageId) {
		try {
			return this.repository.getMessage(messageId);
		} catch {
			const baseMessages = this.repository.getMessages();
			const voiceIdx = this._voiceMessages.findIndex((m) => m.id === messageId);
			if (voiceIdx !== -1) return {
				parentId: voiceIdx > 0 ? this._voiceMessages[voiceIdx - 1].id : baseMessages.at(-1)?.id ?? null,
				message: this._voiceMessages[voiceIdx],
				index: baseMessages.length + voiceIdx
			};
			return;
		}
	}
	getBranches(messageId) {
		if (this._voiceMessages.some((m) => m.id === messageId)) return [];
		return this.repository.getBranches(messageId);
	}
	switchToBranch(branchId) {
		this.repository.switchToBranch(branchId);
		this._notifySubscribers();
	}
	_notifySubscribers() {
		for (const callback of this._subscriptions) callback();
	}
	_notifyEventSubscribers(event, payload) {
		const subscribers = this._eventSubscribers.get(event);
		if (!subscribers) return;
		for (const callback of subscribers) callback(payload);
	}
	subscribe(callback) {
		this._subscriptions.add(callback);
		return () => this._subscriptions.delete(callback);
	}
	submitFeedback({ messageId, type }) {
		const adapter = this.adapters?.feedback;
		if (!adapter) throw new Error("Feedback adapter not configured");
		const { message, parentId } = this.repository.getMessage(messageId);
		adapter.submit({
			message,
			type
		});
		if (message.role === "assistant") {
			const updatedMessage = {
				...message,
				metadata: {
					...message.metadata,
					submittedFeedback: { type }
				}
			};
			this.repository.addOrUpdateMessage(parentId, updatedMessage);
		}
		this._notifySubscribers();
	}
	_stopSpeaking;
	speech;
	speak(messageId) {
		const adapter = this.adapters?.speech;
		if (!adapter) throw new Error("Speech adapter not configured");
		const { message } = this.repository.getMessage(messageId);
		this._stopSpeaking?.();
		const utterance = adapter.speak(getThreadMessageText(message));
		const unsub = utterance.subscribe(() => {
			if (utterance.status.type === "ended") {
				this._stopSpeaking = void 0;
				this.speech = void 0;
			} else this.speech = {
				messageId,
				status: utterance.status
			};
			this._notifySubscribers();
		});
		this.speech = {
			messageId,
			status: utterance.status
		};
		this._notifySubscribers();
		this._stopSpeaking = () => {
			utterance.cancel();
			unsub();
			this.speech = void 0;
			this._stopSpeaking = void 0;
		};
	}
	stopSpeaking() {
		if (!this._stopSpeaking) throw new Error("No message is being spoken");
		this._stopSpeaking();
		this._notifySubscribers();
	}
	_voiceSession;
	_voiceUnsubs = [];
	voice;
	_voiceVolume = 0;
	_voiceVolumeSubscribers = /* @__PURE__ */ new Set();
	getVoiceVolume = () => this._voiceVolume;
	subscribeVoiceVolume = (callback) => {
		this._voiceVolumeSubscribers.add(callback);
		return () => this._voiceVolumeSubscribers.delete(callback);
	};
	connectVoice() {
		const adapter = this.adapters?.voice;
		if (!adapter) throw new Error("Voice adapter not configured");
		this.disconnectVoice();
		const session = adapter.connect({});
		this._voiceSession = session;
		const unsubs = [];
		let currentMode = "listening";
		this.voice = {
			status: session.status,
			isMuted: session.isMuted,
			mode: currentMode
		};
		this._voiceVolume = 0;
		this._notifySubscribers();
		unsubs.push(session.onStatusChange((status) => {
			if (status.type === "ended") {
				this._finishVoiceAssistantMessage();
				this._voiceSession = void 0;
				this.voice = void 0;
			} else this.voice = {
				status,
				isMuted: session.isMuted,
				mode: currentMode
			};
			this._notifySubscribers();
		}));
		unsubs.push(session.onModeChange((mode) => {
			currentMode = mode;
			if (this.voice) {
				this.voice = {
					...this.voice,
					mode
				};
				this._notifySubscribers();
			}
		}));
		unsubs.push(session.onVolumeChange((volume) => {
			this._voiceVolume = volume;
			for (const cb of this._voiceVolumeSubscribers) cb();
		}));
		unsubs.push(session.onTranscript((transcript) => {
			this._handleVoiceTranscript(transcript);
		}));
		this._voiceUnsubs = unsubs;
	}
	_currentAssistantMsg = null;
	_handleVoiceTranscript(transcript) {
		this.ensureInitialized();
		if (transcript.role === "user") {
			this._finishVoiceAssistantMessage();
			this._currentAssistantMsg = null;
			if (transcript.isFinal) {
				this._voiceMessages.push({
					id: generateId(),
					role: "user",
					content: [{
						type: "text",
						text: transcript.text
					}],
					metadata: { custom: {} },
					createdAt: /* @__PURE__ */ new Date(),
					status: {
						type: "complete",
						reason: "unknown"
					},
					attachments: []
				});
				this._markVoiceMessagesDirty();
				this._notifySubscribers();
			}
		} else {
			if (!this._currentAssistantMsg) {
				this._currentAssistantMsg = {
					id: generateId(),
					role: "assistant",
					content: [{
						type: "text",
						text: transcript.text
					}],
					metadata: {
						unstable_state: this.state,
						unstable_annotations: [],
						unstable_data: [],
						steps: [],
						custom: {}
					},
					status: { type: "running" },
					createdAt: /* @__PURE__ */ new Date()
				};
				this._voiceMessages.push(this._currentAssistantMsg);
			} else {
				const idx = this._voiceMessages.indexOf(this._currentAssistantMsg);
				if (idx === -1) return;
				const updated = {
					...this._currentAssistantMsg,
					content: [{
						type: "text",
						text: transcript.text
					}],
					...transcript.isFinal ? { status: {
						type: "complete",
						reason: "stop"
					} } : {}
				};
				this._voiceMessages[idx] = updated;
				this._currentAssistantMsg = updated;
			}
			if (transcript.isFinal) this._currentAssistantMsg = null;
			this._markVoiceMessagesDirty();
			this._notifySubscribers();
		}
	}
	_finishVoiceAssistantMessage() {
		const last = this._voiceMessages.at(-1);
		if (last?.role === "assistant" && last.status.type === "running") {
			const idx = this._voiceMessages.length - 1;
			this._voiceMessages[idx] = {
				...last,
				status: {
					type: "complete",
					reason: "stop"
				}
			};
			this._markVoiceMessagesDirty();
			this._notifySubscribers();
		}
	}
	disconnectVoice() {
		this._finishVoiceAssistantMessage();
		this._currentAssistantMsg = null;
		for (const unsub of this._voiceUnsubs) unsub();
		this._voiceUnsubs = [];
		this._voiceSession?.disconnect();
		this._voiceSession = void 0;
		this.voice = void 0;
		this._voiceVolume = 0;
		for (const cb of this._voiceVolumeSubscribers) cb();
		this._voiceMessages = [];
		this._markVoiceMessagesDirty();
		this._notifySubscribers();
	}
	muteVoice() {
		if (!this._voiceSession) throw new Error("No active voice session");
		this._voiceSession.mute();
		this.voice = {
			...this.voice,
			isMuted: true
		};
		this._notifySubscribers();
	}
	unmuteVoice() {
		if (!this._voiceSession) throw new Error("No active voice session");
		this._voiceSession.unmute();
		this.voice = {
			...this.voice,
			isMuted: false
		};
		this._notifySubscribers();
	}
	ensureInitialized() {
		if (!this._isInitialized) {
			this._isInitialized = true;
			this._notifyEventSubscribers("initialize", {});
		}
	}
	export() {
		return this.repository.export();
	}
	import(data) {
		this.ensureInitialized();
		this.repository.clear();
		this.repository.import(data);
		this._notifySubscribers();
	}
	reset(initialMessages) {
		this.import(ExportedMessageRepository.fromArray(initialMessages ?? []));
	}
	_eventSubscribers = /* @__PURE__ */ new Map();
	unstable_on(event, callback) {
		const wrapped = callback;
		if (event === "modelContextUpdate") return this._contextProvider.subscribe?.(() => wrapped({})) ?? (() => {});
		let subscribers = this._eventSubscribers.get(event);
		if (!subscribers) {
			subscribers = /* @__PURE__ */ new Set();
			this._eventSubscribers.set(event, subscribers);
		}
		subscribers.add(wrapped);
		if (event === "initialize" && this._isInitialized) queueMicrotask(() => {
			if (subscribers.has(wrapped)) wrapped({});
		});
		return () => {
			this._eventSubscribers.get(event)?.delete(wrapped);
		};
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtimes/external-store/thread-message-converter.js
var ThreadMessageConverter = class {
	cache = /* @__PURE__ */ new WeakMap();
	convertMessages(messages, converter) {
		return messages.map((m, idx) => {
			const newMessage = converter(this.cache.get(m), m, idx);
			this.cache.set(m, newMessage);
			return newMessage;
		});
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/utils/json/is-json.js
function isRecord(value) {
	return value != null && typeof value === "object" && !Array.isArray(value);
}
function isJSONValue(value, currentDepth = 0) {
	if (currentDepth > 100) return false;
	if (value === null || typeof value === "string" || typeof value === "boolean") return true;
	if (typeof value === "number") return !Number.isNaN(value) && Number.isFinite(value);
	if (Array.isArray(value)) return value.every((item) => isJSONValue(item, currentDepth + 1));
	if (isRecord(value)) return Object.entries(value).every(([key, val]) => typeof key === "string" && isJSONValue(val, currentDepth + 1));
	return false;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/utils/json/is-json-equal.js
var MAX_JSON_DEPTH = 100;
var isJSONValueEqualAtDepth = (a, b, currentDepth) => {
	if (a === b) return true;
	if (currentDepth > MAX_JSON_DEPTH) return false;
	if (a == null || b == null) return false;
	if (Array.isArray(a)) {
		if (!Array.isArray(b) || a.length !== b.length) return false;
		return a.every((item, index) => isJSONValueEqualAtDepth(item, b[index], currentDepth + 1));
	}
	if (Array.isArray(b)) return false;
	if (!isRecord(a) || !isRecord(b)) return false;
	const aKeys = Object.keys(a);
	const bKeys = Object.keys(b);
	if (aKeys.length !== bKeys.length) return false;
	return aKeys.every((key) => Object.hasOwn(b, key) && isJSONValueEqualAtDepth(a[key], b[key], currentDepth + 1));
};
var isJSONValueEqual = (a, b) => {
	if (!isJSONValue(a) || !isJSONValue(b)) return false;
	return isJSONValueEqualAtDepth(a, b, 0);
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtimes/tool-invocations/ToolInvocationTracker.js
var isArgsTextComplete = (argsText) => {
	try {
		JSON.parse(argsText);
		return true;
	} catch {
		return false;
	}
};
var parseArgsText = (argsText) => {
	try {
		return JSON.parse(argsText);
	} catch {
		return;
	}
};
var isEquivalentCompleteArgsText = (previous, next) => {
	const previousValue = parseArgsText(previous);
	const nextValue = parseArgsText(next);
	if (previousValue === void 0 || nextValue === void 0) return false;
	return isJSONValueEqual(previousValue, nextValue);
};
/**
* Plain-class port of the former `useToolInvocations` React hook. Owns the
* assistant-stream pipeline that drives client-side `streamCall` / `execute`
* for tool-call parts surfaced by a thread runtime, plus the per-tool-call
* status map that consumers render against.
*
* **Contract**: `streamCall` (and `execute`) fires exactly once per logical
* `toolCallId`. Args mutations after first completion, result replacement,
* and result clearing are *not* surfaced through additional `streamCall`
* invocations — by design — so hosts cannot observe spurious re-fires of
* side effects. The follow-up `reader.events()` API will expose those
* post-completion transitions to consumers that opt in.
*
* State-transition safety: every public method that observes runtime state
* (`setState`, `reset`, `abort`, `resume`) wraps its work in try/catch and
* logs to `console.error` rather than throwing. The tracker is built into
* the hot message-processing path, so a malformed snapshot must never crash
* the host runtime. See ./EDGE_CASES.md for the known non-trivial state
* transitions and what each does today.
*/
var ToolInvocationTracker = class {
	_getTools;
	_callbacks;
	_entries = /* @__PURE__ */ new Map();
	/**
	* Tool call ids whose `execute` should be short-circuited in the wrapper.
	* Populated when an entry is created with a result already attached
	* (history reload, mid-run resume, etc.) — `execute` is suppressed so
	* client-side side effects don't double-run. Membership outlives the
	* entry: `reset()` deliberately does *not* clear this so post-abort
	* cancellation `result` chunks for pre-resolved entries can still be
	* recognized and dropped. Growth is bounded by the number of pre-resolved
	* tool calls observed in the session.
	*/
	_skipExecuteStreamIds = /* @__PURE__ */ new Set();
	_humanInput = /* @__PURE__ */ new Map();
	/** In-flight `execute` invocations keyed by tool call id. */
	_executing = /* @__PURE__ */ new Set();
	_settledResolvers = [];
	_statuses = /* @__PURE__ */ new Map();
	_ac = new AbortController();
	_pendingRestore = true;
	/** Cached last snapshot, used to skip processing on identical re-renders. */
	_lastSnapshot = null;
	_isRunning = false;
	_controller;
	/**
	* Set when the assistant-stream pipeline has died (errored out via
	* `.pipeTo(...).catch(...)`). The next `setState` re-initializes the
	* pipeline and demotes all active entries to restored so they survive
	* across the restart without re-firing `streamCall` (preserves the
	* "exactly once" contract). Capped at a single auto-restart per session
	* — repeated failures keep the tracker dead with a more visible error.
	*/
	_pipelineDead = false;
	_pipelineRestartUsed = false;
	constructor(getTools, callbacks) {
		this._getTools = getTools;
		this._callbacks = callbacks;
		this._initPipeline();
	}
	/**
	* Build the assistant-stream pipeline. Called once from the constructor
	* and at most once again if `_pipelineDead` is set (see F.4 in
	* EDGE_CASES.md).
	*/
	_initPipeline() {
		const [stream, controller] = createAssistantStreamController();
		this._controller = controller;
		const transform = toolResultStream(() => this._getWrappedTools(), () => this._ac.signal, (toolCallId, payload) => this._onHumanInput(toolCallId, payload), {
			onExecutionStart: (id) => this._onExecutionStart(id),
			onExecutionEnd: (id) => this._onExecutionEnd(id)
		});
		stream.pipeThrough(transform).pipeThrough(new AssistantMetaTransformStream()).pipeTo(new WritableStream({ write: (chunk) => {
			try {
				if (chunk.type !== "result") return;
				this._handleResultChunk(chunk);
			} catch (err) {
				console.error("[ToolInvocationTracker] result chunk handling failed", err);
			}
		} })).catch((err) => {
			console.error("[ToolInvocationTracker] stream pipeline failed; will attempt single restart on next setState", err);
			this._pipelineDead = true;
		});
	}
	/**
	* Feed the next observed snapshot into the tracker. Called from the host
	* runtime whenever its message list / running state changes.
	*/
	setState(snapshot) {
		try {
			if (this._pipelineDead) {
				if (this._pipelineRestartUsed) return;
				this._pipelineRestartUsed = true;
				this._pipelineDead = false;
				this._demoteEntriesToRestored();
				this._executing.clear();
				this._ac = new AbortController();
				this._initPipeline();
			}
			if (this._lastSnapshot && this._lastSnapshot.messages === snapshot.messages && this._lastSnapshot.isRunning === snapshot.isRunning && this._lastSnapshot.isLoading === snapshot.isLoading) return;
			if (snapshot.isLoading === true) this._pendingRestore = true;
			const previousIsRunning = this._isRunning;
			this._isRunning = snapshot.isRunning;
			try {
				this._processMessages(snapshot.messages);
			} catch (err) {
				this._isRunning = previousIsRunning;
				throw err;
			}
			this._lastSnapshot = snapshot;
			this._pendingRestore = false;
		} catch (err) {
			console.error("[ToolInvocationTracker] setState failed; snapshot dropped", err);
		}
	}
	/**
	* Reset the tracker so the next observed snapshot is treated as historical.
	* Clears entries and aborts any in-flight executions. Used by callers like
	* `importExternalState` to mark a freshly loaded state as restored.
	*/
	reset() {
		try {
			this._pendingRestore = true;
			this._entries.clear();
			this._lastSnapshot = null;
			this.abort().finally(() => {
				this._executing.clear();
			});
		} catch (err) {
			console.error("[ToolInvocationTracker] reset failed", err);
		}
	}
	/**
	* Abort any in-flight `execute()` invocations. Resolves once all of them
	* have settled (or immediately if none are running).
	*/
	abort() {
		try {
			this._humanInput.forEach(({ reject }) => {
				try {
					reject(/* @__PURE__ */ new Error("Tool execution aborted"));
				} catch {}
			});
			this._humanInput.clear();
			this._ac.abort();
			this._ac = new AbortController();
			if (this._executing.size === 0) return Promise.resolve();
			return new Promise((resolve) => {
				this._settledResolvers.push(resolve);
			});
		} catch (err) {
			console.error("[ToolInvocationTracker] abort failed", err);
			return Promise.resolve();
		}
	}
	/**
	* Resolve a pending human-input request for the given tool call. Returns
	* `true` if a pending request was resumed, `false` if the tracker has no
	* outstanding request for that id (the caller should fall back to its own
	* dispatch path).
	*/
	resume(toolCallId, payload) {
		try {
			const handlers = this._humanInput.get(toolCallId);
			if (!handlers) return false;
			this._humanInput.delete(toolCallId);
			this._setStatus(toolCallId, { type: "executing" });
			handlers.resolve(payload);
			return true;
		} catch (err) {
			console.error("[ToolInvocationTracker] resume failed", err);
			return false;
		}
	}
	/**
	* Returns the current tool execution status map. The returned `Map` is
	* the tracker's internal store — do not mutate it. Treat the reference
	* as a snapshot that may be replaced wholesale on the next status
	* transition.
	*/
	getStatuses() {
		return this._statuses;
	}
	_getWrappedTools() {
		const tools = this._getTools();
		if (!tools) return void 0;
		return Object.fromEntries(Object.entries(tools).map(([name, tool]) => {
			const execute = tool.execute;
			if (execute === void 0) return [name, tool];
			return [name, {
				...tool,
				execute: (...[args, context]) => {
					if (this._skipExecuteStreamIds.has(context.toolCallId)) return new Promise(() => {});
					return execute(args, context);
				}
			}];
		}));
	}
	_onHumanInput(toolCallId, payload) {
		return new Promise((resolve, reject) => {
			const previous = this._humanInput.get(toolCallId);
			if (previous) try {
				previous.reject(/* @__PURE__ */ new Error("Human input request was superseded by a new request"));
			} catch {}
			this._humanInput.set(toolCallId, {
				resolve,
				reject
			});
			this._setStatus(toolCallId, {
				type: "interrupt",
				payload: {
					type: "human",
					payload
				}
			});
		});
	}
	_onExecutionStart(toolCallId) {
		if (this._skipExecuteStreamIds.has(toolCallId)) return;
		this._executing.add(toolCallId);
		this._setStatus(toolCallId, { type: "executing" });
	}
	_onExecutionEnd(toolCallId) {
		if (!this._executing.delete(toolCallId)) return;
		this._deleteStatus(toolCallId);
		if (this._executing.size === 0) this._settledResolvers.splice(0).forEach((resolve) => {
			try {
				resolve();
			} catch {}
		});
	}
	_handleResultChunk(chunk) {
		const toolCallId = chunk.meta.toolCallId;
		const entry = this._entries.get(toolCallId);
		if (!entry && this._skipExecuteStreamIds.has(toolCallId)) return;
		if (entry?.hasResult) return;
		this._invokeOnResult({
			type: "add-tool-result",
			toolCallId,
			toolName: chunk.meta.toolName,
			result: chunk.result,
			isError: chunk.isError,
			...chunk.artifact !== void 0 && { artifact: chunk.artifact },
			...chunk.modelContent !== void 0 && { modelContent: chunk.modelContent }
		});
	}
	_invokeOnResult(command) {
		try {
			this._callbacks.onResult(command);
		} catch (err) {
			console.error("[ToolInvocationTracker] onResult callback threw; result dropped", err);
		}
	}
	_invokeOnStatusesChange() {
		try {
			this._callbacks.onStatusesChange(this._statuses);
		} catch (err) {
			console.error("[ToolInvocationTracker] onStatusesChange callback threw; status change not propagated", err);
		}
	}
	_setStatus(toolCallId, status) {
		const next = new Map(this._statuses);
		next.set(toolCallId, status);
		this._statuses = next;
		this._invokeOnStatusesChange();
	}
	_deleteStatus(toolCallId) {
		if (!this._statuses.has(toolCallId)) return;
		const next = new Map(this._statuses);
		next.delete(toolCallId);
		this._statuses = next;
		this._invokeOnStatusesChange();
	}
	_hasExecutableTool(toolName) {
		const tool = this._getTools()?.[toolName];
		return tool?.execute !== void 0 || tool?.streamCall !== void 0;
	}
	_shouldCloseArgsStream({ toolName, argsText, hasResult }) {
		if (hasResult) return true;
		if (!this._hasExecutableTool(toolName)) return !this._isRunning && isArgsTextComplete(argsText);
		return isArgsTextComplete(argsText);
	}
	_startActiveEntry(toolCallId, toolName, skipExecute) {
		const toolCallController = this._controller.addToolCallPart({
			toolName,
			toolCallId
		});
		if (skipExecute) this._skipExecuteStreamIds.add(toolCallId);
		const entry = {
			toolName,
			controller: toolCallController,
			argsText: "",
			hasResult: false,
			argsComplete: false
		};
		this._entries.set(toolCallId, entry);
		return entry;
	}
	/**
	* Demote every active entry back to the restored phase. Used by the
	* pipeline-restart path so that, after a fresh pipeline is built, the
	* next observed snapshot does not re-fire `streamCall` for tool calls
	* that already fired pre-death. Args / hasResult tracking is preserved
	* so signature comparisons still work.
	*/
	_demoteEntriesToRestored() {
		for (const [toolCallId, entry] of this._entries) {
			if (!entry.controller) continue;
			this._entries.set(toolCallId, {
				toolName: entry.toolName,
				argsText: entry.argsText,
				hasResult: entry.hasResult
			});
		}
	}
	_processArgsText(entry, content) {
		if (!entry.controller) return;
		const hasResult = content.result !== void 0;
		if (content.argsText !== entry.argsText) {
			let shouldWriteArgsText = true;
			if (entry.argsComplete) if (isEquivalentCompleteArgsText(entry.argsText, content.argsText)) {
				entry.argsText = content.argsText;
				shouldWriteArgsText = false;
			} else shouldWriteArgsText = false;
			else if (!content.argsText.startsWith(entry.argsText)) if (isArgsTextComplete(entry.argsText) && isArgsTextComplete(content.argsText) && isEquivalentCompleteArgsText(entry.argsText, content.argsText)) {
				const shouldClose = this._shouldCloseArgsStream({
					toolName: content.toolName,
					argsText: content.argsText,
					hasResult
				});
				if (shouldClose) entry.controller.argsText.close();
				entry.argsText = content.argsText;
				entry.argsComplete = shouldClose;
				shouldWriteArgsText = false;
			} else shouldWriteArgsText = false;
			if (shouldWriteArgsText && entry.controller) {
				const delta = content.argsText.slice(entry.argsText.length);
				entry.controller.argsText.append(delta);
				const shouldClose = this._shouldCloseArgsStream({
					toolName: content.toolName,
					argsText: content.argsText,
					hasResult
				});
				if (shouldClose) entry.controller.argsText.close();
				entry.argsText = content.argsText;
				entry.argsComplete = shouldClose;
			}
		}
		if (!entry.argsComplete && entry.controller) {
			if (this._shouldCloseArgsStream({
				toolName: content.toolName,
				argsText: content.argsText,
				hasResult
			})) {
				entry.controller.argsText.close();
				entry.argsText = content.argsText;
				entry.argsComplete = true;
			}
		}
	}
	_processMessages(messages) {
		const isRestore = this._pendingRestore;
		for (const message of messages) {
			if (!message || !Array.isArray(message.content)) continue;
			for (const content of message.content) {
				if (!content || content.type !== "tool-call") continue;
				const existing = this._entries.get(content.toolCallId);
				if (isRestore) {
					if (!existing?.controller) this._entries.set(content.toolCallId, {
						toolName: content.toolName,
						argsText: content.argsText,
						hasResult: content.result !== void 0
					});
					if (content.messages) this._processMessages(content.messages);
					continue;
				}
				let entry = existing;
				if (entry && !entry.controller) {
					if (!(content.argsText !== entry.argsText || content.result !== void 0 !== entry.hasResult)) {
						if (content.messages) this._processMessages(content.messages);
						continue;
					}
					this._entries.delete(content.toolCallId);
					entry = void 0;
				}
				if (!entry) entry = this._startActiveEntry(content.toolCallId, content.toolName, content.result !== void 0);
				this._processArgsText(entry, content);
				if (content.result !== void 0 && !entry.hasResult) {
					const { controller: activeController } = entry;
					if (!activeController) continue;
					entry.hasResult = true;
					entry.argsComplete = true;
					activeController.setResponse(new ToolResponse({
						result: content.result,
						artifact: content.artifact,
						isError: content.isError,
						...content.modelContent !== void 0 ? { modelContent: content.modelContent } : {}
					}));
					activeController.close();
				}
				if (content.messages) this._processMessages(content.messages);
			}
		}
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtimes/external-store/external-store-thread-runtime-core.js
var EMPTY_ARRAY = Object.freeze([]);
var shallowEqual = (a, b) => {
	const aKeys = Object.keys(a);
	if (aKeys.length !== Object.keys(b).length) return false;
	for (const key of aKeys) if (a[key] !== b[key]) return false;
	return true;
};
var hasUpcomingMessage = (isRunning, messages) => {
	return isRunning && messages[messages.length - 1]?.role !== "assistant";
};
var ExternalStoreThreadRuntimeCore = class extends BaseThreadRuntimeCore {
	_capabilities = {
		switchToBranch: false,
		switchBranchDuringRun: false,
		edit: false,
		delete: false,
		reload: false,
		cancel: false,
		unstable_copy: false,
		speech: false,
		dictation: false,
		voice: false,
		attachments: false,
		feedback: false,
		queue: false
	};
	get capabilities() {
		return this._capabilities;
	}
	_messages;
	isDisabled;
	isSendDisabled;
	get isLoading() {
		return this._store.isLoading ?? false;
	}
	get isRunning() {
		return this._store.isRunning;
	}
	_getBaseMessages() {
		return this._messages;
	}
	get state() {
		return this._store.state ?? super.state;
	}
	get adapters() {
		return this._store.adapters;
	}
	suggestions = [];
	extras = void 0;
	_converter = new ThreadMessageConverter();
	_store;
	/**
	* Client-side tool-invocations pipeline. Constructed lazily on first
	* snapshot — only when `adapter.unstable_enableToolInvocations === true`.
	*/
	_toolInvocations = null;
	beginEdit(messageId) {
		if (!this._store.onEdit) throw new Error("Runtime does not support editing.");
		super.beginEdit(messageId);
	}
	constructor(contextProvider, store) {
		super(contextProvider);
		this.__internal_setAdapter(store);
	}
	__internal_setAdapter(store) {
		if (this._store === store) return;
		const isRunning = store.isRunning ?? false;
		this.isDisabled = store.isDisabled ?? false;
		this.isSendDisabled = store.isSendDisabled ?? false;
		const oldStore = this._store;
		this._store = store;
		if (this.extras !== store.extras) this.extras = store.extras;
		const newSuggestions = store.suggestions ?? EMPTY_ARRAY;
		if (!shallowEqual(this.suggestions, newSuggestions)) this.suggestions = newSuggestions;
		const newCapabilities = {
			switchToBranch: this._store.setMessages !== void 0,
			switchBranchDuringRun: false,
			edit: this._store.onEdit !== void 0,
			delete: this._store.onDelete !== void 0 || this._store.setMessages !== void 0,
			reload: this._store.onReload !== void 0,
			cancel: this._store.onCancel !== void 0,
			speech: this._store.adapters?.speech !== void 0,
			dictation: this._store.adapters?.dictation !== void 0,
			voice: this._store.adapters?.voice !== void 0,
			unstable_copy: this._store.unstable_capabilities?.copy !== false,
			attachments: !!this._store.adapters?.attachments,
			feedback: !!this._store.adapters?.feedback,
			queue: this._store.queue !== void 0
		};
		if (!shallowEqual(this._capabilities, newCapabilities)) this._capabilities = newCapabilities;
		let messages;
		if (store.messageRepository) {
			if (oldStore && oldStore.isRunning === store.isRunning && oldStore.messageRepository === store.messageRepository) {
				this._notifySubscribers();
				return;
			}
			const incoming = store.messageRepository.messages;
			const headId = store.messageRepository.headId ?? incoming.at(-1)?.message.id ?? null;
			if (oldStore && oldStore.messageRepository === store.messageRepository) {
				this.repository.resetHead(headId);
				messages = this.repository.getMessages();
			} else {
				const incomingIds = new Set(incoming.map(({ message }) => message.id));
				for (const { message, parentId } of incoming) this.repository.addOrUpdateMessage(parentId, message);
				for (const { message } of this.repository.export().messages) if (!incomingIds.has(message.id)) this.repository.deleteMessage(message.id);
				this.repository.resetHead(headId);
				messages = this.repository.getMessages();
			}
		} else if (store.messages) {
			if (oldStore) {
				if (oldStore.convertMessage !== store.convertMessage) this._converter = new ThreadMessageConverter();
				else if (oldStore.isRunning === store.isRunning && oldStore.messages === store.messages) {
					this._notifySubscribers();
					return;
				}
			}
			messages = !store.convertMessage ? store.messages : this._converter.convertMessages(store.messages, (cache, m, idx) => {
				if (!store.convertMessage) return m;
				const autoStatus = getAutoStatus(idx === (store.messages?.length ?? 0) - 1, isRunning, false, false, void 0);
				if (cache && (cache.role !== "assistant" || !isAutoStatus(cache.status) || cache.status === autoStatus)) return cache;
				const newMessage = fromThreadMessageLike(store.convertMessage(m, idx), idx.toString(), autoStatus);
				bindExternalStoreMessage(newMessage, m);
				return newMessage;
			});
			for (let i = 0; i < messages.length; i++) {
				const message = messages[i];
				const parent = messages[i - 1];
				this.repository.addOrUpdateMessage(parent?.id ?? null, message);
			}
		} else throw new Error("ExternalStoreAdapter must provide either 'messages' or 'messageRepository'");
		if (messages.length > 0) this.ensureInitialized();
		if ((oldStore?.isRunning ?? false) !== (store.isRunning ?? false)) if (store.isRunning) this._notifyEventSubscribers("runStart", {});
		else this._notifyEventSubscribers("runEnd", {});
		let optimisticId = null;
		if (hasUpcomingMessage(isRunning, messages)) {
			optimisticId = generateId();
			this.repository.addOrUpdateMessage(messages.at(-1)?.id ?? null, fromThreadMessageLike({
				role: "assistant",
				content: [],
				metadata: { isOptimistic: true }
			}, optimisticId, { type: "running" }));
		}
		this.repository.resetHead(optimisticId ?? messages.at(-1)?.id ?? null);
		this._messages = this.repository.getMessages();
		this._driveToolInvocations();
		this._notifySubscribers();
	}
	/**
	* Feed the current message snapshot into the tool-invocations tracker.
	* Opt-in via `adapter.unstable_enableToolInvocations: true`. The tracker
	* itself is fail-silent — see ToolInvocationTracker for the
	* state-transition contract.
	*/
	_driveToolInvocations() {
		if (!this._store.unstable_enableToolInvocations) {
			if (this._toolInvocations) {
				this._toolInvocations.reset();
				this._toolInvocations = null;
				this._store.setToolStatuses?.({});
			}
			return;
		}
		if (!this._toolInvocations) this._toolInvocations = new ToolInvocationTracker(() => this.getModelContext().tools, {
			onResult: (command) => {
				try {
					const messageId = this._findMessageIdForToolCall(command.toolCallId);
					if (messageId === void 0) return;
					this._store.onAddToolResult?.({
						messageId,
						toolCallId: command.toolCallId,
						toolName: command.toolName,
						result: command.result,
						isError: command.isError,
						...command.artifact !== void 0 && { artifact: command.artifact },
						...command.modelContent !== void 0 && { modelContent: command.modelContent }
					});
				} catch (err) {
					console.error("[ExternalStoreThreadRuntimeCore] onAddToolResult dispatch failed", err);
				}
			},
			onStatusesChange: (statuses) => {
				this._store.setToolStatuses?.(Object.fromEntries(statuses));
			}
		});
		this._toolInvocations.setState({
			messages: this._messages,
			isRunning: this._store.isRunning ?? false,
			...this._store.isLoading !== void 0 && { isLoading: this._store.isLoading }
		});
	}
	/**
	* Lookup table from `toolCallId` to the owning assistant message's `id`,
	* rebuilt lazily when `_messages` changes (see `_messagesForToolCallIndex`).
	*/
	_toolCallToMessageId = /* @__PURE__ */ new Map();
	_messagesForToolCallIndex = null;
	/**
	* Look up the assistant message that owns a tool-call part. Lazily builds
	* (and caches) a `toolCallId → messageId` map keyed off the current
	* `_messages` reference, so onResult dispatches stay O(1) instead of
	* walking the full thread on every result.
	*/
	_findMessageIdForToolCall(toolCallId) {
		if (this._messagesForToolCallIndex !== this._messages) {
			this._toolCallToMessageId.clear();
			const visit = (messages) => {
				for (const message of messages) {
					if (!Array.isArray(message.content)) continue;
					for (const part of message.content) {
						if (!part || part.type !== "tool-call") continue;
						this._toolCallToMessageId.set(part.toolCallId, message.id);
						if (part.messages) visit(part.messages);
					}
				}
			};
			visit(this._messages);
			this._messagesForToolCallIndex = this._messages;
		}
		return this._toolCallToMessageId.get(toolCallId);
	}
	switchToBranch(branchId) {
		if (!this._store.setMessages) throw new Error("Runtime does not support switching branches.");
		if (this._store.isRunning) return;
		this.repository.switchToBranch(branchId);
		this.updateMessages(this.repository.getMessages());
	}
	async append(message) {
		const isEdit = message.parentId !== (this.messages.at(-1)?.id ?? null);
		if (!isEdit && this._store.queue) {
			this._store.queue.enqueue(message, { steer: message.steer ?? false });
			return;
		}
		if (message.startRun ?? message.role === "user") await this._toolInvocations?.abort();
		if (isEdit) {
			if (!this._store.onEdit) throw new Error("Runtime does not support editing messages.");
			this._store.queue?.clear("edit");
			await this._store.onEdit(message);
		} else await this._store.onNew(message);
	}
	async deleteMessage(messageId) {
		if (this._store.onDelete) {
			await this._store.onDelete(messageId);
			return;
		}
		if (!this._store.setMessages) throw new Error("Runtime does not support deleting messages.");
		if (this._store.isRunning) await this._toolInvocations?.abort();
		const messages = this.repository.getMessages();
		if (messages.findIndex((m) => m.id === messageId) === -1) throw new Error("Message not found.");
		this.updateMessages(messages.filter((message) => message.id !== messageId));
	}
	getQueueItems() {
		return this._store?.queue?.items ?? EMPTY_QUEUE_ITEMS;
	}
	steerQueueItem(queueItemId) {
		this._store?.queue?.steer(queueItemId);
	}
	removeQueueItem(queueItemId) {
		this._store?.queue?.remove(queueItemId);
	}
	async startRun(config) {
		if (!this._store.onReload) throw new Error("Runtime does not support reloading messages.");
		this._store.queue?.clear("reload");
		await this._toolInvocations?.abort();
		await this._store.onReload(config.parentId, config);
	}
	async resumeRun(config) {
		if (!this._store.onResume) throw new Error("Runtime does not support resuming runs.");
		await this._store.onResume(config);
	}
	exportExternalState() {
		if (!this._store.onExportExternalState) throw new Error("Runtime does not support exporting external states.");
		return this._store.onExportExternalState();
	}
	importExternalState(state) {
		if (!this._store.onLoadExternalState) throw new Error("Runtime does not support importing external states.");
		if (this._toolInvocations) {
			this._toolInvocations.reset();
			this._store.setToolStatuses?.({});
		}
		this._store.onLoadExternalState(state);
	}
	cancelRun() {
		if (!this._store.onCancel) throw new Error("Runtime does not support cancelling runs.");
		this._store.queue?.clear("cancel-run");
		this._toolInvocations?.abort();
		this._store.onCancel();
		const head = this.repository.getMessages().at(-1);
		if (head && head.metadata.isOptimistic && head.content.length === 0) this.repository.deleteMessage(head.id);
		let messages = this.repository.getMessages();
		const previousMessage = messages[messages.length - 1];
		if (previousMessage?.role === "user" && previousMessage.id === messages.at(-1)?.id) {
			this.repository.deleteMessage(previousMessage.id);
			if (!this.composer.text.trim()) this.composer.setText(getThreadMessageText(previousMessage));
			messages = this.repository.getMessages();
		} else this._notifySubscribers();
		setTimeout(() => {
			this.updateMessages(messages);
		}, 0);
	}
	addToolResult(options) {
		if (!this._store.onAddToolResult) throw new Error("Runtime does not support tool results.");
		this._store.onAddToolResult?.(options);
	}
	resumeToolCall(options) {
		if (this._toolInvocations?.resume(options.toolCallId, options.payload) ?? false) return;
		if (this._store.onResumeToolCall) {
			this._store.onResumeToolCall(options);
			return;
		}
		throw new Error(`Tool call ${options.toolCallId} is not waiting for resume.`);
	}
	respondToToolApproval(options) {
		if (!this._store.onRespondToToolApproval) throw new Error("Runtime does not support tool approvals.");
		this._store.onRespondToToolApproval(options);
	}
	reset(initialMessages) {
		const repo = new MessageRepository();
		repo.import(ExportedMessageRepository.fromArray(initialMessages ?? []));
		this.updateMessages(repo.getMessages());
	}
	import(data) {
		super.import(data);
		if (this._store.onImport) this._store.onImport(this.repository.getMessages());
	}
	updateMessages = (messages) => {
		if (this._store.convertMessage !== void 0) this._store.setMessages?.(messages.flatMap(getExternalStoreMessages));
		else this._store.setMessages?.(messages);
	};
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/runtimes/external-store/external-store-runtime-core.js
var getThreadListAdapter = (store) => {
	return store.adapters?.threadList ?? {};
};
var ExternalStoreRuntimeCore = class extends BaseAssistantRuntimeCore {
	threads;
	constructor(adapter) {
		super();
		this.threads = new ExternalStoreThreadListRuntimeCore(getThreadListAdapter(adapter), () => new ExternalStoreThreadRuntimeCore(this._contextProvider, adapter));
	}
	setAdapter(adapter) {
		this.threads.__internal_setAdapter(getThreadListAdapter(adapter));
		this.threads.getMainThreadRuntimeCore().__internal_setAdapter(adapter);
	}
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/runtimes/useExternalStoreRuntime.js
var useExternalStoreRuntime = (store) => {
	const $ = c(11);
	let t0;
	if ($[0] !== store) {
		t0 = () => new ExternalStoreRuntimeCore(store);
		$[0] = store;
		$[1] = t0;
	} else t0 = $[1];
	const [runtime] = useState(t0);
	let t1;
	if ($[2] !== runtime || $[3] !== store) {
		t1 = () => {
			runtime.setAdapter(store);
		};
		$[2] = runtime;
		$[3] = store;
		$[4] = t1;
	} else t1 = $[4];
	useEffect(t1);
	const { modelContext } = useRuntimeAdapters() ?? {};
	let t2;
	let t3;
	if ($[5] !== modelContext || $[6] !== runtime) {
		t2 = () => {
			if (!modelContext) return;
			return runtime.registerModelContextProvider(modelContext);
		};
		t3 = [modelContext, runtime];
		$[5] = modelContext;
		$[6] = runtime;
		$[7] = t2;
		$[8] = t3;
	} else {
		t2 = $[7];
		t3 = $[8];
	}
	useEffect(t2, t3);
	let t4;
	if ($[9] !== runtime) {
		t4 = new AssistantRuntimeImpl(runtime);
		$[9] = runtime;
		$[10] = t4;
	} else t4 = $[10];
	return t4;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/thread/ThreadMessages.js
var isComponentsSame = (prev, next) => {
	return prev.Message === next.Message && prev.EditComposer === next.EditComposer && prev.UserEditComposer === next.UserEditComposer && prev.AssistantEditComposer === next.AssistantEditComposer && prev.SystemEditComposer === next.SystemEditComposer && prev.UserMessage === next.UserMessage && prev.AssistantMessage === next.AssistantMessage && prev.SystemMessage === next.SystemMessage;
};
var DEFAULT_SYSTEM_MESSAGE = () => null;
var getComponent$2 = (components, role, isEditing) => {
	switch (role) {
		case "user": if (isEditing) return components.UserEditComposer ?? components.EditComposer ?? components.UserMessage ?? components.Message;
		else return components.UserMessage ?? components.Message;
		case "assistant": if (isEditing) return components.AssistantEditComposer ?? components.EditComposer ?? components.AssistantMessage ?? components.Message;
		else return components.AssistantMessage ?? components.Message;
		case "system": if (isEditing) return components.SystemEditComposer ?? components.EditComposer ?? components.SystemMessage ?? components.Message;
		else return components.SystemMessage ?? components.Message ?? DEFAULT_SYSTEM_MESSAGE;
		default: throw new Error(`Unknown message role: ${role}`);
	}
};
var ThreadMessageComponent = (t0) => {
	const $ = c(6);
	const { components } = t0;
	const role = useAuiState(_temp$11);
	const isEditing = useAuiState(_temp2$1);
	let t1;
	if ($[0] !== components || $[1] !== isEditing || $[2] !== role) {
		t1 = getComponent$2(components, role, isEditing);
		$[0] = components;
		$[1] = isEditing;
		$[2] = role;
		$[3] = t1;
	} else t1 = $[3];
	const Component = t1;
	let t2;
	if ($[4] !== Component) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {});
		$[4] = Component;
		$[5] = t2;
	} else t2 = $[5];
	return t2;
};
/**
* Renders a single message at the specified index in the current thread.
*/
var ThreadPrimitiveMessageByIndex = (0, react_shim_exports.memo)((t0) => {
	const $ = c(5);
	const { index, components } = t0;
	let t1;
	if ($[0] !== components) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadMessageComponent, { components });
		$[0] = components;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== index || $[3] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessageByIndexProvider, {
			index,
			children: t1
		});
		$[2] = index;
		$[3] = t1;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
}, (prev, next) => prev.index === next.index && isComponentsSame(prev.components, next.components));
ThreadPrimitiveMessageByIndex.displayName = "ThreadPrimitive.MessageByIndex";
var ThreadPrimitiveMessagesInner = ({ children }) => {
	const messagesLength = useAuiState((s) => s.thread.messages.length);
	return useMemo(() => {
		if (messagesLength === 0) return null;
		return Array.from({ length: messagesLength }, (_, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessageByIndexProvider, {
			index,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderChildrenWithAccessor, {
				getItemState: (aui) => aui.thread().message({ index }).getState(),
				children: (getItem) => children({ get message() {
					return getItem();
				} })
			})
		}, index));
	}, [messagesLength, children]);
};
/**
* Renders all messages in the current thread.
*
* @example
* ```tsx
* <ThreadPrimitive.Messages>
*   {({ message }) => {
*     if (message.role === "user") return <MyUserMessage />;
*     return <MyAssistantMessage />;
*   }}
* </ThreadPrimitive.Messages>
* ```
*/
var ThreadPrimitiveMessagesImpl = (t0) => {
	const $ = c(4);
	const { components, children } = t0;
	if (components) {
		let t1;
		if ($[0] !== components) {
			t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadPrimitiveMessagesInner, { children: () => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadMessageComponent, { components }) });
			$[0] = components;
			$[1] = t1;
		} else t1 = $[1];
		return t1;
	}
	let t1;
	if ($[2] !== children) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadPrimitiveMessagesInner, { children });
		$[2] = children;
		$[3] = t1;
	} else t1 = $[3];
	return t1;
};
ThreadPrimitiveMessagesImpl.displayName = "ThreadPrimitive.Messages";
var ThreadPrimitiveMessages = (0, react_shim_exports.memo)(ThreadPrimitiveMessagesImpl, (prev, next) => {
	if (prev.children || next.children) return prev.children === next.children;
	return isComponentsSame(prev.components, next.components);
});
function _temp$11(s) {
	return s.message.role;
}
function _temp2$1(s_0) {
	return s_0.message.composer.isEditing;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/utils/getMessageQuote.js
var getMessageQuote = (state) => {
	const metadata = state.message.metadata;
	if (!metadata || typeof metadata !== "object") return void 0;
	return metadata.custom?.quote;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/generativeUI/GenerativeUI.js
/**
* Thrown when a generative-ui spec references a component name that is not
* present in the consumer-provided allowlist. The allowlist is the security
* boundary in the same-realm rendering path — there is no fallback by
* default. Pass `Fallback` to opt into a soft-fail UX.
*/
var GenerativeUIRenderError = class extends Error {
	componentName;
	constructor(componentName, message = `Component "${componentName}" is not in the generative-ui allowlist.`) {
		super(message);
		this.name = "GenerativeUIRenderError";
		this.componentName = componentName;
	}
};
var isObjectNode = (node) => typeof node === "object" && node !== null;
var renderNode$1 = (node, components, Fallback, path) => {
	if (node === void 0 || node === null) return null;
	if (typeof node === "string") return node;
	if (!isObjectNode(node) || !("component" in node)) return null;
	const { component, props, children, key } = node;
	const Resolved = components[component];
	if (!Resolved) {
		if (Fallback) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Fallback, {
			component,
			props
		}, key ?? path);
		throw new GenerativeUIRenderError(component);
	}
	const renderedChildren = children?.length ? children.map((child, i) => renderNode$1(child, components, Fallback, `${path}/${i}`)) : void 0;
	return (0, react_shim_exports.createElement)(Resolved, {
		...props ?? {},
		key: key ?? path
	}, ...renderedChildren ?? []);
};
var normalizeRoot = (spec) => {
	if (!spec || spec.root === void 0 || spec.root === null) return [];
	const root = spec.root;
	return Array.isArray(root) ? root : [root];
};
/**
* Internal renderer. Resolves a {@link GenerativeUISpec} against the consumer
* allowlist. Used by `MessagePrimitive.GenerativeUI` and by
* `MessagePrimitive.Parts` when handling a `generative-ui` part.
*/
var GenerativeUIRender = (t0) => {
	const $ = c(11);
	const { spec, components, Fallback } = t0;
	let t1;
	if ($[0] !== spec) {
		t1 = normalizeRoot(spec);
		$[0] = spec;
		$[1] = t1;
	} else t1 = $[1];
	const nodes = t1;
	let t2;
	if ($[2] !== Fallback || $[3] !== components || $[4] !== nodes) {
		let t3;
		if ($[6] !== Fallback || $[7] !== components) {
			t3 = (node, i) => renderNode$1(node, components, Fallback, `${i}`);
			$[6] = Fallback;
			$[7] = components;
			$[8] = t3;
		} else t3 = $[8];
		t2 = nodes.map(t3);
		$[2] = Fallback;
		$[3] = components;
		$[4] = nodes;
		$[5] = t2;
	} else t2 = $[5];
	let t3;
	if ($[9] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_jsx_runtime.Fragment, { children: t2 });
		$[9] = t2;
		$[10] = t3;
	} else t3 = $[10];
	return t3;
};
GenerativeUIRender.displayName = "GenerativeUIRender";
/**
* Renders a generative-ui message part using a consumer-provided allowlist.
*
* The agent emits a `generative-ui` message part containing a JSON spec
* (see {@link GenerativeUISpec}). This primitive walks the spec and resolves
* each `component` name against the allowlist. Names not in the allowlist
* throw {@link GenerativeUIRenderError} unless a `Fallback` is provided.
*
* Stream-friendly: a partial spec renders progressively as it is filled in.
*
* @example
* ```tsx
* <MessagePrimitive.GenerativeUI
*   components={{ Card: MyCard, Button: MyButton }}
* />
* ```
*/
var MessagePrimitiveGenerativeUI = (t0) => {
	const $ = c(4);
	const { components, spec, Fallback } = t0;
	const storeSpec = useAuiState(_temp$10);
	const partSpec = spec ?? storeSpec;
	if (!partSpec) return null;
	let t1;
	if ($[0] !== Fallback || $[1] !== components || $[2] !== partSpec) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(GenerativeUIRender, {
			spec: partSpec,
			components,
			Fallback
		});
		$[0] = Fallback;
		$[1] = components;
		$[2] = partSpec;
		$[3] = t1;
	} else t1 = $[3];
	return t1;
};
MessagePrimitiveGenerativeUI.displayName = "MessagePrimitive.GenerativeUI";
function _temp$10(s) {
	const part = s.part;
	return part?.type === "generative-ui" ? part.spec : void 0;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/types/message.js
var MCP_APP_URI_SCHEME = "ui://";
var isMcpAppUri = (uri) => !!uri?.startsWith(MCP_APP_URI_SCHEME);
//#endregion
//#region node_modules/zustand/esm/vanilla/shallow.mjs
var isIterable = (obj) => Symbol.iterator in obj;
var hasIterableEntries = (value) => "entries" in value;
var compareEntries = (valueA, valueB) => {
	const mapA = valueA instanceof Map ? valueA : new Map(valueA.entries());
	const mapB = valueB instanceof Map ? valueB : new Map(valueB.entries());
	if (mapA.size !== mapB.size) return false;
	for (const [key, value] of mapA) if (!mapB.has(key) || !Object.is(value, mapB.get(key))) return false;
	return true;
};
var compareIterables = (valueA, valueB) => {
	const iteratorA = valueA[Symbol.iterator]();
	const iteratorB = valueB[Symbol.iterator]();
	let nextA = iteratorA.next();
	let nextB = iteratorB.next();
	while (!nextA.done && !nextB.done) {
		if (!Object.is(nextA.value, nextB.value)) return false;
		nextA = iteratorA.next();
		nextB = iteratorB.next();
	}
	return !!nextA.done && !!nextB.done;
};
function shallow(valueA, valueB) {
	if (Object.is(valueA, valueB)) return true;
	if (typeof valueA !== "object" || valueA === null || typeof valueB !== "object" || valueB === null) return false;
	if (Object.getPrototypeOf(valueA) !== Object.getPrototypeOf(valueB)) return false;
	if (isIterable(valueA) && isIterable(valueB)) {
		if (hasIterableEntries(valueA) && hasIterableEntries(valueB)) return compareEntries(valueA, valueB);
		return compareIterables(valueA, valueB);
	}
	return compareEntries({ entries: () => Object.entries(valueA) }, { entries: () => Object.entries(valueB) });
}
//#endregion
//#region node_modules/zustand/esm/react/shallow.mjs
function useShallow(selector) {
	const prev = import_react.useRef(void 0);
	return (state) => {
		const next = selector(state);
		return shallow(prev.current, next) ? prev.current : prev.current = next;
	};
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/message/MessageParts.js
/**
* Creates a group state manager for a specific part type.
* Returns functions to start, end, and finalize groups.
*/
var createGroupState = (groupType) => {
	let start = -1;
	return {
		startGroup: (index) => {
			if (start === -1) start = index;
		},
		endGroup: (endIndex, ranges) => {
			if (start !== -1) {
				ranges.push({
					type: groupType,
					startIndex: start,
					endIndex
				});
				start = -1;
			}
		},
		finalize: (endIndex, ranges) => {
			if (start !== -1) ranges.push({
				type: groupType,
				startIndex: start,
				endIndex
			});
		}
	};
};
/**
* Groups consecutive tool-call and reasoning message parts into ranges.
* Always groups tool calls and reasoning parts, even if there's only one.
* When useChainOfThought is true, groups tool-call and reasoning parts together.
* `partIds[i]` optionally carries a stable identity for part `i`; group
* ranges derive an `idKey` from their first part's id (first claim wins).
*/
var groupMessageParts = (messageTypes, useChainOfThought, partIds) => {
	const ranges = [];
	if (useChainOfThought) {
		const chainOfThoughtGroup = createGroupState("chainOfThoughtGroup");
		for (let i = 0; i < messageTypes.length; i++) {
			const type = messageTypes[i];
			if (type === "tool-call" || type === "reasoning") chainOfThoughtGroup.startGroup(i);
			else {
				chainOfThoughtGroup.endGroup(i - 1, ranges);
				ranges.push({
					type: "single",
					index: i
				});
			}
		}
		chainOfThoughtGroup.finalize(messageTypes.length - 1, ranges);
	} else {
		const toolGroup = createGroupState("toolGroup");
		const reasoningGroup = createGroupState("reasoningGroup");
		for (let i = 0; i < messageTypes.length; i++) {
			const type = messageTypes[i];
			if (type === "tool-call") {
				reasoningGroup.endGroup(i - 1, ranges);
				toolGroup.startGroup(i);
			} else if (type === "reasoning") {
				toolGroup.endGroup(i - 1, ranges);
				reasoningGroup.startGroup(i);
			} else {
				toolGroup.endGroup(i - 1, ranges);
				reasoningGroup.endGroup(i - 1, ranges);
				ranges.push({
					type: "single",
					index: i
				});
			}
		}
		toolGroup.finalize(messageTypes.length - 1, ranges);
		reasoningGroup.finalize(messageTypes.length - 1, ranges);
	}
	if (partIds) {
		const claimed = /* @__PURE__ */ new Set();
		for (const range of ranges) {
			if (range.type === "single") continue;
			const id = partIds[range.startIndex];
			if (id !== void 0 && !claimed.has(id)) {
				claimed.add(id);
				range.idKey = `id:${id}`;
			}
		}
	}
	return ranges;
};
var useMessagePartsGroups = (useChainOfThought) => {
	const $ = c(10);
	const messageTypes = useAuiState(useShallow(_temp2));
	const partIds = useAuiState(useShallow(_temp4));
	let t0;
	bb0: {
		if (messageTypes.length === 0) {
			let t1;
			if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
				t1 = [];
				$[0] = t1;
			} else t1 = $[0];
			let t2;
			if ($[1] !== partIds) {
				t2 = {
					ranges: t1,
					partIds
				};
				$[1] = partIds;
				$[2] = t2;
			} else t2 = $[2];
			t0 = t2;
			break bb0;
		}
		let t1;
		if ($[3] !== messageTypes || $[4] !== partIds || $[5] !== useChainOfThought) {
			t1 = groupMessageParts(messageTypes, useChainOfThought, partIds);
			$[3] = messageTypes;
			$[4] = partIds;
			$[5] = useChainOfThought;
			$[6] = t1;
		} else t1 = $[6];
		let t2;
		if ($[7] !== partIds || $[8] !== t1) {
			t2 = {
				ranges: t1,
				partIds
			};
			$[7] = partIds;
			$[8] = t1;
			$[9] = t2;
		} else t2 = $[9];
		t0 = t2;
	}
	return t0;
};
var ToolUIDisplay = (t0) => {
	const $ = c(9);
	let Fallback;
	let props;
	if ($[0] !== t0) {
		({Fallback, ...props} = t0);
		$[0] = t0;
		$[1] = Fallback;
		$[2] = props;
	} else {
		Fallback = $[1];
		props = $[2];
	}
	let t1;
	if ($[3] !== Fallback || $[4] !== props.toolName) {
		t1 = (s) => s.tools.toolUIs[props.toolName]?.[0]?.render ?? Fallback;
		$[3] = Fallback;
		$[4] = props.toolName;
		$[5] = t1;
	} else t1 = $[5];
	const Render = useAuiState(t1);
	if (!Render) return null;
	let t2;
	if ($[6] !== Render || $[7] !== props) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Render, { ...props });
		$[6] = Render;
		$[7] = props;
		$[8] = t2;
	} else t2 = $[8];
	return t2;
};
var getDataRenderer = (dataRenderers, name, inlineFallback) => {
	const named = dataRenderers.renderers[name]?.[0];
	if (named) return named;
	return dataRenderers.fallbacks[0] ?? inlineFallback;
};
var DataUIDisplay = (t0) => {
	const $ = c(9);
	let Fallback;
	let props;
	if ($[0] !== t0) {
		({Fallback, ...props} = t0);
		$[0] = t0;
		$[1] = Fallback;
		$[2] = props;
	} else {
		Fallback = $[1];
		props = $[2];
	}
	let t1;
	if ($[3] !== Fallback || $[4] !== props.name) {
		t1 = (s) => getDataRenderer(s.dataRenderers, props.name, Fallback);
		$[3] = Fallback;
		$[4] = props.name;
		$[5] = t1;
	} else t1 = $[5];
	const Render = useAuiState(t1);
	if (!Render) return null;
	let t2;
	if ($[6] !== Render || $[7] !== props) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Render, { ...props });
		$[6] = Render;
		$[7] = props;
		$[8] = t2;
	} else t2 = $[8];
	return t2;
};
/**
* Platform-agnostic no-op default components.
* Each platform (web, RN) wraps MessagePrimitiveParts with its own defaults.
*/
var defaultComponents = {
	Text: () => null,
	Reasoning: () => null,
	Source: () => null,
	Image: () => null,
	File: () => null,
	Unstable_Audio: () => null,
	ToolGroup: ({ children }) => children,
	ReasoningGroup: ({ children }) => children
};
var MessagePartComponent = (t0) => {
	const $ = c(47);
	const { components: t1 } = t0;
	let t2;
	if ($[0] !== t1) {
		t2 = t1 === void 0 ? {} : t1;
		$[0] = t1;
		$[1] = t2;
	} else t2 = $[1];
	const { Text: t3, Reasoning: t4, Image: t5, Source: t6, File: t7, Unstable_Audio: t8, tools: t9, data, generativeUI } = t2;
	const Text = t3 === void 0 ? defaultComponents.Text : t3;
	const Reasoning = t4 === void 0 ? defaultComponents.Reasoning : t4;
	const Image = t5 === void 0 ? defaultComponents.Image : t5;
	const Source = t6 === void 0 ? defaultComponents.Source : t6;
	const File = t7 === void 0 ? defaultComponents.File : t7;
	const Audio = t8 === void 0 ? defaultComponents.Unstable_Audio : t8;
	let t10;
	if ($[2] !== t9) {
		t10 = t9 === void 0 ? {} : t9;
		$[2] = t9;
		$[3] = t10;
	} else t10 = $[3];
	const tools = t10;
	const aui = useAui();
	const part = useAuiState(_temp5);
	const type = part.type;
	if (type === "tool-call") {
		let t11;
		if ($[4] !== aui) {
			t11 = aui.part();
			$[4] = aui;
			$[5] = t11;
		} else t11 = $[5];
		const addResult = t11.addToolResult;
		let t12;
		if ($[6] !== aui) {
			t12 = aui.part();
			$[6] = aui;
			$[7] = t12;
		} else t12 = $[7];
		const resume = t12.resumeToolCall;
		let t13;
		if ($[8] !== aui) {
			t13 = aui.part();
			$[8] = aui;
			$[9] = t13;
		} else t13 = $[9];
		const respondToApproval = t13.respondToToolApproval;
		if ("Override" in tools) {
			let t14;
			if ($[10] !== addResult || $[11] !== part || $[12] !== respondToApproval || $[13] !== resume || $[14] !== tools.Override) {
				t14 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(tools.Override, {
					...part,
					addResult,
					resume,
					respondToApproval
				});
				$[10] = addResult;
				$[11] = part;
				$[12] = respondToApproval;
				$[13] = resume;
				$[14] = tools.Override;
				$[15] = t14;
			} else t14 = $[15];
			return t14;
		}
		const Tool = tools.by_name?.[part.toolName] ?? tools.Fallback;
		let t14;
		if ($[16] !== Tool || $[17] !== addResult || $[18] !== part || $[19] !== respondToApproval || $[20] !== resume) {
			t14 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ToolUIDisplay, {
				...part,
				Fallback: Tool,
				addResult,
				resume,
				respondToApproval
			});
			$[16] = Tool;
			$[17] = addResult;
			$[18] = part;
			$[19] = respondToApproval;
			$[20] = resume;
			$[21] = t14;
		} else t14 = $[21];
		return t14;
	}
	if (part.status?.type === "requires-action") throw new Error("Encountered unexpected requires-action status");
	switch (type) {
		case "text": {
			let t11;
			if ($[22] !== Text || $[23] !== part) {
				t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Text, { ...part });
				$[22] = Text;
				$[23] = part;
				$[24] = t11;
			} else t11 = $[24];
			return t11;
		}
		case "reasoning": {
			let t11;
			if ($[25] !== Reasoning || $[26] !== part) {
				t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Reasoning, { ...part });
				$[25] = Reasoning;
				$[26] = part;
				$[27] = t11;
			} else t11 = $[27];
			return t11;
		}
		case "source": {
			let t11;
			if ($[28] !== Source || $[29] !== part) {
				t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Source, { ...part });
				$[28] = Source;
				$[29] = part;
				$[30] = t11;
			} else t11 = $[30];
			return t11;
		}
		case "image": {
			let t11;
			if ($[31] !== Image || $[32] !== part) {
				t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Image, { ...part });
				$[31] = Image;
				$[32] = part;
				$[33] = t11;
			} else t11 = $[33];
			return t11;
		}
		case "file": {
			let t11;
			if ($[34] !== File || $[35] !== part) {
				t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(File, { ...part });
				$[34] = File;
				$[35] = part;
				$[36] = t11;
			} else t11 = $[36];
			return t11;
		}
		case "audio": {
			let t11;
			if ($[37] !== Audio || $[38] !== part) {
				t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Audio, { ...part });
				$[37] = Audio;
				$[38] = part;
				$[39] = t11;
			} else t11 = $[39];
			return t11;
		}
		case "data": {
			const Data = data?.by_name?.[part.name] ?? data?.Fallback;
			let t11;
			if ($[40] !== Data || $[41] !== part) {
				t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DataUIDisplay, {
					...part,
					Fallback: Data
				});
				$[40] = Data;
				$[41] = part;
				$[42] = t11;
			} else t11 = $[42];
			return t11;
		}
		case "generative-ui": {
			if (!generativeUI?.components) return null;
			const t11 = part;
			let t12;
			if ($[43] !== generativeUI.Fallback || $[44] !== generativeUI.components || $[45] !== t11.spec) {
				t12 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(GenerativeUIRender, {
					spec: t11.spec,
					components: generativeUI.components,
					Fallback: generativeUI.Fallback
				});
				$[43] = generativeUI.Fallback;
				$[44] = generativeUI.components;
				$[45] = t11.spec;
				$[46] = t12;
			} else t12 = $[46];
			return t12;
		}
		default:
			console.warn(`Unknown message part type: ${type}`);
			return null;
	}
};
/**
* Renders a single message part at the specified index.
*/
var MessagePrimitivePartByIndex = (0, react_shim_exports.memo)((t0) => {
	const $ = c(5);
	const { index, components } = t0;
	let t1;
	if ($[0] !== components) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartComponent, { components });
		$[0] = components;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== index || $[3] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PartByIndexProvider, {
			index,
			children: t1
		});
		$[2] = index;
		$[3] = t1;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
}, (prev, next) => prev.index === next.index && prev.components?.Text === next.components?.Text && prev.components?.Reasoning === next.components?.Reasoning && prev.components?.Source === next.components?.Source && prev.components?.Image === next.components?.Image && prev.components?.File === next.components?.File && prev.components?.Unstable_Audio === next.components?.Unstable_Audio && prev.components?.tools === next.components?.tools && prev.components?.data === next.components?.data && prev.components?.generativeUI === next.components?.generativeUI && prev.components?.ToolGroup === next.components?.ToolGroup && prev.components?.ReasoningGroup === next.components?.ReasoningGroup);
MessagePrimitivePartByIndex.displayName = "MessagePrimitive.PartByIndex";
var EmptyPartFallback = (t0) => {
	const $ = c(6);
	const { status, component: Component } = t0;
	const t1 = status.type === "running";
	let t2;
	if ($[0] !== Component || $[1] !== status) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {
			type: "text",
			text: "",
			status
		});
		$[0] = Component;
		$[1] = status;
		$[2] = t2;
	} else t2 = $[2];
	let t3;
	if ($[3] !== t1 || $[4] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextMessagePartProvider, {
			text: "",
			isRunning: t1,
			children: t2
		});
		$[3] = t1;
		$[4] = t2;
		$[5] = t3;
	} else t3 = $[5];
	return t3;
};
var COMPLETE_STATUS$1 = Object.freeze({ type: "complete" });
var RUNNING_STATUS = Object.freeze({ type: "running" });
var EmptyPartsImpl = (t0) => {
	const $ = c(6);
	const { components } = t0;
	const status = useAuiState(_temp6);
	if (components?.Empty) {
		let t1;
		if ($[0] !== components.Empty || $[1] !== status) {
			t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(components.Empty, { status });
			$[0] = components.Empty;
			$[1] = status;
			$[2] = t1;
		} else t1 = $[2];
		return t1;
	}
	if (status.type !== "running") return null;
	const t1 = components?.Text ?? defaultComponents.Text;
	let t2;
	if ($[3] !== status || $[4] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(EmptyPartFallback, {
			status,
			component: t1
		});
		$[3] = status;
		$[4] = t1;
		$[5] = t2;
	} else t2 = $[5];
	return t2;
};
var EmptyParts = (0, react_shim_exports.memo)(EmptyPartsImpl, (prev, next) => prev.components?.Empty === next.components?.Empty && prev.components?.Text === next.components?.Text);
var ConditionalEmptyImpl = (t0) => {
	const $ = c(4);
	const { components, enabled } = t0;
	let t1;
	if ($[0] !== enabled) {
		t1 = (s) => {
			if (!enabled) return false;
			if (s.message.parts.length === 0) return false;
			const lastPart = s.message.parts[s.message.parts.length - 1];
			return lastPart?.type !== "text" && lastPart?.type !== "reasoning";
		};
		$[0] = enabled;
		$[1] = t1;
	} else t1 = $[1];
	if (!useAuiState(t1)) return null;
	let t2;
	if ($[2] !== components) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(EmptyParts, { components });
		$[2] = components;
		$[3] = t2;
	} else t2 = $[3];
	return t2;
};
var ConditionalEmpty = (0, react_shim_exports.memo)(ConditionalEmptyImpl, (prev, next) => prev.enabled === next.enabled && prev.components?.Empty === next.components?.Empty && prev.components?.Text === next.components?.Text);
var QuoteRendererImpl = (t0) => {
	const $ = c(4);
	const { Quote } = t0;
	const quoteInfo = useAuiState(getMessageQuote);
	if (!quoteInfo) return null;
	let t1;
	if ($[0] !== Quote || $[1] !== quoteInfo.messageId || $[2] !== quoteInfo.text) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Quote, {
			text: quoteInfo.text,
			messageId: quoteInfo.messageId
		});
		$[0] = Quote;
		$[1] = quoteInfo.messageId;
		$[2] = quoteInfo.text;
		$[3] = t1;
	} else t1 = $[3];
	return t1;
};
var QuoteRenderer = (0, react_shim_exports.memo)(QuoteRendererImpl);
function resolveToolRender(toolsState, part) {
	const named = toolsState.toolUIs[part.toolName]?.[0]?.render ?? null;
	if (named) return named;
	if (isMcpAppUri(part.mcp?.app?.resourceUri) && toolsState.mcpApp) return toolsState.mcpApp.render;
	return null;
}
/**
* Stable propless component that renders the registered tool UI for the
* current part context. Reads tool registry and part state from context.
*/
var RegisteredToolUI = () => {
	const $ = c(12);
	const aui = useAui();
	const part = useAuiState(_temp7);
	const Render = useAuiState(_temp8);
	if (!Render || part.type !== "tool-call") return null;
	let t0;
	if ($[0] !== aui) {
		t0 = aui.part();
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const t1 = t0.addToolResult;
	let t2;
	if ($[2] !== aui) {
		t2 = aui.part();
		$[2] = aui;
		$[3] = t2;
	} else t2 = $[3];
	const t3 = t2.resumeToolCall;
	let t4;
	if ($[4] !== aui) {
		t4 = aui.part();
		$[4] = aui;
		$[5] = t4;
	} else t4 = $[5];
	let t5;
	if ($[6] !== Render || $[7] !== part || $[8] !== t0.addToolResult || $[9] !== t2.resumeToolCall || $[10] !== t4.respondToToolApproval) {
		t5 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Render, {
			...part,
			addResult: t1,
			resume: t3,
			respondToApproval: t4.respondToToolApproval
		});
		$[6] = Render;
		$[7] = part;
		$[8] = t0.addToolResult;
		$[9] = t2.resumeToolCall;
		$[10] = t4.respondToToolApproval;
		$[11] = t5;
	} else t5 = $[11];
	return t5;
};
/**
* Stable propless component that renders the registered data renderer UI
* for the current part context.
*/
var RegisteredDataRendererUI = () => {
	const $ = c(3);
	const part = useAuiState(_temp9);
	const Render = useAuiState(_temp0);
	if (!Render || part.type !== "data") return null;
	const t0 = part;
	let t1;
	if ($[0] !== Render || $[1] !== t0) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Render, { ...t0 });
		$[0] = Render;
		$[1] = t0;
		$[2] = t1;
	} else t1 = $[2];
	return t1;
};
/**
* Fallback component rendered when the children render function returns null.
* Renders registered tool/data UIs via context.
* For all other part types, renders nothing.
*
* This allows users to write:
*   {({ part }) => {
*     if (part.type === "text") return <MyText />;
*     return null; // tool UIs and data UIs still render via registry
*   }}
*
* To explicitly render nothing (suppressing registered UIs), return <></>.
*/
var DefaultPartFallback = () => {
	const $ = c(2);
	const partType = useAuiState(_temp1);
	if (partType === "tool-call") {
		let t0;
		if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
			t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RegisteredToolUI, {});
			$[0] = t0;
		} else t0 = $[0];
		return t0;
	}
	if (partType === "data") {
		let t0;
		if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
			t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RegisteredDataRendererUI, {});
			$[1] = t0;
		} else t0 = $[1];
		return t0;
	}
	return null;
};
var EMPTY_RUNNING_TEXT_PART = Object.freeze({
	type: "text",
	text: "",
	status: RUNNING_STATUS
});
/**
* @internal
* Renders a single part by index, calling `children` with the
* {@link EnrichedPartState} (tool/data UI enrichments + addResult/resume
* for tool calls). Shared between `<MessagePrimitive.Parts>` and
* `<MessagePrimitive.GroupedParts>`. Returns whatever `children`
* returns — callers decide how to handle a `null` return.
*/
var MessagePartChildren = ({ index, children }) => {
	const aui = useAui();
	const dataRenderers = useAuiState((s) => s.dataRenderers);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PartByIndexProvider, {
		index,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderChildrenWithAccessor, {
			getItemState: (aui_0) => aui_0.message().part({ index }).getState(),
			children: (getItem) => children({ get part() {
				const state = getItem();
				if (state.type === "tool-call") {
					const hasUI = resolveToolRender(aui.tools().getState(), state) !== null;
					const partMethods = aui.message().part({ index });
					return {
						...state,
						toolUI: hasUI ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RegisteredToolUI, {}) : null,
						addResult: partMethods.addToolResult,
						resume: partMethods.resumeToolCall,
						respondToApproval: partMethods.respondToToolApproval
					};
				}
				if (state.type === "data") {
					const hasUI = getDataRenderer(dataRenderers, state.name, void 0) !== void 0;
					return {
						...state,
						dataRendererUI: hasUI ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RegisteredDataRendererUI, {}) : null
					};
				}
				return state;
			} })
		})
	});
};
var MessagePrimitivePartsInner = (t0) => {
	const $ = c(9);
	const { children } = t0;
	const contentLength = useAuiState(_temp10);
	const isRunning = useAuiState(_temp11);
	const isEmptyRunning = contentLength === 0 && isRunning;
	if (contentLength === 0) {
		if (!isEmptyRunning) return null;
		let t1;
		if ($[0] !== children) {
			t1 = children({ part: EMPTY_RUNNING_TEXT_PART });
			$[0] = children;
			$[1] = t1;
		} else t1 = $[1];
		let t2;
		if ($[2] !== t1) {
			t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextMessagePartProvider, {
				text: "",
				isRunning: true,
				children: t1
			});
			$[2] = t1;
			$[3] = t2;
		} else t2 = $[3];
		return t2;
	}
	let t1;
	if ($[4] !== children || $[5] !== contentLength) {
		let t2;
		if ($[7] !== children) {
			t2 = (_, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartChildren, {
				index,
				children: (value) => children(value) ?? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DefaultPartFallback, {})
			}, index);
			$[7] = children;
			$[8] = t2;
		} else t2 = $[8];
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_jsx_runtime.Fragment, { children: Array.from({ length: contentLength }, t2) });
		$[4] = children;
		$[5] = contentLength;
		$[6] = t1;
	} else t1 = $[6];
	return t1;
};
/**
* Renders the parts of a message with support for multiple content types.
*
* This is the platform-agnostic base. Each platform wraps this with its own
* default components (web uses `<p>`, `<span>`; RN would use `<Text>`, etc.).
*/
var MessagePrimitiveParts = (t0) => {
	const $ = c(5);
	const { components, unstable_showEmptyOnNonTextEnd: t1, children } = t0;
	const unstable_showEmptyOnNonTextEnd = t1 === void 0 ? true : t1;
	if (children) {
		let t2;
		if ($[0] !== children) {
			t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitivePartsInner, { children });
			$[0] = children;
			$[1] = t2;
		} else t2 = $[1];
		return t2;
	}
	let t2;
	if ($[2] !== components || $[3] !== unstable_showEmptyOnNonTextEnd) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitivePartsCompat, {
			components,
			unstable_showEmptyOnNonTextEnd
		});
		$[2] = components;
		$[3] = unstable_showEmptyOnNonTextEnd;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
};
MessagePrimitiveParts.displayName = "MessagePrimitive.Parts";
var MessagePrimitivePartsCompat = (t0) => {
	const $ = c(15);
	const { components, unstable_showEmptyOnNonTextEnd } = t0;
	const contentLength = useAuiState(_temp12);
	const { ranges: messageRanges, partIds } = useMessagePartsGroups(!!components?.ChainOfThought);
	let t1;
	bb0: {
		if (contentLength === 0) {
			let t2;
			if ($[0] !== components) {
				t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(EmptyParts, { components });
				$[0] = components;
				$[1] = t2;
			} else t2 = $[1];
			t1 = t2;
			break bb0;
		}
		let t2;
		if ($[2] !== components || $[3] !== messageRanges || $[4] !== partIds) {
			const claimed = /* @__PURE__ */ new Set();
			const toolLeafKey = (partIndex) => {
				const id = partIds[partIndex];
				if (id !== void 0 && !claimed.has(id)) {
					claimed.add(id);
					return `part-id:${id}`;
				}
				return `part-${partIndex}`;
			};
			t2 = messageRanges.map((range) => {
				if (range.type === "single") return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitivePartByIndex, {
					index: range.index,
					components
				}, range.index);
				else if (range.type === "chainOfThoughtGroup") {
					const ChainOfThoughtComponent = components?.ChainOfThought;
					if (!ChainOfThoughtComponent) return null;
					return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ChainOfThoughtByIndicesProvider, {
						startIndex: range.startIndex,
						endIndex: range.endIndex,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ChainOfThoughtComponent, {})
					}, `chainOfThought-${range.idKey ?? range.startIndex}`);
				} else if (range.type === "toolGroup") return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(components?.ToolGroup ?? defaultComponents.ToolGroup, {
					startIndex: range.startIndex,
					endIndex: range.endIndex,
					children: Array.from({ length: range.endIndex - range.startIndex + 1 }, (_, i) => {
						const partIndex_0 = range.startIndex + i;
						return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitivePartByIndex, {
							index: partIndex_0,
							components
						}, toolLeafKey(partIndex_0));
					})
				}, `tool-${range.idKey ?? range.startIndex}`);
				else return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(components?.ReasoningGroup ?? defaultComponents.ReasoningGroup, {
					startIndex: range.startIndex,
					endIndex: range.endIndex,
					children: Array.from({ length: range.endIndex - range.startIndex + 1 }, (__0, i_0) => {
						const partIndex_1 = range.startIndex + i_0;
						return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitivePartByIndex, {
							index: partIndex_1,
							components
						}, `part-${partIndex_1}`);
					})
				}, `reasoning-${range.startIndex}`);
			});
			$[2] = components;
			$[3] = messageRanges;
			$[4] = partIds;
			$[5] = t2;
		} else t2 = $[5];
		t1 = t2;
	}
	const partsElements = t1;
	let t2;
	if ($[6] !== components) {
		t2 = components?.Quote && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(QuoteRenderer, { Quote: components.Quote });
		$[6] = components;
		$[7] = t2;
	} else t2 = $[7];
	let t3;
	if ($[8] !== components || $[9] !== unstable_showEmptyOnNonTextEnd) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ConditionalEmpty, {
			components,
			enabled: unstable_showEmptyOnNonTextEnd
		});
		$[8] = components;
		$[9] = unstable_showEmptyOnNonTextEnd;
		$[10] = t3;
	} else t3 = $[10];
	let t4;
	if ($[11] !== partsElements || $[12] !== t2 || $[13] !== t3) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
			t2,
			partsElements,
			t3
		] });
		$[11] = partsElements;
		$[12] = t2;
		$[13] = t3;
		$[14] = t4;
	} else t4 = $[14];
	return t4;
};
function _temp$9(c) {
	return c.type;
}
function _temp2(s) {
	return s.message.parts.map(_temp$9);
}
function _temp3(c_0) {
	return c_0.type === "tool-call" ? c_0.toolCallId : void 0;
}
function _temp4(s_0) {
	return s_0.message.parts.map(_temp3);
}
function _temp5(s) {
	return s.part;
}
function _temp6(s) {
	return s.message.status ?? COMPLETE_STATUS$1;
}
function _temp7(s) {
	return s.part;
}
function _temp8(s_0) {
	return s_0.part.type === "tool-call" ? resolveToolRender(s_0.tools, s_0.part) : null;
}
function _temp9(s) {
	return s.part;
}
function _temp0(s_0) {
	return s_0.part.type === "data" ? getDataRenderer(s_0.dataRenderers, s_0.part.name, void 0) ?? null : null;
}
function _temp1(s) {
	return s.part.type;
}
function _temp10(s) {
	return s.message.parts.length;
}
function _temp11(s_0) {
	return (s_0.message.status?.type ?? "complete") === "running";
}
function _temp12(s) {
	return s.message.parts.length;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/utils/groupParts.js
/**
* Hierarchical adjacent-coalescing grouping for message parts.
*
* Given a group path per part (from `groupBy`), builds a tree of group
* nodes wrapping individual parts. Adjacent parts sharing a path prefix
* coalesce into the same group; ungrouped parts are direct children of
* the root.
*
* Each node gets a structural `nodeKey` built from sibling indices
* (`"0.1.0"`), stable under append-only streaming.
*/
/**
* Symbol attached to memoizable `groupBy` functions (e.g. those returned
* by {@link groupPartByType}). Carries a string fingerprint of the config
* so `MessagePrimitive.GroupedParts` can memo the tree on
* `[parts, memoKey]` across renders — even when the helper call site
* reconstructs the function each render.
*/
var GROUPBY_MEMO_KEY = Symbol.for("@assistant-ui/groupBy.memoKey");
var makeChildNodeKey = (parent) => {
	const idx = parent.nextChildIdx++;
	return parent.nodeKey === "" ? String(idx) : `${parent.nodeKey}.${idx}`;
};
var claimIdKey = (frame, id) => {
	if (id === void 0 || frame.claimed.has(id)) return void 0;
	frame.claimed.add(id);
	return `id:${id}`;
};
/**
* Build the group tree from an array of normalized group paths.
* `paths[i]` is the path for part `i`. The output tree contains one
* `part` node per part and one `group` node per coalesced run.
* `partIds[i]` optionally carries a stable identity for part `i` (e.g. a
* tool call id), from which nodes derive an `idKey`.
*/
var buildGroupTree = (paths, partIds) => {
	const root = {
		key: "",
		nodeKey: "",
		indices: [],
		children: [],
		nextChildIdx: 0,
		claimed: /* @__PURE__ */ new Set()
	};
	const stack = [root];
	const closeTop = () => {
		const closing = stack.pop();
		const parent = stack[stack.length - 1];
		parent.children.push({
			type: "group",
			key: closing.key,
			nodeKey: closing.nodeKey,
			idKey: claimIdKey(parent, partIds?.[closing.indices[0]]),
			indices: closing.indices,
			children: closing.children
		});
	};
	for (let i = 0; i < paths.length; i++) {
		const path = paths[i];
		let common = 0;
		while (common < stack.length - 1 && common < path.length && stack[common + 1].key === path[common]) common++;
		while (stack.length - 1 > common) closeTop();
		while (stack.length - 1 < path.length) {
			const parent = stack[stack.length - 1];
			stack.push({
				key: path[stack.length - 1],
				nodeKey: makeChildNodeKey(parent),
				indices: [],
				children: [],
				nextChildIdx: 0,
				claimed: /* @__PURE__ */ new Set()
			});
		}
		const top = stack[stack.length - 1];
		top.children.push({
			type: "part",
			index: i,
			nodeKey: makeChildNodeKey(top),
			idKey: claimIdKey(top, partIds?.[i])
		});
		for (let s = 1; s < stack.length; s++) stack[s].indices.push(i);
	}
	while (stack.length > 1) closeTop();
	return root.children;
};
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/message/MessageGroupedParts.js
var COMPLETE_STATUS = Object.freeze({ type: "complete" });
var shouldShowIndicator = (mode, parts, isRunning) => {
	if (!isRunning) return false;
	switch (mode) {
		case "never": return false;
		case "always": return true;
		case "empty": return parts.length === 0;
		case "no-text": {
			const last = parts[parts.length - 1];
			return last === void 0 || last.type !== "text" && last.type !== "reasoning";
		}
	}
};
/**
* `children` placeholder passed for leaf-part renders. Leaf parts have no
* inner subtree; rendering this sentinel signals the consumer wrote
* `default: return children;` and accidentally fell through for a part —
* surface the bug loudly instead of silently rendering nothing.
*/
var PartChildrenSentinel = () => {
	throw new Error("MessagePrimitive.GroupedParts: rendered `children` under a leaf part. `children` is only meaningful for `group-…` cases — add a matching case for the part type or return `null` to skip it.");
};
var renderNode = (node, parts, render) => {
	if (node.type === "part") return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartChildren, {
		index: node.index,
		children: ({ part }) => render({
			part,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PartChildrenSentinel, {})
		})
	}, node.idKey ? `part-${node.idKey}` : `part-${node.index}`);
	const status = parts[node.indices.at(-1)]?.status ?? COMPLETE_STATUS;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(react_shim_exports.Fragment, { children: render({
		part: {
			type: node.key,
			status,
			indices: node.indices
		},
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_jsx_runtime.Fragment, { children: node.children.map((child) => renderNode(child, parts, render)) })
	}) }, node.idKey ?? node.nodeKey);
};
/**
* Groups adjacent message parts into a tree of coalesced runs and
* renders each node — group or part — through a single `children`
* function.
*
* The render function receives `{ part, children }` where `part.type`
* is either a `"group-…"` literal (for a group, `children` is the
* recursively-rendered subtree) or a real part type (`"text"`,
* `"tool-call"`, …) for a leaf (`children` is a sentinel that throws
* if rendered — use `part.type` to distinguish).
*
* @example
* ```tsx
* <MessagePrimitive.GroupedParts
*   groupBy={groupPartByType({
*     reasoning: ["group-thought", "group-reasoning"],
*     "tool-call": ["group-thought", "group-tool"],
*   })}
* >
*   {({ part, children }) => {
*     switch (part.type) {
*       case "group-thought":   return <Thought>{children}</Thought>;
*       case "group-reasoning": return <Reasoning>{children}</Reasoning>;
*       case "group-tool":      return <ToolStack>{children}</ToolStack>;
*       case "text":            return <MarkdownText />;
*       case "tool-call":       return part.toolUI ?? <ToolFallback {...part} />;
*       case "indicator":       return <LoadingDots />;
*       default:                return null;
*     }
*   }}
* </MessagePrimitive.GroupedParts>
* ```
*/
var MessagePrimitiveGroupedParts = ({ groupBy, indicator = "no-text", children }) => {
	const parts = useAuiState(useShallow((s) => s.message.parts));
	const toolUIs = useAuiState((s_0) => s_0.tools.toolUIs);
	const isRunning = useAuiState((s_1) => indicator === "never" ? false : s_1.message.status?.type === "running");
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [useMemo(() => {
		const context = { toolUIs };
		return buildGroupTree(parts.map((part) => groupBy(part, context) ?? []), parts.map((part_0) => part_0.type === "tool-call" ? part_0.toolCallId : void 0));
	}, [
		parts,
		groupBy[GROUPBY_MEMO_KEY] ?? groupBy,
		toolUIs
	]).map((node) => renderNode(node, parts, children)), shouldShowIndicator(indicator, parts, isRunning) && children({
		part: { type: "indicator" },
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PartChildrenSentinel, {})
	})] });
};
MessagePrimitiveGroupedParts.displayName = "MessagePrimitive.GroupedParts";
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/message/MessageQuote.js
/**
* Renders a quote block if the message has quote metadata.
* Place this above `MessagePrimitive.Parts` in your message layout.
*
* @example
* ```tsx
* <MessagePrimitive.Quote>
*   {({ text, messageId }) => <QuoteBlock text={text} messageId={messageId} />}
* </MessagePrimitive.Quote>
* <MessagePrimitive.Parts>
*   {({ part }) => { ... }}
* </MessagePrimitive.Parts>
* ```
*/
var MessagePrimitiveQuoteImpl = (t0) => {
	const $ = c(5);
	const { children } = t0;
	const quoteInfo = useAuiState(getMessageQuote);
	if (!quoteInfo) return null;
	let t1;
	if ($[0] !== children || $[1] !== quoteInfo) {
		t1 = children(quoteInfo);
		$[0] = children;
		$[1] = quoteInfo;
		$[2] = t1;
	} else t1 = $[2];
	let t2;
	if ($[3] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_jsx_runtime.Fragment, { children: t1 });
		$[3] = t1;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
};
var MessagePrimitiveQuote = (0, react_shim_exports.memo)(MessagePrimitiveQuoteImpl);
MessagePrimitiveQuote.displayName = "MessagePrimitive.Quote";
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/message/MessageAttachments.js
var getComponent$1 = (components, attachment) => {
	switch (attachment.type) {
		case "image": return components?.Image ?? components?.Attachment;
		case "document": return components?.Document ?? components?.Attachment;
		case "file": return components?.File ?? components?.Attachment;
		default: return components?.Attachment;
	}
};
var AttachmentComponent$1 = (t0) => {
	const $ = c(5);
	const { components } = t0;
	const attachment = useAuiState(_temp$8);
	if (!attachment) return null;
	const t1 = attachment;
	let t2;
	if ($[0] !== components || $[1] !== t1) {
		t2 = getComponent$1(components, t1);
		$[0] = components;
		$[1] = t1;
		$[2] = t2;
	} else t2 = $[2];
	const Component = t2;
	if (!Component) return null;
	let t3;
	if ($[3] !== Component) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {});
		$[3] = Component;
		$[4] = t3;
	} else t3 = $[4];
	return t3;
};
/**
* Renders a single attachment at the specified index within the current message.
*/
var MessagePrimitiveAttachmentByIndex = (0, react_shim_exports.memo)((t0) => {
	const $ = c(5);
	const { index, components } = t0;
	let t1;
	if ($[0] !== components) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AttachmentComponent$1, { components });
		$[0] = components;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== index || $[3] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessageAttachmentByIndexProvider, {
			index,
			children: t1
		});
		$[2] = index;
		$[3] = t1;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
}, (prev, next) => prev.index === next.index && prev.components?.Image === next.components?.Image && prev.components?.Document === next.components?.Document && prev.components?.File === next.components?.File && prev.components?.Attachment === next.components?.Attachment);
MessagePrimitiveAttachmentByIndex.displayName = "MessagePrimitive.AttachmentByIndex";
var MessagePrimitiveAttachmentsInner = ({ children }) => {
	const attachmentsCount = useAuiState((s) => {
		if (s.message.role !== "user") return 0;
		return (s.message.attachments ?? []).length;
	});
	return useMemo(() => Array.from({ length: attachmentsCount }, (_, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessageAttachmentByIndexProvider, {
		index,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderChildrenWithAccessor, {
			getItemState: (aui) => aui.message().attachment({ index }).getState(),
			children: (getItem) => children({ get attachment() {
				return getItem();
			} })
		})
	}, index)), [attachmentsCount, children]);
};
var MessagePrimitiveAttachments = (t0) => {
	const $ = c(4);
	const { components, children } = t0;
	if (components) {
		let t1;
		if ($[0] !== components) {
			t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitiveAttachmentsInner, { children: (t2) => {
				const { attachment } = t2;
				const Component = getComponent$1(components, attachment);
				if (!Component) return null;
				return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {});
			} });
			$[0] = components;
			$[1] = t1;
		} else t1 = $[1];
		return t1;
	}
	let t1;
	if ($[2] !== children) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitiveAttachmentsInner, { children });
		$[2] = children;
		$[3] = t1;
	} else t1 = $[3];
	return t1;
};
MessagePrimitiveAttachments.displayName = "MessagePrimitive.Attachments";
function _temp$8(s) {
	return s.attachment;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/composer/ComposerAttachments.js
var getComponent = (components, attachment) => {
	switch (attachment.type) {
		case "image": return components?.Image ?? components?.Attachment;
		case "document": return components?.Document ?? components?.Attachment;
		case "file": return components?.File ?? components?.Attachment;
		default: return components?.Attachment;
	}
};
var AttachmentComponent = (t0) => {
	const $ = c(5);
	const { components } = t0;
	const attachment = useAuiState(_temp$7);
	if (!attachment) return null;
	let t1;
	if ($[0] !== attachment || $[1] !== components) {
		t1 = getComponent(components, attachment);
		$[0] = attachment;
		$[1] = components;
		$[2] = t1;
	} else t1 = $[2];
	const Component = t1;
	if (!Component) return null;
	let t2;
	if ($[3] !== Component) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {});
		$[3] = Component;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
};
/**
* Renders a single attachment at the specified index within the composer.
*/
var ComposerPrimitiveAttachmentByIndex = (0, react_shim_exports.memo)((t0) => {
	const $ = c(5);
	const { index, components } = t0;
	let t1;
	if ($[0] !== components) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AttachmentComponent, { components });
		$[0] = components;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== index || $[3] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ComposerAttachmentByIndexProvider, {
			index,
			children: t1
		});
		$[2] = index;
		$[3] = t1;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
}, (prev, next) => prev.index === next.index && prev.components?.Image === next.components?.Image && prev.components?.Document === next.components?.Document && prev.components?.File === next.components?.File && prev.components?.Attachment === next.components?.Attachment);
ComposerPrimitiveAttachmentByIndex.displayName = "ComposerPrimitive.AttachmentByIndex";
var ComposerPrimitiveAttachmentsInner = ({ children }) => {
	const attachmentsCount = useAuiState((s) => s.composer.attachments.length);
	return useMemo(() => Array.from({ length: attachmentsCount }, (_, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ComposerAttachmentByIndexProvider, {
		index,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderChildrenWithAccessor, {
			getItemState: (aui) => aui.composer().attachment({ index }).getState(),
			children: (getItem) => children({ get attachment() {
				return getItem();
			} })
		})
	}, index)), [attachmentsCount, children]);
};
var ComposerPrimitiveAttachments = (t0) => {
	const $ = c(4);
	const { components, children } = t0;
	if (components) {
		let t1;
		if ($[0] !== components) {
			t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ComposerPrimitiveAttachmentsInner, { children: (t2) => {
				const { attachment } = t2;
				const Component = getComponent(components, attachment);
				if (!Component) return null;
				return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {});
			} });
			$[0] = components;
			$[1] = t1;
		} else t1 = $[1];
		return t1;
	}
	let t1;
	if ($[2] !== children) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ComposerPrimitiveAttachmentsInner, { children });
		$[2] = children;
		$[3] = t1;
	} else t1 = $[3];
	return t1;
};
ComposerPrimitiveAttachments.displayName = "ComposerPrimitive.Attachments";
function _temp$7(s) {
	return s.attachment;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/composer/ComposerQueue.js
var ComposerPrimitiveQueueInner = ({ children }) => {
	const queue = useAuiState((s) => s.composer.queue.length);
	return useMemo(() => Array.from({ length: queue }, (_, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(QueueItemByIndexProvider, {
		index,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderChildrenWithAccessor, {
			getItemState: (aui) => aui.composer().queueItem({ index }).getState(),
			children: (getItem) => children({ get queueItem() {
				return getItem();
			} })
		})
	}, index)), [queue, children]);
};
/**
* Renders all queue items in the composer.
*
* @example
* ```tsx
* <ComposerPrimitive.Queue>
*   {({ queueItem }) => (
*     <div>
*       <QueueItemPrimitive.Text />
*       <QueueItemPrimitive.Steer>Run Now</QueueItemPrimitive.Steer>
*     </div>
*   )}
* </ComposerPrimitive.Queue>
* ```
*/
var ComposerPrimitiveQueue = (0, react_shim_exports.memo)(ComposerPrimitiveQueueInner);
ComposerPrimitiveQueue.displayName = "ComposerPrimitive.Queue";
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/messagePart/MessagePartInProgress.js
var MessagePartPrimitiveInProgress = (t0) => {
	const { children } = t0;
	return useAuiState(_temp$6) ? children : null;
};
MessagePartPrimitiveInProgress.displayName = "MessagePartPrimitive.InProgress";
function _temp$6(s) {
	return s.part.status.type === "running";
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/thread/ThreadSuggestions.js
var SuggestionComponent = (t0) => {
	const $ = c(2);
	const { components } = t0;
	const Component = components.Suggestion;
	let t1;
	if ($[0] !== Component) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {});
		$[0] = Component;
		$[1] = t1;
	} else t1 = $[1];
	return t1;
};
/**
* Renders a single suggestion at the specified index.
*/
var ThreadPrimitiveSuggestionByIndex = (0, react_shim_exports.memo)((t0) => {
	const $ = c(5);
	const { index, components } = t0;
	let t1;
	if ($[0] !== components) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SuggestionComponent, { components });
		$[0] = components;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== index || $[3] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SuggestionByIndexProvider, {
			index,
			children: t1
		});
		$[2] = index;
		$[3] = t1;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
}, (prev, next) => prev.index === next.index && prev.components.Suggestion === next.components.Suggestion);
ThreadPrimitiveSuggestionByIndex.displayName = "ThreadPrimitive.SuggestionByIndex";
var ThreadPrimitiveSuggestionsInner = ({ children }) => {
	const suggestionsLength = useAuiState((s) => s.suggestions.suggestions.length);
	return useMemo(() => {
		if (suggestionsLength === 0) return null;
		return Array.from({ length: suggestionsLength }, (_, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SuggestionByIndexProvider, {
			index,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RenderChildrenWithAccessor, {
				getItemState: (aui) => aui.suggestions().suggestion({ index }).getState(),
				children: (getItem) => children({ get suggestion() {
					return getItem();
				} })
			})
		}, index));
	}, [suggestionsLength, children]);
};
/**
* Renders all suggestions.
*/
var ThreadPrimitiveSuggestionsImpl = (t0) => {
	const $ = c(4);
	const { components, children } = t0;
	if (components) {
		let t1;
		if ($[0] !== components) {
			t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadPrimitiveSuggestionsInner, { children: () => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SuggestionComponent, { components }) });
			$[0] = components;
			$[1] = t1;
		} else t1 = $[1];
		return t1;
	}
	let t1;
	if ($[2] !== children) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadPrimitiveSuggestionsInner, { children });
		$[2] = children;
		$[3] = t1;
	} else t1 = $[3];
	return t1;
};
ThreadPrimitiveSuggestionsImpl.displayName = "ThreadPrimitive.Suggestions";
var ThreadPrimitiveSuggestions = (0, react_shim_exports.memo)(ThreadPrimitiveSuggestionsImpl, (prev, next) => {
	if (prev.children || next.children) return prev.children === next.children;
	return prev.components.Suggestion === next.components.Suggestion;
});
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitives/composer/ComposerIf.js
var useComposerIf = (props) => {
	const $ = c(3);
	let t0;
	if ($[0] !== props.dictation || $[1] !== props.editing) {
		t0 = (s) => {
			if (props.editing === true && !s.composer.isEditing) return false;
			if (props.editing === false && s.composer.isEditing) return false;
			const isDictating = s.composer.dictation != null;
			if (props.dictation === true && !isDictating) return false;
			if (props.dictation === false && isDictating) return false;
			return true;
		};
		$[0] = props.dictation;
		$[1] = props.editing;
		$[2] = t0;
	} else t0 = $[2];
	return useAuiState(t0);
};
/**
* @deprecated Use `<AuiIf condition={(s) => s.composer...} />` instead.
*/
var ComposerPrimitiveIf = (t0) => {
	const $ = c(3);
	let children;
	let query;
	if ($[0] !== t0) {
		({children, ...query} = t0);
		$[0] = t0;
		$[1] = children;
		$[2] = query;
	} else {
		children = $[1];
		query = $[2];
	}
	return useComposerIf(query) ? children : null;
};
ComposerPrimitiveIf.displayName = "ComposerPrimitive.If";
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitive-hooks/useComposerSend.js
var useComposerSend = () => {
	const $ = c(5);
	const aui = useAui();
	const disabled = useAuiState(_temp$5);
	let t0;
	if ($[0] !== aui) {
		t0 = (opts) => {
			aui.composer().send(opts);
		};
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const send = t0;
	let t1;
	if ($[2] !== disabled || $[3] !== send) {
		t1 = {
			send,
			disabled
		};
		$[2] = disabled;
		$[3] = send;
		$[4] = t1;
	} else t1 = $[4];
	return t1;
};
function _temp$5(s) {
	return !s.composer.canSend || s.thread.isRunning && !s.thread.capabilities.queue;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitive-hooks/useComposerCancel.js
var useComposerCancel = () => {
	const $ = c(5);
	const aui = useAui();
	const disabled = useAuiState(_temp$4);
	let t0;
	if ($[0] !== aui) {
		t0 = () => {
			aui.composer().cancel();
		};
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const cancel = t0;
	let t1;
	if ($[2] !== cancel || $[3] !== disabled) {
		t1 = {
			cancel,
			disabled
		};
		$[2] = cancel;
		$[3] = disabled;
		$[4] = t1;
	} else t1 = $[4];
	return t1;
};
function _temp$4(s) {
	return !s.composer.canCancel;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitive-hooks/useComposerDictate.js
var useComposerDictate = () => {
	const $ = c(5);
	const aui = useAui();
	const disabled = useAuiState(_temp$3);
	let t0;
	if ($[0] !== aui) {
		t0 = () => {
			aui.composer().startDictation();
		};
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const startDictation = t0;
	let t1;
	if ($[2] !== disabled || $[3] !== startDictation) {
		t1 = {
			startDictation,
			disabled
		};
		$[2] = disabled;
		$[3] = startDictation;
		$[4] = t1;
	} else t1 = $[4];
	return t1;
};
function _temp$3(s) {
	return s.composer.dictation != null || !s.thread.capabilities.dictation || !s.composer.isEditing;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitive-hooks/useComposerAddAttachment.js
var useComposerAddAttachment = () => {
	const $ = c(5);
	const aui = useAui();
	const disabled = useAuiState(_temp$2);
	let t0;
	if ($[0] !== aui) {
		t0 = (file) => aui.composer().addAttachment(file);
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const addAttachment = t0;
	let t1;
	if ($[2] !== addAttachment || $[3] !== disabled) {
		t1 = {
			addAttachment,
			disabled
		};
		$[2] = addAttachment;
		$[3] = disabled;
		$[4] = t1;
	} else t1 = $[4];
	return t1;
};
function _temp$2(s) {
	return !s.composer.isEditing;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitive-hooks/useSuggestionTrigger.js
var useSuggestionTrigger = (t0) => {
	const $ = c(8);
	const { prompt, send, clearComposer: t1 } = t0;
	const clearComposer = t1 === void 0 ? true : t1;
	const aui = useAui();
	const disabled = useAuiState(_temp$1);
	const resolvedSend = send ?? false;
	let t2;
	if ($[0] !== aui || $[1] !== clearComposer || $[2] !== prompt || $[3] !== resolvedSend) {
		t2 = () => {
			const isRunning = aui.thread().getState().isRunning;
			if (resolvedSend && !isRunning) {
				aui.thread().append({
					content: [{
						type: "text",
						text: prompt
					}],
					runConfig: aui.composer().getState().runConfig
				});
				if (clearComposer) aui.composer().setText("");
			} else if (clearComposer) aui.composer().setText(prompt);
			else {
				const currentText = aui.composer().getState().text;
				aui.composer().setText(currentText.trim() ? `${currentText} ${prompt}` : prompt);
			}
		};
		$[0] = aui;
		$[1] = clearComposer;
		$[2] = prompt;
		$[3] = resolvedSend;
		$[4] = t2;
	} else t2 = $[4];
	const trigger = t2;
	let t3;
	if ($[5] !== disabled || $[6] !== trigger) {
		t3 = {
			trigger,
			disabled
		};
		$[5] = disabled;
		$[6] = trigger;
		$[7] = t3;
	} else t3 = $[7];
	return t3;
};
function _temp$1(s) {
	return s.thread.isDisabled;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/react/primitive-hooks/useMessageError.js
var useMessageError = () => {
	return useAuiState(_temp);
};
function _temp(s) {
	return s.message.status?.type === "incomplete" && s.message.status.reason === "error" ? s.message.status.error ?? "An error occurred" : void 0;
}
//#endregion
//#region node_modules/@assistant-ui/core/dist/adapters/directive-formatter.js
var DIRECTIVE_RE = /:([\w-]{1,64})\[([^\]\n]{1,1024})\](?:\{name=([^}\n]{1,1024})\})?/gu;
/**
* Default directive formatter using the `:type[label]{name=id}` syntax.
*
* When `id` equals `label`, the `{name=…}` attribute is omitted for brevity.
*/
var unstable_defaultDirectiveFormatter = {
	serialize(item) {
		const attrs = item.id !== item.label ? `{name=${item.id}}` : "";
		return `:${item.type}[${item.label}]${attrs}`;
	},
	parse(text) {
		const segments = [];
		let lastIndex = 0;
		for (const match of text.matchAll(DIRECTIVE_RE)) {
			if (match.index > lastIndex) segments.push({
				kind: "text",
				text: text.slice(lastIndex, match.index)
			});
			const label = match[2];
			segments.push({
				kind: "mention",
				type: match[1],
				label,
				id: match[3] ?? label
			});
			lastIndex = match.index + match[0].length;
		}
		if (lastIndex < text.length) segments.push({
			kind: "text",
			text: text.slice(lastIndex)
		});
		return segments;
	}
};
//#endregion
export { useAui as A, useContext as B, ThreadPrimitiveMessageByIndex as C, PartByIndexProvider as D, TextMessagePartProvider as E, resource as F, useRef as G, useEffectEvent as H, c as I, require_react as J, useState as K, createContext as L, require_jsx_runtime as M, useResource as N, AssistantProviderBase as O, flushTapSync as P, react_shim_exports as R, MessagePrimitiveGenerativeUI as S, useExternalStoreRuntime as T, useLayoutEffect as U, useEffect as V, useMemo as W, MessagePrimitiveQuote as _, useComposerDictate as a, MessagePrimitiveParts as b, ComposerPrimitiveIf as c, MessagePartPrimitiveInProgress as d, ComposerPrimitiveQueue as f, MessagePrimitiveAttachments as g, MessagePrimitiveAttachmentByIndex as h, useComposerAddAttachment as i, normalizeEventSelector as j, useAuiState as k, ThreadPrimitiveSuggestionByIndex as l, ComposerPrimitiveAttachments as m, useMessageError as n, useComposerCancel as o, ComposerPrimitiveAttachmentByIndex as p, useSyncExternalStore as q, useSuggestionTrigger as r, useComposerSend as s, unstable_defaultDirectiveFormatter as t, ThreadPrimitiveSuggestions as u, MessagePrimitiveGroupedParts as v, ThreadPrimitiveMessages as w, defaultComponents as x, MessagePrimitivePartByIndex as y, useCallback as z };
