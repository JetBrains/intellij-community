import { o as __toESM, t as __commonJSMin } from "./rolldown-runtime.js";
import { $ as useLayoutEffect$1, A as defaultComponents$1, B as normalizeEventSelector, C as ComposerPrimitiveAttachments, D as MessagePrimitiveGroupedParts, E as MessagePrimitiveQuote, F as TextMessagePartProvider, G as c, H as useResource, I as PartByIndexProvider, J as useCallback, K as createContext, L as AssistantProviderBase, M as ThreadPrimitiveMessageByIndex, N as ThreadPrimitiveMessages, O as MessagePrimitivePartByIndex, Q as useEffectEvent$1, R as useAuiState, S as ComposerPrimitiveAttachmentByIndex, T as MessagePrimitiveAttachments, U as flushTapSync, V as require_jsx_runtime, W as resource, X as useDebugValue, Y as useContext, Z as useEffect, _ as ThreadListItemPrimitiveTitle, a as useThreadListItemTrigger, b as ThreadListPrimitiveItems, c as useThreadListItemArchive, d as useComposerDictate, et as useMemo, f as useComposerCancel, g as ThreadPrimitiveSuggestions, h as ThreadPrimitiveSuggestionByIndex, i as useThreadListNew, it as require_react, j as MessagePrimitiveGenerativeUI, k as MessagePrimitiveParts$1, l as useSuggestionTrigger, m as ComposerPrimitiveIf, n as useMessageError, nt as useState, o as useThreadListItemUnarchive, p as useComposerSend$1, q as react_shim_exports, r as useThreadListLoadMore, rt as useSyncExternalStore, s as useThreadListItemDelete, t as unstable_defaultDirectiveFormatter, tt as useRef, u as useComposerAddAttachment, v as MessagePartPrimitiveInProgress, w as MessagePrimitiveAttachmentByIndex, x as ComposerPrimitiveQueue, y as ThreadListPrimitiveItemByIndex, z as useAui } from "./assistant-ui-core.js";
/**
* @license React
* scheduler.production.min.js
*
* Copyright (c) Facebook, Inc. and its affiliates.
*
* This source code is licensed under the MIT license found in the
* LICENSE file in the root directory of this source tree.
*/
var require_scheduler_production_min = /* @__PURE__ */ __commonJSMin(((exports) => {
	function f(a, b) {
		var c = a.length;
		a.push(b);
		a: for (; 0 < c;) {
			var d = c - 1 >>> 1, e = a[d];
			if (0 < g(e, b)) a[d] = b, a[c] = e, c = d;
			else break a;
		}
	}
	function h(a) {
		return 0 === a.length ? null : a[0];
	}
	function k(a) {
		if (0 === a.length) return null;
		var b = a[0], c = a.pop();
		if (c !== b) {
			a[0] = c;
			a: for (var d = 0, e = a.length, w = e >>> 1; d < w;) {
				var m = 2 * (d + 1) - 1, C = a[m], n = m + 1, x = a[n];
				if (0 > g(C, c)) n < e && 0 > g(x, C) ? (a[d] = x, a[n] = c, d = n) : (a[d] = C, a[m] = c, d = m);
				else if (n < e && 0 > g(x, c)) a[d] = x, a[n] = c, d = n;
				else break a;
			}
		}
		return b;
	}
	function g(a, b) {
		var c = a.sortIndex - b.sortIndex;
		return 0 !== c ? c : a.id - b.id;
	}
	if ("object" === typeof performance && "function" === typeof performance.now) {
		var l = performance;
		exports.unstable_now = function() {
			return l.now();
		};
	} else {
		var p = Date, q = p.now();
		exports.unstable_now = function() {
			return p.now() - q;
		};
	}
	var r = [], t = [], u = 1, v = null, y = 3, z = !1, A = !1, B = !1, D = "function" === typeof setTimeout ? setTimeout : null, E = "function" === typeof clearTimeout ? clearTimeout : null, F = "undefined" !== typeof setImmediate ? setImmediate : null;
	"undefined" !== typeof navigator && void 0 !== navigator.scheduling && void 0 !== navigator.scheduling.isInputPending && navigator.scheduling.isInputPending.bind(navigator.scheduling);
	function G(a) {
		for (var b = h(t); null !== b;) {
			if (null === b.callback) k(t);
			else if (b.startTime <= a) k(t), b.sortIndex = b.expirationTime, f(r, b);
			else break;
			b = h(t);
		}
	}
	function H(a) {
		B = !1;
		G(a);
		if (!A) if (null !== h(r)) A = !0, I(J);
		else {
			var b = h(t);
			null !== b && K(H, b.startTime - a);
		}
	}
	function J(a, b) {
		A = !1;
		B && (B = !1, E(L), L = -1);
		z = !0;
		var c = y;
		try {
			G(b);
			for (v = h(r); null !== v && (!(v.expirationTime > b) || a && !M());) {
				var d = v.callback;
				if ("function" === typeof d) {
					v.callback = null;
					y = v.priorityLevel;
					var e = d(v.expirationTime <= b);
					b = exports.unstable_now();
					"function" === typeof e ? v.callback = e : v === h(r) && k(r);
					G(b);
				} else k(r);
				v = h(r);
			}
			if (null !== v) var w = !0;
			else {
				var m = h(t);
				null !== m && K(H, m.startTime - b);
				w = !1;
			}
			return w;
		} finally {
			v = null, y = c, z = !1;
		}
	}
	var N = !1, O = null, L = -1, P = 5, Q = -1;
	function M() {
		return exports.unstable_now() - Q < P ? !1 : !0;
	}
	function R() {
		if (null !== O) {
			var a = exports.unstable_now();
			Q = a;
			var b = !0;
			try {
				b = O(!0, a);
			} finally {
				b ? S() : (N = !1, O = null);
			}
		} else N = !1;
	}
	var S;
	if ("function" === typeof F) S = function() {
		F(R);
	};
	else if ("undefined" !== typeof MessageChannel) {
		var T = new MessageChannel(), U = T.port2;
		T.port1.onmessage = R;
		S = function() {
			U.postMessage(null);
		};
	} else S = function() {
		D(R, 0);
	};
	function I(a) {
		O = a;
		N || (N = !0, S());
	}
	function K(a, b) {
		L = D(function() {
			a(exports.unstable_now());
		}, b);
	}
	exports.unstable_IdlePriority = 5;
	exports.unstable_ImmediatePriority = 1;
	exports.unstable_LowPriority = 4;
	exports.unstable_NormalPriority = 3;
	exports.unstable_Profiling = null;
	exports.unstable_UserBlockingPriority = 2;
	exports.unstable_cancelCallback = function(a) {
		a.callback = null;
	};
	exports.unstable_continueExecution = function() {
		A || z || (A = !0, I(J));
	};
	exports.unstable_forceFrameRate = function(a) {
		0 > a || 125 < a ? console.error("forceFrameRate takes a positive int between 0 and 125, forcing frame rates higher than 125 fps is not supported") : P = 0 < a ? Math.floor(1e3 / a) : 5;
	};
	exports.unstable_getCurrentPriorityLevel = function() {
		return y;
	};
	exports.unstable_getFirstCallbackNode = function() {
		return h(r);
	};
	exports.unstable_next = function(a) {
		switch (y) {
			case 1:
			case 2:
			case 3:
				var b = 3;
				break;
			default: b = y;
		}
		var c = y;
		y = b;
		try {
			return a();
		} finally {
			y = c;
		}
	};
	exports.unstable_pauseExecution = function() {};
	exports.unstable_requestPaint = function() {};
	exports.unstable_runWithPriority = function(a, b) {
		switch (a) {
			case 1:
			case 2:
			case 3:
			case 4:
			case 5: break;
			default: a = 3;
		}
		var c = y;
		y = a;
		try {
			return b();
		} finally {
			y = c;
		}
	};
	exports.unstable_scheduleCallback = function(a, b, c) {
		var d = exports.unstable_now();
		"object" === typeof c && null !== c ? (c = c.delay, c = "number" === typeof c && 0 < c ? d + c : d) : c = d;
		switch (a) {
			case 1:
				var e = -1;
				break;
			case 2:
				e = 250;
				break;
			case 5:
				e = 1073741823;
				break;
			case 4:
				e = 1e4;
				break;
			default: e = 5e3;
		}
		e = c + e;
		a = {
			id: u++,
			callback: b,
			priorityLevel: a,
			startTime: c,
			expirationTime: e,
			sortIndex: -1
		};
		c > d ? (a.sortIndex = c, f(t, a), null === h(r) && a === h(t) && (B ? (E(L), L = -1) : B = !0, K(H, c - d))) : (a.sortIndex = e, f(r, a), A || z || (A = !0, I(J)));
		return a;
	};
	exports.unstable_shouldYield = M;
	exports.unstable_wrapCallback = function(a) {
		var b = y;
		return function() {
			var c = y;
			y = b;
			try {
				return a.apply(this, arguments);
			} finally {
				y = c;
			}
		};
	};
}));
var require_scheduler = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	module.exports = require_scheduler_production_min();
}));
/**
* @license React
* react-dom.production.min.js
*
* Copyright (c) Facebook, Inc. and its affiliates.
*
* This source code is licensed under the MIT license found in the
* LICENSE file in the root directory of this source tree.
*/
var require_react_dom_production_min = /* @__PURE__ */ __commonJSMin(((exports) => {
	var aa = require_react(), ca = require_scheduler();
	function p(a) {
		for (var b = "https://reactjs.org/docs/error-decoder.html?invariant=" + a, c = 1; c < arguments.length; c++) b += "&args[]=" + encodeURIComponent(arguments[c]);
		return "Minified React error #" + a + "; visit " + b + " for the full message or use the non-minified dev environment for full errors and additional helpful warnings.";
	}
	var da = /* @__PURE__ */ new Set(), ea = {};
	function fa(a, b) {
		ha(a, b);
		ha(a + "Capture", b);
	}
	function ha(a, b) {
		ea[a] = b;
		for (a = 0; a < b.length; a++) da.add(b[a]);
	}
	var ia = !("undefined" === typeof window || "undefined" === typeof window.document || "undefined" === typeof window.document.createElement), ja = Object.prototype.hasOwnProperty, ka = /^[:A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD][:A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD\-.0-9\u00B7\u0300-\u036F\u203F-\u2040]*$/, la = {}, ma = {};
	function oa(a) {
		if (ja.call(ma, a)) return !0;
		if (ja.call(la, a)) return !1;
		if (ka.test(a)) return ma[a] = !0;
		la[a] = !0;
		return !1;
	}
	function pa(a, b, c, d) {
		if (null !== c && 0 === c.type) return !1;
		switch (typeof b) {
			case "function":
			case "symbol": return !0;
			case "boolean":
				if (d) return !1;
				if (null !== c) return !c.acceptsBooleans;
				a = a.toLowerCase().slice(0, 5);
				return "data-" !== a && "aria-" !== a;
			default: return !1;
		}
	}
	function qa(a, b, c, d) {
		if (null === b || "undefined" === typeof b || pa(a, b, c, d)) return !0;
		if (d) return !1;
		if (null !== c) switch (c.type) {
			case 3: return !b;
			case 4: return !1 === b;
			case 5: return isNaN(b);
			case 6: return isNaN(b) || 1 > b;
		}
		return !1;
	}
	function v(a, b, c, d, e, f, g) {
		this.acceptsBooleans = 2 === b || 3 === b || 4 === b;
		this.attributeName = d;
		this.attributeNamespace = e;
		this.mustUseProperty = c;
		this.propertyName = a;
		this.type = b;
		this.sanitizeURL = f;
		this.removeEmptyString = g;
	}
	var z = {};
	"children dangerouslySetInnerHTML defaultValue defaultChecked innerHTML suppressContentEditableWarning suppressHydrationWarning style".split(" ").forEach(function(a) {
		z[a] = new v(a, 0, !1, a, null, !1, !1);
	});
	[
		["acceptCharset", "accept-charset"],
		["className", "class"],
		["htmlFor", "for"],
		["httpEquiv", "http-equiv"]
	].forEach(function(a) {
		var b = a[0];
		z[b] = new v(b, 1, !1, a[1], null, !1, !1);
	});
	[
		"contentEditable",
		"draggable",
		"spellCheck",
		"value"
	].forEach(function(a) {
		z[a] = new v(a, 2, !1, a.toLowerCase(), null, !1, !1);
	});
	[
		"autoReverse",
		"externalResourcesRequired",
		"focusable",
		"preserveAlpha"
	].forEach(function(a) {
		z[a] = new v(a, 2, !1, a, null, !1, !1);
	});
	"allowFullScreen async autoFocus autoPlay controls default defer disabled disablePictureInPicture disableRemotePlayback formNoValidate hidden loop noModule noValidate open playsInline readOnly required reversed scoped seamless itemScope".split(" ").forEach(function(a) {
		z[a] = new v(a, 3, !1, a.toLowerCase(), null, !1, !1);
	});
	[
		"checked",
		"multiple",
		"muted",
		"selected"
	].forEach(function(a) {
		z[a] = new v(a, 3, !0, a, null, !1, !1);
	});
	["capture", "download"].forEach(function(a) {
		z[a] = new v(a, 4, !1, a, null, !1, !1);
	});
	[
		"cols",
		"rows",
		"size",
		"span"
	].forEach(function(a) {
		z[a] = new v(a, 6, !1, a, null, !1, !1);
	});
	["rowSpan", "start"].forEach(function(a) {
		z[a] = new v(a, 5, !1, a.toLowerCase(), null, !1, !1);
	});
	var ra = /[\-:]([a-z])/g;
	function sa(a) {
		return a[1].toUpperCase();
	}
	"accent-height alignment-baseline arabic-form baseline-shift cap-height clip-path clip-rule color-interpolation color-interpolation-filters color-profile color-rendering dominant-baseline enable-background fill-opacity fill-rule flood-color flood-opacity font-family font-size font-size-adjust font-stretch font-style font-variant font-weight glyph-name glyph-orientation-horizontal glyph-orientation-vertical horiz-adv-x horiz-origin-x image-rendering letter-spacing lighting-color marker-end marker-mid marker-start overline-position overline-thickness paint-order panose-1 pointer-events rendering-intent shape-rendering stop-color stop-opacity strikethrough-position strikethrough-thickness stroke-dasharray stroke-dashoffset stroke-linecap stroke-linejoin stroke-miterlimit stroke-opacity stroke-width text-anchor text-decoration text-rendering underline-position underline-thickness unicode-bidi unicode-range units-per-em v-alphabetic v-hanging v-ideographic v-mathematical vector-effect vert-adv-y vert-origin-x vert-origin-y word-spacing writing-mode xmlns:xlink x-height".split(" ").forEach(function(a) {
		var b = a.replace(ra, sa);
		z[b] = new v(b, 1, !1, a, null, !1, !1);
	});
	"xlink:actuate xlink:arcrole xlink:role xlink:show xlink:title xlink:type".split(" ").forEach(function(a) {
		var b = a.replace(ra, sa);
		z[b] = new v(b, 1, !1, a, "http://www.w3.org/1999/xlink", !1, !1);
	});
	[
		"xml:base",
		"xml:lang",
		"xml:space"
	].forEach(function(a) {
		var b = a.replace(ra, sa);
		z[b] = new v(b, 1, !1, a, "http://www.w3.org/XML/1998/namespace", !1, !1);
	});
	["tabIndex", "crossOrigin"].forEach(function(a) {
		z[a] = new v(a, 1, !1, a.toLowerCase(), null, !1, !1);
	});
	z.xlinkHref = new v("xlinkHref", 1, !1, "xlink:href", "http://www.w3.org/1999/xlink", !0, !1);
	[
		"src",
		"href",
		"action",
		"formAction"
	].forEach(function(a) {
		z[a] = new v(a, 1, !1, a.toLowerCase(), null, !0, !0);
	});
	function ta(a, b, c, d) {
		var e = z.hasOwnProperty(b) ? z[b] : null;
		if (null !== e ? 0 !== e.type : d || !(2 < b.length) || "o" !== b[0] && "O" !== b[0] || "n" !== b[1] && "N" !== b[1]) qa(b, c, e, d) && (c = null), d || null === e ? oa(b) && (null === c ? a.removeAttribute(b) : a.setAttribute(b, "" + c)) : e.mustUseProperty ? a[e.propertyName] = null === c ? 3 === e.type ? !1 : "" : c : (b = e.attributeName, d = e.attributeNamespace, null === c ? a.removeAttribute(b) : (e = e.type, c = 3 === e || 4 === e && !0 === c ? "" : "" + c, d ? a.setAttributeNS(d, b, c) : a.setAttribute(b, c)));
	}
	var ua = aa.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED, va = Symbol.for("react.element"), wa = Symbol.for("react.portal"), ya = Symbol.for("react.fragment"), za = Symbol.for("react.strict_mode"), Aa = Symbol.for("react.profiler"), Ba = Symbol.for("react.provider"), Ca = Symbol.for("react.context"), Da = Symbol.for("react.forward_ref"), Ea = Symbol.for("react.suspense"), Fa = Symbol.for("react.suspense_list"), Ga = Symbol.for("react.memo"), Ha = Symbol.for("react.lazy");
	var Ia = Symbol.for("react.offscreen");
	var Ja = Symbol.iterator;
	function Ka(a) {
		if (null === a || "object" !== typeof a) return null;
		a = Ja && a[Ja] || a["@@iterator"];
		return "function" === typeof a ? a : null;
	}
	var A = Object.assign, La;
	function Ma(a) {
		if (void 0 === La) try {
			throw Error();
		} catch (c) {
			var b = c.stack.trim().match(/\n( *(at )?)/);
			La = b && b[1] || "";
		}
		return "\n" + La + a;
	}
	var Na = !1;
	function Oa(a, b) {
		if (!a || Na) return "";
		Na = !0;
		var c = Error.prepareStackTrace;
		Error.prepareStackTrace = void 0;
		try {
			if (b) if (b = function() {
				throw Error();
			}, Object.defineProperty(b.prototype, "props", { set: function() {
				throw Error();
			} }), "object" === typeof Reflect && Reflect.construct) {
				try {
					Reflect.construct(b, []);
				} catch (l) {
					var d = l;
				}
				Reflect.construct(a, [], b);
			} else {
				try {
					b.call();
				} catch (l) {
					d = l;
				}
				a.call(b.prototype);
			}
			else {
				try {
					throw Error();
				} catch (l) {
					d = l;
				}
				a();
			}
		} catch (l) {
			if (l && d && "string" === typeof l.stack) {
				for (var e = l.stack.split("\n"), f = d.stack.split("\n"), g = e.length - 1, h = f.length - 1; 1 <= g && 0 <= h && e[g] !== f[h];) h--;
				for (; 1 <= g && 0 <= h; g--, h--) if (e[g] !== f[h]) {
					if (1 !== g || 1 !== h) do
						if (g--, h--, 0 > h || e[g] !== f[h]) {
							var k = "\n" + e[g].replace(" at new ", " at ");
							a.displayName && k.includes("<anonymous>") && (k = k.replace("<anonymous>", a.displayName));
							return k;
						}
					while (1 <= g && 0 <= h);
					break;
				}
			}
		} finally {
			Na = !1, Error.prepareStackTrace = c;
		}
		return (a = a ? a.displayName || a.name : "") ? Ma(a) : "";
	}
	function Pa(a) {
		switch (a.tag) {
			case 5: return Ma(a.type);
			case 16: return Ma("Lazy");
			case 13: return Ma("Suspense");
			case 19: return Ma("SuspenseList");
			case 0:
			case 2:
			case 15: return a = Oa(a.type, !1), a;
			case 11: return a = Oa(a.type.render, !1), a;
			case 1: return a = Oa(a.type, !0), a;
			default: return "";
		}
	}
	function Qa(a) {
		if (null == a) return null;
		if ("function" === typeof a) return a.displayName || a.name || null;
		if ("string" === typeof a) return a;
		switch (a) {
			case ya: return "Fragment";
			case wa: return "Portal";
			case Aa: return "Profiler";
			case za: return "StrictMode";
			case Ea: return "Suspense";
			case Fa: return "SuspenseList";
		}
		if ("object" === typeof a) switch (a.$$typeof) {
			case Ca: return (a.displayName || "Context") + ".Consumer";
			case Ba: return (a._context.displayName || "Context") + ".Provider";
			case Da:
				var b = a.render;
				a = a.displayName;
				a || (a = b.displayName || b.name || "", a = "" !== a ? "ForwardRef(" + a + ")" : "ForwardRef");
				return a;
			case Ga: return b = a.displayName || null, null !== b ? b : Qa(a.type) || "Memo";
			case Ha:
				b = a._payload;
				a = a._init;
				try {
					return Qa(a(b));
				} catch (c) {}
		}
		return null;
	}
	function Ra(a) {
		var b = a.type;
		switch (a.tag) {
			case 24: return "Cache";
			case 9: return (b.displayName || "Context") + ".Consumer";
			case 10: return (b._context.displayName || "Context") + ".Provider";
			case 18: return "DehydratedFragment";
			case 11: return a = b.render, a = a.displayName || a.name || "", b.displayName || ("" !== a ? "ForwardRef(" + a + ")" : "ForwardRef");
			case 7: return "Fragment";
			case 5: return b;
			case 4: return "Portal";
			case 3: return "Root";
			case 6: return "Text";
			case 16: return Qa(b);
			case 8: return b === za ? "StrictMode" : "Mode";
			case 22: return "Offscreen";
			case 12: return "Profiler";
			case 21: return "Scope";
			case 13: return "Suspense";
			case 19: return "SuspenseList";
			case 25: return "TracingMarker";
			case 1:
			case 0:
			case 17:
			case 2:
			case 14:
			case 15:
				if ("function" === typeof b) return b.displayName || b.name || null;
				if ("string" === typeof b) return b;
		}
		return null;
	}
	function Sa(a) {
		switch (typeof a) {
			case "boolean":
			case "number":
			case "string":
			case "undefined": return a;
			case "object": return a;
			default: return "";
		}
	}
	function Ta(a) {
		var b = a.type;
		return (a = a.nodeName) && "input" === a.toLowerCase() && ("checkbox" === b || "radio" === b);
	}
	function Ua(a) {
		var b = Ta(a) ? "checked" : "value", c = Object.getOwnPropertyDescriptor(a.constructor.prototype, b), d = "" + a[b];
		if (!a.hasOwnProperty(b) && "undefined" !== typeof c && "function" === typeof c.get && "function" === typeof c.set) {
			var e = c.get, f = c.set;
			Object.defineProperty(a, b, {
				configurable: !0,
				get: function() {
					return e.call(this);
				},
				set: function(a) {
					d = "" + a;
					f.call(this, a);
				}
			});
			Object.defineProperty(a, b, { enumerable: c.enumerable });
			return {
				getValue: function() {
					return d;
				},
				setValue: function(a) {
					d = "" + a;
				},
				stopTracking: function() {
					a._valueTracker = null;
					delete a[b];
				}
			};
		}
	}
	function Va(a) {
		a._valueTracker || (a._valueTracker = Ua(a));
	}
	function Wa(a) {
		if (!a) return !1;
		var b = a._valueTracker;
		if (!b) return !0;
		var c = b.getValue();
		var d = "";
		a && (d = Ta(a) ? a.checked ? "true" : "false" : a.value);
		a = d;
		return a !== c ? (b.setValue(a), !0) : !1;
	}
	function Xa(a) {
		a = a || ("undefined" !== typeof document ? document : void 0);
		if ("undefined" === typeof a) return null;
		try {
			return a.activeElement || a.body;
		} catch (b) {
			return a.body;
		}
	}
	function Ya(a, b) {
		var c = b.checked;
		return A({}, b, {
			defaultChecked: void 0,
			defaultValue: void 0,
			value: void 0,
			checked: null != c ? c : a._wrapperState.initialChecked
		});
	}
	function Za(a, b) {
		var c = null == b.defaultValue ? "" : b.defaultValue, d = null != b.checked ? b.checked : b.defaultChecked;
		c = Sa(null != b.value ? b.value : c);
		a._wrapperState = {
			initialChecked: d,
			initialValue: c,
			controlled: "checkbox" === b.type || "radio" === b.type ? null != b.checked : null != b.value
		};
	}
	function ab(a, b) {
		b = b.checked;
		null != b && ta(a, "checked", b, !1);
	}
	function bb(a, b) {
		ab(a, b);
		var c = Sa(b.value), d = b.type;
		if (null != c) if ("number" === d) {
			if (0 === c && "" === a.value || a.value != c) a.value = "" + c;
		} else a.value !== "" + c && (a.value = "" + c);
		else if ("submit" === d || "reset" === d) {
			a.removeAttribute("value");
			return;
		}
		b.hasOwnProperty("value") ? cb(a, b.type, c) : b.hasOwnProperty("defaultValue") && cb(a, b.type, Sa(b.defaultValue));
		null == b.checked && null != b.defaultChecked && (a.defaultChecked = !!b.defaultChecked);
	}
	function db(a, b, c) {
		if (b.hasOwnProperty("value") || b.hasOwnProperty("defaultValue")) {
			var d = b.type;
			if (!("submit" !== d && "reset" !== d || void 0 !== b.value && null !== b.value)) return;
			b = "" + a._wrapperState.initialValue;
			c || b === a.value || (a.value = b);
			a.defaultValue = b;
		}
		c = a.name;
		"" !== c && (a.name = "");
		a.defaultChecked = !!a._wrapperState.initialChecked;
		"" !== c && (a.name = c);
	}
	function cb(a, b, c) {
		if ("number" !== b || Xa(a.ownerDocument) !== a) null == c ? a.defaultValue = "" + a._wrapperState.initialValue : a.defaultValue !== "" + c && (a.defaultValue = "" + c);
	}
	var eb = Array.isArray;
	function fb(a, b, c, d) {
		a = a.options;
		if (b) {
			b = {};
			for (var e = 0; e < c.length; e++) b["$" + c[e]] = !0;
			for (c = 0; c < a.length; c++) e = b.hasOwnProperty("$" + a[c].value), a[c].selected !== e && (a[c].selected = e), e && d && (a[c].defaultSelected = !0);
		} else {
			c = "" + Sa(c);
			b = null;
			for (e = 0; e < a.length; e++) {
				if (a[e].value === c) {
					a[e].selected = !0;
					d && (a[e].defaultSelected = !0);
					return;
				}
				null !== b || a[e].disabled || (b = a[e]);
			}
			null !== b && (b.selected = !0);
		}
	}
	function gb(a, b) {
		if (null != b.dangerouslySetInnerHTML) throw Error(p(91));
		return A({}, b, {
			value: void 0,
			defaultValue: void 0,
			children: "" + a._wrapperState.initialValue
		});
	}
	function hb(a, b) {
		var c = b.value;
		if (null == c) {
			c = b.children;
			b = b.defaultValue;
			if (null != c) {
				if (null != b) throw Error(p(92));
				if (eb(c)) {
					if (1 < c.length) throw Error(p(93));
					c = c[0];
				}
				b = c;
			}
			b ??= "";
			c = b;
		}
		a._wrapperState = { initialValue: Sa(c) };
	}
	function ib(a, b) {
		var c = Sa(b.value), d = Sa(b.defaultValue);
		null != c && (c = "" + c, c !== a.value && (a.value = c), null == b.defaultValue && a.defaultValue !== c && (a.defaultValue = c));
		null != d && (a.defaultValue = "" + d);
	}
	function jb(a) {
		var b = a.textContent;
		b === a._wrapperState.initialValue && "" !== b && null !== b && (a.value = b);
	}
	function kb(a) {
		switch (a) {
			case "svg": return "http://www.w3.org/2000/svg";
			case "math": return "http://www.w3.org/1998/Math/MathML";
			default: return "http://www.w3.org/1999/xhtml";
		}
	}
	function lb(a, b) {
		return null == a || "http://www.w3.org/1999/xhtml" === a ? kb(b) : "http://www.w3.org/2000/svg" === a && "foreignObject" === b ? "http://www.w3.org/1999/xhtml" : a;
	}
	var mb, nb = function(a) {
		return "undefined" !== typeof MSApp && MSApp.execUnsafeLocalFunction ? function(b, c, d, e) {
			MSApp.execUnsafeLocalFunction(function() {
				return a(b, c, d, e);
			});
		} : a;
	}(function(a, b) {
		if ("http://www.w3.org/2000/svg" !== a.namespaceURI || "innerHTML" in a) a.innerHTML = b;
		else {
			mb = mb || document.createElement("div");
			mb.innerHTML = "<svg>" + b.valueOf().toString() + "</svg>";
			for (b = mb.firstChild; a.firstChild;) a.removeChild(a.firstChild);
			for (; b.firstChild;) a.appendChild(b.firstChild);
		}
	});
	function ob(a, b) {
		if (b) {
			var c = a.firstChild;
			if (c && c === a.lastChild && 3 === c.nodeType) {
				c.nodeValue = b;
				return;
			}
		}
		a.textContent = b;
	}
	var pb = {
		animationIterationCount: !0,
		aspectRatio: !0,
		borderImageOutset: !0,
		borderImageSlice: !0,
		borderImageWidth: !0,
		boxFlex: !0,
		boxFlexGroup: !0,
		boxOrdinalGroup: !0,
		columnCount: !0,
		columns: !0,
		flex: !0,
		flexGrow: !0,
		flexPositive: !0,
		flexShrink: !0,
		flexNegative: !0,
		flexOrder: !0,
		gridArea: !0,
		gridRow: !0,
		gridRowEnd: !0,
		gridRowSpan: !0,
		gridRowStart: !0,
		gridColumn: !0,
		gridColumnEnd: !0,
		gridColumnSpan: !0,
		gridColumnStart: !0,
		fontWeight: !0,
		lineClamp: !0,
		lineHeight: !0,
		opacity: !0,
		order: !0,
		orphans: !0,
		tabSize: !0,
		widows: !0,
		zIndex: !0,
		zoom: !0,
		fillOpacity: !0,
		floodOpacity: !0,
		stopOpacity: !0,
		strokeDasharray: !0,
		strokeDashoffset: !0,
		strokeMiterlimit: !0,
		strokeOpacity: !0,
		strokeWidth: !0
	}, qb = [
		"Webkit",
		"ms",
		"Moz",
		"O"
	];
	Object.keys(pb).forEach(function(a) {
		qb.forEach(function(b) {
			b = b + a.charAt(0).toUpperCase() + a.substring(1);
			pb[b] = pb[a];
		});
	});
	function rb(a, b, c) {
		return null == b || "boolean" === typeof b || "" === b ? "" : c || "number" !== typeof b || 0 === b || pb.hasOwnProperty(a) && pb[a] ? ("" + b).trim() : b + "px";
	}
	function sb(a, b) {
		a = a.style;
		for (var c in b) if (b.hasOwnProperty(c)) {
			var d = 0 === c.indexOf("--"), e = rb(c, b[c], d);
			"float" === c && (c = "cssFloat");
			d ? a.setProperty(c, e) : a[c] = e;
		}
	}
	var tb = A({ menuitem: !0 }, {
		area: !0,
		base: !0,
		br: !0,
		col: !0,
		embed: !0,
		hr: !0,
		img: !0,
		input: !0,
		keygen: !0,
		link: !0,
		meta: !0,
		param: !0,
		source: !0,
		track: !0,
		wbr: !0
	});
	function ub(a, b) {
		if (b) {
			if (tb[a] && (null != b.children || null != b.dangerouslySetInnerHTML)) throw Error(p(137, a));
			if (null != b.dangerouslySetInnerHTML) {
				if (null != b.children) throw Error(p(60));
				if ("object" !== typeof b.dangerouslySetInnerHTML || !("__html" in b.dangerouslySetInnerHTML)) throw Error(p(61));
			}
			if (null != b.style && "object" !== typeof b.style) throw Error(p(62));
		}
	}
	function vb(a, b) {
		if (-1 === a.indexOf("-")) return "string" === typeof b.is;
		switch (a) {
			case "annotation-xml":
			case "color-profile":
			case "font-face":
			case "font-face-src":
			case "font-face-uri":
			case "font-face-format":
			case "font-face-name":
			case "missing-glyph": return !1;
			default: return !0;
		}
	}
	var wb = null;
	function xb(a) {
		a = a.target || a.srcElement || window;
		a.correspondingUseElement && (a = a.correspondingUseElement);
		return 3 === a.nodeType ? a.parentNode : a;
	}
	var yb = null, zb = null, Ab = null;
	function Bb(a) {
		if (a = Cb(a)) {
			if ("function" !== typeof yb) throw Error(p(280));
			var b = a.stateNode;
			b && (b = Db(b), yb(a.stateNode, a.type, b));
		}
	}
	function Eb(a) {
		zb ? Ab ? Ab.push(a) : Ab = [a] : zb = a;
	}
	function Fb() {
		if (zb) {
			var a = zb, b = Ab;
			Ab = zb = null;
			Bb(a);
			if (b) for (a = 0; a < b.length; a++) Bb(b[a]);
		}
	}
	function Gb(a, b) {
		return a(b);
	}
	function Hb() {}
	var Ib = !1;
	function Jb(a, b, c) {
		if (Ib) return a(b, c);
		Ib = !0;
		try {
			return Gb(a, b, c);
		} finally {
			if (Ib = !1, null !== zb || null !== Ab) Hb(), Fb();
		}
	}
	function Kb(a, b) {
		var c = a.stateNode;
		if (null === c) return null;
		var d = Db(c);
		if (null === d) return null;
		c = d[b];
		a: switch (b) {
			case "onClick":
			case "onClickCapture":
			case "onDoubleClick":
			case "onDoubleClickCapture":
			case "onMouseDown":
			case "onMouseDownCapture":
			case "onMouseMove":
			case "onMouseMoveCapture":
			case "onMouseUp":
			case "onMouseUpCapture":
			case "onMouseEnter":
				(d = !d.disabled) || (a = a.type, d = !("button" === a || "input" === a || "select" === a || "textarea" === a));
				a = !d;
				break a;
			default: a = !1;
		}
		if (a) return null;
		if (c && "function" !== typeof c) throw Error(p(231, b, typeof c));
		return c;
	}
	var Lb = !1;
	if (ia) try {
		var Mb = {};
		Object.defineProperty(Mb, "passive", { get: function() {
			Lb = !0;
		} });
		window.addEventListener("test", Mb, Mb);
		window.removeEventListener("test", Mb, Mb);
	} catch (a) {
		Lb = !1;
	}
	function Nb(a, b, c, d, e, f, g, h, k) {
		var l = Array.prototype.slice.call(arguments, 3);
		try {
			b.apply(c, l);
		} catch (m) {
			this.onError(m);
		}
	}
	var Ob = !1, Pb = null, Qb = !1, Rb = null, Sb = { onError: function(a) {
		Ob = !0;
		Pb = a;
	} };
	function Tb(a, b, c, d, e, f, g, h, k) {
		Ob = !1;
		Pb = null;
		Nb.apply(Sb, arguments);
	}
	function Ub(a, b, c, d, e, f, g, h, k) {
		Tb.apply(this, arguments);
		if (Ob) {
			if (Ob) {
				var l = Pb;
				Ob = !1;
				Pb = null;
			} else throw Error(p(198));
			Qb || (Qb = !0, Rb = l);
		}
	}
	function Vb(a) {
		var b = a, c = a;
		if (a.alternate) for (; b.return;) b = b.return;
		else {
			a = b;
			do
				b = a, 0 !== (b.flags & 4098) && (c = b.return), a = b.return;
			while (a);
		}
		return 3 === b.tag ? c : null;
	}
	function Wb(a) {
		if (13 === a.tag) {
			var b = a.memoizedState;
			null === b && (a = a.alternate, null !== a && (b = a.memoizedState));
			if (null !== b) return b.dehydrated;
		}
		return null;
	}
	function Xb(a) {
		if (Vb(a) !== a) throw Error(p(188));
	}
	function Yb(a) {
		var b = a.alternate;
		if (!b) {
			b = Vb(a);
			if (null === b) throw Error(p(188));
			return b !== a ? null : a;
		}
		for (var c = a, d = b;;) {
			var e = c.return;
			if (null === e) break;
			var f = e.alternate;
			if (null === f) {
				d = e.return;
				if (null !== d) {
					c = d;
					continue;
				}
				break;
			}
			if (e.child === f.child) {
				for (f = e.child; f;) {
					if (f === c) return Xb(e), a;
					if (f === d) return Xb(e), b;
					f = f.sibling;
				}
				throw Error(p(188));
			}
			if (c.return !== d.return) c = e, d = f;
			else {
				for (var g = !1, h = e.child; h;) {
					if (h === c) {
						g = !0;
						c = e;
						d = f;
						break;
					}
					if (h === d) {
						g = !0;
						d = e;
						c = f;
						break;
					}
					h = h.sibling;
				}
				if (!g) {
					for (h = f.child; h;) {
						if (h === c) {
							g = !0;
							c = f;
							d = e;
							break;
						}
						if (h === d) {
							g = !0;
							d = f;
							c = e;
							break;
						}
						h = h.sibling;
					}
					if (!g) throw Error(p(189));
				}
			}
			if (c.alternate !== d) throw Error(p(190));
		}
		if (3 !== c.tag) throw Error(p(188));
		return c.stateNode.current === c ? a : b;
	}
	function Zb(a) {
		a = Yb(a);
		return null !== a ? $b(a) : null;
	}
	function $b(a) {
		if (5 === a.tag || 6 === a.tag) return a;
		for (a = a.child; null !== a;) {
			var b = $b(a);
			if (null !== b) return b;
			a = a.sibling;
		}
		return null;
	}
	var ac = ca.unstable_scheduleCallback, bc = ca.unstable_cancelCallback, cc = ca.unstable_shouldYield, dc = ca.unstable_requestPaint, B = ca.unstable_now, ec = ca.unstable_getCurrentPriorityLevel, fc = ca.unstable_ImmediatePriority, gc = ca.unstable_UserBlockingPriority, hc = ca.unstable_NormalPriority, ic = ca.unstable_LowPriority, jc = ca.unstable_IdlePriority, kc = null, lc = null;
	function mc(a) {
		if (lc && "function" === typeof lc.onCommitFiberRoot) try {
			lc.onCommitFiberRoot(kc, a, void 0, 128 === (a.current.flags & 128));
		} catch (b) {}
	}
	var oc = Math.clz32 ? Math.clz32 : nc, pc = Math.log, qc = Math.LN2;
	function nc(a) {
		a >>>= 0;
		return 0 === a ? 32 : 31 - (pc(a) / qc | 0) | 0;
	}
	var rc = 64, sc = 4194304;
	function tc(a) {
		switch (a & -a) {
			case 1: return 1;
			case 2: return 2;
			case 4: return 4;
			case 8: return 8;
			case 16: return 16;
			case 32: return 32;
			case 64:
			case 128:
			case 256:
			case 512:
			case 1024:
			case 2048:
			case 4096:
			case 8192:
			case 16384:
			case 32768:
			case 65536:
			case 131072:
			case 262144:
			case 524288:
			case 1048576:
			case 2097152: return a & 4194240;
			case 4194304:
			case 8388608:
			case 16777216:
			case 33554432:
			case 67108864: return a & 130023424;
			case 134217728: return 134217728;
			case 268435456: return 268435456;
			case 536870912: return 536870912;
			case 1073741824: return 1073741824;
			default: return a;
		}
	}
	function uc(a, b) {
		var c = a.pendingLanes;
		if (0 === c) return 0;
		var d = 0, e = a.suspendedLanes, f = a.pingedLanes, g = c & 268435455;
		if (0 !== g) {
			var h = g & ~e;
			0 !== h ? d = tc(h) : (f &= g, 0 !== f && (d = tc(f)));
		} else g = c & ~e, 0 !== g ? d = tc(g) : 0 !== f && (d = tc(f));
		if (0 === d) return 0;
		if (0 !== b && b !== d && 0 === (b & e) && (e = d & -d, f = b & -b, e >= f || 16 === e && 0 !== (f & 4194240))) return b;
		0 !== (d & 4) && (d |= c & 16);
		b = a.entangledLanes;
		if (0 !== b) for (a = a.entanglements, b &= d; 0 < b;) c = 31 - oc(b), e = 1 << c, d |= a[c], b &= ~e;
		return d;
	}
	function vc(a, b) {
		switch (a) {
			case 1:
			case 2:
			case 4: return b + 250;
			case 8:
			case 16:
			case 32:
			case 64:
			case 128:
			case 256:
			case 512:
			case 1024:
			case 2048:
			case 4096:
			case 8192:
			case 16384:
			case 32768:
			case 65536:
			case 131072:
			case 262144:
			case 524288:
			case 1048576:
			case 2097152: return b + 5e3;
			case 4194304:
			case 8388608:
			case 16777216:
			case 33554432:
			case 67108864: return -1;
			case 134217728:
			case 268435456:
			case 536870912:
			case 1073741824: return -1;
			default: return -1;
		}
	}
	function wc(a, b) {
		for (var c = a.suspendedLanes, d = a.pingedLanes, e = a.expirationTimes, f = a.pendingLanes; 0 < f;) {
			var g = 31 - oc(f), h = 1 << g, k = e[g];
			if (-1 === k) {
				if (0 === (h & c) || 0 !== (h & d)) e[g] = vc(h, b);
			} else k <= b && (a.expiredLanes |= h);
			f &= ~h;
		}
	}
	function xc(a) {
		a = a.pendingLanes & -1073741825;
		return 0 !== a ? a : a & 1073741824 ? 1073741824 : 0;
	}
	function yc() {
		var a = rc;
		rc <<= 1;
		0 === (rc & 4194240) && (rc = 64);
		return a;
	}
	function zc(a) {
		for (var b = [], c = 0; 31 > c; c++) b.push(a);
		return b;
	}
	function Ac(a, b, c) {
		a.pendingLanes |= b;
		536870912 !== b && (a.suspendedLanes = 0, a.pingedLanes = 0);
		a = a.eventTimes;
		b = 31 - oc(b);
		a[b] = c;
	}
	function Bc(a, b) {
		var c = a.pendingLanes & ~b;
		a.pendingLanes = b;
		a.suspendedLanes = 0;
		a.pingedLanes = 0;
		a.expiredLanes &= b;
		a.mutableReadLanes &= b;
		a.entangledLanes &= b;
		b = a.entanglements;
		var d = a.eventTimes;
		for (a = a.expirationTimes; 0 < c;) {
			var e = 31 - oc(c), f = 1 << e;
			b[e] = 0;
			d[e] = -1;
			a[e] = -1;
			c &= ~f;
		}
	}
	function Cc(a, b) {
		var c = a.entangledLanes |= b;
		for (a = a.entanglements; c;) {
			var d = 31 - oc(c), e = 1 << d;
			e & b | a[d] & b && (a[d] |= b);
			c &= ~e;
		}
	}
	var C = 0;
	function Dc(a) {
		a &= -a;
		return 1 < a ? 4 < a ? 0 !== (a & 268435455) ? 16 : 536870912 : 4 : 1;
	}
	var Ec, Fc, Gc, Hc, Ic, Jc = !1, Kc = [], Lc = null, Mc = null, Nc = null, Oc = /* @__PURE__ */ new Map(), Pc = /* @__PURE__ */ new Map(), Qc = [], Rc = "mousedown mouseup touchcancel touchend touchstart auxclick dblclick pointercancel pointerdown pointerup dragend dragstart drop compositionend compositionstart keydown keypress keyup input textInput copy cut paste click change contextmenu reset submit".split(" ");
	function Sc(a, b) {
		switch (a) {
			case "focusin":
			case "focusout":
				Lc = null;
				break;
			case "dragenter":
			case "dragleave":
				Mc = null;
				break;
			case "mouseover":
			case "mouseout":
				Nc = null;
				break;
			case "pointerover":
			case "pointerout":
				Oc.delete(b.pointerId);
				break;
			case "gotpointercapture":
			case "lostpointercapture": Pc.delete(b.pointerId);
		}
	}
	function Tc(a, b, c, d, e, f) {
		if (null === a || a.nativeEvent !== f) return a = {
			blockedOn: b,
			domEventName: c,
			eventSystemFlags: d,
			nativeEvent: f,
			targetContainers: [e]
		}, null !== b && (b = Cb(b), null !== b && Fc(b)), a;
		a.eventSystemFlags |= d;
		b = a.targetContainers;
		null !== e && -1 === b.indexOf(e) && b.push(e);
		return a;
	}
	function Uc(a, b, c, d, e) {
		switch (b) {
			case "focusin": return Lc = Tc(Lc, a, b, c, d, e), !0;
			case "dragenter": return Mc = Tc(Mc, a, b, c, d, e), !0;
			case "mouseover": return Nc = Tc(Nc, a, b, c, d, e), !0;
			case "pointerover":
				var f = e.pointerId;
				Oc.set(f, Tc(Oc.get(f) || null, a, b, c, d, e));
				return !0;
			case "gotpointercapture": return f = e.pointerId, Pc.set(f, Tc(Pc.get(f) || null, a, b, c, d, e)), !0;
		}
		return !1;
	}
	function Vc(a) {
		var b = Wc(a.target);
		if (null !== b) {
			var c = Vb(b);
			if (null !== c) {
				if (b = c.tag, 13 === b) {
					if (b = Wb(c), null !== b) {
						a.blockedOn = b;
						Ic(a.priority, function() {
							Gc(c);
						});
						return;
					}
				} else if (3 === b && c.stateNode.current.memoizedState.isDehydrated) {
					a.blockedOn = 3 === c.tag ? c.stateNode.containerInfo : null;
					return;
				}
			}
		}
		a.blockedOn = null;
	}
	function Xc(a) {
		if (null !== a.blockedOn) return !1;
		for (var b = a.targetContainers; 0 < b.length;) {
			var c = Yc(a.domEventName, a.eventSystemFlags, b[0], a.nativeEvent);
			if (null === c) {
				c = a.nativeEvent;
				var d = new c.constructor(c.type, c);
				wb = d;
				c.target.dispatchEvent(d);
				wb = null;
			} else return b = Cb(c), null !== b && Fc(b), a.blockedOn = c, !1;
			b.shift();
		}
		return !0;
	}
	function Zc(a, b, c) {
		Xc(a) && c.delete(b);
	}
	function $c() {
		Jc = !1;
		null !== Lc && Xc(Lc) && (Lc = null);
		null !== Mc && Xc(Mc) && (Mc = null);
		null !== Nc && Xc(Nc) && (Nc = null);
		Oc.forEach(Zc);
		Pc.forEach(Zc);
	}
	function ad(a, b) {
		a.blockedOn === b && (a.blockedOn = null, Jc || (Jc = !0, ca.unstable_scheduleCallback(ca.unstable_NormalPriority, $c)));
	}
	function bd(a) {
		function b(b) {
			return ad(b, a);
		}
		if (0 < Kc.length) {
			ad(Kc[0], a);
			for (var c = 1; c < Kc.length; c++) {
				var d = Kc[c];
				d.blockedOn === a && (d.blockedOn = null);
			}
		}
		null !== Lc && ad(Lc, a);
		null !== Mc && ad(Mc, a);
		null !== Nc && ad(Nc, a);
		Oc.forEach(b);
		Pc.forEach(b);
		for (c = 0; c < Qc.length; c++) d = Qc[c], d.blockedOn === a && (d.blockedOn = null);
		for (; 0 < Qc.length && (c = Qc[0], null === c.blockedOn);) Vc(c), null === c.blockedOn && Qc.shift();
	}
	var cd = ua.ReactCurrentBatchConfig, dd = !0;
	function ed(a, b, c, d) {
		var e = C, f = cd.transition;
		cd.transition = null;
		try {
			C = 1, fd(a, b, c, d);
		} finally {
			C = e, cd.transition = f;
		}
	}
	function gd(a, b, c, d) {
		var e = C, f = cd.transition;
		cd.transition = null;
		try {
			C = 4, fd(a, b, c, d);
		} finally {
			C = e, cd.transition = f;
		}
	}
	function fd(a, b, c, d) {
		if (dd) {
			var e = Yc(a, b, c, d);
			if (null === e) hd(a, b, d, id, c), Sc(a, d);
			else if (Uc(e, a, b, c, d)) d.stopPropagation();
			else if (Sc(a, d), b & 4 && -1 < Rc.indexOf(a)) {
				for (; null !== e;) {
					var f = Cb(e);
					null !== f && Ec(f);
					f = Yc(a, b, c, d);
					null === f && hd(a, b, d, id, c);
					if (f === e) break;
					e = f;
				}
				null !== e && d.stopPropagation();
			} else hd(a, b, d, null, c);
		}
	}
	var id = null;
	function Yc(a, b, c, d) {
		id = null;
		a = xb(d);
		a = Wc(a);
		if (null !== a) if (b = Vb(a), null === b) a = null;
		else if (c = b.tag, 13 === c) {
			a = Wb(b);
			if (null !== a) return a;
			a = null;
		} else if (3 === c) {
			if (b.stateNode.current.memoizedState.isDehydrated) return 3 === b.tag ? b.stateNode.containerInfo : null;
			a = null;
		} else b !== a && (a = null);
		id = a;
		return null;
	}
	function jd(a) {
		switch (a) {
			case "cancel":
			case "click":
			case "close":
			case "contextmenu":
			case "copy":
			case "cut":
			case "auxclick":
			case "dblclick":
			case "dragend":
			case "dragstart":
			case "drop":
			case "focusin":
			case "focusout":
			case "input":
			case "invalid":
			case "keydown":
			case "keypress":
			case "keyup":
			case "mousedown":
			case "mouseup":
			case "paste":
			case "pause":
			case "play":
			case "pointercancel":
			case "pointerdown":
			case "pointerup":
			case "ratechange":
			case "reset":
			case "resize":
			case "seeked":
			case "submit":
			case "touchcancel":
			case "touchend":
			case "touchstart":
			case "volumechange":
			case "change":
			case "selectionchange":
			case "textInput":
			case "compositionstart":
			case "compositionend":
			case "compositionupdate":
			case "beforeblur":
			case "afterblur":
			case "beforeinput":
			case "blur":
			case "fullscreenchange":
			case "focus":
			case "hashchange":
			case "popstate":
			case "select":
			case "selectstart": return 1;
			case "drag":
			case "dragenter":
			case "dragexit":
			case "dragleave":
			case "dragover":
			case "mousemove":
			case "mouseout":
			case "mouseover":
			case "pointermove":
			case "pointerout":
			case "pointerover":
			case "scroll":
			case "toggle":
			case "touchmove":
			case "wheel":
			case "mouseenter":
			case "mouseleave":
			case "pointerenter":
			case "pointerleave": return 4;
			case "message": switch (ec()) {
				case fc: return 1;
				case gc: return 4;
				case hc:
				case ic: return 16;
				case jc: return 536870912;
				default: return 16;
			}
			default: return 16;
		}
	}
	var kd = null, ld = null, md = null;
	function nd() {
		if (md) return md;
		var a, b = ld, c = b.length, d, e = "value" in kd ? kd.value : kd.textContent, f = e.length;
		for (a = 0; a < c && b[a] === e[a]; a++);
		var g = c - a;
		for (d = 1; d <= g && b[c - d] === e[f - d]; d++);
		return md = e.slice(a, 1 < d ? 1 - d : void 0);
	}
	function od(a) {
		var b = a.keyCode;
		"charCode" in a ? (a = a.charCode, 0 === a && 13 === b && (a = 13)) : a = b;
		10 === a && (a = 13);
		return 32 <= a || 13 === a ? a : 0;
	}
	function pd() {
		return !0;
	}
	function qd() {
		return !1;
	}
	function rd(a) {
		function b(b, d, e, f, g) {
			this._reactName = b;
			this._targetInst = e;
			this.type = d;
			this.nativeEvent = f;
			this.target = g;
			this.currentTarget = null;
			for (var c in a) a.hasOwnProperty(c) && (b = a[c], this[c] = b ? b(f) : f[c]);
			this.isDefaultPrevented = (null != f.defaultPrevented ? f.defaultPrevented : !1 === f.returnValue) ? pd : qd;
			this.isPropagationStopped = qd;
			return this;
		}
		A(b.prototype, {
			preventDefault: function() {
				this.defaultPrevented = !0;
				var a = this.nativeEvent;
				a && (a.preventDefault ? a.preventDefault() : "unknown" !== typeof a.returnValue && (a.returnValue = !1), this.isDefaultPrevented = pd);
			},
			stopPropagation: function() {
				var a = this.nativeEvent;
				a && (a.stopPropagation ? a.stopPropagation() : "unknown" !== typeof a.cancelBubble && (a.cancelBubble = !0), this.isPropagationStopped = pd);
			},
			persist: function() {},
			isPersistent: pd
		});
		return b;
	}
	var sd = {
		eventPhase: 0,
		bubbles: 0,
		cancelable: 0,
		timeStamp: function(a) {
			return a.timeStamp || Date.now();
		},
		defaultPrevented: 0,
		isTrusted: 0
	}, td = rd(sd), ud = A({}, sd, {
		view: 0,
		detail: 0
	}), vd = rd(ud), wd, xd, yd, Ad = A({}, ud, {
		screenX: 0,
		screenY: 0,
		clientX: 0,
		clientY: 0,
		pageX: 0,
		pageY: 0,
		ctrlKey: 0,
		shiftKey: 0,
		altKey: 0,
		metaKey: 0,
		getModifierState: zd,
		button: 0,
		buttons: 0,
		relatedTarget: function(a) {
			return void 0 === a.relatedTarget ? a.fromElement === a.srcElement ? a.toElement : a.fromElement : a.relatedTarget;
		},
		movementX: function(a) {
			if ("movementX" in a) return a.movementX;
			a !== yd && (yd && "mousemove" === a.type ? (wd = a.screenX - yd.screenX, xd = a.screenY - yd.screenY) : xd = wd = 0, yd = a);
			return wd;
		},
		movementY: function(a) {
			return "movementY" in a ? a.movementY : xd;
		}
	}), Bd = rd(Ad), Dd = rd(A({}, Ad, { dataTransfer: 0 })), Fd = rd(A({}, ud, { relatedTarget: 0 })), Hd = rd(A({}, sd, {
		animationName: 0,
		elapsedTime: 0,
		pseudoElement: 0
	})), Jd = rd(A({}, sd, { clipboardData: function(a) {
		return "clipboardData" in a ? a.clipboardData : window.clipboardData;
	} })), Ld = rd(A({}, sd, { data: 0 })), Md = {
		Esc: "Escape",
		Spacebar: " ",
		Left: "ArrowLeft",
		Up: "ArrowUp",
		Right: "ArrowRight",
		Down: "ArrowDown",
		Del: "Delete",
		Win: "OS",
		Menu: "ContextMenu",
		Apps: "ContextMenu",
		Scroll: "ScrollLock",
		MozPrintableKey: "Unidentified"
	}, Nd = {
		8: "Backspace",
		9: "Tab",
		12: "Clear",
		13: "Enter",
		16: "Shift",
		17: "Control",
		18: "Alt",
		19: "Pause",
		20: "CapsLock",
		27: "Escape",
		32: " ",
		33: "PageUp",
		34: "PageDown",
		35: "End",
		36: "Home",
		37: "ArrowLeft",
		38: "ArrowUp",
		39: "ArrowRight",
		40: "ArrowDown",
		45: "Insert",
		46: "Delete",
		112: "F1",
		113: "F2",
		114: "F3",
		115: "F4",
		116: "F5",
		117: "F6",
		118: "F7",
		119: "F8",
		120: "F9",
		121: "F10",
		122: "F11",
		123: "F12",
		144: "NumLock",
		145: "ScrollLock",
		224: "Meta"
	}, Od = {
		Alt: "altKey",
		Control: "ctrlKey",
		Meta: "metaKey",
		Shift: "shiftKey"
	};
	function Pd(a) {
		var b = this.nativeEvent;
		return b.getModifierState ? b.getModifierState(a) : (a = Od[a]) ? !!b[a] : !1;
	}
	function zd() {
		return Pd;
	}
	var Rd = rd(A({}, ud, {
		key: function(a) {
			if (a.key) {
				var b = Md[a.key] || a.key;
				if ("Unidentified" !== b) return b;
			}
			return "keypress" === a.type ? (a = od(a), 13 === a ? "Enter" : String.fromCharCode(a)) : "keydown" === a.type || "keyup" === a.type ? Nd[a.keyCode] || "Unidentified" : "";
		},
		code: 0,
		location: 0,
		ctrlKey: 0,
		shiftKey: 0,
		altKey: 0,
		metaKey: 0,
		repeat: 0,
		locale: 0,
		getModifierState: zd,
		charCode: function(a) {
			return "keypress" === a.type ? od(a) : 0;
		},
		keyCode: function(a) {
			return "keydown" === a.type || "keyup" === a.type ? a.keyCode : 0;
		},
		which: function(a) {
			return "keypress" === a.type ? od(a) : "keydown" === a.type || "keyup" === a.type ? a.keyCode : 0;
		}
	})), Td = rd(A({}, Ad, {
		pointerId: 0,
		width: 0,
		height: 0,
		pressure: 0,
		tangentialPressure: 0,
		tiltX: 0,
		tiltY: 0,
		twist: 0,
		pointerType: 0,
		isPrimary: 0
	})), Vd = rd(A({}, ud, {
		touches: 0,
		targetTouches: 0,
		changedTouches: 0,
		altKey: 0,
		metaKey: 0,
		ctrlKey: 0,
		shiftKey: 0,
		getModifierState: zd
	})), Xd = rd(A({}, sd, {
		propertyName: 0,
		elapsedTime: 0,
		pseudoElement: 0
	})), Zd = rd(A({}, Ad, {
		deltaX: function(a) {
			return "deltaX" in a ? a.deltaX : "wheelDeltaX" in a ? -a.wheelDeltaX : 0;
		},
		deltaY: function(a) {
			return "deltaY" in a ? a.deltaY : "wheelDeltaY" in a ? -a.wheelDeltaY : "wheelDelta" in a ? -a.wheelDelta : 0;
		},
		deltaZ: 0,
		deltaMode: 0
	})), $d = [
		9,
		13,
		27,
		32
	], ae = ia && "CompositionEvent" in window, be = null;
	ia && "documentMode" in document && (be = document.documentMode);
	var ce = ia && "TextEvent" in window && !be, de = ia && (!ae || be && 8 < be && 11 >= be), ee = String.fromCharCode(32), fe = !1;
	function ge(a, b) {
		switch (a) {
			case "keyup": return -1 !== $d.indexOf(b.keyCode);
			case "keydown": return 229 !== b.keyCode;
			case "keypress":
			case "mousedown":
			case "focusout": return !0;
			default: return !1;
		}
	}
	function he(a) {
		a = a.detail;
		return "object" === typeof a && "data" in a ? a.data : null;
	}
	var ie = !1;
	function je(a, b) {
		switch (a) {
			case "compositionend": return he(b);
			case "keypress":
				if (32 !== b.which) return null;
				fe = !0;
				return ee;
			case "textInput": return a = b.data, a === ee && fe ? null : a;
			default: return null;
		}
	}
	function ke(a, b) {
		if (ie) return "compositionend" === a || !ae && ge(a, b) ? (a = nd(), md = ld = kd = null, ie = !1, a) : null;
		switch (a) {
			case "paste": return null;
			case "keypress":
				if (!(b.ctrlKey || b.altKey || b.metaKey) || b.ctrlKey && b.altKey) {
					if (b.char && 1 < b.char.length) return b.char;
					if (b.which) return String.fromCharCode(b.which);
				}
				return null;
			case "compositionend": return de && "ko" !== b.locale ? null : b.data;
			default: return null;
		}
	}
	var le = {
		color: !0,
		date: !0,
		datetime: !0,
		"datetime-local": !0,
		email: !0,
		month: !0,
		number: !0,
		password: !0,
		range: !0,
		search: !0,
		tel: !0,
		text: !0,
		time: !0,
		url: !0,
		week: !0
	};
	function me(a) {
		var b = a && a.nodeName && a.nodeName.toLowerCase();
		return "input" === b ? !!le[a.type] : "textarea" === b ? !0 : !1;
	}
	function ne(a, b, c, d) {
		Eb(d);
		b = oe(b, "onChange");
		0 < b.length && (c = new td("onChange", "change", null, c, d), a.push({
			event: c,
			listeners: b
		}));
	}
	var pe = null, qe = null;
	function re(a) {
		se(a, 0);
	}
	function te(a) {
		if (Wa(ue(a))) return a;
	}
	function ve(a, b) {
		if ("change" === a) return b;
	}
	var we = !1;
	if (ia) {
		var xe;
		if (ia) {
			var ye = "oninput" in document;
			if (!ye) {
				var ze = document.createElement("div");
				ze.setAttribute("oninput", "return;");
				ye = "function" === typeof ze.oninput;
			}
			xe = ye;
		} else xe = !1;
		we = xe && (!document.documentMode || 9 < document.documentMode);
	}
	function Ae() {
		pe && (pe.detachEvent("onpropertychange", Be), qe = pe = null);
	}
	function Be(a) {
		if ("value" === a.propertyName && te(qe)) {
			var b = [];
			ne(b, qe, a, xb(a));
			Jb(re, b);
		}
	}
	function Ce(a, b, c) {
		"focusin" === a ? (Ae(), pe = b, qe = c, pe.attachEvent("onpropertychange", Be)) : "focusout" === a && Ae();
	}
	function De(a) {
		if ("selectionchange" === a || "keyup" === a || "keydown" === a) return te(qe);
	}
	function Ee(a, b) {
		if ("click" === a) return te(b);
	}
	function Fe(a, b) {
		if ("input" === a || "change" === a) return te(b);
	}
	function Ge(a, b) {
		return a === b && (0 !== a || 1 / a === 1 / b) || a !== a && b !== b;
	}
	var He = "function" === typeof Object.is ? Object.is : Ge;
	function Ie(a, b) {
		if (He(a, b)) return !0;
		if ("object" !== typeof a || null === a || "object" !== typeof b || null === b) return !1;
		var c = Object.keys(a), d = Object.keys(b);
		if (c.length !== d.length) return !1;
		for (d = 0; d < c.length; d++) {
			var e = c[d];
			if (!ja.call(b, e) || !He(a[e], b[e])) return !1;
		}
		return !0;
	}
	function Je(a) {
		for (; a && a.firstChild;) a = a.firstChild;
		return a;
	}
	function Ke(a, b) {
		var c = Je(a);
		a = 0;
		for (var d; c;) {
			if (3 === c.nodeType) {
				d = a + c.textContent.length;
				if (a <= b && d >= b) return {
					node: c,
					offset: b - a
				};
				a = d;
			}
			a: {
				for (; c;) {
					if (c.nextSibling) {
						c = c.nextSibling;
						break a;
					}
					c = c.parentNode;
				}
				c = void 0;
			}
			c = Je(c);
		}
	}
	function Le(a, b) {
		return a && b ? a === b ? !0 : a && 3 === a.nodeType ? !1 : b && 3 === b.nodeType ? Le(a, b.parentNode) : "contains" in a ? a.contains(b) : a.compareDocumentPosition ? !!(a.compareDocumentPosition(b) & 16) : !1 : !1;
	}
	function Me() {
		for (var a = window, b = Xa(); b instanceof a.HTMLIFrameElement;) {
			try {
				var c = "string" === typeof b.contentWindow.location.href;
			} catch (d) {
				c = !1;
			}
			if (c) a = b.contentWindow;
			else break;
			b = Xa(a.document);
		}
		return b;
	}
	function Ne(a) {
		var b = a && a.nodeName && a.nodeName.toLowerCase();
		return b && ("input" === b && ("text" === a.type || "search" === a.type || "tel" === a.type || "url" === a.type || "password" === a.type) || "textarea" === b || "true" === a.contentEditable);
	}
	function Oe(a) {
		var b = Me(), c = a.focusedElem, d = a.selectionRange;
		if (b !== c && c && c.ownerDocument && Le(c.ownerDocument.documentElement, c)) {
			if (null !== d && Ne(c)) {
				if (b = d.start, a = d.end, void 0 === a && (a = b), "selectionStart" in c) c.selectionStart = b, c.selectionEnd = Math.min(a, c.value.length);
				else if (a = (b = c.ownerDocument || document) && b.defaultView || window, a.getSelection) {
					a = a.getSelection();
					var e = c.textContent.length, f = Math.min(d.start, e);
					d = void 0 === d.end ? f : Math.min(d.end, e);
					!a.extend && f > d && (e = d, d = f, f = e);
					e = Ke(c, f);
					var g = Ke(c, d);
					e && g && (1 !== a.rangeCount || a.anchorNode !== e.node || a.anchorOffset !== e.offset || a.focusNode !== g.node || a.focusOffset !== g.offset) && (b = b.createRange(), b.setStart(e.node, e.offset), a.removeAllRanges(), f > d ? (a.addRange(b), a.extend(g.node, g.offset)) : (b.setEnd(g.node, g.offset), a.addRange(b)));
				}
			}
			b = [];
			for (a = c; a = a.parentNode;) 1 === a.nodeType && b.push({
				element: a,
				left: a.scrollLeft,
				top: a.scrollTop
			});
			"function" === typeof c.focus && c.focus();
			for (c = 0; c < b.length; c++) a = b[c], a.element.scrollLeft = a.left, a.element.scrollTop = a.top;
		}
	}
	var Pe = ia && "documentMode" in document && 11 >= document.documentMode, Qe = null, Re = null, Se = null, Te = !1;
	function Ue(a, b, c) {
		var d = c.window === c ? c.document : 9 === c.nodeType ? c : c.ownerDocument;
		Te || null == Qe || Qe !== Xa(d) || (d = Qe, "selectionStart" in d && Ne(d) ? d = {
			start: d.selectionStart,
			end: d.selectionEnd
		} : (d = (d.ownerDocument && d.ownerDocument.defaultView || window).getSelection(), d = {
			anchorNode: d.anchorNode,
			anchorOffset: d.anchorOffset,
			focusNode: d.focusNode,
			focusOffset: d.focusOffset
		}), Se && Ie(Se, d) || (Se = d, d = oe(Re, "onSelect"), 0 < d.length && (b = new td("onSelect", "select", null, b, c), a.push({
			event: b,
			listeners: d
		}), b.target = Qe)));
	}
	function Ve(a, b) {
		var c = {};
		c[a.toLowerCase()] = b.toLowerCase();
		c["Webkit" + a] = "webkit" + b;
		c["Moz" + a] = "moz" + b;
		return c;
	}
	var We = {
		animationend: Ve("Animation", "AnimationEnd"),
		animationiteration: Ve("Animation", "AnimationIteration"),
		animationstart: Ve("Animation", "AnimationStart"),
		transitionend: Ve("Transition", "TransitionEnd")
	}, Xe = {}, Ye = {};
	ia && (Ye = document.createElement("div").style, "AnimationEvent" in window || (delete We.animationend.animation, delete We.animationiteration.animation, delete We.animationstart.animation), "TransitionEvent" in window || delete We.transitionend.transition);
	function Ze(a) {
		if (Xe[a]) return Xe[a];
		if (!We[a]) return a;
		var b = We[a], c;
		for (c in b) if (b.hasOwnProperty(c) && c in Ye) return Xe[a] = b[c];
		return a;
	}
	var $e = Ze("animationend"), af = Ze("animationiteration"), bf = Ze("animationstart"), cf = Ze("transitionend"), df = /* @__PURE__ */ new Map(), ef = "abort auxClick cancel canPlay canPlayThrough click close contextMenu copy cut drag dragEnd dragEnter dragExit dragLeave dragOver dragStart drop durationChange emptied encrypted ended error gotPointerCapture input invalid keyDown keyPress keyUp load loadedData loadedMetadata loadStart lostPointerCapture mouseDown mouseMove mouseOut mouseOver mouseUp paste pause play playing pointerCancel pointerDown pointerMove pointerOut pointerOver pointerUp progress rateChange reset resize seeked seeking stalled submit suspend timeUpdate touchCancel touchEnd touchStart volumeChange scroll toggle touchMove waiting wheel".split(" ");
	function ff(a, b) {
		df.set(a, b);
		fa(b, [a]);
	}
	for (var gf = 0; gf < ef.length; gf++) {
		var hf = ef[gf];
		ff(hf.toLowerCase(), "on" + (hf[0].toUpperCase() + hf.slice(1)));
	}
	ff($e, "onAnimationEnd");
	ff(af, "onAnimationIteration");
	ff(bf, "onAnimationStart");
	ff("dblclick", "onDoubleClick");
	ff("focusin", "onFocus");
	ff("focusout", "onBlur");
	ff(cf, "onTransitionEnd");
	ha("onMouseEnter", ["mouseout", "mouseover"]);
	ha("onMouseLeave", ["mouseout", "mouseover"]);
	ha("onPointerEnter", ["pointerout", "pointerover"]);
	ha("onPointerLeave", ["pointerout", "pointerover"]);
	fa("onChange", "change click focusin focusout input keydown keyup selectionchange".split(" "));
	fa("onSelect", "focusout contextmenu dragend focusin keydown keyup mousedown mouseup selectionchange".split(" "));
	fa("onBeforeInput", [
		"compositionend",
		"keypress",
		"textInput",
		"paste"
	]);
	fa("onCompositionEnd", "compositionend focusout keydown keypress keyup mousedown".split(" "));
	fa("onCompositionStart", "compositionstart focusout keydown keypress keyup mousedown".split(" "));
	fa("onCompositionUpdate", "compositionupdate focusout keydown keypress keyup mousedown".split(" "));
	var lf = "abort canplay canplaythrough durationchange emptied encrypted ended error loadeddata loadedmetadata loadstart pause play playing progress ratechange resize seeked seeking stalled suspend timeupdate volumechange waiting".split(" "), mf = new Set("cancel close invalid load scroll toggle".split(" ").concat(lf));
	function nf(a, b, c) {
		var d = a.type || "unknown-event";
		a.currentTarget = c;
		Ub(d, b, void 0, a);
		a.currentTarget = null;
	}
	function se(a, b) {
		b = 0 !== (b & 4);
		for (var c = 0; c < a.length; c++) {
			var d = a[c], e = d.event;
			d = d.listeners;
			a: {
				var f = void 0;
				if (b) for (var g = d.length - 1; 0 <= g; g--) {
					var h = d[g], k = h.instance, l = h.currentTarget;
					h = h.listener;
					if (k !== f && e.isPropagationStopped()) break a;
					nf(e, h, l);
					f = k;
				}
				else for (g = 0; g < d.length; g++) {
					h = d[g];
					k = h.instance;
					l = h.currentTarget;
					h = h.listener;
					if (k !== f && e.isPropagationStopped()) break a;
					nf(e, h, l);
					f = k;
				}
			}
		}
		if (Qb) throw a = Rb, Qb = !1, Rb = null, a;
	}
	function D(a, b) {
		var c = b[of];
		void 0 === c && (c = b[of] = /* @__PURE__ */ new Set());
		var d = a + "__bubble";
		c.has(d) || (pf(b, a, 2, !1), c.add(d));
	}
	function qf(a, b, c) {
		var d = 0;
		b && (d |= 4);
		pf(c, a, d, b);
	}
	var rf = "_reactListening" + Math.random().toString(36).slice(2);
	function sf(a) {
		if (!a[rf]) {
			a[rf] = !0;
			da.forEach(function(b) {
				"selectionchange" !== b && (mf.has(b) || qf(b, !1, a), qf(b, !0, a));
			});
			var b = 9 === a.nodeType ? a : a.ownerDocument;
			null === b || b[rf] || (b[rf] = !0, qf("selectionchange", !1, b));
		}
	}
	function pf(a, b, c, d) {
		switch (jd(b)) {
			case 1:
				var e = ed;
				break;
			case 4:
				e = gd;
				break;
			default: e = fd;
		}
		c = e.bind(null, b, c, a);
		e = void 0;
		!Lb || "touchstart" !== b && "touchmove" !== b && "wheel" !== b || (e = !0);
		d ? void 0 !== e ? a.addEventListener(b, c, {
			capture: !0,
			passive: e
		}) : a.addEventListener(b, c, !0) : void 0 !== e ? a.addEventListener(b, c, { passive: e }) : a.addEventListener(b, c, !1);
	}
	function hd(a, b, c, d, e) {
		var f = d;
		if (0 === (b & 1) && 0 === (b & 2) && null !== d) a: for (;;) {
			if (null === d) return;
			var g = d.tag;
			if (3 === g || 4 === g) {
				var h = d.stateNode.containerInfo;
				if (h === e || 8 === h.nodeType && h.parentNode === e) break;
				if (4 === g) for (g = d.return; null !== g;) {
					var k = g.tag;
					if (3 === k || 4 === k) {
						if (k = g.stateNode.containerInfo, k === e || 8 === k.nodeType && k.parentNode === e) return;
					}
					g = g.return;
				}
				for (; null !== h;) {
					g = Wc(h);
					if (null === g) return;
					k = g.tag;
					if (5 === k || 6 === k) {
						d = f = g;
						continue a;
					}
					h = h.parentNode;
				}
			}
			d = d.return;
		}
		Jb(function() {
			var d = f, e = xb(c), g = [];
			a: {
				var h = df.get(a);
				if (void 0 !== h) {
					var k = td, n = a;
					switch (a) {
						case "keypress": if (0 === od(c)) break a;
						case "keydown":
						case "keyup":
							k = Rd;
							break;
						case "focusin":
							n = "focus";
							k = Fd;
							break;
						case "focusout":
							n = "blur";
							k = Fd;
							break;
						case "beforeblur":
						case "afterblur":
							k = Fd;
							break;
						case "click": if (2 === c.button) break a;
						case "auxclick":
						case "dblclick":
						case "mousedown":
						case "mousemove":
						case "mouseup":
						case "mouseout":
						case "mouseover":
						case "contextmenu":
							k = Bd;
							break;
						case "drag":
						case "dragend":
						case "dragenter":
						case "dragexit":
						case "dragleave":
						case "dragover":
						case "dragstart":
						case "drop":
							k = Dd;
							break;
						case "touchcancel":
						case "touchend":
						case "touchmove":
						case "touchstart":
							k = Vd;
							break;
						case $e:
						case af:
						case bf:
							k = Hd;
							break;
						case cf:
							k = Xd;
							break;
						case "scroll":
							k = vd;
							break;
						case "wheel":
							k = Zd;
							break;
						case "copy":
						case "cut":
						case "paste":
							k = Jd;
							break;
						case "gotpointercapture":
						case "lostpointercapture":
						case "pointercancel":
						case "pointerdown":
						case "pointermove":
						case "pointerout":
						case "pointerover":
						case "pointerup": k = Td;
					}
					var t = 0 !== (b & 4), J = !t && "scroll" === a, x = t ? null !== h ? h + "Capture" : null : h;
					t = [];
					for (var w = d, u; null !== w;) {
						u = w;
						var F = u.stateNode;
						5 === u.tag && null !== F && (u = F, null !== x && (F = Kb(w, x), null != F && t.push(tf(w, F, u))));
						if (J) break;
						w = w.return;
					}
					0 < t.length && (h = new k(h, n, null, c, e), g.push({
						event: h,
						listeners: t
					}));
				}
			}
			if (0 === (b & 7)) {
				a: {
					h = "mouseover" === a || "pointerover" === a;
					k = "mouseout" === a || "pointerout" === a;
					if (h && c !== wb && (n = c.relatedTarget || c.fromElement) && (Wc(n) || n[uf])) break a;
					if (k || h) {
						h = e.window === e ? e : (h = e.ownerDocument) ? h.defaultView || h.parentWindow : window;
						if (k) {
							if (n = c.relatedTarget || c.toElement, k = d, n = n ? Wc(n) : null, null !== n && (J = Vb(n), n !== J || 5 !== n.tag && 6 !== n.tag)) n = null;
						} else k = null, n = d;
						if (k !== n) {
							t = Bd;
							F = "onMouseLeave";
							x = "onMouseEnter";
							w = "mouse";
							if ("pointerout" === a || "pointerover" === a) t = Td, F = "onPointerLeave", x = "onPointerEnter", w = "pointer";
							J = null == k ? h : ue(k);
							u = null == n ? h : ue(n);
							h = new t(F, w + "leave", k, c, e);
							h.target = J;
							h.relatedTarget = u;
							F = null;
							Wc(e) === d && (t = new t(x, w + "enter", n, c, e), t.target = u, t.relatedTarget = J, F = t);
							J = F;
							if (k && n) b: {
								t = k;
								x = n;
								w = 0;
								for (u = t; u; u = vf(u)) w++;
								u = 0;
								for (F = x; F; F = vf(F)) u++;
								for (; 0 < w - u;) t = vf(t), w--;
								for (; 0 < u - w;) x = vf(x), u--;
								for (; w--;) {
									if (t === x || null !== x && t === x.alternate) break b;
									t = vf(t);
									x = vf(x);
								}
								t = null;
							}
							else t = null;
							null !== k && wf(g, h, k, t, !1);
							null !== n && null !== J && wf(g, J, n, t, !0);
						}
					}
				}
				a: {
					h = d ? ue(d) : window;
					k = h.nodeName && h.nodeName.toLowerCase();
					if ("select" === k || "input" === k && "file" === h.type) var na = ve;
					else if (me(h)) if (we) na = Fe;
					else {
						na = De;
						var xa = Ce;
					}
					else (k = h.nodeName) && "input" === k.toLowerCase() && ("checkbox" === h.type || "radio" === h.type) && (na = Ee);
					if (na && (na = na(a, d))) {
						ne(g, na, c, e);
						break a;
					}
					xa && xa(a, h, d);
					"focusout" === a && (xa = h._wrapperState) && xa.controlled && "number" === h.type && cb(h, "number", h.value);
				}
				xa = d ? ue(d) : window;
				switch (a) {
					case "focusin":
						if (me(xa) || "true" === xa.contentEditable) Qe = xa, Re = d, Se = null;
						break;
					case "focusout":
						Se = Re = Qe = null;
						break;
					case "mousedown":
						Te = !0;
						break;
					case "contextmenu":
					case "mouseup":
					case "dragend":
						Te = !1;
						Ue(g, c, e);
						break;
					case "selectionchange": if (Pe) break;
					case "keydown":
					case "keyup": Ue(g, c, e);
				}
				var $a;
				if (ae) b: {
					switch (a) {
						case "compositionstart":
							var ba = "onCompositionStart";
							break b;
						case "compositionend":
							ba = "onCompositionEnd";
							break b;
						case "compositionupdate":
							ba = "onCompositionUpdate";
							break b;
					}
					ba = void 0;
				}
				else ie ? ge(a, c) && (ba = "onCompositionEnd") : "keydown" === a && 229 === c.keyCode && (ba = "onCompositionStart");
				ba && (de && "ko" !== c.locale && (ie || "onCompositionStart" !== ba ? "onCompositionEnd" === ba && ie && ($a = nd()) : (kd = e, ld = "value" in kd ? kd.value : kd.textContent, ie = !0)), xa = oe(d, ba), 0 < xa.length && (ba = new Ld(ba, a, null, c, e), g.push({
					event: ba,
					listeners: xa
				}), $a ? ba.data = $a : ($a = he(c), null !== $a && (ba.data = $a))));
				if ($a = ce ? je(a, c) : ke(a, c)) d = oe(d, "onBeforeInput"), 0 < d.length && (e = new Ld("onBeforeInput", "beforeinput", null, c, e), g.push({
					event: e,
					listeners: d
				}), e.data = $a);
			}
			se(g, b);
		});
	}
	function tf(a, b, c) {
		return {
			instance: a,
			listener: b,
			currentTarget: c
		};
	}
	function oe(a, b) {
		for (var c = b + "Capture", d = []; null !== a;) {
			var e = a, f = e.stateNode;
			5 === e.tag && null !== f && (e = f, f = Kb(a, c), null != f && d.unshift(tf(a, f, e)), f = Kb(a, b), null != f && d.push(tf(a, f, e)));
			a = a.return;
		}
		return d;
	}
	function vf(a) {
		if (null === a) return null;
		do
			a = a.return;
		while (a && 5 !== a.tag);
		return a ? a : null;
	}
	function wf(a, b, c, d, e) {
		for (var f = b._reactName, g = []; null !== c && c !== d;) {
			var h = c, k = h.alternate, l = h.stateNode;
			if (null !== k && k === d) break;
			5 === h.tag && null !== l && (h = l, e ? (k = Kb(c, f), null != k && g.unshift(tf(c, k, h))) : e || (k = Kb(c, f), null != k && g.push(tf(c, k, h))));
			c = c.return;
		}
		0 !== g.length && a.push({
			event: b,
			listeners: g
		});
	}
	var xf = /\r\n?/g, yf = /\u0000|\uFFFD/g;
	function zf(a) {
		return ("string" === typeof a ? a : "" + a).replace(xf, "\n").replace(yf, "");
	}
	function Af(a, b, c) {
		b = zf(b);
		if (zf(a) !== b && c) throw Error(p(425));
	}
	function Bf() {}
	var Cf = null, Df = null;
	function Ef(a, b) {
		return "textarea" === a || "noscript" === a || "string" === typeof b.children || "number" === typeof b.children || "object" === typeof b.dangerouslySetInnerHTML && null !== b.dangerouslySetInnerHTML && null != b.dangerouslySetInnerHTML.__html;
	}
	var Ff = "function" === typeof setTimeout ? setTimeout : void 0, Gf = "function" === typeof clearTimeout ? clearTimeout : void 0, Hf = "function" === typeof Promise ? Promise : void 0, Jf = "function" === typeof queueMicrotask ? queueMicrotask : "undefined" !== typeof Hf ? function(a) {
		return Hf.resolve(null).then(a).catch(If);
	} : Ff;
	function If(a) {
		setTimeout(function() {
			throw a;
		});
	}
	function Kf(a, b) {
		var c = b, d = 0;
		do {
			var e = c.nextSibling;
			a.removeChild(c);
			if (e && 8 === e.nodeType) if (c = e.data, "/$" === c) {
				if (0 === d) {
					a.removeChild(e);
					bd(b);
					return;
				}
				d--;
			} else "$" !== c && "$?" !== c && "$!" !== c || d++;
			c = e;
		} while (c);
		bd(b);
	}
	function Lf(a) {
		for (; null != a; a = a.nextSibling) {
			var b = a.nodeType;
			if (1 === b || 3 === b) break;
			if (8 === b) {
				b = a.data;
				if ("$" === b || "$!" === b || "$?" === b) break;
				if ("/$" === b) return null;
			}
		}
		return a;
	}
	function Mf(a) {
		a = a.previousSibling;
		for (var b = 0; a;) {
			if (8 === a.nodeType) {
				var c = a.data;
				if ("$" === c || "$!" === c || "$?" === c) {
					if (0 === b) return a;
					b--;
				} else "/$" === c && b++;
			}
			a = a.previousSibling;
		}
		return null;
	}
	var Nf = Math.random().toString(36).slice(2), Of = "__reactFiber$" + Nf, Pf = "__reactProps$" + Nf, uf = "__reactContainer$" + Nf, of = "__reactEvents$" + Nf, Qf = "__reactListeners$" + Nf, Rf = "__reactHandles$" + Nf;
	function Wc(a) {
		var b = a[Of];
		if (b) return b;
		for (var c = a.parentNode; c;) {
			if (b = c[uf] || c[Of]) {
				c = b.alternate;
				if (null !== b.child || null !== c && null !== c.child) for (a = Mf(a); null !== a;) {
					if (c = a[Of]) return c;
					a = Mf(a);
				}
				return b;
			}
			a = c;
			c = a.parentNode;
		}
		return null;
	}
	function Cb(a) {
		a = a[Of] || a[uf];
		return !a || 5 !== a.tag && 6 !== a.tag && 13 !== a.tag && 3 !== a.tag ? null : a;
	}
	function ue(a) {
		if (5 === a.tag || 6 === a.tag) return a.stateNode;
		throw Error(p(33));
	}
	function Db(a) {
		return a[Pf] || null;
	}
	var Sf = [], Tf = -1;
	function Uf(a) {
		return { current: a };
	}
	function E(a) {
		0 > Tf || (a.current = Sf[Tf], Sf[Tf] = null, Tf--);
	}
	function G(a, b) {
		Tf++;
		Sf[Tf] = a.current;
		a.current = b;
	}
	var Vf = {}, H = Uf(Vf), Wf = Uf(!1), Xf = Vf;
	function Yf(a, b) {
		var c = a.type.contextTypes;
		if (!c) return Vf;
		var d = a.stateNode;
		if (d && d.__reactInternalMemoizedUnmaskedChildContext === b) return d.__reactInternalMemoizedMaskedChildContext;
		var e = {}, f;
		for (f in c) e[f] = b[f];
		d && (a = a.stateNode, a.__reactInternalMemoizedUnmaskedChildContext = b, a.__reactInternalMemoizedMaskedChildContext = e);
		return e;
	}
	function Zf(a) {
		a = a.childContextTypes;
		return null !== a && void 0 !== a;
	}
	function $f() {
		E(Wf);
		E(H);
	}
	function ag(a, b, c) {
		if (H.current !== Vf) throw Error(p(168));
		G(H, b);
		G(Wf, c);
	}
	function bg(a, b, c) {
		var d = a.stateNode;
		b = b.childContextTypes;
		if ("function" !== typeof d.getChildContext) return c;
		d = d.getChildContext();
		for (var e in d) if (!(e in b)) throw Error(p(108, Ra(a) || "Unknown", e));
		return A({}, c, d);
	}
	function cg(a) {
		a = (a = a.stateNode) && a.__reactInternalMemoizedMergedChildContext || Vf;
		Xf = H.current;
		G(H, a);
		G(Wf, Wf.current);
		return !0;
	}
	function dg(a, b, c) {
		var d = a.stateNode;
		if (!d) throw Error(p(169));
		c ? (a = bg(a, b, Xf), d.__reactInternalMemoizedMergedChildContext = a, E(Wf), E(H), G(H, a)) : E(Wf);
		G(Wf, c);
	}
	var eg = null, fg = !1, gg = !1;
	function hg(a) {
		null === eg ? eg = [a] : eg.push(a);
	}
	function ig(a) {
		fg = !0;
		hg(a);
	}
	function jg() {
		if (!gg && null !== eg) {
			gg = !0;
			var a = 0, b = C;
			try {
				var c = eg;
				for (C = 1; a < c.length; a++) {
					var d = c[a];
					do
						d = d(!0);
					while (null !== d);
				}
				eg = null;
				fg = !1;
			} catch (e) {
				throw null !== eg && (eg = eg.slice(a + 1)), ac(fc, jg), e;
			} finally {
				C = b, gg = !1;
			}
		}
		return null;
	}
	var kg = [], lg = 0, mg = null, ng = 0, og = [], pg = 0, qg = null, rg = 1, sg = "";
	function tg(a, b) {
		kg[lg++] = ng;
		kg[lg++] = mg;
		mg = a;
		ng = b;
	}
	function ug(a, b, c) {
		og[pg++] = rg;
		og[pg++] = sg;
		og[pg++] = qg;
		qg = a;
		var d = rg;
		a = sg;
		var e = 32 - oc(d) - 1;
		d &= ~(1 << e);
		c += 1;
		var f = 32 - oc(b) + e;
		if (30 < f) {
			var g = e - e % 5;
			f = (d & (1 << g) - 1).toString(32);
			d >>= g;
			e -= g;
			rg = 1 << 32 - oc(b) + e | c << e | d;
			sg = f + a;
		} else rg = 1 << f | c << e | d, sg = a;
	}
	function vg(a) {
		null !== a.return && (tg(a, 1), ug(a, 1, 0));
	}
	function wg(a) {
		for (; a === mg;) mg = kg[--lg], kg[lg] = null, ng = kg[--lg], kg[lg] = null;
		for (; a === qg;) qg = og[--pg], og[pg] = null, sg = og[--pg], og[pg] = null, rg = og[--pg], og[pg] = null;
	}
	var xg = null, yg = null, I = !1, zg = null;
	function Ag(a, b) {
		var c = Bg(5, null, null, 0);
		c.elementType = "DELETED";
		c.stateNode = b;
		c.return = a;
		b = a.deletions;
		null === b ? (a.deletions = [c], a.flags |= 16) : b.push(c);
	}
	function Cg(a, b) {
		switch (a.tag) {
			case 5:
				var c = a.type;
				b = 1 !== b.nodeType || c.toLowerCase() !== b.nodeName.toLowerCase() ? null : b;
				return null !== b ? (a.stateNode = b, xg = a, yg = Lf(b.firstChild), !0) : !1;
			case 6: return b = "" === a.pendingProps || 3 !== b.nodeType ? null : b, null !== b ? (a.stateNode = b, xg = a, yg = null, !0) : !1;
			case 13: return b = 8 !== b.nodeType ? null : b, null !== b ? (c = null !== qg ? {
				id: rg,
				overflow: sg
			} : null, a.memoizedState = {
				dehydrated: b,
				treeContext: c,
				retryLane: 1073741824
			}, c = Bg(18, null, null, 0), c.stateNode = b, c.return = a, a.child = c, xg = a, yg = null, !0) : !1;
			default: return !1;
		}
	}
	function Dg(a) {
		return 0 !== (a.mode & 1) && 0 === (a.flags & 128);
	}
	function Eg(a) {
		if (I) {
			var b = yg;
			if (b) {
				var c = b;
				if (!Cg(a, b)) {
					if (Dg(a)) throw Error(p(418));
					b = Lf(c.nextSibling);
					var d = xg;
					b && Cg(a, b) ? Ag(d, c) : (a.flags = a.flags & -4097 | 2, I = !1, xg = a);
				}
			} else {
				if (Dg(a)) throw Error(p(418));
				a.flags = a.flags & -4097 | 2;
				I = !1;
				xg = a;
			}
		}
	}
	function Fg(a) {
		for (a = a.return; null !== a && 5 !== a.tag && 3 !== a.tag && 13 !== a.tag;) a = a.return;
		xg = a;
	}
	function Gg(a) {
		if (a !== xg) return !1;
		if (!I) return Fg(a), I = !0, !1;
		var b;
		(b = 3 !== a.tag) && !(b = 5 !== a.tag) && (b = a.type, b = "head" !== b && "body" !== b && !Ef(a.type, a.memoizedProps));
		if (b && (b = yg)) {
			if (Dg(a)) throw Hg(), Error(p(418));
			for (; b;) Ag(a, b), b = Lf(b.nextSibling);
		}
		Fg(a);
		if (13 === a.tag) {
			a = a.memoizedState;
			a = null !== a ? a.dehydrated : null;
			if (!a) throw Error(p(317));
			a: {
				a = a.nextSibling;
				for (b = 0; a;) {
					if (8 === a.nodeType) {
						var c = a.data;
						if ("/$" === c) {
							if (0 === b) {
								yg = Lf(a.nextSibling);
								break a;
							}
							b--;
						} else "$" !== c && "$!" !== c && "$?" !== c || b++;
					}
					a = a.nextSibling;
				}
				yg = null;
			}
		} else yg = xg ? Lf(a.stateNode.nextSibling) : null;
		return !0;
	}
	function Hg() {
		for (var a = yg; a;) a = Lf(a.nextSibling);
	}
	function Ig() {
		yg = xg = null;
		I = !1;
	}
	function Jg(a) {
		null === zg ? zg = [a] : zg.push(a);
	}
	var Kg = ua.ReactCurrentBatchConfig;
	function Lg(a, b, c) {
		a = c.ref;
		if (null !== a && "function" !== typeof a && "object" !== typeof a) {
			if (c._owner) {
				c = c._owner;
				if (c) {
					if (1 !== c.tag) throw Error(p(309));
					var d = c.stateNode;
				}
				if (!d) throw Error(p(147, a));
				var e = d, f = "" + a;
				if (null !== b && null !== b.ref && "function" === typeof b.ref && b.ref._stringRef === f) return b.ref;
				b = function(a) {
					var b = e.refs;
					null === a ? delete b[f] : b[f] = a;
				};
				b._stringRef = f;
				return b;
			}
			if ("string" !== typeof a) throw Error(p(284));
			if (!c._owner) throw Error(p(290, a));
		}
		return a;
	}
	function Mg(a, b) {
		a = Object.prototype.toString.call(b);
		throw Error(p(31, "[object Object]" === a ? "object with keys {" + Object.keys(b).join(", ") + "}" : a));
	}
	function Ng(a) {
		var b = a._init;
		return b(a._payload);
	}
	function Og(a) {
		function b(b, c) {
			if (a) {
				var d = b.deletions;
				null === d ? (b.deletions = [c], b.flags |= 16) : d.push(c);
			}
		}
		function c(c, d) {
			if (!a) return null;
			for (; null !== d;) b(c, d), d = d.sibling;
			return null;
		}
		function d(a, b) {
			for (a = /* @__PURE__ */ new Map(); null !== b;) null !== b.key ? a.set(b.key, b) : a.set(b.index, b), b = b.sibling;
			return a;
		}
		function e(a, b) {
			a = Pg(a, b);
			a.index = 0;
			a.sibling = null;
			return a;
		}
		function f(b, c, d) {
			b.index = d;
			if (!a) return b.flags |= 1048576, c;
			d = b.alternate;
			if (null !== d) return d = d.index, d < c ? (b.flags |= 2, c) : d;
			b.flags |= 2;
			return c;
		}
		function g(b) {
			a && null === b.alternate && (b.flags |= 2);
			return b;
		}
		function h(a, b, c, d) {
			if (null === b || 6 !== b.tag) return b = Qg(c, a.mode, d), b.return = a, b;
			b = e(b, c);
			b.return = a;
			return b;
		}
		function k(a, b, c, d) {
			var f = c.type;
			if (f === ya) return m(a, b, c.props.children, d, c.key);
			if (null !== b && (b.elementType === f || "object" === typeof f && null !== f && f.$$typeof === Ha && Ng(f) === b.type)) return d = e(b, c.props), d.ref = Lg(a, b, c), d.return = a, d;
			d = Rg(c.type, c.key, c.props, null, a.mode, d);
			d.ref = Lg(a, b, c);
			d.return = a;
			return d;
		}
		function l(a, b, c, d) {
			if (null === b || 4 !== b.tag || b.stateNode.containerInfo !== c.containerInfo || b.stateNode.implementation !== c.implementation) return b = Sg(c, a.mode, d), b.return = a, b;
			b = e(b, c.children || []);
			b.return = a;
			return b;
		}
		function m(a, b, c, d, f) {
			if (null === b || 7 !== b.tag) return b = Tg(c, a.mode, d, f), b.return = a, b;
			b = e(b, c);
			b.return = a;
			return b;
		}
		function q(a, b, c) {
			if ("string" === typeof b && "" !== b || "number" === typeof b) return b = Qg("" + b, a.mode, c), b.return = a, b;
			if ("object" === typeof b && null !== b) {
				switch (b.$$typeof) {
					case va: return c = Rg(b.type, b.key, b.props, null, a.mode, c), c.ref = Lg(a, null, b), c.return = a, c;
					case wa: return b = Sg(b, a.mode, c), b.return = a, b;
					case Ha:
						var d = b._init;
						return q(a, d(b._payload), c);
				}
				if (eb(b) || Ka(b)) return b = Tg(b, a.mode, c, null), b.return = a, b;
				Mg(a, b);
			}
			return null;
		}
		function r(a, b, c, d) {
			var e = null !== b ? b.key : null;
			if ("string" === typeof c && "" !== c || "number" === typeof c) return null !== e ? null : h(a, b, "" + c, d);
			if ("object" === typeof c && null !== c) {
				switch (c.$$typeof) {
					case va: return c.key === e ? k(a, b, c, d) : null;
					case wa: return c.key === e ? l(a, b, c, d) : null;
					case Ha: return e = c._init, r(a, b, e(c._payload), d);
				}
				if (eb(c) || Ka(c)) return null !== e ? null : m(a, b, c, d, null);
				Mg(a, c);
			}
			return null;
		}
		function y(a, b, c, d, e) {
			if ("string" === typeof d && "" !== d || "number" === typeof d) return a = a.get(c) || null, h(b, a, "" + d, e);
			if ("object" === typeof d && null !== d) {
				switch (d.$$typeof) {
					case va: return a = a.get(null === d.key ? c : d.key) || null, k(b, a, d, e);
					case wa: return a = a.get(null === d.key ? c : d.key) || null, l(b, a, d, e);
					case Ha:
						var f = d._init;
						return y(a, b, c, f(d._payload), e);
				}
				if (eb(d) || Ka(d)) return a = a.get(c) || null, m(b, a, d, e, null);
				Mg(b, d);
			}
			return null;
		}
		function n(e, g, h, k) {
			for (var l = null, m = null, u = g, w = g = 0, x = null; null !== u && w < h.length; w++) {
				u.index > w ? (x = u, u = null) : x = u.sibling;
				var n = r(e, u, h[w], k);
				if (null === n) {
					null === u && (u = x);
					break;
				}
				a && u && null === n.alternate && b(e, u);
				g = f(n, g, w);
				null === m ? l = n : m.sibling = n;
				m = n;
				u = x;
			}
			if (w === h.length) return c(e, u), I && tg(e, w), l;
			if (null === u) {
				for (; w < h.length; w++) u = q(e, h[w], k), null !== u && (g = f(u, g, w), null === m ? l = u : m.sibling = u, m = u);
				I && tg(e, w);
				return l;
			}
			for (u = d(e, u); w < h.length; w++) x = y(u, e, w, h[w], k), null !== x && (a && null !== x.alternate && u.delete(null === x.key ? w : x.key), g = f(x, g, w), null === m ? l = x : m.sibling = x, m = x);
			a && u.forEach(function(a) {
				return b(e, a);
			});
			I && tg(e, w);
			return l;
		}
		function t(e, g, h, k) {
			var l = Ka(h);
			if ("function" !== typeof l) throw Error(p(150));
			h = l.call(h);
			if (null == h) throw Error(p(151));
			for (var u = l = null, m = g, w = g = 0, x = null, n = h.next(); null !== m && !n.done; w++, n = h.next()) {
				m.index > w ? (x = m, m = null) : x = m.sibling;
				var t = r(e, m, n.value, k);
				if (null === t) {
					null === m && (m = x);
					break;
				}
				a && m && null === t.alternate && b(e, m);
				g = f(t, g, w);
				null === u ? l = t : u.sibling = t;
				u = t;
				m = x;
			}
			if (n.done) return c(e, m), I && tg(e, w), l;
			if (null === m) {
				for (; !n.done; w++, n = h.next()) n = q(e, n.value, k), null !== n && (g = f(n, g, w), null === u ? l = n : u.sibling = n, u = n);
				I && tg(e, w);
				return l;
			}
			for (m = d(e, m); !n.done; w++, n = h.next()) n = y(m, e, w, n.value, k), null !== n && (a && null !== n.alternate && m.delete(null === n.key ? w : n.key), g = f(n, g, w), null === u ? l = n : u.sibling = n, u = n);
			a && m.forEach(function(a) {
				return b(e, a);
			});
			I && tg(e, w);
			return l;
		}
		function J(a, d, f, h) {
			"object" === typeof f && null !== f && f.type === ya && null === f.key && (f = f.props.children);
			if ("object" === typeof f && null !== f) {
				switch (f.$$typeof) {
					case va:
						a: {
							for (var k = f.key, l = d; null !== l;) {
								if (l.key === k) {
									k = f.type;
									if (k === ya) {
										if (7 === l.tag) {
											c(a, l.sibling);
											d = e(l, f.props.children);
											d.return = a;
											a = d;
											break a;
										}
									} else if (l.elementType === k || "object" === typeof k && null !== k && k.$$typeof === Ha && Ng(k) === l.type) {
										c(a, l.sibling);
										d = e(l, f.props);
										d.ref = Lg(a, l, f);
										d.return = a;
										a = d;
										break a;
									}
									c(a, l);
									break;
								} else b(a, l);
								l = l.sibling;
							}
							f.type === ya ? (d = Tg(f.props.children, a.mode, h, f.key), d.return = a, a = d) : (h = Rg(f.type, f.key, f.props, null, a.mode, h), h.ref = Lg(a, d, f), h.return = a, a = h);
						}
						return g(a);
					case wa:
						a: {
							for (l = f.key; null !== d;) {
								if (d.key === l) if (4 === d.tag && d.stateNode.containerInfo === f.containerInfo && d.stateNode.implementation === f.implementation) {
									c(a, d.sibling);
									d = e(d, f.children || []);
									d.return = a;
									a = d;
									break a;
								} else {
									c(a, d);
									break;
								}
								else b(a, d);
								d = d.sibling;
							}
							d = Sg(f, a.mode, h);
							d.return = a;
							a = d;
						}
						return g(a);
					case Ha: return l = f._init, J(a, d, l(f._payload), h);
				}
				if (eb(f)) return n(a, d, f, h);
				if (Ka(f)) return t(a, d, f, h);
				Mg(a, f);
			}
			return "string" === typeof f && "" !== f || "number" === typeof f ? (f = "" + f, null !== d && 6 === d.tag ? (c(a, d.sibling), d = e(d, f), d.return = a, a = d) : (c(a, d), d = Qg(f, a.mode, h), d.return = a, a = d), g(a)) : c(a, d);
		}
		return J;
	}
	var Ug = Og(!0), Vg = Og(!1), Wg = Uf(null), Xg = null, Yg = null, Zg = null;
	function $g() {
		Zg = Yg = Xg = null;
	}
	function ah(a) {
		var b = Wg.current;
		E(Wg);
		a._currentValue = b;
	}
	function bh(a, b, c) {
		for (; null !== a;) {
			var d = a.alternate;
			(a.childLanes & b) !== b ? (a.childLanes |= b, null !== d && (d.childLanes |= b)) : null !== d && (d.childLanes & b) !== b && (d.childLanes |= b);
			if (a === c) break;
			a = a.return;
		}
	}
	function ch(a, b) {
		Xg = a;
		Zg = Yg = null;
		a = a.dependencies;
		null !== a && null !== a.firstContext && (0 !== (a.lanes & b) && (dh = !0), a.firstContext = null);
	}
	function eh(a) {
		var b = a._currentValue;
		if (Zg !== a) if (a = {
			context: a,
			memoizedValue: b,
			next: null
		}, null === Yg) {
			if (null === Xg) throw Error(p(308));
			Yg = a;
			Xg.dependencies = {
				lanes: 0,
				firstContext: a
			};
		} else Yg = Yg.next = a;
		return b;
	}
	var fh = null;
	function gh(a) {
		null === fh ? fh = [a] : fh.push(a);
	}
	function hh(a, b, c, d) {
		var e = b.interleaved;
		null === e ? (c.next = c, gh(b)) : (c.next = e.next, e.next = c);
		b.interleaved = c;
		return ih(a, d);
	}
	function ih(a, b) {
		a.lanes |= b;
		var c = a.alternate;
		null !== c && (c.lanes |= b);
		c = a;
		for (a = a.return; null !== a;) a.childLanes |= b, c = a.alternate, null !== c && (c.childLanes |= b), c = a, a = a.return;
		return 3 === c.tag ? c.stateNode : null;
	}
	var jh = !1;
	function kh(a) {
		a.updateQueue = {
			baseState: a.memoizedState,
			firstBaseUpdate: null,
			lastBaseUpdate: null,
			shared: {
				pending: null,
				interleaved: null,
				lanes: 0
			},
			effects: null
		};
	}
	function lh(a, b) {
		a = a.updateQueue;
		b.updateQueue === a && (b.updateQueue = {
			baseState: a.baseState,
			firstBaseUpdate: a.firstBaseUpdate,
			lastBaseUpdate: a.lastBaseUpdate,
			shared: a.shared,
			effects: a.effects
		});
	}
	function mh(a, b) {
		return {
			eventTime: a,
			lane: b,
			tag: 0,
			payload: null,
			callback: null,
			next: null
		};
	}
	function nh(a, b, c) {
		var d = a.updateQueue;
		if (null === d) return null;
		d = d.shared;
		if (0 !== (K & 2)) {
			var e = d.pending;
			null === e ? b.next = b : (b.next = e.next, e.next = b);
			d.pending = b;
			return ih(a, c);
		}
		e = d.interleaved;
		null === e ? (b.next = b, gh(d)) : (b.next = e.next, e.next = b);
		d.interleaved = b;
		return ih(a, c);
	}
	function oh(a, b, c) {
		b = b.updateQueue;
		if (null !== b && (b = b.shared, 0 !== (c & 4194240))) {
			var d = b.lanes;
			d &= a.pendingLanes;
			c |= d;
			b.lanes = c;
			Cc(a, c);
		}
	}
	function ph(a, b) {
		var c = a.updateQueue, d = a.alternate;
		if (null !== d && (d = d.updateQueue, c === d)) {
			var e = null, f = null;
			c = c.firstBaseUpdate;
			if (null !== c) {
				do {
					var g = {
						eventTime: c.eventTime,
						lane: c.lane,
						tag: c.tag,
						payload: c.payload,
						callback: c.callback,
						next: null
					};
					null === f ? e = f = g : f = f.next = g;
					c = c.next;
				} while (null !== c);
				null === f ? e = f = b : f = f.next = b;
			} else e = f = b;
			c = {
				baseState: d.baseState,
				firstBaseUpdate: e,
				lastBaseUpdate: f,
				shared: d.shared,
				effects: d.effects
			};
			a.updateQueue = c;
			return;
		}
		a = c.lastBaseUpdate;
		null === a ? c.firstBaseUpdate = b : a.next = b;
		c.lastBaseUpdate = b;
	}
	function qh(a, b, c, d) {
		var e = a.updateQueue;
		jh = !1;
		var f = e.firstBaseUpdate, g = e.lastBaseUpdate, h = e.shared.pending;
		if (null !== h) {
			e.shared.pending = null;
			var k = h, l = k.next;
			k.next = null;
			null === g ? f = l : g.next = l;
			g = k;
			var m = a.alternate;
			null !== m && (m = m.updateQueue, h = m.lastBaseUpdate, h !== g && (null === h ? m.firstBaseUpdate = l : h.next = l, m.lastBaseUpdate = k));
		}
		if (null !== f) {
			var q = e.baseState;
			g = 0;
			m = l = k = null;
			h = f;
			do {
				var r = h.lane, y = h.eventTime;
				if ((d & r) === r) {
					null !== m && (m = m.next = {
						eventTime: y,
						lane: 0,
						tag: h.tag,
						payload: h.payload,
						callback: h.callback,
						next: null
					});
					a: {
						var n = a, t = h;
						r = b;
						y = c;
						switch (t.tag) {
							case 1:
								n = t.payload;
								if ("function" === typeof n) {
									q = n.call(y, q, r);
									break a;
								}
								q = n;
								break a;
							case 3: n.flags = n.flags & -65537 | 128;
							case 0:
								n = t.payload;
								r = "function" === typeof n ? n.call(y, q, r) : n;
								if (null === r || void 0 === r) break a;
								q = A({}, q, r);
								break a;
							case 2: jh = !0;
						}
					}
					null !== h.callback && 0 !== h.lane && (a.flags |= 64, r = e.effects, null === r ? e.effects = [h] : r.push(h));
				} else y = {
					eventTime: y,
					lane: r,
					tag: h.tag,
					payload: h.payload,
					callback: h.callback,
					next: null
				}, null === m ? (l = m = y, k = q) : m = m.next = y, g |= r;
				h = h.next;
				if (null === h) if (h = e.shared.pending, null === h) break;
				else r = h, h = r.next, r.next = null, e.lastBaseUpdate = r, e.shared.pending = null;
			} while (1);
			null === m && (k = q);
			e.baseState = k;
			e.firstBaseUpdate = l;
			e.lastBaseUpdate = m;
			b = e.shared.interleaved;
			if (null !== b) {
				e = b;
				do
					g |= e.lane, e = e.next;
				while (e !== b);
			} else null === f && (e.shared.lanes = 0);
			rh |= g;
			a.lanes = g;
			a.memoizedState = q;
		}
	}
	function sh(a, b, c) {
		a = b.effects;
		b.effects = null;
		if (null !== a) for (b = 0; b < a.length; b++) {
			var d = a[b], e = d.callback;
			if (null !== e) {
				d.callback = null;
				d = c;
				if ("function" !== typeof e) throw Error(p(191, e));
				e.call(d);
			}
		}
	}
	var th = {}, uh = Uf(th), vh = Uf(th), wh = Uf(th);
	function xh(a) {
		if (a === th) throw Error(p(174));
		return a;
	}
	function yh(a, b) {
		G(wh, b);
		G(vh, a);
		G(uh, th);
		a = b.nodeType;
		switch (a) {
			case 9:
			case 11:
				b = (b = b.documentElement) ? b.namespaceURI : lb(null, "");
				break;
			default: a = 8 === a ? b.parentNode : b, b = a.namespaceURI || null, a = a.tagName, b = lb(b, a);
		}
		E(uh);
		G(uh, b);
	}
	function zh() {
		E(uh);
		E(vh);
		E(wh);
	}
	function Ah(a) {
		xh(wh.current);
		var b = xh(uh.current);
		var c = lb(b, a.type);
		b !== c && (G(vh, a), G(uh, c));
	}
	function Bh(a) {
		vh.current === a && (E(uh), E(vh));
	}
	var L = Uf(0);
	function Ch(a) {
		for (var b = a; null !== b;) {
			if (13 === b.tag) {
				var c = b.memoizedState;
				if (null !== c && (c = c.dehydrated, null === c || "$?" === c.data || "$!" === c.data)) return b;
			} else if (19 === b.tag && void 0 !== b.memoizedProps.revealOrder) {
				if (0 !== (b.flags & 128)) return b;
			} else if (null !== b.child) {
				b.child.return = b;
				b = b.child;
				continue;
			}
			if (b === a) break;
			for (; null === b.sibling;) {
				if (null === b.return || b.return === a) return null;
				b = b.return;
			}
			b.sibling.return = b.return;
			b = b.sibling;
		}
		return null;
	}
	var Dh = [];
	function Eh() {
		for (var a = 0; a < Dh.length; a++) Dh[a]._workInProgressVersionPrimary = null;
		Dh.length = 0;
	}
	var Fh = ua.ReactCurrentDispatcher, Gh = ua.ReactCurrentBatchConfig, Hh = 0, M = null, N = null, O = null, Ih = !1, Jh = !1, Kh = 0, Lh = 0;
	function P() {
		throw Error(p(321));
	}
	function Mh(a, b) {
		if (null === b) return !1;
		for (var c = 0; c < b.length && c < a.length; c++) if (!He(a[c], b[c])) return !1;
		return !0;
	}
	function Nh(a, b, c, d, e, f) {
		Hh = f;
		M = b;
		b.memoizedState = null;
		b.updateQueue = null;
		b.lanes = 0;
		Fh.current = null === a || null === a.memoizedState ? Oh : Ph;
		a = c(d, e);
		if (Jh) {
			f = 0;
			do {
				Jh = !1;
				Kh = 0;
				if (25 <= f) throw Error(p(301));
				f += 1;
				O = N = null;
				b.updateQueue = null;
				Fh.current = Qh;
				a = c(d, e);
			} while (Jh);
		}
		Fh.current = Rh;
		b = null !== N && null !== N.next;
		Hh = 0;
		O = N = M = null;
		Ih = !1;
		if (b) throw Error(p(300));
		return a;
	}
	function Sh() {
		var a = 0 !== Kh;
		Kh = 0;
		return a;
	}
	function Th() {
		var a = {
			memoizedState: null,
			baseState: null,
			baseQueue: null,
			queue: null,
			next: null
		};
		null === O ? M.memoizedState = O = a : O = O.next = a;
		return O;
	}
	function Uh() {
		if (null === N) {
			var a = M.alternate;
			a = null !== a ? a.memoizedState : null;
		} else a = N.next;
		var b = null === O ? M.memoizedState : O.next;
		if (null !== b) O = b, N = a;
		else {
			if (null === a) throw Error(p(310));
			N = a;
			a = {
				memoizedState: N.memoizedState,
				baseState: N.baseState,
				baseQueue: N.baseQueue,
				queue: N.queue,
				next: null
			};
			null === O ? M.memoizedState = O = a : O = O.next = a;
		}
		return O;
	}
	function Vh(a, b) {
		return "function" === typeof b ? b(a) : b;
	}
	function Wh(a) {
		var b = Uh(), c = b.queue;
		if (null === c) throw Error(p(311));
		c.lastRenderedReducer = a;
		var d = N, e = d.baseQueue, f = c.pending;
		if (null !== f) {
			if (null !== e) {
				var g = e.next;
				e.next = f.next;
				f.next = g;
			}
			d.baseQueue = e = f;
			c.pending = null;
		}
		if (null !== e) {
			f = e.next;
			d = d.baseState;
			var h = g = null, k = null, l = f;
			do {
				var m = l.lane;
				if ((Hh & m) === m) null !== k && (k = k.next = {
					lane: 0,
					action: l.action,
					hasEagerState: l.hasEagerState,
					eagerState: l.eagerState,
					next: null
				}), d = l.hasEagerState ? l.eagerState : a(d, l.action);
				else {
					var q = {
						lane: m,
						action: l.action,
						hasEagerState: l.hasEagerState,
						eagerState: l.eagerState,
						next: null
					};
					null === k ? (h = k = q, g = d) : k = k.next = q;
					M.lanes |= m;
					rh |= m;
				}
				l = l.next;
			} while (null !== l && l !== f);
			null === k ? g = d : k.next = h;
			He(d, b.memoizedState) || (dh = !0);
			b.memoizedState = d;
			b.baseState = g;
			b.baseQueue = k;
			c.lastRenderedState = d;
		}
		a = c.interleaved;
		if (null !== a) {
			e = a;
			do
				f = e.lane, M.lanes |= f, rh |= f, e = e.next;
			while (e !== a);
		} else null === e && (c.lanes = 0);
		return [b.memoizedState, c.dispatch];
	}
	function Xh(a) {
		var b = Uh(), c = b.queue;
		if (null === c) throw Error(p(311));
		c.lastRenderedReducer = a;
		var d = c.dispatch, e = c.pending, f = b.memoizedState;
		if (null !== e) {
			c.pending = null;
			var g = e = e.next;
			do
				f = a(f, g.action), g = g.next;
			while (g !== e);
			He(f, b.memoizedState) || (dh = !0);
			b.memoizedState = f;
			null === b.baseQueue && (b.baseState = f);
			c.lastRenderedState = f;
		}
		return [f, d];
	}
	function Yh() {}
	function Zh(a, b) {
		var c = M, d = Uh(), e = b(), f = !He(d.memoizedState, e);
		f && (d.memoizedState = e, dh = !0);
		d = d.queue;
		$h(ai.bind(null, c, d, a), [a]);
		if (d.getSnapshot !== b || f || null !== O && O.memoizedState.tag & 1) {
			c.flags |= 2048;
			bi(9, ci.bind(null, c, d, e, b), void 0, null);
			if (null === Q) throw Error(p(349));
			0 !== (Hh & 30) || di(c, b, e);
		}
		return e;
	}
	function di(a, b, c) {
		a.flags |= 16384;
		a = {
			getSnapshot: b,
			value: c
		};
		b = M.updateQueue;
		null === b ? (b = {
			lastEffect: null,
			stores: null
		}, M.updateQueue = b, b.stores = [a]) : (c = b.stores, null === c ? b.stores = [a] : c.push(a));
	}
	function ci(a, b, c, d) {
		b.value = c;
		b.getSnapshot = d;
		ei(b) && fi(a);
	}
	function ai(a, b, c) {
		return c(function() {
			ei(b) && fi(a);
		});
	}
	function ei(a) {
		var b = a.getSnapshot;
		a = a.value;
		try {
			var c = b();
			return !He(a, c);
		} catch (d) {
			return !0;
		}
	}
	function fi(a) {
		var b = ih(a, 1);
		null !== b && gi(b, a, 1, -1);
	}
	function hi(a) {
		var b = Th();
		"function" === typeof a && (a = a());
		b.memoizedState = b.baseState = a;
		a = {
			pending: null,
			interleaved: null,
			lanes: 0,
			dispatch: null,
			lastRenderedReducer: Vh,
			lastRenderedState: a
		};
		b.queue = a;
		a = a.dispatch = ii.bind(null, M, a);
		return [b.memoizedState, a];
	}
	function bi(a, b, c, d) {
		a = {
			tag: a,
			create: b,
			destroy: c,
			deps: d,
			next: null
		};
		b = M.updateQueue;
		null === b ? (b = {
			lastEffect: null,
			stores: null
		}, M.updateQueue = b, b.lastEffect = a.next = a) : (c = b.lastEffect, null === c ? b.lastEffect = a.next = a : (d = c.next, c.next = a, a.next = d, b.lastEffect = a));
		return a;
	}
	function ji() {
		return Uh().memoizedState;
	}
	function ki(a, b, c, d) {
		var e = Th();
		M.flags |= a;
		e.memoizedState = bi(1 | b, c, void 0, void 0 === d ? null : d);
	}
	function li(a, b, c, d) {
		var e = Uh();
		d = void 0 === d ? null : d;
		var f = void 0;
		if (null !== N) {
			var g = N.memoizedState;
			f = g.destroy;
			if (null !== d && Mh(d, g.deps)) {
				e.memoizedState = bi(b, c, f, d);
				return;
			}
		}
		M.flags |= a;
		e.memoizedState = bi(1 | b, c, f, d);
	}
	function mi(a, b) {
		return ki(8390656, 8, a, b);
	}
	function $h(a, b) {
		return li(2048, 8, a, b);
	}
	function ni(a, b) {
		return li(4, 2, a, b);
	}
	function oi(a, b) {
		return li(4, 4, a, b);
	}
	function pi(a, b) {
		if ("function" === typeof b) return a = a(), b(a), function() {
			b(null);
		};
		if (null !== b && void 0 !== b) return a = a(), b.current = a, function() {
			b.current = null;
		};
	}
	function qi(a, b, c) {
		c = null !== c && void 0 !== c ? c.concat([a]) : null;
		return li(4, 4, pi.bind(null, b, a), c);
	}
	function ri() {}
	function si(a, b) {
		var c = Uh();
		b = void 0 === b ? null : b;
		var d = c.memoizedState;
		if (null !== d && null !== b && Mh(b, d[1])) return d[0];
		c.memoizedState = [a, b];
		return a;
	}
	function ti(a, b) {
		var c = Uh();
		b = void 0 === b ? null : b;
		var d = c.memoizedState;
		if (null !== d && null !== b && Mh(b, d[1])) return d[0];
		a = a();
		c.memoizedState = [a, b];
		return a;
	}
	function ui(a, b, c) {
		if (0 === (Hh & 21)) return a.baseState && (a.baseState = !1, dh = !0), a.memoizedState = c;
		He(c, b) || (c = yc(), M.lanes |= c, rh |= c, a.baseState = !0);
		return b;
	}
	function vi(a, b) {
		var c = C;
		C = 0 !== c && 4 > c ? c : 4;
		a(!0);
		var d = Gh.transition;
		Gh.transition = {};
		try {
			a(!1), b();
		} finally {
			C = c, Gh.transition = d;
		}
	}
	function wi() {
		return Uh().memoizedState;
	}
	function xi(a, b, c) {
		var d = yi(a);
		c = {
			lane: d,
			action: c,
			hasEagerState: !1,
			eagerState: null,
			next: null
		};
		if (zi(a)) Ai(b, c);
		else if (c = hh(a, b, c, d), null !== c) {
			var e = R();
			gi(c, a, d, e);
			Bi(c, b, d);
		}
	}
	function ii(a, b, c) {
		var d = yi(a), e = {
			lane: d,
			action: c,
			hasEagerState: !1,
			eagerState: null,
			next: null
		};
		if (zi(a)) Ai(b, e);
		else {
			var f = a.alternate;
			if (0 === a.lanes && (null === f || 0 === f.lanes) && (f = b.lastRenderedReducer, null !== f)) try {
				var g = b.lastRenderedState, h = f(g, c);
				e.hasEagerState = !0;
				e.eagerState = h;
				if (He(h, g)) {
					var k = b.interleaved;
					null === k ? (e.next = e, gh(b)) : (e.next = k.next, k.next = e);
					b.interleaved = e;
					return;
				}
			} catch (l) {}
			c = hh(a, b, e, d);
			null !== c && (e = R(), gi(c, a, d, e), Bi(c, b, d));
		}
	}
	function zi(a) {
		var b = a.alternate;
		return a === M || null !== b && b === M;
	}
	function Ai(a, b) {
		Jh = Ih = !0;
		var c = a.pending;
		null === c ? b.next = b : (b.next = c.next, c.next = b);
		a.pending = b;
	}
	function Bi(a, b, c) {
		if (0 !== (c & 4194240)) {
			var d = b.lanes;
			d &= a.pendingLanes;
			c |= d;
			b.lanes = c;
			Cc(a, c);
		}
	}
	var Rh = {
		readContext: eh,
		useCallback: P,
		useContext: P,
		useEffect: P,
		useImperativeHandle: P,
		useInsertionEffect: P,
		useLayoutEffect: P,
		useMemo: P,
		useReducer: P,
		useRef: P,
		useState: P,
		useDebugValue: P,
		useDeferredValue: P,
		useTransition: P,
		useMutableSource: P,
		useSyncExternalStore: P,
		useId: P,
		unstable_isNewReconciler: !1
	}, Oh = {
		readContext: eh,
		useCallback: function(a, b) {
			Th().memoizedState = [a, void 0 === b ? null : b];
			return a;
		},
		useContext: eh,
		useEffect: mi,
		useImperativeHandle: function(a, b, c) {
			c = null !== c && void 0 !== c ? c.concat([a]) : null;
			return ki(4194308, 4, pi.bind(null, b, a), c);
		},
		useLayoutEffect: function(a, b) {
			return ki(4194308, 4, a, b);
		},
		useInsertionEffect: function(a, b) {
			return ki(4, 2, a, b);
		},
		useMemo: function(a, b) {
			var c = Th();
			b = void 0 === b ? null : b;
			a = a();
			c.memoizedState = [a, b];
			return a;
		},
		useReducer: function(a, b, c) {
			var d = Th();
			b = void 0 !== c ? c(b) : b;
			d.memoizedState = d.baseState = b;
			a = {
				pending: null,
				interleaved: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: a,
				lastRenderedState: b
			};
			d.queue = a;
			a = a.dispatch = xi.bind(null, M, a);
			return [d.memoizedState, a];
		},
		useRef: function(a) {
			var b = Th();
			a = { current: a };
			return b.memoizedState = a;
		},
		useState: hi,
		useDebugValue: ri,
		useDeferredValue: function(a) {
			return Th().memoizedState = a;
		},
		useTransition: function() {
			var a = hi(!1), b = a[0];
			a = vi.bind(null, a[1]);
			Th().memoizedState = a;
			return [b, a];
		},
		useMutableSource: function() {},
		useSyncExternalStore: function(a, b, c) {
			var d = M, e = Th();
			if (I) {
				if (void 0 === c) throw Error(p(407));
				c = c();
			} else {
				c = b();
				if (null === Q) throw Error(p(349));
				0 !== (Hh & 30) || di(d, b, c);
			}
			e.memoizedState = c;
			var f = {
				value: c,
				getSnapshot: b
			};
			e.queue = f;
			mi(ai.bind(null, d, f, a), [a]);
			d.flags |= 2048;
			bi(9, ci.bind(null, d, f, c, b), void 0, null);
			return c;
		},
		useId: function() {
			var a = Th(), b = Q.identifierPrefix;
			if (I) {
				var c = sg;
				var d = rg;
				c = (d & ~(1 << 32 - oc(d) - 1)).toString(32) + c;
				b = ":" + b + "R" + c;
				c = Kh++;
				0 < c && (b += "H" + c.toString(32));
				b += ":";
			} else c = Lh++, b = ":" + b + "r" + c.toString(32) + ":";
			return a.memoizedState = b;
		},
		unstable_isNewReconciler: !1
	}, Ph = {
		readContext: eh,
		useCallback: si,
		useContext: eh,
		useEffect: $h,
		useImperativeHandle: qi,
		useInsertionEffect: ni,
		useLayoutEffect: oi,
		useMemo: ti,
		useReducer: Wh,
		useRef: ji,
		useState: function() {
			return Wh(Vh);
		},
		useDebugValue: ri,
		useDeferredValue: function(a) {
			return ui(Uh(), N.memoizedState, a);
		},
		useTransition: function() {
			return [Wh(Vh)[0], Uh().memoizedState];
		},
		useMutableSource: Yh,
		useSyncExternalStore: Zh,
		useId: wi,
		unstable_isNewReconciler: !1
	}, Qh = {
		readContext: eh,
		useCallback: si,
		useContext: eh,
		useEffect: $h,
		useImperativeHandle: qi,
		useInsertionEffect: ni,
		useLayoutEffect: oi,
		useMemo: ti,
		useReducer: Xh,
		useRef: ji,
		useState: function() {
			return Xh(Vh);
		},
		useDebugValue: ri,
		useDeferredValue: function(a) {
			var b = Uh();
			return null === N ? b.memoizedState = a : ui(b, N.memoizedState, a);
		},
		useTransition: function() {
			return [Xh(Vh)[0], Uh().memoizedState];
		},
		useMutableSource: Yh,
		useSyncExternalStore: Zh,
		useId: wi,
		unstable_isNewReconciler: !1
	};
	function Ci(a, b) {
		if (a && a.defaultProps) {
			b = A({}, b);
			a = a.defaultProps;
			for (var c in a) void 0 === b[c] && (b[c] = a[c]);
			return b;
		}
		return b;
	}
	function Di(a, b, c, d) {
		b = a.memoizedState;
		c = c(d, b);
		c = null === c || void 0 === c ? b : A({}, b, c);
		a.memoizedState = c;
		0 === a.lanes && (a.updateQueue.baseState = c);
	}
	var Ei = {
		isMounted: function(a) {
			return (a = a._reactInternals) ? Vb(a) === a : !1;
		},
		enqueueSetState: function(a, b, c) {
			a = a._reactInternals;
			var d = R(), e = yi(a), f = mh(d, e);
			f.payload = b;
			void 0 !== c && null !== c && (f.callback = c);
			b = nh(a, f, e);
			null !== b && (gi(b, a, e, d), oh(b, a, e));
		},
		enqueueReplaceState: function(a, b, c) {
			a = a._reactInternals;
			var d = R(), e = yi(a), f = mh(d, e);
			f.tag = 1;
			f.payload = b;
			void 0 !== c && null !== c && (f.callback = c);
			b = nh(a, f, e);
			null !== b && (gi(b, a, e, d), oh(b, a, e));
		},
		enqueueForceUpdate: function(a, b) {
			a = a._reactInternals;
			var c = R(), d = yi(a), e = mh(c, d);
			e.tag = 2;
			void 0 !== b && null !== b && (e.callback = b);
			b = nh(a, e, d);
			null !== b && (gi(b, a, d, c), oh(b, a, d));
		}
	};
	function Fi(a, b, c, d, e, f, g) {
		a = a.stateNode;
		return "function" === typeof a.shouldComponentUpdate ? a.shouldComponentUpdate(d, f, g) : b.prototype && b.prototype.isPureReactComponent ? !Ie(c, d) || !Ie(e, f) : !0;
	}
	function Gi(a, b, c) {
		var d = !1, e = Vf;
		var f = b.contextType;
		"object" === typeof f && null !== f ? f = eh(f) : (e = Zf(b) ? Xf : H.current, d = b.contextTypes, f = (d = null !== d && void 0 !== d) ? Yf(a, e) : Vf);
		b = new b(c, f);
		a.memoizedState = null !== b.state && void 0 !== b.state ? b.state : null;
		b.updater = Ei;
		a.stateNode = b;
		b._reactInternals = a;
		d && (a = a.stateNode, a.__reactInternalMemoizedUnmaskedChildContext = e, a.__reactInternalMemoizedMaskedChildContext = f);
		return b;
	}
	function Hi(a, b, c, d) {
		a = b.state;
		"function" === typeof b.componentWillReceiveProps && b.componentWillReceiveProps(c, d);
		"function" === typeof b.UNSAFE_componentWillReceiveProps && b.UNSAFE_componentWillReceiveProps(c, d);
		b.state !== a && Ei.enqueueReplaceState(b, b.state, null);
	}
	function Ii(a, b, c, d) {
		var e = a.stateNode;
		e.props = c;
		e.state = a.memoizedState;
		e.refs = {};
		kh(a);
		var f = b.contextType;
		"object" === typeof f && null !== f ? e.context = eh(f) : (f = Zf(b) ? Xf : H.current, e.context = Yf(a, f));
		e.state = a.memoizedState;
		f = b.getDerivedStateFromProps;
		"function" === typeof f && (Di(a, b, f, c), e.state = a.memoizedState);
		"function" === typeof b.getDerivedStateFromProps || "function" === typeof e.getSnapshotBeforeUpdate || "function" !== typeof e.UNSAFE_componentWillMount && "function" !== typeof e.componentWillMount || (b = e.state, "function" === typeof e.componentWillMount && e.componentWillMount(), "function" === typeof e.UNSAFE_componentWillMount && e.UNSAFE_componentWillMount(), b !== e.state && Ei.enqueueReplaceState(e, e.state, null), qh(a, c, e, d), e.state = a.memoizedState);
		"function" === typeof e.componentDidMount && (a.flags |= 4194308);
	}
	function Ji(a, b) {
		try {
			var c = "", d = b;
			do
				c += Pa(d), d = d.return;
			while (d);
			var e = c;
		} catch (f) {
			e = "\nError generating stack: " + f.message + "\n" + f.stack;
		}
		return {
			value: a,
			source: b,
			stack: e,
			digest: null
		};
	}
	function Ki(a, b, c) {
		return {
			value: a,
			source: null,
			stack: null != c ? c : null,
			digest: null != b ? b : null
		};
	}
	function Li(a, b) {
		try {
			console.error(b.value);
		} catch (c) {
			setTimeout(function() {
				throw c;
			});
		}
	}
	var Mi = "function" === typeof WeakMap ? WeakMap : Map;
	function Ni(a, b, c) {
		c = mh(-1, c);
		c.tag = 3;
		c.payload = { element: null };
		var d = b.value;
		c.callback = function() {
			Oi || (Oi = !0, Pi = d);
			Li(a, b);
		};
		return c;
	}
	function Qi(a, b, c) {
		c = mh(-1, c);
		c.tag = 3;
		var d = a.type.getDerivedStateFromError;
		if ("function" === typeof d) {
			var e = b.value;
			c.payload = function() {
				return d(e);
			};
			c.callback = function() {
				Li(a, b);
			};
		}
		var f = a.stateNode;
		null !== f && "function" === typeof f.componentDidCatch && (c.callback = function() {
			Li(a, b);
			"function" !== typeof d && (null === Ri ? Ri = new Set([this]) : Ri.add(this));
			var c = b.stack;
			this.componentDidCatch(b.value, { componentStack: null !== c ? c : "" });
		});
		return c;
	}
	function Si(a, b, c) {
		var d = a.pingCache;
		if (null === d) {
			d = a.pingCache = new Mi();
			var e = /* @__PURE__ */ new Set();
			d.set(b, e);
		} else e = d.get(b), void 0 === e && (e = /* @__PURE__ */ new Set(), d.set(b, e));
		e.has(c) || (e.add(c), a = Ti.bind(null, a, b, c), b.then(a, a));
	}
	function Ui(a) {
		do {
			var b;
			if (b = 13 === a.tag) b = a.memoizedState, b = null !== b ? null !== b.dehydrated ? !0 : !1 : !0;
			if (b) return a;
			a = a.return;
		} while (null !== a);
		return null;
	}
	function Vi(a, b, c, d, e) {
		if (0 === (a.mode & 1)) return a === b ? a.flags |= 65536 : (a.flags |= 128, c.flags |= 131072, c.flags &= -52805, 1 === c.tag && (null === c.alternate ? c.tag = 17 : (b = mh(-1, 1), b.tag = 2, nh(c, b, 1))), c.lanes |= 1), a;
		a.flags |= 65536;
		a.lanes = e;
		return a;
	}
	var Wi = ua.ReactCurrentOwner, dh = !1;
	function Xi(a, b, c, d) {
		b.child = null === a ? Vg(b, null, c, d) : Ug(b, a.child, c, d);
	}
	function Yi(a, b, c, d, e) {
		c = c.render;
		var f = b.ref;
		ch(b, e);
		d = Nh(a, b, c, d, f, e);
		c = Sh();
		if (null !== a && !dh) return b.updateQueue = a.updateQueue, b.flags &= -2053, a.lanes &= ~e, Zi(a, b, e);
		I && c && vg(b);
		b.flags |= 1;
		Xi(a, b, d, e);
		return b.child;
	}
	function $i(a, b, c, d, e) {
		if (null === a) {
			var f = c.type;
			if ("function" === typeof f && !aj(f) && void 0 === f.defaultProps && null === c.compare && void 0 === c.defaultProps) return b.tag = 15, b.type = f, bj(a, b, f, d, e);
			a = Rg(c.type, null, d, b, b.mode, e);
			a.ref = b.ref;
			a.return = b;
			return b.child = a;
		}
		f = a.child;
		if (0 === (a.lanes & e)) {
			var g = f.memoizedProps;
			c = c.compare;
			c = null !== c ? c : Ie;
			if (c(g, d) && a.ref === b.ref) return Zi(a, b, e);
		}
		b.flags |= 1;
		a = Pg(f, d);
		a.ref = b.ref;
		a.return = b;
		return b.child = a;
	}
	function bj(a, b, c, d, e) {
		if (null !== a) {
			var f = a.memoizedProps;
			if (Ie(f, d) && a.ref === b.ref) if (dh = !1, b.pendingProps = d = f, 0 !== (a.lanes & e)) 0 !== (a.flags & 131072) && (dh = !0);
			else return b.lanes = a.lanes, Zi(a, b, e);
		}
		return cj(a, b, c, d, e);
	}
	function dj(a, b, c) {
		var d = b.pendingProps, e = d.children, f = null !== a ? a.memoizedState : null;
		if ("hidden" === d.mode) if (0 === (b.mode & 1)) b.memoizedState = {
			baseLanes: 0,
			cachePool: null,
			transitions: null
		}, G(ej, fj), fj |= c;
		else {
			if (0 === (c & 1073741824)) return a = null !== f ? f.baseLanes | c : c, b.lanes = b.childLanes = 1073741824, b.memoizedState = {
				baseLanes: a,
				cachePool: null,
				transitions: null
			}, b.updateQueue = null, G(ej, fj), fj |= a, null;
			b.memoizedState = {
				baseLanes: 0,
				cachePool: null,
				transitions: null
			};
			d = null !== f ? f.baseLanes : c;
			G(ej, fj);
			fj |= d;
		}
		else null !== f ? (d = f.baseLanes | c, b.memoizedState = null) : d = c, G(ej, fj), fj |= d;
		Xi(a, b, e, c);
		return b.child;
	}
	function gj(a, b) {
		var c = b.ref;
		if (null === a && null !== c || null !== a && a.ref !== c) b.flags |= 512, b.flags |= 2097152;
	}
	function cj(a, b, c, d, e) {
		var f = Zf(c) ? Xf : H.current;
		f = Yf(b, f);
		ch(b, e);
		c = Nh(a, b, c, d, f, e);
		d = Sh();
		if (null !== a && !dh) return b.updateQueue = a.updateQueue, b.flags &= -2053, a.lanes &= ~e, Zi(a, b, e);
		I && d && vg(b);
		b.flags |= 1;
		Xi(a, b, c, e);
		return b.child;
	}
	function hj(a, b, c, d, e) {
		if (Zf(c)) {
			var f = !0;
			cg(b);
		} else f = !1;
		ch(b, e);
		if (null === b.stateNode) ij(a, b), Gi(b, c, d), Ii(b, c, d, e), d = !0;
		else if (null === a) {
			var g = b.stateNode, h = b.memoizedProps;
			g.props = h;
			var k = g.context, l = c.contextType;
			"object" === typeof l && null !== l ? l = eh(l) : (l = Zf(c) ? Xf : H.current, l = Yf(b, l));
			var m = c.getDerivedStateFromProps, q = "function" === typeof m || "function" === typeof g.getSnapshotBeforeUpdate;
			q || "function" !== typeof g.UNSAFE_componentWillReceiveProps && "function" !== typeof g.componentWillReceiveProps || (h !== d || k !== l) && Hi(b, g, d, l);
			jh = !1;
			var r = b.memoizedState;
			g.state = r;
			qh(b, d, g, e);
			k = b.memoizedState;
			h !== d || r !== k || Wf.current || jh ? ("function" === typeof m && (Di(b, c, m, d), k = b.memoizedState), (h = jh || Fi(b, c, h, d, r, k, l)) ? (q || "function" !== typeof g.UNSAFE_componentWillMount && "function" !== typeof g.componentWillMount || ("function" === typeof g.componentWillMount && g.componentWillMount(), "function" === typeof g.UNSAFE_componentWillMount && g.UNSAFE_componentWillMount()), "function" === typeof g.componentDidMount && (b.flags |= 4194308)) : ("function" === typeof g.componentDidMount && (b.flags |= 4194308), b.memoizedProps = d, b.memoizedState = k), g.props = d, g.state = k, g.context = l, d = h) : ("function" === typeof g.componentDidMount && (b.flags |= 4194308), d = !1);
		} else {
			g = b.stateNode;
			lh(a, b);
			h = b.memoizedProps;
			l = b.type === b.elementType ? h : Ci(b.type, h);
			g.props = l;
			q = b.pendingProps;
			r = g.context;
			k = c.contextType;
			"object" === typeof k && null !== k ? k = eh(k) : (k = Zf(c) ? Xf : H.current, k = Yf(b, k));
			var y = c.getDerivedStateFromProps;
			(m = "function" === typeof y || "function" === typeof g.getSnapshotBeforeUpdate) || "function" !== typeof g.UNSAFE_componentWillReceiveProps && "function" !== typeof g.componentWillReceiveProps || (h !== q || r !== k) && Hi(b, g, d, k);
			jh = !1;
			r = b.memoizedState;
			g.state = r;
			qh(b, d, g, e);
			var n = b.memoizedState;
			h !== q || r !== n || Wf.current || jh ? ("function" === typeof y && (Di(b, c, y, d), n = b.memoizedState), (l = jh || Fi(b, c, l, d, r, n, k) || !1) ? (m || "function" !== typeof g.UNSAFE_componentWillUpdate && "function" !== typeof g.componentWillUpdate || ("function" === typeof g.componentWillUpdate && g.componentWillUpdate(d, n, k), "function" === typeof g.UNSAFE_componentWillUpdate && g.UNSAFE_componentWillUpdate(d, n, k)), "function" === typeof g.componentDidUpdate && (b.flags |= 4), "function" === typeof g.getSnapshotBeforeUpdate && (b.flags |= 1024)) : ("function" !== typeof g.componentDidUpdate || h === a.memoizedProps && r === a.memoizedState || (b.flags |= 4), "function" !== typeof g.getSnapshotBeforeUpdate || h === a.memoizedProps && r === a.memoizedState || (b.flags |= 1024), b.memoizedProps = d, b.memoizedState = n), g.props = d, g.state = n, g.context = k, d = l) : ("function" !== typeof g.componentDidUpdate || h === a.memoizedProps && r === a.memoizedState || (b.flags |= 4), "function" !== typeof g.getSnapshotBeforeUpdate || h === a.memoizedProps && r === a.memoizedState || (b.flags |= 1024), d = !1);
		}
		return jj(a, b, c, d, f, e);
	}
	function jj(a, b, c, d, e, f) {
		gj(a, b);
		var g = 0 !== (b.flags & 128);
		if (!d && !g) return e && dg(b, c, !1), Zi(a, b, f);
		d = b.stateNode;
		Wi.current = b;
		var h = g && "function" !== typeof c.getDerivedStateFromError ? null : d.render();
		b.flags |= 1;
		null !== a && g ? (b.child = Ug(b, a.child, null, f), b.child = Ug(b, null, h, f)) : Xi(a, b, h, f);
		b.memoizedState = d.state;
		e && dg(b, c, !0);
		return b.child;
	}
	function kj(a) {
		var b = a.stateNode;
		b.pendingContext ? ag(a, b.pendingContext, b.pendingContext !== b.context) : b.context && ag(a, b.context, !1);
		yh(a, b.containerInfo);
	}
	function lj(a, b, c, d, e) {
		Ig();
		Jg(e);
		b.flags |= 256;
		Xi(a, b, c, d);
		return b.child;
	}
	var mj = {
		dehydrated: null,
		treeContext: null,
		retryLane: 0
	};
	function nj(a) {
		return {
			baseLanes: a,
			cachePool: null,
			transitions: null
		};
	}
	function oj(a, b, c) {
		var d = b.pendingProps, e = L.current, f = !1, g = 0 !== (b.flags & 128), h;
		(h = g) || (h = null !== a && null === a.memoizedState ? !1 : 0 !== (e & 2));
		if (h) f = !0, b.flags &= -129;
		else if (null === a || null !== a.memoizedState) e |= 1;
		G(L, e & 1);
		if (null === a) {
			Eg(b);
			a = b.memoizedState;
			if (null !== a && (a = a.dehydrated, null !== a)) return 0 === (b.mode & 1) ? b.lanes = 1 : "$!" === a.data ? b.lanes = 8 : b.lanes = 1073741824, null;
			g = d.children;
			a = d.fallback;
			return f ? (d = b.mode, f = b.child, g = {
				mode: "hidden",
				children: g
			}, 0 === (d & 1) && null !== f ? (f.childLanes = 0, f.pendingProps = g) : f = pj(g, d, 0, null), a = Tg(a, d, c, null), f.return = b, a.return = b, f.sibling = a, b.child = f, b.child.memoizedState = nj(c), b.memoizedState = mj, a) : qj(b, g);
		}
		e = a.memoizedState;
		if (null !== e && (h = e.dehydrated, null !== h)) return rj(a, b, g, d, h, e, c);
		if (f) {
			f = d.fallback;
			g = b.mode;
			e = a.child;
			h = e.sibling;
			var k = {
				mode: "hidden",
				children: d.children
			};
			0 === (g & 1) && b.child !== e ? (d = b.child, d.childLanes = 0, d.pendingProps = k, b.deletions = null) : (d = Pg(e, k), d.subtreeFlags = e.subtreeFlags & 14680064);
			null !== h ? f = Pg(h, f) : (f = Tg(f, g, c, null), f.flags |= 2);
			f.return = b;
			d.return = b;
			d.sibling = f;
			b.child = d;
			d = f;
			f = b.child;
			g = a.child.memoizedState;
			g = null === g ? nj(c) : {
				baseLanes: g.baseLanes | c,
				cachePool: null,
				transitions: g.transitions
			};
			f.memoizedState = g;
			f.childLanes = a.childLanes & ~c;
			b.memoizedState = mj;
			return d;
		}
		f = a.child;
		a = f.sibling;
		d = Pg(f, {
			mode: "visible",
			children: d.children
		});
		0 === (b.mode & 1) && (d.lanes = c);
		d.return = b;
		d.sibling = null;
		null !== a && (c = b.deletions, null === c ? (b.deletions = [a], b.flags |= 16) : c.push(a));
		b.child = d;
		b.memoizedState = null;
		return d;
	}
	function qj(a, b) {
		b = pj({
			mode: "visible",
			children: b
		}, a.mode, 0, null);
		b.return = a;
		return a.child = b;
	}
	function sj(a, b, c, d) {
		null !== d && Jg(d);
		Ug(b, a.child, null, c);
		a = qj(b, b.pendingProps.children);
		a.flags |= 2;
		b.memoizedState = null;
		return a;
	}
	function rj(a, b, c, d, e, f, g) {
		if (c) {
			if (b.flags & 256) return b.flags &= -257, d = Ki(Error(p(422))), sj(a, b, g, d);
			if (null !== b.memoizedState) return b.child = a.child, b.flags |= 128, null;
			f = d.fallback;
			e = b.mode;
			d = pj({
				mode: "visible",
				children: d.children
			}, e, 0, null);
			f = Tg(f, e, g, null);
			f.flags |= 2;
			d.return = b;
			f.return = b;
			d.sibling = f;
			b.child = d;
			0 !== (b.mode & 1) && Ug(b, a.child, null, g);
			b.child.memoizedState = nj(g);
			b.memoizedState = mj;
			return f;
		}
		if (0 === (b.mode & 1)) return sj(a, b, g, null);
		if ("$!" === e.data) {
			d = e.nextSibling && e.nextSibling.dataset;
			if (d) var h = d.dgst;
			d = h;
			f = Error(p(419));
			d = Ki(f, d, void 0);
			return sj(a, b, g, d);
		}
		h = 0 !== (g & a.childLanes);
		if (dh || h) {
			d = Q;
			if (null !== d) {
				switch (g & -g) {
					case 4:
						e = 2;
						break;
					case 16:
						e = 8;
						break;
					case 64:
					case 128:
					case 256:
					case 512:
					case 1024:
					case 2048:
					case 4096:
					case 8192:
					case 16384:
					case 32768:
					case 65536:
					case 131072:
					case 262144:
					case 524288:
					case 1048576:
					case 2097152:
					case 4194304:
					case 8388608:
					case 16777216:
					case 33554432:
					case 67108864:
						e = 32;
						break;
					case 536870912:
						e = 268435456;
						break;
					default: e = 0;
				}
				e = 0 !== (e & (d.suspendedLanes | g)) ? 0 : e;
				0 !== e && e !== f.retryLane && (f.retryLane = e, ih(a, e), gi(d, a, e, -1));
			}
			tj();
			d = Ki(Error(p(421)));
			return sj(a, b, g, d);
		}
		if ("$?" === e.data) return b.flags |= 128, b.child = a.child, b = uj.bind(null, a), e._reactRetry = b, null;
		a = f.treeContext;
		yg = Lf(e.nextSibling);
		xg = b;
		I = !0;
		zg = null;
		null !== a && (og[pg++] = rg, og[pg++] = sg, og[pg++] = qg, rg = a.id, sg = a.overflow, qg = b);
		b = qj(b, d.children);
		b.flags |= 4096;
		return b;
	}
	function vj(a, b, c) {
		a.lanes |= b;
		var d = a.alternate;
		null !== d && (d.lanes |= b);
		bh(a.return, b, c);
	}
	function wj(a, b, c, d, e) {
		var f = a.memoizedState;
		null === f ? a.memoizedState = {
			isBackwards: b,
			rendering: null,
			renderingStartTime: 0,
			last: d,
			tail: c,
			tailMode: e
		} : (f.isBackwards = b, f.rendering = null, f.renderingStartTime = 0, f.last = d, f.tail = c, f.tailMode = e);
	}
	function xj(a, b, c) {
		var d = b.pendingProps, e = d.revealOrder, f = d.tail;
		Xi(a, b, d.children, c);
		d = L.current;
		if (0 !== (d & 2)) d = d & 1 | 2, b.flags |= 128;
		else {
			if (null !== a && 0 !== (a.flags & 128)) a: for (a = b.child; null !== a;) {
				if (13 === a.tag) null !== a.memoizedState && vj(a, c, b);
				else if (19 === a.tag) vj(a, c, b);
				else if (null !== a.child) {
					a.child.return = a;
					a = a.child;
					continue;
				}
				if (a === b) break a;
				for (; null === a.sibling;) {
					if (null === a.return || a.return === b) break a;
					a = a.return;
				}
				a.sibling.return = a.return;
				a = a.sibling;
			}
			d &= 1;
		}
		G(L, d);
		if (0 === (b.mode & 1)) b.memoizedState = null;
		else switch (e) {
			case "forwards":
				c = b.child;
				for (e = null; null !== c;) a = c.alternate, null !== a && null === Ch(a) && (e = c), c = c.sibling;
				c = e;
				null === c ? (e = b.child, b.child = null) : (e = c.sibling, c.sibling = null);
				wj(b, !1, e, c, f);
				break;
			case "backwards":
				c = null;
				e = b.child;
				for (b.child = null; null !== e;) {
					a = e.alternate;
					if (null !== a && null === Ch(a)) {
						b.child = e;
						break;
					}
					a = e.sibling;
					e.sibling = c;
					c = e;
					e = a;
				}
				wj(b, !0, c, null, f);
				break;
			case "together":
				wj(b, !1, null, null, void 0);
				break;
			default: b.memoizedState = null;
		}
		return b.child;
	}
	function ij(a, b) {
		0 === (b.mode & 1) && null !== a && (a.alternate = null, b.alternate = null, b.flags |= 2);
	}
	function Zi(a, b, c) {
		null !== a && (b.dependencies = a.dependencies);
		rh |= b.lanes;
		if (0 === (c & b.childLanes)) return null;
		if (null !== a && b.child !== a.child) throw Error(p(153));
		if (null !== b.child) {
			a = b.child;
			c = Pg(a, a.pendingProps);
			b.child = c;
			for (c.return = b; null !== a.sibling;) a = a.sibling, c = c.sibling = Pg(a, a.pendingProps), c.return = b;
			c.sibling = null;
		}
		return b.child;
	}
	function yj(a, b, c) {
		switch (b.tag) {
			case 3:
				kj(b);
				Ig();
				break;
			case 5:
				Ah(b);
				break;
			case 1:
				Zf(b.type) && cg(b);
				break;
			case 4:
				yh(b, b.stateNode.containerInfo);
				break;
			case 10:
				var d = b.type._context, e = b.memoizedProps.value;
				G(Wg, d._currentValue);
				d._currentValue = e;
				break;
			case 13:
				d = b.memoizedState;
				if (null !== d) {
					if (null !== d.dehydrated) return G(L, L.current & 1), b.flags |= 128, null;
					if (0 !== (c & b.child.childLanes)) return oj(a, b, c);
					G(L, L.current & 1);
					a = Zi(a, b, c);
					return null !== a ? a.sibling : null;
				}
				G(L, L.current & 1);
				break;
			case 19:
				d = 0 !== (c & b.childLanes);
				if (0 !== (a.flags & 128)) {
					if (d) return xj(a, b, c);
					b.flags |= 128;
				}
				e = b.memoizedState;
				null !== e && (e.rendering = null, e.tail = null, e.lastEffect = null);
				G(L, L.current);
				if (d) break;
				else return null;
			case 22:
			case 23: return b.lanes = 0, dj(a, b, c);
		}
		return Zi(a, b, c);
	}
	var zj = function(a, b) {
		for (var c = b.child; null !== c;) {
			if (5 === c.tag || 6 === c.tag) a.appendChild(c.stateNode);
			else if (4 !== c.tag && null !== c.child) {
				c.child.return = c;
				c = c.child;
				continue;
			}
			if (c === b) break;
			for (; null === c.sibling;) {
				if (null === c.return || c.return === b) return;
				c = c.return;
			}
			c.sibling.return = c.return;
			c = c.sibling;
		}
	}, Bj = function(a, b, c, d) {
		var e = a.memoizedProps;
		if (e !== d) {
			a = b.stateNode;
			xh(uh.current);
			var f = null;
			switch (c) {
				case "input":
					e = Ya(a, e);
					d = Ya(a, d);
					f = [];
					break;
				case "select":
					e = A({}, e, { value: void 0 });
					d = A({}, d, { value: void 0 });
					f = [];
					break;
				case "textarea":
					e = gb(a, e);
					d = gb(a, d);
					f = [];
					break;
				default: "function" !== typeof e.onClick && "function" === typeof d.onClick && (a.onclick = Bf);
			}
			ub(c, d);
			var g;
			c = null;
			for (l in e) if (!d.hasOwnProperty(l) && e.hasOwnProperty(l) && null != e[l]) if ("style" === l) {
				var h = e[l];
				for (g in h) h.hasOwnProperty(g) && (c || (c = {}), c[g] = "");
			} else "dangerouslySetInnerHTML" !== l && "children" !== l && "suppressContentEditableWarning" !== l && "suppressHydrationWarning" !== l && "autoFocus" !== l && (ea.hasOwnProperty(l) ? f || (f = []) : (f = f || []).push(l, null));
			for (l in d) {
				var k = d[l];
				h = null != e ? e[l] : void 0;
				if (d.hasOwnProperty(l) && k !== h && (null != k || null != h)) if ("style" === l) if (h) {
					for (g in h) !h.hasOwnProperty(g) || k && k.hasOwnProperty(g) || (c || (c = {}), c[g] = "");
					for (g in k) k.hasOwnProperty(g) && h[g] !== k[g] && (c || (c = {}), c[g] = k[g]);
				} else c || (f || (f = []), f.push(l, c)), c = k;
				else "dangerouslySetInnerHTML" === l ? (k = k ? k.__html : void 0, h = h ? h.__html : void 0, null != k && h !== k && (f = f || []).push(l, k)) : "children" === l ? "string" !== typeof k && "number" !== typeof k || (f = f || []).push(l, "" + k) : "suppressContentEditableWarning" !== l && "suppressHydrationWarning" !== l && (ea.hasOwnProperty(l) ? (null != k && "onScroll" === l && D("scroll", a), f || h === k || (f = [])) : (f = f || []).push(l, k));
			}
			c && (f = f || []).push("style", c);
			var l = f;
			if (b.updateQueue = l) b.flags |= 4;
		}
	}, Cj = function(a, b, c, d) {
		c !== d && (b.flags |= 4);
	};
	function Dj(a, b) {
		if (!I) switch (a.tailMode) {
			case "hidden":
				b = a.tail;
				for (var c = null; null !== b;) null !== b.alternate && (c = b), b = b.sibling;
				null === c ? a.tail = null : c.sibling = null;
				break;
			case "collapsed":
				c = a.tail;
				for (var d = null; null !== c;) null !== c.alternate && (d = c), c = c.sibling;
				null === d ? b || null === a.tail ? a.tail = null : a.tail.sibling = null : d.sibling = null;
		}
	}
	function S(a) {
		var b = null !== a.alternate && a.alternate.child === a.child, c = 0, d = 0;
		if (b) for (var e = a.child; null !== e;) c |= e.lanes | e.childLanes, d |= e.subtreeFlags & 14680064, d |= e.flags & 14680064, e.return = a, e = e.sibling;
		else for (e = a.child; null !== e;) c |= e.lanes | e.childLanes, d |= e.subtreeFlags, d |= e.flags, e.return = a, e = e.sibling;
		a.subtreeFlags |= d;
		a.childLanes = c;
		return b;
	}
	function Ej(a, b, c) {
		var d = b.pendingProps;
		wg(b);
		switch (b.tag) {
			case 2:
			case 16:
			case 15:
			case 0:
			case 11:
			case 7:
			case 8:
			case 12:
			case 9:
			case 14: return S(b), null;
			case 1: return Zf(b.type) && $f(), S(b), null;
			case 3:
				d = b.stateNode;
				zh();
				E(Wf);
				E(H);
				Eh();
				d.pendingContext && (d.context = d.pendingContext, d.pendingContext = null);
				if (null === a || null === a.child) Gg(b) ? b.flags |= 4 : null === a || a.memoizedState.isDehydrated && 0 === (b.flags & 256) || (b.flags |= 1024, null !== zg && (Fj(zg), zg = null));
				S(b);
				return null;
			case 5:
				Bh(b);
				var e = xh(wh.current);
				c = b.type;
				if (null !== a && null != b.stateNode) Bj(a, b, c, d, e), a.ref !== b.ref && (b.flags |= 512, b.flags |= 2097152);
				else {
					if (!d) {
						if (null === b.stateNode) throw Error(p(166));
						S(b);
						return null;
					}
					a = xh(uh.current);
					if (Gg(b)) {
						d = b.stateNode;
						c = b.type;
						var f = b.memoizedProps;
						d[Of] = b;
						d[Pf] = f;
						a = 0 !== (b.mode & 1);
						switch (c) {
							case "dialog":
								D("cancel", d);
								D("close", d);
								break;
							case "iframe":
							case "object":
							case "embed":
								D("load", d);
								break;
							case "video":
							case "audio":
								for (e = 0; e < lf.length; e++) D(lf[e], d);
								break;
							case "source":
								D("error", d);
								break;
							case "img":
							case "image":
							case "link":
								D("error", d);
								D("load", d);
								break;
							case "details":
								D("toggle", d);
								break;
							case "input":
								Za(d, f);
								D("invalid", d);
								break;
							case "select":
								d._wrapperState = { wasMultiple: !!f.multiple };
								D("invalid", d);
								break;
							case "textarea": hb(d, f), D("invalid", d);
						}
						ub(c, f);
						e = null;
						for (var g in f) if (f.hasOwnProperty(g)) {
							var h = f[g];
							"children" === g ? "string" === typeof h ? d.textContent !== h && (!0 !== f.suppressHydrationWarning && Af(d.textContent, h, a), e = ["children", h]) : "number" === typeof h && d.textContent !== "" + h && (!0 !== f.suppressHydrationWarning && Af(d.textContent, h, a), e = ["children", "" + h]) : ea.hasOwnProperty(g) && null != h && "onScroll" === g && D("scroll", d);
						}
						switch (c) {
							case "input":
								Va(d);
								db(d, f, !0);
								break;
							case "textarea":
								Va(d);
								jb(d);
								break;
							case "select":
							case "option": break;
							default: "function" === typeof f.onClick && (d.onclick = Bf);
						}
						d = e;
						b.updateQueue = d;
						null !== d && (b.flags |= 4);
					} else {
						g = 9 === e.nodeType ? e : e.ownerDocument;
						"http://www.w3.org/1999/xhtml" === a && (a = kb(c));
						"http://www.w3.org/1999/xhtml" === a ? "script" === c ? (a = g.createElement("div"), a.innerHTML = "<script><\/script>", a = a.removeChild(a.firstChild)) : "string" === typeof d.is ? a = g.createElement(c, { is: d.is }) : (a = g.createElement(c), "select" === c && (g = a, d.multiple ? g.multiple = !0 : d.size && (g.size = d.size))) : a = g.createElementNS(a, c);
						a[Of] = b;
						a[Pf] = d;
						zj(a, b, !1, !1);
						b.stateNode = a;
						a: {
							g = vb(c, d);
							switch (c) {
								case "dialog":
									D("cancel", a);
									D("close", a);
									e = d;
									break;
								case "iframe":
								case "object":
								case "embed":
									D("load", a);
									e = d;
									break;
								case "video":
								case "audio":
									for (e = 0; e < lf.length; e++) D(lf[e], a);
									e = d;
									break;
								case "source":
									D("error", a);
									e = d;
									break;
								case "img":
								case "image":
								case "link":
									D("error", a);
									D("load", a);
									e = d;
									break;
								case "details":
									D("toggle", a);
									e = d;
									break;
								case "input":
									Za(a, d);
									e = Ya(a, d);
									D("invalid", a);
									break;
								case "option":
									e = d;
									break;
								case "select":
									a._wrapperState = { wasMultiple: !!d.multiple };
									e = A({}, d, { value: void 0 });
									D("invalid", a);
									break;
								case "textarea":
									hb(a, d);
									e = gb(a, d);
									D("invalid", a);
									break;
								default: e = d;
							}
							ub(c, e);
							h = e;
							for (f in h) if (h.hasOwnProperty(f)) {
								var k = h[f];
								"style" === f ? sb(a, k) : "dangerouslySetInnerHTML" === f ? (k = k ? k.__html : void 0, null != k && nb(a, k)) : "children" === f ? "string" === typeof k ? ("textarea" !== c || "" !== k) && ob(a, k) : "number" === typeof k && ob(a, "" + k) : "suppressContentEditableWarning" !== f && "suppressHydrationWarning" !== f && "autoFocus" !== f && (ea.hasOwnProperty(f) ? null != k && "onScroll" === f && D("scroll", a) : null != k && ta(a, f, k, g));
							}
							switch (c) {
								case "input":
									Va(a);
									db(a, d, !1);
									break;
								case "textarea":
									Va(a);
									jb(a);
									break;
								case "option":
									null != d.value && a.setAttribute("value", "" + Sa(d.value));
									break;
								case "select":
									a.multiple = !!d.multiple;
									f = d.value;
									null != f ? fb(a, !!d.multiple, f, !1) : null != d.defaultValue && fb(a, !!d.multiple, d.defaultValue, !0);
									break;
								default: "function" === typeof e.onClick && (a.onclick = Bf);
							}
							switch (c) {
								case "button":
								case "input":
								case "select":
								case "textarea":
									d = !!d.autoFocus;
									break a;
								case "img":
									d = !0;
									break a;
								default: d = !1;
							}
						}
						d && (b.flags |= 4);
					}
					null !== b.ref && (b.flags |= 512, b.flags |= 2097152);
				}
				S(b);
				return null;
			case 6:
				if (a && null != b.stateNode) Cj(a, b, a.memoizedProps, d);
				else {
					if ("string" !== typeof d && null === b.stateNode) throw Error(p(166));
					c = xh(wh.current);
					xh(uh.current);
					if (Gg(b)) {
						d = b.stateNode;
						c = b.memoizedProps;
						d[Of] = b;
						if (f = d.nodeValue !== c) {
							if (a = xg, null !== a) switch (a.tag) {
								case 3:
									Af(d.nodeValue, c, 0 !== (a.mode & 1));
									break;
								case 5: !0 !== a.memoizedProps.suppressHydrationWarning && Af(d.nodeValue, c, 0 !== (a.mode & 1));
							}
						}
						f && (b.flags |= 4);
					} else d = (9 === c.nodeType ? c : c.ownerDocument).createTextNode(d), d[Of] = b, b.stateNode = d;
				}
				S(b);
				return null;
			case 13:
				E(L);
				d = b.memoizedState;
				if (null === a || null !== a.memoizedState && null !== a.memoizedState.dehydrated) {
					if (I && null !== yg && 0 !== (b.mode & 1) && 0 === (b.flags & 128)) Hg(), Ig(), b.flags |= 98560, f = !1;
					else if (f = Gg(b), null !== d && null !== d.dehydrated) {
						if (null === a) {
							if (!f) throw Error(p(318));
							f = b.memoizedState;
							f = null !== f ? f.dehydrated : null;
							if (!f) throw Error(p(317));
							f[Of] = b;
						} else Ig(), 0 === (b.flags & 128) && (b.memoizedState = null), b.flags |= 4;
						S(b);
						f = !1;
					} else null !== zg && (Fj(zg), zg = null), f = !0;
					if (!f) return b.flags & 65536 ? b : null;
				}
				if (0 !== (b.flags & 128)) return b.lanes = c, b;
				d = null !== d;
				d !== (null !== a && null !== a.memoizedState) && d && (b.child.flags |= 8192, 0 !== (b.mode & 1) && (null === a || 0 !== (L.current & 1) ? 0 === T && (T = 3) : tj()));
				null !== b.updateQueue && (b.flags |= 4);
				S(b);
				return null;
			case 4: return zh(), null === a && sf(b.stateNode.containerInfo), S(b), null;
			case 10: return ah(b.type._context), S(b), null;
			case 17: return Zf(b.type) && $f(), S(b), null;
			case 19:
				E(L);
				f = b.memoizedState;
				if (null === f) return S(b), null;
				d = 0 !== (b.flags & 128);
				g = f.rendering;
				if (null === g) if (d) Dj(f, !1);
				else {
					if (0 !== T || null !== a && 0 !== (a.flags & 128)) for (a = b.child; null !== a;) {
						g = Ch(a);
						if (null !== g) {
							b.flags |= 128;
							Dj(f, !1);
							d = g.updateQueue;
							null !== d && (b.updateQueue = d, b.flags |= 4);
							b.subtreeFlags = 0;
							d = c;
							for (c = b.child; null !== c;) f = c, a = d, f.flags &= 14680066, g = f.alternate, null === g ? (f.childLanes = 0, f.lanes = a, f.child = null, f.subtreeFlags = 0, f.memoizedProps = null, f.memoizedState = null, f.updateQueue = null, f.dependencies = null, f.stateNode = null) : (f.childLanes = g.childLanes, f.lanes = g.lanes, f.child = g.child, f.subtreeFlags = 0, f.deletions = null, f.memoizedProps = g.memoizedProps, f.memoizedState = g.memoizedState, f.updateQueue = g.updateQueue, f.type = g.type, a = g.dependencies, f.dependencies = null === a ? null : {
								lanes: a.lanes,
								firstContext: a.firstContext
							}), c = c.sibling;
							G(L, L.current & 1 | 2);
							return b.child;
						}
						a = a.sibling;
					}
					null !== f.tail && B() > Gj && (b.flags |= 128, d = !0, Dj(f, !1), b.lanes = 4194304);
				}
				else {
					if (!d) if (a = Ch(g), null !== a) {
						if (b.flags |= 128, d = !0, c = a.updateQueue, null !== c && (b.updateQueue = c, b.flags |= 4), Dj(f, !0), null === f.tail && "hidden" === f.tailMode && !g.alternate && !I) return S(b), null;
					} else 2 * B() - f.renderingStartTime > Gj && 1073741824 !== c && (b.flags |= 128, d = !0, Dj(f, !1), b.lanes = 4194304);
					f.isBackwards ? (g.sibling = b.child, b.child = g) : (c = f.last, null !== c ? c.sibling = g : b.child = g, f.last = g);
				}
				if (null !== f.tail) return b = f.tail, f.rendering = b, f.tail = b.sibling, f.renderingStartTime = B(), b.sibling = null, c = L.current, G(L, d ? c & 1 | 2 : c & 1), b;
				S(b);
				return null;
			case 22:
			case 23: return Hj(), d = null !== b.memoizedState, null !== a && null !== a.memoizedState !== d && (b.flags |= 8192), d && 0 !== (b.mode & 1) ? 0 !== (fj & 1073741824) && (S(b), b.subtreeFlags & 6 && (b.flags |= 8192)) : S(b), null;
			case 24: return null;
			case 25: return null;
		}
		throw Error(p(156, b.tag));
	}
	function Ij(a, b) {
		wg(b);
		switch (b.tag) {
			case 1: return Zf(b.type) && $f(), a = b.flags, a & 65536 ? (b.flags = a & -65537 | 128, b) : null;
			case 3: return zh(), E(Wf), E(H), Eh(), a = b.flags, 0 !== (a & 65536) && 0 === (a & 128) ? (b.flags = a & -65537 | 128, b) : null;
			case 5: return Bh(b), null;
			case 13:
				E(L);
				a = b.memoizedState;
				if (null !== a && null !== a.dehydrated) {
					if (null === b.alternate) throw Error(p(340));
					Ig();
				}
				a = b.flags;
				return a & 65536 ? (b.flags = a & -65537 | 128, b) : null;
			case 19: return E(L), null;
			case 4: return zh(), null;
			case 10: return ah(b.type._context), null;
			case 22:
			case 23: return Hj(), null;
			case 24: return null;
			default: return null;
		}
	}
	var Jj = !1, U = !1, Kj = "function" === typeof WeakSet ? WeakSet : Set, V = null;
	function Lj(a, b) {
		var c = a.ref;
		if (null !== c) if ("function" === typeof c) try {
			c(null);
		} catch (d) {
			W(a, b, d);
		}
		else c.current = null;
	}
	function Mj(a, b, c) {
		try {
			c();
		} catch (d) {
			W(a, b, d);
		}
	}
	var Nj = !1;
	function Oj(a, b) {
		Cf = dd;
		a = Me();
		if (Ne(a)) {
			if ("selectionStart" in a) var c = {
				start: a.selectionStart,
				end: a.selectionEnd
			};
			else a: {
				c = (c = a.ownerDocument) && c.defaultView || window;
				var d = c.getSelection && c.getSelection();
				if (d && 0 !== d.rangeCount) {
					c = d.anchorNode;
					var e = d.anchorOffset, f = d.focusNode;
					d = d.focusOffset;
					try {
						c.nodeType, f.nodeType;
					} catch (F) {
						c = null;
						break a;
					}
					var g = 0, h = -1, k = -1, l = 0, m = 0, q = a, r = null;
					b: for (;;) {
						for (var y;;) {
							q !== c || 0 !== e && 3 !== q.nodeType || (h = g + e);
							q !== f || 0 !== d && 3 !== q.nodeType || (k = g + d);
							3 === q.nodeType && (g += q.nodeValue.length);
							if (null === (y = q.firstChild)) break;
							r = q;
							q = y;
						}
						for (;;) {
							if (q === a) break b;
							r === c && ++l === e && (h = g);
							r === f && ++m === d && (k = g);
							if (null !== (y = q.nextSibling)) break;
							q = r;
							r = q.parentNode;
						}
						q = y;
					}
					c = -1 === h || -1 === k ? null : {
						start: h,
						end: k
					};
				} else c = null;
			}
			c = c || {
				start: 0,
				end: 0
			};
		} else c = null;
		Df = {
			focusedElem: a,
			selectionRange: c
		};
		dd = !1;
		for (V = b; null !== V;) if (b = V, a = b.child, 0 !== (b.subtreeFlags & 1028) && null !== a) a.return = b, V = a;
		else for (; null !== V;) {
			b = V;
			try {
				var n = b.alternate;
				if (0 !== (b.flags & 1024)) switch (b.tag) {
					case 0:
					case 11:
					case 15: break;
					case 1:
						if (null !== n) {
							var t = n.memoizedProps, J = n.memoizedState, x = b.stateNode;
							x.__reactInternalSnapshotBeforeUpdate = x.getSnapshotBeforeUpdate(b.elementType === b.type ? t : Ci(b.type, t), J);
						}
						break;
					case 3:
						var u = b.stateNode.containerInfo;
						1 === u.nodeType ? u.textContent = "" : 9 === u.nodeType && u.documentElement && u.removeChild(u.documentElement);
						break;
					case 5:
					case 6:
					case 4:
					case 17: break;
					default: throw Error(p(163));
				}
			} catch (F) {
				W(b, b.return, F);
			}
			a = b.sibling;
			if (null !== a) {
				a.return = b.return;
				V = a;
				break;
			}
			V = b.return;
		}
		n = Nj;
		Nj = !1;
		return n;
	}
	function Pj(a, b, c) {
		var d = b.updateQueue;
		d = null !== d ? d.lastEffect : null;
		if (null !== d) {
			var e = d = d.next;
			do {
				if ((e.tag & a) === a) {
					var f = e.destroy;
					e.destroy = void 0;
					void 0 !== f && Mj(b, c, f);
				}
				e = e.next;
			} while (e !== d);
		}
	}
	function Qj(a, b) {
		b = b.updateQueue;
		b = null !== b ? b.lastEffect : null;
		if (null !== b) {
			var c = b = b.next;
			do {
				if ((c.tag & a) === a) {
					var d = c.create;
					c.destroy = d();
				}
				c = c.next;
			} while (c !== b);
		}
	}
	function Rj(a) {
		var b = a.ref;
		if (null !== b) {
			var c = a.stateNode;
			switch (a.tag) {
				case 5:
					a = c;
					break;
				default: a = c;
			}
			"function" === typeof b ? b(a) : b.current = a;
		}
	}
	function Sj(a) {
		var b = a.alternate;
		null !== b && (a.alternate = null, Sj(b));
		a.child = null;
		a.deletions = null;
		a.sibling = null;
		5 === a.tag && (b = a.stateNode, null !== b && (delete b[Of], delete b[Pf], delete b[of], delete b[Qf], delete b[Rf]));
		a.stateNode = null;
		a.return = null;
		a.dependencies = null;
		a.memoizedProps = null;
		a.memoizedState = null;
		a.pendingProps = null;
		a.stateNode = null;
		a.updateQueue = null;
	}
	function Tj(a) {
		return 5 === a.tag || 3 === a.tag || 4 === a.tag;
	}
	function Uj(a) {
		a: for (;;) {
			for (; null === a.sibling;) {
				if (null === a.return || Tj(a.return)) return null;
				a = a.return;
			}
			a.sibling.return = a.return;
			for (a = a.sibling; 5 !== a.tag && 6 !== a.tag && 18 !== a.tag;) {
				if (a.flags & 2) continue a;
				if (null === a.child || 4 === a.tag) continue a;
				else a.child.return = a, a = a.child;
			}
			if (!(a.flags & 2)) return a.stateNode;
		}
	}
	function Vj(a, b, c) {
		var d = a.tag;
		if (5 === d || 6 === d) a = a.stateNode, b ? 8 === c.nodeType ? c.parentNode.insertBefore(a, b) : c.insertBefore(a, b) : (8 === c.nodeType ? (b = c.parentNode, b.insertBefore(a, c)) : (b = c, b.appendChild(a)), c = c._reactRootContainer, null !== c && void 0 !== c || null !== b.onclick || (b.onclick = Bf));
		else if (4 !== d && (a = a.child, null !== a)) for (Vj(a, b, c), a = a.sibling; null !== a;) Vj(a, b, c), a = a.sibling;
	}
	function Wj(a, b, c) {
		var d = a.tag;
		if (5 === d || 6 === d) a = a.stateNode, b ? c.insertBefore(a, b) : c.appendChild(a);
		else if (4 !== d && (a = a.child, null !== a)) for (Wj(a, b, c), a = a.sibling; null !== a;) Wj(a, b, c), a = a.sibling;
	}
	var X = null, Xj = !1;
	function Yj(a, b, c) {
		for (c = c.child; null !== c;) Zj(a, b, c), c = c.sibling;
	}
	function Zj(a, b, c) {
		if (lc && "function" === typeof lc.onCommitFiberUnmount) try {
			lc.onCommitFiberUnmount(kc, c);
		} catch (h) {}
		switch (c.tag) {
			case 5: U || Lj(c, b);
			case 6:
				var d = X, e = Xj;
				X = null;
				Yj(a, b, c);
				X = d;
				Xj = e;
				null !== X && (Xj ? (a = X, c = c.stateNode, 8 === a.nodeType ? a.parentNode.removeChild(c) : a.removeChild(c)) : X.removeChild(c.stateNode));
				break;
			case 18:
				null !== X && (Xj ? (a = X, c = c.stateNode, 8 === a.nodeType ? Kf(a.parentNode, c) : 1 === a.nodeType && Kf(a, c), bd(a)) : Kf(X, c.stateNode));
				break;
			case 4:
				d = X;
				e = Xj;
				X = c.stateNode.containerInfo;
				Xj = !0;
				Yj(a, b, c);
				X = d;
				Xj = e;
				break;
			case 0:
			case 11:
			case 14:
			case 15:
				if (!U && (d = c.updateQueue, null !== d && (d = d.lastEffect, null !== d))) {
					e = d = d.next;
					do {
						var f = e, g = f.destroy;
						f = f.tag;
						void 0 !== g && (0 !== (f & 2) ? Mj(c, b, g) : 0 !== (f & 4) && Mj(c, b, g));
						e = e.next;
					} while (e !== d);
				}
				Yj(a, b, c);
				break;
			case 1:
				if (!U && (Lj(c, b), d = c.stateNode, "function" === typeof d.componentWillUnmount)) try {
					d.props = c.memoizedProps, d.state = c.memoizedState, d.componentWillUnmount();
				} catch (h) {
					W(c, b, h);
				}
				Yj(a, b, c);
				break;
			case 21:
				Yj(a, b, c);
				break;
			case 22:
				c.mode & 1 ? (U = (d = U) || null !== c.memoizedState, Yj(a, b, c), U = d) : Yj(a, b, c);
				break;
			default: Yj(a, b, c);
		}
	}
	function ak(a) {
		var b = a.updateQueue;
		if (null !== b) {
			a.updateQueue = null;
			var c = a.stateNode;
			null === c && (c = a.stateNode = new Kj());
			b.forEach(function(b) {
				var d = bk.bind(null, a, b);
				c.has(b) || (c.add(b), b.then(d, d));
			});
		}
	}
	function ck(a, b) {
		var c = b.deletions;
		if (null !== c) for (var d = 0; d < c.length; d++) {
			var e = c[d];
			try {
				var f = a, g = b, h = g;
				a: for (; null !== h;) {
					switch (h.tag) {
						case 5:
							X = h.stateNode;
							Xj = !1;
							break a;
						case 3:
							X = h.stateNode.containerInfo;
							Xj = !0;
							break a;
						case 4:
							X = h.stateNode.containerInfo;
							Xj = !0;
							break a;
					}
					h = h.return;
				}
				if (null === X) throw Error(p(160));
				Zj(f, g, e);
				X = null;
				Xj = !1;
				var k = e.alternate;
				null !== k && (k.return = null);
				e.return = null;
			} catch (l) {
				W(e, b, l);
			}
		}
		if (b.subtreeFlags & 12854) for (b = b.child; null !== b;) dk(b, a), b = b.sibling;
	}
	function dk(a, b) {
		var c = a.alternate, d = a.flags;
		switch (a.tag) {
			case 0:
			case 11:
			case 14:
			case 15:
				ck(b, a);
				ek(a);
				if (d & 4) {
					try {
						Pj(3, a, a.return), Qj(3, a);
					} catch (t) {
						W(a, a.return, t);
					}
					try {
						Pj(5, a, a.return);
					} catch (t) {
						W(a, a.return, t);
					}
				}
				break;
			case 1:
				ck(b, a);
				ek(a);
				d & 512 && null !== c && Lj(c, c.return);
				break;
			case 5:
				ck(b, a);
				ek(a);
				d & 512 && null !== c && Lj(c, c.return);
				if (a.flags & 32) {
					var e = a.stateNode;
					try {
						ob(e, "");
					} catch (t) {
						W(a, a.return, t);
					}
				}
				if (d & 4 && (e = a.stateNode, null != e)) {
					var f = a.memoizedProps, g = null !== c ? c.memoizedProps : f, h = a.type, k = a.updateQueue;
					a.updateQueue = null;
					if (null !== k) try {
						"input" === h && "radio" === f.type && null != f.name && ab(e, f);
						vb(h, g);
						var l = vb(h, f);
						for (g = 0; g < k.length; g += 2) {
							var m = k[g], q = k[g + 1];
							"style" === m ? sb(e, q) : "dangerouslySetInnerHTML" === m ? nb(e, q) : "children" === m ? ob(e, q) : ta(e, m, q, l);
						}
						switch (h) {
							case "input":
								bb(e, f);
								break;
							case "textarea":
								ib(e, f);
								break;
							case "select":
								var r = e._wrapperState.wasMultiple;
								e._wrapperState.wasMultiple = !!f.multiple;
								var y = f.value;
								null != y ? fb(e, !!f.multiple, y, !1) : r !== !!f.multiple && (null != f.defaultValue ? fb(e, !!f.multiple, f.defaultValue, !0) : fb(e, !!f.multiple, f.multiple ? [] : "", !1));
						}
						e[Pf] = f;
					} catch (t) {
						W(a, a.return, t);
					}
				}
				break;
			case 6:
				ck(b, a);
				ek(a);
				if (d & 4) {
					if (null === a.stateNode) throw Error(p(162));
					e = a.stateNode;
					f = a.memoizedProps;
					try {
						e.nodeValue = f;
					} catch (t) {
						W(a, a.return, t);
					}
				}
				break;
			case 3:
				ck(b, a);
				ek(a);
				if (d & 4 && null !== c && c.memoizedState.isDehydrated) try {
					bd(b.containerInfo);
				} catch (t) {
					W(a, a.return, t);
				}
				break;
			case 4:
				ck(b, a);
				ek(a);
				break;
			case 13:
				ck(b, a);
				ek(a);
				e = a.child;
				e.flags & 8192 && (f = null !== e.memoizedState, e.stateNode.isHidden = f, !f || null !== e.alternate && null !== e.alternate.memoizedState || (fk = B()));
				d & 4 && ak(a);
				break;
			case 22:
				m = null !== c && null !== c.memoizedState;
				a.mode & 1 ? (U = (l = U) || m, ck(b, a), U = l) : ck(b, a);
				ek(a);
				if (d & 8192) {
					l = null !== a.memoizedState;
					if ((a.stateNode.isHidden = l) && !m && 0 !== (a.mode & 1)) for (V = a, m = a.child; null !== m;) {
						for (q = V = m; null !== V;) {
							r = V;
							y = r.child;
							switch (r.tag) {
								case 0:
								case 11:
								case 14:
								case 15:
									Pj(4, r, r.return);
									break;
								case 1:
									Lj(r, r.return);
									var n = r.stateNode;
									if ("function" === typeof n.componentWillUnmount) {
										d = r;
										c = r.return;
										try {
											b = d, n.props = b.memoizedProps, n.state = b.memoizedState, n.componentWillUnmount();
										} catch (t) {
											W(d, c, t);
										}
									}
									break;
								case 5:
									Lj(r, r.return);
									break;
								case 22: if (null !== r.memoizedState) {
									gk(q);
									continue;
								}
							}
							null !== y ? (y.return = r, V = y) : gk(q);
						}
						m = m.sibling;
					}
					a: for (m = null, q = a;;) {
						if (5 === q.tag) {
							if (null === m) {
								m = q;
								try {
									e = q.stateNode, l ? (f = e.style, "function" === typeof f.setProperty ? f.setProperty("display", "none", "important") : f.display = "none") : (h = q.stateNode, k = q.memoizedProps.style, g = void 0 !== k && null !== k && k.hasOwnProperty("display") ? k.display : null, h.style.display = rb("display", g));
								} catch (t) {
									W(a, a.return, t);
								}
							}
						} else if (6 === q.tag) {
							if (null === m) try {
								q.stateNode.nodeValue = l ? "" : q.memoizedProps;
							} catch (t) {
								W(a, a.return, t);
							}
						} else if ((22 !== q.tag && 23 !== q.tag || null === q.memoizedState || q === a) && null !== q.child) {
							q.child.return = q;
							q = q.child;
							continue;
						}
						if (q === a) break a;
						for (; null === q.sibling;) {
							if (null === q.return || q.return === a) break a;
							m === q && (m = null);
							q = q.return;
						}
						m === q && (m = null);
						q.sibling.return = q.return;
						q = q.sibling;
					}
				}
				break;
			case 19:
				ck(b, a);
				ek(a);
				d & 4 && ak(a);
				break;
			case 21: break;
			default: ck(b, a), ek(a);
		}
	}
	function ek(a) {
		var b = a.flags;
		if (b & 2) {
			try {
				a: {
					for (var c = a.return; null !== c;) {
						if (Tj(c)) {
							var d = c;
							break a;
						}
						c = c.return;
					}
					throw Error(p(160));
				}
				switch (d.tag) {
					case 5:
						var e = d.stateNode;
						d.flags & 32 && (ob(e, ""), d.flags &= -33);
						Wj(a, Uj(a), e);
						break;
					case 3:
					case 4:
						var g = d.stateNode.containerInfo;
						Vj(a, Uj(a), g);
						break;
					default: throw Error(p(161));
				}
			} catch (k) {
				W(a, a.return, k);
			}
			a.flags &= -3;
		}
		b & 4096 && (a.flags &= -4097);
	}
	function hk(a, b, c) {
		V = a;
		ik(a, b, c);
	}
	function ik(a, b, c) {
		for (var d = 0 !== (a.mode & 1); null !== V;) {
			var e = V, f = e.child;
			if (22 === e.tag && d) {
				var g = null !== e.memoizedState || Jj;
				if (!g) {
					var h = e.alternate, k = null !== h && null !== h.memoizedState || U;
					h = Jj;
					var l = U;
					Jj = g;
					if ((U = k) && !l) for (V = e; null !== V;) g = V, k = g.child, 22 === g.tag && null !== g.memoizedState ? jk(e) : null !== k ? (k.return = g, V = k) : jk(e);
					for (; null !== f;) V = f, ik(f, b, c), f = f.sibling;
					V = e;
					Jj = h;
					U = l;
				}
				kk(a, b, c);
			} else 0 !== (e.subtreeFlags & 8772) && null !== f ? (f.return = e, V = f) : kk(a, b, c);
		}
	}
	function kk(a) {
		for (; null !== V;) {
			var b = V;
			if (0 !== (b.flags & 8772)) {
				var c = b.alternate;
				try {
					if (0 !== (b.flags & 8772)) switch (b.tag) {
						case 0:
						case 11:
						case 15:
							U || Qj(5, b);
							break;
						case 1:
							var d = b.stateNode;
							if (b.flags & 4 && !U) if (null === c) d.componentDidMount();
							else {
								var e = b.elementType === b.type ? c.memoizedProps : Ci(b.type, c.memoizedProps);
								d.componentDidUpdate(e, c.memoizedState, d.__reactInternalSnapshotBeforeUpdate);
							}
							var f = b.updateQueue;
							null !== f && sh(b, f, d);
							break;
						case 3:
							var g = b.updateQueue;
							if (null !== g) {
								c = null;
								if (null !== b.child) switch (b.child.tag) {
									case 5:
										c = b.child.stateNode;
										break;
									case 1: c = b.child.stateNode;
								}
								sh(b, g, c);
							}
							break;
						case 5:
							var h = b.stateNode;
							if (null === c && b.flags & 4) {
								c = h;
								var k = b.memoizedProps;
								switch (b.type) {
									case "button":
									case "input":
									case "select":
									case "textarea":
										k.autoFocus && c.focus();
										break;
									case "img": k.src && (c.src = k.src);
								}
							}
							break;
						case 6: break;
						case 4: break;
						case 12: break;
						case 13:
							if (null === b.memoizedState) {
								var l = b.alternate;
								if (null !== l) {
									var m = l.memoizedState;
									if (null !== m) {
										var q = m.dehydrated;
										null !== q && bd(q);
									}
								}
							}
							break;
						case 19:
						case 17:
						case 21:
						case 22:
						case 23:
						case 25: break;
						default: throw Error(p(163));
					}
					U || b.flags & 512 && Rj(b);
				} catch (r) {
					W(b, b.return, r);
				}
			}
			if (b === a) {
				V = null;
				break;
			}
			c = b.sibling;
			if (null !== c) {
				c.return = b.return;
				V = c;
				break;
			}
			V = b.return;
		}
	}
	function gk(a) {
		for (; null !== V;) {
			var b = V;
			if (b === a) {
				V = null;
				break;
			}
			var c = b.sibling;
			if (null !== c) {
				c.return = b.return;
				V = c;
				break;
			}
			V = b.return;
		}
	}
	function jk(a) {
		for (; null !== V;) {
			var b = V;
			try {
				switch (b.tag) {
					case 0:
					case 11:
					case 15:
						var c = b.return;
						try {
							Qj(4, b);
						} catch (k) {
							W(b, c, k);
						}
						break;
					case 1:
						var d = b.stateNode;
						if ("function" === typeof d.componentDidMount) {
							var e = b.return;
							try {
								d.componentDidMount();
							} catch (k) {
								W(b, e, k);
							}
						}
						var f = b.return;
						try {
							Rj(b);
						} catch (k) {
							W(b, f, k);
						}
						break;
					case 5:
						var g = b.return;
						try {
							Rj(b);
						} catch (k) {
							W(b, g, k);
						}
				}
			} catch (k) {
				W(b, b.return, k);
			}
			if (b === a) {
				V = null;
				break;
			}
			var h = b.sibling;
			if (null !== h) {
				h.return = b.return;
				V = h;
				break;
			}
			V = b.return;
		}
	}
	var lk = Math.ceil, mk = ua.ReactCurrentDispatcher, nk = ua.ReactCurrentOwner, ok = ua.ReactCurrentBatchConfig, K = 0, Q = null, Y = null, Z = 0, fj = 0, ej = Uf(0), T = 0, pk = null, rh = 0, qk = 0, rk = 0, sk = null, tk = null, fk = 0, Gj = Infinity, uk = null, Oi = !1, Pi = null, Ri = null, vk = !1, wk = null, xk = 0, yk = 0, zk = null, Ak = -1, Bk = 0;
	function R() {
		return 0 !== (K & 6) ? B() : -1 !== Ak ? Ak : Ak = B();
	}
	function yi(a) {
		if (0 === (a.mode & 1)) return 1;
		if (0 !== (K & 2) && 0 !== Z) return Z & -Z;
		if (null !== Kg.transition) return 0 === Bk && (Bk = yc()), Bk;
		a = C;
		if (0 !== a) return a;
		a = window.event;
		a = void 0 === a ? 16 : jd(a.type);
		return a;
	}
	function gi(a, b, c, d) {
		if (50 < yk) throw yk = 0, zk = null, Error(p(185));
		Ac(a, c, d);
		if (0 === (K & 2) || a !== Q) a === Q && (0 === (K & 2) && (qk |= c), 4 === T && Ck(a, Z)), Dk(a, d), 1 === c && 0 === K && 0 === (b.mode & 1) && (Gj = B() + 500, fg && jg());
	}
	function Dk(a, b) {
		var c = a.callbackNode;
		wc(a, b);
		var d = uc(a, a === Q ? Z : 0);
		if (0 === d) null !== c && bc(c), a.callbackNode = null, a.callbackPriority = 0;
		else if (b = d & -d, a.callbackPriority !== b) {
			null != c && bc(c);
			if (1 === b) 0 === a.tag ? ig(Ek.bind(null, a)) : hg(Ek.bind(null, a)), Jf(function() {
				0 === (K & 6) && jg();
			}), c = null;
			else {
				switch (Dc(d)) {
					case 1:
						c = fc;
						break;
					case 4:
						c = gc;
						break;
					case 16:
						c = hc;
						break;
					case 536870912:
						c = jc;
						break;
					default: c = hc;
				}
				c = Fk(c, Gk.bind(null, a));
			}
			a.callbackPriority = b;
			a.callbackNode = c;
		}
	}
	function Gk(a, b) {
		Ak = -1;
		Bk = 0;
		if (0 !== (K & 6)) throw Error(p(327));
		var c = a.callbackNode;
		if (Hk() && a.callbackNode !== c) return null;
		var d = uc(a, a === Q ? Z : 0);
		if (0 === d) return null;
		if (0 !== (d & 30) || 0 !== (d & a.expiredLanes) || b) b = Ik(a, d);
		else {
			b = d;
			var e = K;
			K |= 2;
			var f = Jk();
			if (Q !== a || Z !== b) uk = null, Gj = B() + 500, Kk(a, b);
			do
				try {
					Lk();
					break;
				} catch (h) {
					Mk(a, h);
				}
			while (1);
			$g();
			mk.current = f;
			K = e;
			null !== Y ? b = 0 : (Q = null, Z = 0, b = T);
		}
		if (0 !== b) {
			2 === b && (e = xc(a), 0 !== e && (d = e, b = Nk(a, e)));
			if (1 === b) throw c = pk, Kk(a, 0), Ck(a, d), Dk(a, B()), c;
			if (6 === b) Ck(a, d);
			else {
				e = a.current.alternate;
				if (0 === (d & 30) && !Ok(e) && (b = Ik(a, d), 2 === b && (f = xc(a), 0 !== f && (d = f, b = Nk(a, f))), 1 === b)) throw c = pk, Kk(a, 0), Ck(a, d), Dk(a, B()), c;
				a.finishedWork = e;
				a.finishedLanes = d;
				switch (b) {
					case 0:
					case 1: throw Error(p(345));
					case 2:
						Pk(a, tk, uk);
						break;
					case 3:
						Ck(a, d);
						if ((d & 130023424) === d && (b = fk + 500 - B(), 10 < b)) {
							if (0 !== uc(a, 0)) break;
							e = a.suspendedLanes;
							if ((e & d) !== d) {
								R();
								a.pingedLanes |= a.suspendedLanes & e;
								break;
							}
							a.timeoutHandle = Ff(Pk.bind(null, a, tk, uk), b);
							break;
						}
						Pk(a, tk, uk);
						break;
					case 4:
						Ck(a, d);
						if ((d & 4194240) === d) break;
						b = a.eventTimes;
						for (e = -1; 0 < d;) {
							var g = 31 - oc(d);
							f = 1 << g;
							g = b[g];
							g > e && (e = g);
							d &= ~f;
						}
						d = e;
						d = B() - d;
						d = (120 > d ? 120 : 480 > d ? 480 : 1080 > d ? 1080 : 1920 > d ? 1920 : 3e3 > d ? 3e3 : 4320 > d ? 4320 : 1960 * lk(d / 1960)) - d;
						if (10 < d) {
							a.timeoutHandle = Ff(Pk.bind(null, a, tk, uk), d);
							break;
						}
						Pk(a, tk, uk);
						break;
					case 5:
						Pk(a, tk, uk);
						break;
					default: throw Error(p(329));
				}
			}
		}
		Dk(a, B());
		return a.callbackNode === c ? Gk.bind(null, a) : null;
	}
	function Nk(a, b) {
		var c = sk;
		a.current.memoizedState.isDehydrated && (Kk(a, b).flags |= 256);
		a = Ik(a, b);
		2 !== a && (b = tk, tk = c, null !== b && Fj(b));
		return a;
	}
	function Fj(a) {
		null === tk ? tk = a : tk.push.apply(tk, a);
	}
	function Ok(a) {
		for (var b = a;;) {
			if (b.flags & 16384) {
				var c = b.updateQueue;
				if (null !== c && (c = c.stores, null !== c)) for (var d = 0; d < c.length; d++) {
					var e = c[d], f = e.getSnapshot;
					e = e.value;
					try {
						if (!He(f(), e)) return !1;
					} catch (g) {
						return !1;
					}
				}
			}
			c = b.child;
			if (b.subtreeFlags & 16384 && null !== c) c.return = b, b = c;
			else {
				if (b === a) break;
				for (; null === b.sibling;) {
					if (null === b.return || b.return === a) return !0;
					b = b.return;
				}
				b.sibling.return = b.return;
				b = b.sibling;
			}
		}
		return !0;
	}
	function Ck(a, b) {
		b &= ~rk;
		b &= ~qk;
		a.suspendedLanes |= b;
		a.pingedLanes &= ~b;
		for (a = a.expirationTimes; 0 < b;) {
			var c = 31 - oc(b), d = 1 << c;
			a[c] = -1;
			b &= ~d;
		}
	}
	function Ek(a) {
		if (0 !== (K & 6)) throw Error(p(327));
		Hk();
		var b = uc(a, 0);
		if (0 === (b & 1)) return Dk(a, B()), null;
		var c = Ik(a, b);
		if (0 !== a.tag && 2 === c) {
			var d = xc(a);
			0 !== d && (b = d, c = Nk(a, d));
		}
		if (1 === c) throw c = pk, Kk(a, 0), Ck(a, b), Dk(a, B()), c;
		if (6 === c) throw Error(p(345));
		a.finishedWork = a.current.alternate;
		a.finishedLanes = b;
		Pk(a, tk, uk);
		Dk(a, B());
		return null;
	}
	function Qk(a, b) {
		var c = K;
		K |= 1;
		try {
			return a(b);
		} finally {
			K = c, 0 === K && (Gj = B() + 500, fg && jg());
		}
	}
	function Rk(a) {
		null !== wk && 0 === wk.tag && 0 === (K & 6) && Hk();
		var b = K;
		K |= 1;
		var c = ok.transition, d = C;
		try {
			if (ok.transition = null, C = 1, a) return a();
		} finally {
			C = d, ok.transition = c, K = b, 0 === (K & 6) && jg();
		}
	}
	function Hj() {
		fj = ej.current;
		E(ej);
	}
	function Kk(a, b) {
		a.finishedWork = null;
		a.finishedLanes = 0;
		var c = a.timeoutHandle;
		-1 !== c && (a.timeoutHandle = -1, Gf(c));
		if (null !== Y) for (c = Y.return; null !== c;) {
			var d = c;
			wg(d);
			switch (d.tag) {
				case 1:
					d = d.type.childContextTypes;
					null !== d && void 0 !== d && $f();
					break;
				case 3:
					zh();
					E(Wf);
					E(H);
					Eh();
					break;
				case 5:
					Bh(d);
					break;
				case 4:
					zh();
					break;
				case 13:
					E(L);
					break;
				case 19:
					E(L);
					break;
				case 10:
					ah(d.type._context);
					break;
				case 22:
				case 23: Hj();
			}
			c = c.return;
		}
		Q = a;
		Y = a = Pg(a.current, null);
		Z = fj = b;
		T = 0;
		pk = null;
		rk = qk = rh = 0;
		tk = sk = null;
		if (null !== fh) {
			for (b = 0; b < fh.length; b++) if (c = fh[b], d = c.interleaved, null !== d) {
				c.interleaved = null;
				var e = d.next, f = c.pending;
				if (null !== f) {
					var g = f.next;
					f.next = e;
					d.next = g;
				}
				c.pending = d;
			}
			fh = null;
		}
		return a;
	}
	function Mk(a, b) {
		do {
			var c = Y;
			try {
				$g();
				Fh.current = Rh;
				if (Ih) {
					for (var d = M.memoizedState; null !== d;) {
						var e = d.queue;
						null !== e && (e.pending = null);
						d = d.next;
					}
					Ih = !1;
				}
				Hh = 0;
				O = N = M = null;
				Jh = !1;
				Kh = 0;
				nk.current = null;
				if (null === c || null === c.return) {
					T = 1;
					pk = b;
					Y = null;
					break;
				}
				a: {
					var f = a, g = c.return, h = c, k = b;
					b = Z;
					h.flags |= 32768;
					if (null !== k && "object" === typeof k && "function" === typeof k.then) {
						var l = k, m = h, q = m.tag;
						if (0 === (m.mode & 1) && (0 === q || 11 === q || 15 === q)) {
							var r = m.alternate;
							r ? (m.updateQueue = r.updateQueue, m.memoizedState = r.memoizedState, m.lanes = r.lanes) : (m.updateQueue = null, m.memoizedState = null);
						}
						var y = Ui(g);
						if (null !== y) {
							y.flags &= -257;
							Vi(y, g, h, f, b);
							y.mode & 1 && Si(f, l, b);
							b = y;
							k = l;
							var n = b.updateQueue;
							if (null === n) {
								var t = /* @__PURE__ */ new Set();
								t.add(k);
								b.updateQueue = t;
							} else n.add(k);
							break a;
						} else {
							if (0 === (b & 1)) {
								Si(f, l, b);
								tj();
								break a;
							}
							k = Error(p(426));
						}
					} else if (I && h.mode & 1) {
						var J = Ui(g);
						if (null !== J) {
							0 === (J.flags & 65536) && (J.flags |= 256);
							Vi(J, g, h, f, b);
							Jg(Ji(k, h));
							break a;
						}
					}
					f = k = Ji(k, h);
					4 !== T && (T = 2);
					null === sk ? sk = [f] : sk.push(f);
					f = g;
					do {
						switch (f.tag) {
							case 3:
								f.flags |= 65536;
								b &= -b;
								f.lanes |= b;
								var x = Ni(f, k, b);
								ph(f, x);
								break a;
							case 1:
								h = k;
								var w = f.type, u = f.stateNode;
								if (0 === (f.flags & 128) && ("function" === typeof w.getDerivedStateFromError || null !== u && "function" === typeof u.componentDidCatch && (null === Ri || !Ri.has(u)))) {
									f.flags |= 65536;
									b &= -b;
									f.lanes |= b;
									var F = Qi(f, h, b);
									ph(f, F);
									break a;
								}
						}
						f = f.return;
					} while (null !== f);
				}
				Sk(c);
			} catch (na) {
				b = na;
				Y === c && null !== c && (Y = c = c.return);
				continue;
			}
			break;
		} while (1);
	}
	function Jk() {
		var a = mk.current;
		mk.current = Rh;
		return null === a ? Rh : a;
	}
	function tj() {
		if (0 === T || 3 === T || 2 === T) T = 4;
		null === Q || 0 === (rh & 268435455) && 0 === (qk & 268435455) || Ck(Q, Z);
	}
	function Ik(a, b) {
		var c = K;
		K |= 2;
		var d = Jk();
		if (Q !== a || Z !== b) uk = null, Kk(a, b);
		do
			try {
				Tk();
				break;
			} catch (e) {
				Mk(a, e);
			}
		while (1);
		$g();
		K = c;
		mk.current = d;
		if (null !== Y) throw Error(p(261));
		Q = null;
		Z = 0;
		return T;
	}
	function Tk() {
		for (; null !== Y;) Uk(Y);
	}
	function Lk() {
		for (; null !== Y && !cc();) Uk(Y);
	}
	function Uk(a) {
		var b = Vk(a.alternate, a, fj);
		a.memoizedProps = a.pendingProps;
		null === b ? Sk(a) : Y = b;
		nk.current = null;
	}
	function Sk(a) {
		var b = a;
		do {
			var c = b.alternate;
			a = b.return;
			if (0 === (b.flags & 32768)) {
				if (c = Ej(c, b, fj), null !== c) {
					Y = c;
					return;
				}
			} else {
				c = Ij(c, b);
				if (null !== c) {
					c.flags &= 32767;
					Y = c;
					return;
				}
				if (null !== a) a.flags |= 32768, a.subtreeFlags = 0, a.deletions = null;
				else {
					T = 6;
					Y = null;
					return;
				}
			}
			b = b.sibling;
			if (null !== b) {
				Y = b;
				return;
			}
			Y = b = a;
		} while (null !== b);
		0 === T && (T = 5);
	}
	function Pk(a, b, c) {
		var d = C, e = ok.transition;
		try {
			ok.transition = null, C = 1, Wk(a, b, c, d);
		} finally {
			ok.transition = e, C = d;
		}
		return null;
	}
	function Wk(a, b, c, d) {
		do
			Hk();
		while (null !== wk);
		if (0 !== (K & 6)) throw Error(p(327));
		c = a.finishedWork;
		var e = a.finishedLanes;
		if (null === c) return null;
		a.finishedWork = null;
		a.finishedLanes = 0;
		if (c === a.current) throw Error(p(177));
		a.callbackNode = null;
		a.callbackPriority = 0;
		var f = c.lanes | c.childLanes;
		Bc(a, f);
		a === Q && (Y = Q = null, Z = 0);
		0 === (c.subtreeFlags & 2064) && 0 === (c.flags & 2064) || vk || (vk = !0, Fk(hc, function() {
			Hk();
			return null;
		}));
		f = 0 !== (c.flags & 15990);
		if (0 !== (c.subtreeFlags & 15990) || f) {
			f = ok.transition;
			ok.transition = null;
			var g = C;
			C = 1;
			var h = K;
			K |= 4;
			nk.current = null;
			Oj(a, c);
			dk(c, a);
			Oe(Df);
			dd = !!Cf;
			Df = Cf = null;
			a.current = c;
			hk(c, a, e);
			dc();
			K = h;
			C = g;
			ok.transition = f;
		} else a.current = c;
		vk && (vk = !1, wk = a, xk = e);
		f = a.pendingLanes;
		0 === f && (Ri = null);
		mc(c.stateNode, d);
		Dk(a, B());
		if (null !== b) for (d = a.onRecoverableError, c = 0; c < b.length; c++) e = b[c], d(e.value, {
			componentStack: e.stack,
			digest: e.digest
		});
		if (Oi) throw Oi = !1, a = Pi, Pi = null, a;
		0 !== (xk & 1) && 0 !== a.tag && Hk();
		f = a.pendingLanes;
		0 !== (f & 1) ? a === zk ? yk++ : (yk = 0, zk = a) : yk = 0;
		jg();
		return null;
	}
	function Hk() {
		if (null !== wk) {
			var a = Dc(xk), b = ok.transition, c = C;
			try {
				ok.transition = null;
				C = 16 > a ? 16 : a;
				if (null === wk) var d = !1;
				else {
					a = wk;
					wk = null;
					xk = 0;
					if (0 !== (K & 6)) throw Error(p(331));
					var e = K;
					K |= 4;
					for (V = a.current; null !== V;) {
						var f = V, g = f.child;
						if (0 !== (V.flags & 16)) {
							var h = f.deletions;
							if (null !== h) {
								for (var k = 0; k < h.length; k++) {
									var l = h[k];
									for (V = l; null !== V;) {
										var m = V;
										switch (m.tag) {
											case 0:
											case 11:
											case 15: Pj(8, m, f);
										}
										var q = m.child;
										if (null !== q) q.return = m, V = q;
										else for (; null !== V;) {
											m = V;
											var r = m.sibling, y = m.return;
											Sj(m);
											if (m === l) {
												V = null;
												break;
											}
											if (null !== r) {
												r.return = y;
												V = r;
												break;
											}
											V = y;
										}
									}
								}
								var n = f.alternate;
								if (null !== n) {
									var t = n.child;
									if (null !== t) {
										n.child = null;
										do {
											var J = t.sibling;
											t.sibling = null;
											t = J;
										} while (null !== t);
									}
								}
								V = f;
							}
						}
						if (0 !== (f.subtreeFlags & 2064) && null !== g) g.return = f, V = g;
						else b: for (; null !== V;) {
							f = V;
							if (0 !== (f.flags & 2048)) switch (f.tag) {
								case 0:
								case 11:
								case 15: Pj(9, f, f.return);
							}
							var x = f.sibling;
							if (null !== x) {
								x.return = f.return;
								V = x;
								break b;
							}
							V = f.return;
						}
					}
					var w = a.current;
					for (V = w; null !== V;) {
						g = V;
						var u = g.child;
						if (0 !== (g.subtreeFlags & 2064) && null !== u) u.return = g, V = u;
						else b: for (g = w; null !== V;) {
							h = V;
							if (0 !== (h.flags & 2048)) try {
								switch (h.tag) {
									case 0:
									case 11:
									case 15: Qj(9, h);
								}
							} catch (na) {
								W(h, h.return, na);
							}
							if (h === g) {
								V = null;
								break b;
							}
							var F = h.sibling;
							if (null !== F) {
								F.return = h.return;
								V = F;
								break b;
							}
							V = h.return;
						}
					}
					K = e;
					jg();
					if (lc && "function" === typeof lc.onPostCommitFiberRoot) try {
						lc.onPostCommitFiberRoot(kc, a);
					} catch (na) {}
					d = !0;
				}
				return d;
			} finally {
				C = c, ok.transition = b;
			}
		}
		return !1;
	}
	function Xk(a, b, c) {
		b = Ji(c, b);
		b = Ni(a, b, 1);
		a = nh(a, b, 1);
		b = R();
		null !== a && (Ac(a, 1, b), Dk(a, b));
	}
	function W(a, b, c) {
		if (3 === a.tag) Xk(a, a, c);
		else for (; null !== b;) {
			if (3 === b.tag) {
				Xk(b, a, c);
				break;
			} else if (1 === b.tag) {
				var d = b.stateNode;
				if ("function" === typeof b.type.getDerivedStateFromError || "function" === typeof d.componentDidCatch && (null === Ri || !Ri.has(d))) {
					a = Ji(c, a);
					a = Qi(b, a, 1);
					b = nh(b, a, 1);
					a = R();
					null !== b && (Ac(b, 1, a), Dk(b, a));
					break;
				}
			}
			b = b.return;
		}
	}
	function Ti(a, b, c) {
		var d = a.pingCache;
		null !== d && d.delete(b);
		b = R();
		a.pingedLanes |= a.suspendedLanes & c;
		Q === a && (Z & c) === c && (4 === T || 3 === T && (Z & 130023424) === Z && 500 > B() - fk ? Kk(a, 0) : rk |= c);
		Dk(a, b);
	}
	function Yk(a, b) {
		0 === b && (0 === (a.mode & 1) ? b = 1 : (b = sc, sc <<= 1, 0 === (sc & 130023424) && (sc = 4194304)));
		var c = R();
		a = ih(a, b);
		null !== a && (Ac(a, b, c), Dk(a, c));
	}
	function uj(a) {
		var b = a.memoizedState, c = 0;
		null !== b && (c = b.retryLane);
		Yk(a, c);
	}
	function bk(a, b) {
		var c = 0;
		switch (a.tag) {
			case 13:
				var d = a.stateNode;
				var e = a.memoizedState;
				null !== e && (c = e.retryLane);
				break;
			case 19:
				d = a.stateNode;
				break;
			default: throw Error(p(314));
		}
		null !== d && d.delete(b);
		Yk(a, c);
	}
	var Vk = function(a, b, c) {
		if (null !== a) if (a.memoizedProps !== b.pendingProps || Wf.current) dh = !0;
		else {
			if (0 === (a.lanes & c) && 0 === (b.flags & 128)) return dh = !1, yj(a, b, c);
			dh = 0 !== (a.flags & 131072) ? !0 : !1;
		}
		else dh = !1, I && 0 !== (b.flags & 1048576) && ug(b, ng, b.index);
		b.lanes = 0;
		switch (b.tag) {
			case 2:
				var d = b.type;
				ij(a, b);
				a = b.pendingProps;
				var e = Yf(b, H.current);
				ch(b, c);
				e = Nh(null, b, d, a, e, c);
				var f = Sh();
				b.flags |= 1;
				"object" === typeof e && null !== e && "function" === typeof e.render && void 0 === e.$$typeof ? (b.tag = 1, b.memoizedState = null, b.updateQueue = null, Zf(d) ? (f = !0, cg(b)) : f = !1, b.memoizedState = null !== e.state && void 0 !== e.state ? e.state : null, kh(b), e.updater = Ei, b.stateNode = e, e._reactInternals = b, Ii(b, d, a, c), b = jj(null, b, d, !0, f, c)) : (b.tag = 0, I && f && vg(b), Xi(null, b, e, c), b = b.child);
				return b;
			case 16:
				d = b.elementType;
				a: {
					ij(a, b);
					a = b.pendingProps;
					e = d._init;
					d = e(d._payload);
					b.type = d;
					e = b.tag = Zk(d);
					a = Ci(d, a);
					switch (e) {
						case 0:
							b = cj(null, b, d, a, c);
							break a;
						case 1:
							b = hj(null, b, d, a, c);
							break a;
						case 11:
							b = Yi(null, b, d, a, c);
							break a;
						case 14:
							b = $i(null, b, d, Ci(d.type, a), c);
							break a;
					}
					throw Error(p(306, d, ""));
				}
				return b;
			case 0: return d = b.type, e = b.pendingProps, e = b.elementType === d ? e : Ci(d, e), cj(a, b, d, e, c);
			case 1: return d = b.type, e = b.pendingProps, e = b.elementType === d ? e : Ci(d, e), hj(a, b, d, e, c);
			case 3:
				a: {
					kj(b);
					if (null === a) throw Error(p(387));
					d = b.pendingProps;
					f = b.memoizedState;
					e = f.element;
					lh(a, b);
					qh(b, d, null, c);
					var g = b.memoizedState;
					d = g.element;
					if (f.isDehydrated) if (f = {
						element: d,
						isDehydrated: !1,
						cache: g.cache,
						pendingSuspenseBoundaries: g.pendingSuspenseBoundaries,
						transitions: g.transitions
					}, b.updateQueue.baseState = f, b.memoizedState = f, b.flags & 256) {
						e = Ji(Error(p(423)), b);
						b = lj(a, b, d, c, e);
						break a;
					} else if (d !== e) {
						e = Ji(Error(p(424)), b);
						b = lj(a, b, d, c, e);
						break a;
					} else for (yg = Lf(b.stateNode.containerInfo.firstChild), xg = b, I = !0, zg = null, c = Vg(b, null, d, c), b.child = c; c;) c.flags = c.flags & -3 | 4096, c = c.sibling;
					else {
						Ig();
						if (d === e) {
							b = Zi(a, b, c);
							break a;
						}
						Xi(a, b, d, c);
					}
					b = b.child;
				}
				return b;
			case 5: return Ah(b), null === a && Eg(b), d = b.type, e = b.pendingProps, f = null !== a ? a.memoizedProps : null, g = e.children, Ef(d, e) ? g = null : null !== f && Ef(d, f) && (b.flags |= 32), gj(a, b), Xi(a, b, g, c), b.child;
			case 6: return null === a && Eg(b), null;
			case 13: return oj(a, b, c);
			case 4: return yh(b, b.stateNode.containerInfo), d = b.pendingProps, null === a ? b.child = Ug(b, null, d, c) : Xi(a, b, d, c), b.child;
			case 11: return d = b.type, e = b.pendingProps, e = b.elementType === d ? e : Ci(d, e), Yi(a, b, d, e, c);
			case 7: return Xi(a, b, b.pendingProps, c), b.child;
			case 8: return Xi(a, b, b.pendingProps.children, c), b.child;
			case 12: return Xi(a, b, b.pendingProps.children, c), b.child;
			case 10:
				a: {
					d = b.type._context;
					e = b.pendingProps;
					f = b.memoizedProps;
					g = e.value;
					G(Wg, d._currentValue);
					d._currentValue = g;
					if (null !== f) if (He(f.value, g)) {
						if (f.children === e.children && !Wf.current) {
							b = Zi(a, b, c);
							break a;
						}
					} else for (f = b.child, null !== f && (f.return = b); null !== f;) {
						var h = f.dependencies;
						if (null !== h) {
							g = f.child;
							for (var k = h.firstContext; null !== k;) {
								if (k.context === d) {
									if (1 === f.tag) {
										k = mh(-1, c & -c);
										k.tag = 2;
										var l = f.updateQueue;
										if (null !== l) {
											l = l.shared;
											var m = l.pending;
											null === m ? k.next = k : (k.next = m.next, m.next = k);
											l.pending = k;
										}
									}
									f.lanes |= c;
									k = f.alternate;
									null !== k && (k.lanes |= c);
									bh(f.return, c, b);
									h.lanes |= c;
									break;
								}
								k = k.next;
							}
						} else if (10 === f.tag) g = f.type === b.type ? null : f.child;
						else if (18 === f.tag) {
							g = f.return;
							if (null === g) throw Error(p(341));
							g.lanes |= c;
							h = g.alternate;
							null !== h && (h.lanes |= c);
							bh(g, c, b);
							g = f.sibling;
						} else g = f.child;
						if (null !== g) g.return = f;
						else for (g = f; null !== g;) {
							if (g === b) {
								g = null;
								break;
							}
							f = g.sibling;
							if (null !== f) {
								f.return = g.return;
								g = f;
								break;
							}
							g = g.return;
						}
						f = g;
					}
					Xi(a, b, e.children, c);
					b = b.child;
				}
				return b;
			case 9: return e = b.type, d = b.pendingProps.children, ch(b, c), e = eh(e), d = d(e), b.flags |= 1, Xi(a, b, d, c), b.child;
			case 14: return d = b.type, e = Ci(d, b.pendingProps), e = Ci(d.type, e), $i(a, b, d, e, c);
			case 15: return bj(a, b, b.type, b.pendingProps, c);
			case 17: return d = b.type, e = b.pendingProps, e = b.elementType === d ? e : Ci(d, e), ij(a, b), b.tag = 1, Zf(d) ? (a = !0, cg(b)) : a = !1, ch(b, c), Gi(b, d, e), Ii(b, d, e, c), jj(null, b, d, !0, a, c);
			case 19: return xj(a, b, c);
			case 22: return dj(a, b, c);
		}
		throw Error(p(156, b.tag));
	};
	function Fk(a, b) {
		return ac(a, b);
	}
	function $k(a, b, c, d) {
		this.tag = a;
		this.key = c;
		this.sibling = this.child = this.return = this.stateNode = this.type = this.elementType = null;
		this.index = 0;
		this.ref = null;
		this.pendingProps = b;
		this.dependencies = this.memoizedState = this.updateQueue = this.memoizedProps = null;
		this.mode = d;
		this.subtreeFlags = this.flags = 0;
		this.deletions = null;
		this.childLanes = this.lanes = 0;
		this.alternate = null;
	}
	function Bg(a, b, c, d) {
		return new $k(a, b, c, d);
	}
	function aj(a) {
		a = a.prototype;
		return !(!a || !a.isReactComponent);
	}
	function Zk(a) {
		if ("function" === typeof a) return aj(a) ? 1 : 0;
		if (void 0 !== a && null !== a) {
			a = a.$$typeof;
			if (a === Da) return 11;
			if (a === Ga) return 14;
		}
		return 2;
	}
	function Pg(a, b) {
		var c = a.alternate;
		null === c ? (c = Bg(a.tag, b, a.key, a.mode), c.elementType = a.elementType, c.type = a.type, c.stateNode = a.stateNode, c.alternate = a, a.alternate = c) : (c.pendingProps = b, c.type = a.type, c.flags = 0, c.subtreeFlags = 0, c.deletions = null);
		c.flags = a.flags & 14680064;
		c.childLanes = a.childLanes;
		c.lanes = a.lanes;
		c.child = a.child;
		c.memoizedProps = a.memoizedProps;
		c.memoizedState = a.memoizedState;
		c.updateQueue = a.updateQueue;
		b = a.dependencies;
		c.dependencies = null === b ? null : {
			lanes: b.lanes,
			firstContext: b.firstContext
		};
		c.sibling = a.sibling;
		c.index = a.index;
		c.ref = a.ref;
		return c;
	}
	function Rg(a, b, c, d, e, f) {
		var g = 2;
		d = a;
		if ("function" === typeof a) aj(a) && (g = 1);
		else if ("string" === typeof a) g = 5;
		else a: switch (a) {
			case ya: return Tg(c.children, e, f, b);
			case za:
				g = 8;
				e |= 8;
				break;
			case Aa: return a = Bg(12, c, b, e | 2), a.elementType = Aa, a.lanes = f, a;
			case Ea: return a = Bg(13, c, b, e), a.elementType = Ea, a.lanes = f, a;
			case Fa: return a = Bg(19, c, b, e), a.elementType = Fa, a.lanes = f, a;
			case Ia: return pj(c, e, f, b);
			default:
				if ("object" === typeof a && null !== a) switch (a.$$typeof) {
					case Ba:
						g = 10;
						break a;
					case Ca:
						g = 9;
						break a;
					case Da:
						g = 11;
						break a;
					case Ga:
						g = 14;
						break a;
					case Ha:
						g = 16;
						d = null;
						break a;
				}
				throw Error(p(130, null == a ? a : typeof a, ""));
		}
		b = Bg(g, c, b, e);
		b.elementType = a;
		b.type = d;
		b.lanes = f;
		return b;
	}
	function Tg(a, b, c, d) {
		a = Bg(7, a, d, b);
		a.lanes = c;
		return a;
	}
	function pj(a, b, c, d) {
		a = Bg(22, a, d, b);
		a.elementType = Ia;
		a.lanes = c;
		a.stateNode = { isHidden: !1 };
		return a;
	}
	function Qg(a, b, c) {
		a = Bg(6, a, null, b);
		a.lanes = c;
		return a;
	}
	function Sg(a, b, c) {
		b = Bg(4, null !== a.children ? a.children : [], a.key, b);
		b.lanes = c;
		b.stateNode = {
			containerInfo: a.containerInfo,
			pendingChildren: null,
			implementation: a.implementation
		};
		return b;
	}
	function al(a, b, c, d, e) {
		this.tag = b;
		this.containerInfo = a;
		this.finishedWork = this.pingCache = this.current = this.pendingChildren = null;
		this.timeoutHandle = -1;
		this.callbackNode = this.pendingContext = this.context = null;
		this.callbackPriority = 0;
		this.eventTimes = zc(0);
		this.expirationTimes = zc(-1);
		this.entangledLanes = this.finishedLanes = this.mutableReadLanes = this.expiredLanes = this.pingedLanes = this.suspendedLanes = this.pendingLanes = 0;
		this.entanglements = zc(0);
		this.identifierPrefix = d;
		this.onRecoverableError = e;
		this.mutableSourceEagerHydrationData = null;
	}
	function bl(a, b, c, d, e, f, g, h, k) {
		a = new al(a, b, c, h, k);
		1 === b ? (b = 1, !0 === f && (b |= 8)) : b = 0;
		f = Bg(3, null, null, b);
		a.current = f;
		f.stateNode = a;
		f.memoizedState = {
			element: d,
			isDehydrated: c,
			cache: null,
			transitions: null,
			pendingSuspenseBoundaries: null
		};
		kh(f);
		return a;
	}
	function cl(a, b, c) {
		var d = 3 < arguments.length && void 0 !== arguments[3] ? arguments[3] : null;
		return {
			$$typeof: wa,
			key: null == d ? null : "" + d,
			children: a,
			containerInfo: b,
			implementation: c
		};
	}
	function dl(a) {
		if (!a) return Vf;
		a = a._reactInternals;
		a: {
			if (Vb(a) !== a || 1 !== a.tag) throw Error(p(170));
			var b = a;
			do {
				switch (b.tag) {
					case 3:
						b = b.stateNode.context;
						break a;
					case 1: if (Zf(b.type)) {
						b = b.stateNode.__reactInternalMemoizedMergedChildContext;
						break a;
					}
				}
				b = b.return;
			} while (null !== b);
			throw Error(p(171));
		}
		if (1 === a.tag) {
			var c = a.type;
			if (Zf(c)) return bg(a, c, b);
		}
		return b;
	}
	function el(a, b, c, d, e, f, g, h, k) {
		a = bl(c, d, !0, a, e, f, g, h, k);
		a.context = dl(null);
		c = a.current;
		d = R();
		e = yi(c);
		f = mh(d, e);
		f.callback = void 0 !== b && null !== b ? b : null;
		nh(c, f, e);
		a.current.lanes = e;
		Ac(a, e, d);
		Dk(a, d);
		return a;
	}
	function fl(a, b, c, d) {
		var e = b.current, f = R(), g = yi(e);
		c = dl(c);
		null === b.context ? b.context = c : b.pendingContext = c;
		b = mh(f, g);
		b.payload = { element: a };
		d = void 0 === d ? null : d;
		null !== d && (b.callback = d);
		a = nh(e, b, g);
		null !== a && (gi(a, e, g, f), oh(a, e, g));
		return g;
	}
	function gl(a) {
		a = a.current;
		if (!a.child) return null;
		switch (a.child.tag) {
			case 5: return a.child.stateNode;
			default: return a.child.stateNode;
		}
	}
	function hl(a, b) {
		a = a.memoizedState;
		if (null !== a && null !== a.dehydrated) {
			var c = a.retryLane;
			a.retryLane = 0 !== c && c < b ? c : b;
		}
	}
	function il(a, b) {
		hl(a, b);
		(a = a.alternate) && hl(a, b);
	}
	function jl() {
		return null;
	}
	var kl = "function" === typeof reportError ? reportError : function(a) {
		console.error(a);
	};
	function ll(a) {
		this._internalRoot = a;
	}
	ml.prototype.render = ll.prototype.render = function(a) {
		var b = this._internalRoot;
		if (null === b) throw Error(p(409));
		fl(a, b, null, null);
	};
	ml.prototype.unmount = ll.prototype.unmount = function() {
		var a = this._internalRoot;
		if (null !== a) {
			this._internalRoot = null;
			var b = a.containerInfo;
			Rk(function() {
				fl(null, a, null, null);
			});
			b[uf] = null;
		}
	};
	function ml(a) {
		this._internalRoot = a;
	}
	ml.prototype.unstable_scheduleHydration = function(a) {
		if (a) {
			var b = Hc();
			a = {
				blockedOn: null,
				target: a,
				priority: b
			};
			for (var c = 0; c < Qc.length && 0 !== b && b < Qc[c].priority; c++);
			Qc.splice(c, 0, a);
			0 === c && Vc(a);
		}
	};
	function nl(a) {
		return !(!a || 1 !== a.nodeType && 9 !== a.nodeType && 11 !== a.nodeType);
	}
	function ol(a) {
		return !(!a || 1 !== a.nodeType && 9 !== a.nodeType && 11 !== a.nodeType && (8 !== a.nodeType || " react-mount-point-unstable " !== a.nodeValue));
	}
	function pl() {}
	function ql(a, b, c, d, e) {
		if (e) {
			if ("function" === typeof d) {
				var f = d;
				d = function() {
					var a = gl(g);
					f.call(a);
				};
			}
			var g = el(b, d, a, 0, null, !1, !1, "", pl);
			a._reactRootContainer = g;
			a[uf] = g.current;
			sf(8 === a.nodeType ? a.parentNode : a);
			Rk();
			return g;
		}
		for (; e = a.lastChild;) a.removeChild(e);
		if ("function" === typeof d) {
			var h = d;
			d = function() {
				var a = gl(k);
				h.call(a);
			};
		}
		var k = bl(a, 0, !1, null, null, !1, !1, "", pl);
		a._reactRootContainer = k;
		a[uf] = k.current;
		sf(8 === a.nodeType ? a.parentNode : a);
		Rk(function() {
			fl(b, k, c, d);
		});
		return k;
	}
	function rl(a, b, c, d, e) {
		var f = c._reactRootContainer;
		if (f) {
			var g = f;
			if ("function" === typeof e) {
				var h = e;
				e = function() {
					var a = gl(g);
					h.call(a);
				};
			}
			fl(b, g, a, e);
		} else g = ql(c, b, a, e, d);
		return gl(g);
	}
	Ec = function(a) {
		switch (a.tag) {
			case 3:
				var b = a.stateNode;
				if (b.current.memoizedState.isDehydrated) {
					var c = tc(b.pendingLanes);
					0 !== c && (Cc(b, c | 1), Dk(b, B()), 0 === (K & 6) && (Gj = B() + 500, jg()));
				}
				break;
			case 13: Rk(function() {
				var b = ih(a, 1);
				if (null !== b) gi(b, a, 1, R());
			}), il(a, 1);
		}
	};
	Fc = function(a) {
		if (13 === a.tag) {
			var b = ih(a, 134217728);
			if (null !== b) gi(b, a, 134217728, R());
			il(a, 134217728);
		}
	};
	Gc = function(a) {
		if (13 === a.tag) {
			var b = yi(a), c = ih(a, b);
			if (null !== c) gi(c, a, b, R());
			il(a, b);
		}
	};
	Hc = function() {
		return C;
	};
	Ic = function(a, b) {
		var c = C;
		try {
			return C = a, b();
		} finally {
			C = c;
		}
	};
	yb = function(a, b, c) {
		switch (b) {
			case "input":
				bb(a, c);
				b = c.name;
				if ("radio" === c.type && null != b) {
					for (c = a; c.parentNode;) c = c.parentNode;
					c = c.querySelectorAll("input[name=" + JSON.stringify("" + b) + "][type=\"radio\"]");
					for (b = 0; b < c.length; b++) {
						var d = c[b];
						if (d !== a && d.form === a.form) {
							var e = Db(d);
							if (!e) throw Error(p(90));
							Wa(d);
							bb(d, e);
						}
					}
				}
				break;
			case "textarea":
				ib(a, c);
				break;
			case "select": b = c.value, null != b && fb(a, !!c.multiple, b, !1);
		}
	};
	Gb = Qk;
	Hb = Rk;
	var sl = {
		usingClientEntryPoint: !1,
		Events: [
			Cb,
			ue,
			Db,
			Eb,
			Fb,
			Qk
		]
	}, tl = {
		findFiberByHostInstance: Wc,
		bundleType: 0,
		version: "18.3.1",
		rendererPackageName: "react-dom"
	};
	var ul = {
		bundleType: tl.bundleType,
		version: tl.version,
		rendererPackageName: tl.rendererPackageName,
		rendererConfig: tl.rendererConfig,
		overrideHookState: null,
		overrideHookStateDeletePath: null,
		overrideHookStateRenamePath: null,
		overrideProps: null,
		overridePropsDeletePath: null,
		overridePropsRenamePath: null,
		setErrorHandler: null,
		setSuspenseHandler: null,
		scheduleUpdate: null,
		currentDispatcherRef: ua.ReactCurrentDispatcher,
		findHostInstanceByFiber: function(a) {
			a = Zb(a);
			return null === a ? null : a.stateNode;
		},
		findFiberByHostInstance: tl.findFiberByHostInstance || jl,
		findHostInstancesForRefresh: null,
		scheduleRefresh: null,
		scheduleRoot: null,
		setRefreshHandler: null,
		getCurrentFiber: null,
		reconcilerVersion: "18.3.1-next-f1338f8080-20240426"
	};
	if ("undefined" !== typeof __REACT_DEVTOOLS_GLOBAL_HOOK__) {
		var vl = __REACT_DEVTOOLS_GLOBAL_HOOK__;
		if (!vl.isDisabled && vl.supportsFiber) try {
			kc = vl.inject(ul), lc = vl;
		} catch (a) {}
	}
	exports.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED = sl;
	exports.createPortal = function(a, b) {
		var c = 2 < arguments.length && void 0 !== arguments[2] ? arguments[2] : null;
		if (!nl(b)) throw Error(p(200));
		return cl(a, b, null, c);
	};
	exports.createRoot = function(a, b) {
		if (!nl(a)) throw Error(p(299));
		var c = !1, d = "", e = kl;
		null !== b && void 0 !== b && (!0 === b.unstable_strictMode && (c = !0), void 0 !== b.identifierPrefix && (d = b.identifierPrefix), void 0 !== b.onRecoverableError && (e = b.onRecoverableError));
		b = bl(a, 1, !1, null, null, c, !1, d, e);
		a[uf] = b.current;
		sf(8 === a.nodeType ? a.parentNode : a);
		return new ll(b);
	};
	exports.findDOMNode = function(a) {
		if (null == a) return null;
		if (1 === a.nodeType) return a;
		var b = a._reactInternals;
		if (void 0 === b) {
			if ("function" === typeof a.render) throw Error(p(188));
			a = Object.keys(a).join(",");
			throw Error(p(268, a));
		}
		a = Zb(b);
		a = null === a ? null : a.stateNode;
		return a;
	};
	exports.flushSync = function(a) {
		return Rk(a);
	};
	exports.hydrate = function(a, b, c) {
		if (!ol(b)) throw Error(p(200));
		return rl(null, a, b, !0, c);
	};
	exports.hydrateRoot = function(a, b, c) {
		if (!nl(a)) throw Error(p(405));
		var d = null != c && c.hydratedSources || null, e = !1, f = "", g = kl;
		null !== c && void 0 !== c && (!0 === c.unstable_strictMode && (e = !0), void 0 !== c.identifierPrefix && (f = c.identifierPrefix), void 0 !== c.onRecoverableError && (g = c.onRecoverableError));
		b = el(b, null, a, 1, null != c ? c : null, e, !1, f, g);
		a[uf] = b.current;
		sf(a);
		if (d) for (a = 0; a < d.length; a++) c = d[a], e = c._getVersion, e = e(c._source), null == b.mutableSourceEagerHydrationData ? b.mutableSourceEagerHydrationData = [c, e] : b.mutableSourceEagerHydrationData.push(c, e);
		return new ml(b);
	};
	exports.render = function(a, b, c) {
		if (!ol(b)) throw Error(p(200));
		return rl(null, a, b, !1, c);
	};
	exports.unmountComponentAtNode = function(a) {
		if (!ol(a)) throw Error(p(40));
		return a._reactRootContainer ? (Rk(function() {
			rl(null, null, a, !1, function() {
				a._reactRootContainer = null;
				a[uf] = null;
			});
		}), !0) : !1;
	};
	exports.unstable_batchedUpdates = Qk;
	exports.unstable_renderSubtreeIntoContainer = function(a, b, c, d) {
		if (!ol(c)) throw Error(p(200));
		if (null == a || void 0 === a._reactInternals) throw Error(p(38));
		return rl(a, b, c, !1, d);
	};
	exports.version = "18.3.1-next-f1338f8080-20240426";
}));
var require_react_dom = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	function checkDCE() {
		if (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ === "undefined" || typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE !== "function") return;
		try {
			__REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE(checkDCE);
		} catch (err) {
			console.error(err);
		}
	}
	checkDCE();
	module.exports = require_react_dom_production_min();
}));
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var context = import_react.createContext(!0);
function forbiddenInRender() {
	throw new Error("A function wrapped in useEffectEvent can't be called during rendering.");
}
var isInvalidExecutionContextForEventFunction = "use" in import_react.default ? () => {
	try {
		return import_react.default.use(context);
	} catch {
		return !1;
	}
} : () => !1;
function useEffectEvent(fn) {
	const ref = import_react.useRef(forbiddenInRender);
	return import_react.useInsertionEffect(() => {
		ref.current = fn;
	}, [fn]), (...args) => {
		isInvalidExecutionContextForEventFunction() && forbiddenInRender();
		const latestFn = ref.current;
		return latestFn(...args);
	};
}
/**
* Subscribes to an assistant event for the lifetime of the component.
*
* The subscription is established on mount and re-established whenever the
* scope or event name changes. The `callback` is wrapped in an effect-event
* shim, so the latest closure is invoked on each emission — you do not
* need to memoize it.
*
* @param selector - Either a dotted event name like
*   `"thread.modelContextUpdate"` or an object `{ scope, event }`. Use
*   `scope: "*"` to subscribe at the root client and receive emissions
*   from any descendant scope, regardless of which one is in React
*   context.
* @param callback - Invoked with the event payload. The most recent
*   reference is always called. Return values are ignored, async callbacks
*   are not awaited, and the callback cannot be called during render.
*
* @example
* ```tsx
* // React to transient model-context changes.
* useAuiEvent("thread.modelContextUpdate", ({ threadId }) => {
*   analytics.track("model_context_update", { threadId });
* });
* ```
*
* @example
* ```tsx
* // React to thread switches.
* useAuiEvent("threadListItem.switchedTo", () => {
*   resetLocalState();
* });
* ```
*
* @example
* ```tsx
* // Listen from the root client rather than the current React context.
* useAuiEvent({ scope: "*", event: "thread.modelContextUpdate" }, (payload) => {
*   analytics.track("model_context_update", payload);
* });
* ```
*/
var useAuiEvent = (selector, callback) => {
	const $ = c(11);
	const aui = useAui();
	const callbackRef = useEffectEvent(callback);
	let t0;
	if ($[0] !== selector) {
		t0 = normalizeEventSelector(selector);
		$[0] = selector;
		$[1] = t0;
	} else t0 = $[1];
	const { scope, event } = t0;
	let t1;
	if ($[2] !== aui || $[3] !== callbackRef || $[4] !== event || $[5] !== scope) {
		t1 = () => aui.on({
			scope,
			event
		}, callbackRef);
		$[2] = aui;
		$[3] = callbackRef;
		$[4] = event;
		$[5] = scope;
		$[6] = t1;
	} else t1 = $[6];
	let t2;
	if ($[7] !== aui || $[8] !== event || $[9] !== scope) {
		t2 = [
			aui,
			scope,
			event
		];
		$[7] = aui;
		$[8] = event;
		$[9] = scope;
		$[10] = t2;
	} else t2 = $[10];
	useEffect(t1, t2);
};
var createStoreImpl = (createState) => {
	let state;
	const listeners = /* @__PURE__ */ new Set();
	const setState = (partial, replace) => {
		const nextState = typeof partial === "function" ? partial(state) : partial;
		if (!Object.is(nextState, state)) {
			const previousState = state;
			state = (replace != null ? replace : typeof nextState !== "object" || nextState === null) ? nextState : Object.assign({}, state, nextState);
			listeners.forEach((listener) => listener(state, previousState));
		}
	};
	const getState = () => state;
	const getInitialState = () => initialState;
	const subscribe = (listener) => {
		listeners.add(listener);
		return () => listeners.delete(listener);
	};
	const api = {
		setState,
		getState,
		getInitialState,
		subscribe
	};
	const initialState = state = createState(setState, getState, api);
	return api;
};
var createStore = ((createState) => createState ? createStoreImpl(createState) : createStoreImpl);
var identity$1 = (arg) => arg;
function useStore(api, selector = identity$1) {
	const slice = import_react.useSyncExternalStore(api.subscribe, import_react.useCallback(() => selector(api.getState()), [api, selector]), import_react.useCallback(() => selector(api.getInitialState()), [api, selector]));
	import_react.useDebugValue(slice);
	return slice;
}
var createImpl = (createState) => {
	const api = createStore(createState);
	const useBoundStore = (selector) => useStore(api, selector);
	Object.assign(useBoundStore, api);
	return useBoundStore;
};
var create = ((createState) => createState ? createImpl(createState) : createImpl);
/**
* Creates a context hook with optional support.
* @param context - The React context to consume.
* @param providerName - The name of the provider for error messages.
* @returns A hook function that provides the context value.
*/
function createContextHook(context, providerName) {
	function useContextHook(options) {
		const contextValue = useContext(context);
		if (!options?.optional && !contextValue) throw new Error(`This component must be used within ${providerName}.`);
		return contextValue;
	}
	return useContextHook;
}
/**
* Creates hooks for accessing a store within a context.
* @param contextHook - The hook to access the context.
* @param contextKey - The key of the store in the context.
* @returns An object containing the hooks: `use...` and `use...Store`.
*/
function createContextStoreHook(contextHook, contextKey) {
	function useStoreStoreHook(options) {
		const context = contextHook(options);
		if (!context) return null;
		return context[contextKey];
	}
	function useStoreHook(param) {
		let optional = false;
		let selector;
		if (typeof param === "function") selector = param;
		else if (param && typeof param === "object") {
			optional = !!param.optional;
			selector = param.selector;
		}
		const useStore = useStoreStoreHook({ optional });
		if (!useStore) return null;
		return selector ? useStore(selector) : useStore();
	}
	return {
		[contextKey]: useStoreHook,
		[`${contextKey}Store`]: useStoreStoreHook
	};
}
var ThreadViewportContext = createContext(null);
var { useThreadViewport, useThreadViewportStore } = createContextStoreHook(createContextHook(ThreadViewportContext, "ThreadPrimitive.Viewport"), "useThreadViewport");
var createSizeRegistry = (onChange) => {
	const entries = /* @__PURE__ */ new Map();
	const recalculate = () => {
		let total = 0;
		for (const height of entries.values()) total += height;
		onChange(total);
	};
	return { register: () => {
		const id = Symbol();
		entries.set(id, 0);
		return {
			setHeight: (height) => {
				if (entries.get(id) !== height) {
					entries.set(id, height);
					recalculate();
				}
			},
			unregister: () => {
				entries.delete(id);
				recalculate();
			}
		};
	} };
};
var makeThreadViewportStore = (options = {}) => {
	const scrollToBottomListeners = /* @__PURE__ */ new Set();
	const viewportRegistry = createSizeRegistry((total) => {
		store.setState({ height: {
			...store.getState().height,
			viewport: total
		} });
	});
	const insetRegistry = createSizeRegistry((total) => {
		store.setState({ height: {
			...store.getState().height,
			inset: total
		} });
	});
	const registerElementSlot = (key, element) => {
		store.setState({ element: {
			...store.getState().element,
			[key]: element
		} });
		return () => {
			if (store.getState().element[key] !== element) return;
			store.setState({ element: {
				...store.getState().element,
				[key]: null
			} });
		};
	};
	const store = create(() => ({
		isAtBottom: true,
		scrollToBottom: ({ behavior = "auto" } = {}) => {
			for (const listener of scrollToBottomListeners) listener({ behavior });
		},
		onScrollToBottom: (callback) => {
			scrollToBottomListeners.add(callback);
			return () => {
				scrollToBottomListeners.delete(callback);
			};
		},
		turnAnchor: options.turnAnchor ?? "bottom",
		topAnchorMessageClamp: {
			tallerThan: options.topAnchorMessageClamp?.tallerThan ?? "10em",
			visibleHeight: options.topAnchorMessageClamp?.visibleHeight ?? "6em"
		},
		height: {
			viewport: 0,
			inset: 0
		},
		element: {
			viewport: null,
			anchor: null,
			target: null
		},
		targetConfig: null,
		topAnchorTurn: null,
		registerViewport: viewportRegistry.register,
		registerContentInset: insetRegistry.register,
		registerViewportElement: (element) => registerElementSlot("viewport", element),
		registerAnchorElement: (element) => registerElementSlot("anchor", element),
		registerAnchorTargetElement: (element, config) => {
			store.setState({
				element: {
					...store.getState().element,
					target: element
				},
				targetConfig: element && config ? config : null
			});
			return () => {
				if (store.getState().element.target !== element) return;
				store.setState({
					element: {
						...store.getState().element,
						target: null
					},
					targetConfig: null
				});
			};
		},
		setTopAnchorTurn: (topAnchorTurn) => {
			store.setState({ topAnchorTurn });
		}
	}));
	return store;
};
var writableStore = (store) => {
	return store;
};
var import_jsx_runtime = require_jsx_runtime();
var useThreadViewportStoreValue = (options) => {
	const $ = c(11);
	let t0;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t0 = { optional: true };
		$[0] = t0;
	} else t0 = $[0];
	const outerViewport = useThreadViewportStore(t0);
	let t1;
	if ($[1] !== options) {
		t1 = () => makeThreadViewportStore(options);
		$[1] = options;
		$[2] = t1;
	} else t1 = $[2];
	const [store] = useState(t1);
	let t2;
	let t3;
	if ($[3] !== outerViewport || $[4] !== store) {
		t2 = () => outerViewport?.getState().onScrollToBottom(() => {
			store.getState().scrollToBottom();
		});
		t3 = [outerViewport, store];
		$[3] = outerViewport;
		$[4] = store;
		$[5] = t2;
		$[6] = t3;
	} else {
		t2 = $[5];
		t3 = $[6];
	}
	useEffect(t2, t3);
	let t4;
	let t5;
	if ($[7] !== outerViewport || $[8] !== store) {
		t4 = () => {
			if (!outerViewport) return;
			return store.subscribe((state) => {
				if (outerViewport.getState().isAtBottom !== state.isAtBottom) writableStore(outerViewport).setState({ isAtBottom: state.isAtBottom });
			});
		};
		t5 = [store, outerViewport];
		$[7] = outerViewport;
		$[8] = store;
		$[9] = t4;
		$[10] = t5;
	} else {
		t4 = $[9];
		t5 = $[10];
	}
	useEffect(t4, t5);
	return store;
};
var ThreadPrimitiveViewportProvider = (t0) => {
	const $ = c(7);
	const { children, options: t1 } = t0;
	let t2;
	if ($[0] !== t1) {
		t2 = t1 === void 0 ? {} : t1;
		$[0] = t1;
		$[1] = t2;
	} else t2 = $[1];
	const useThreadViewport = useThreadViewportStoreValue(t2);
	let t3;
	if ($[2] !== useThreadViewport) {
		t3 = () => ({ useThreadViewport });
		$[2] = useThreadViewport;
		$[3] = t3;
	} else t3 = $[3];
	const [context] = useState(t3);
	let t4;
	if ($[4] !== children || $[5] !== context) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadViewportContext.Provider, {
			value: context,
			children
		});
		$[4] = children;
		$[5] = context;
		$[6] = t4;
	} else t4 = $[6];
	return t4;
};
var DevToolsRegistration = () => {
	const $ = c(3);
	const aui = useAui();
	let t0;
	let t1;
	if ($[0] !== aui) {
		t0 = () => {};
		t1 = [aui];
		$[0] = aui;
		$[1] = t0;
		$[2] = t1;
	} else {
		t0 = $[1];
		t1 = $[2];
	}
	useEffect(t0, t1);
	return null;
};
var AssistantRuntimeProviderImpl = (t0) => {
	const $ = c(7);
	const { children, aui, runtime } = t0;
	const t1 = aui ?? null;
	let t2;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DevToolsRegistration, {});
		$[0] = t2;
	} else t2 = $[0];
	let t3;
	if ($[1] !== children) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadPrimitiveViewportProvider, { children });
		$[1] = children;
		$[2] = t3;
	} else t3 = $[2];
	let t4;
	if ($[3] !== runtime || $[4] !== t1 || $[5] !== t3) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(AssistantProviderBase, {
			runtime,
			aui: t1,
			children: [t2, t3]
		});
		$[3] = runtime;
		$[4] = t1;
		$[5] = t3;
		$[6] = t4;
	} else t4 = $[6];
	return t4;
};
var AssistantRuntimeProvider = (0, react_shim_exports.memo)(AssistantRuntimeProviderImpl);
var ensureBinding = (r) => {
	const runtime = r;
	if (runtime.__isBound) return;
	runtime.__internal_bindMethods?.();
	runtime.__isBound = true;
};
function useRuntimeStateInternal(runtime, t0) {
	const $ = c(4);
	const selector = t0 === void 0 ? identity : t0;
	ensureBinding(runtime);
	let t1;
	let t2;
	if ($[0] !== runtime || $[1] !== selector) {
		t1 = () => selector(runtime.getState());
		t2 = () => selector(runtime.getState());
		$[0] = runtime;
		$[1] = selector;
		$[2] = t1;
		$[3] = t2;
	} else {
		t1 = $[2];
		t2 = $[3];
	}
	const slice = useSyncExternalStore(runtime.subscribe, t1, t2);
	useDebugValue(slice);
	return slice;
}
var identity = (arg) => arg;
function createStateHookForRuntime(useRuntime) {
	function useStoreHook(param) {
		let optional = false;
		let selector;
		if (typeof param === "function") selector = param;
		else if (param) {
			optional = !!param.optional;
			selector = param.selector;
		}
		const store = useRuntime({ optional });
		if (!store) return null;
		return useRuntimeStateInternal(store, selector);
	}
	return useStoreHook;
}
function useComposerRuntime(options) {
	const $ = c(2);
	const aui = useAui();
	let t0;
	if ($[0] !== aui) {
		t0 = () => aui.composer.source ? aui.composer().__internal_getRuntime?.() ?? null : null;
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const runtime = useAuiState(t0);
	if (!runtime && !options?.optional) throw new Error("ComposerRuntime is not available");
	return runtime;
}
function useMessageRuntime(options) {
	const $ = c(2);
	const aui = useAui();
	let t0;
	if ($[0] !== aui) {
		t0 = () => aui.message.source ? aui.message().__internal_getRuntime?.() ?? null : null;
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const runtime = useAuiState(t0);
	if (!runtime && !options?.optional) throw new Error("MessageRuntime is not available");
	return runtime;
}
/**
* @deprecated Use {@link useAuiState}: `useAuiState((s) => s.message)`. See the {@link https://assistant-ui.com/docs/migrations/v0-12 migration guide}.
*
* Hook to access the current message state.
*
* This hook provides reactive access to the message's state, including content,
* role, status, and other message-level properties.
*
* @param selector Optional selector function to pick specific state properties
* @returns The selected message state or the entire message state if no selector provided
*
* @example
* ```tsx
* // Before:
* function MessageContent() {
*   const role = useMessage((state) => state.role);
*   const content = useMessage((state) => state.content);
*   const isLoading = useMessage((state) => state.status.type === "running");
*   return (
*     <div className={`message-${role}`}>
*       {isLoading ? "Loading..." : content.map(part => part.text).join("")}
*     </div>
*   );
* }
*
* // After:
* function MessageContent() {
*   const role = useAuiState((s) => s.message.role);
*   const content = useAuiState((s) => s.message.content);
*   const isLoading = useAuiState((s) => s.message.status.type === "running");
*   return (
*     <div className={`message-${role}`}>
*       {isLoading ? "Loading..." : content.map(part => part.text).join("")}
*     </div>
*   );
* }
* ```
*/
var useMessage = createStateHookForRuntime(useMessageRuntime);
var import_react_dom = /* @__PURE__ */ __toESM(require_react_dom(), 1);
function setRef(ref, value) {
	if (typeof ref === "function") return ref(value);
	else if (ref !== null && ref !== void 0) ref.current = value;
}
function composeRefs(...refs) {
	return (node) => {
		let hasCleanup = false;
		const cleanups = refs.map((ref) => {
			const cleanup = setRef(ref, node);
			if (!hasCleanup && typeof cleanup == "function") hasCleanup = true;
			return cleanup;
		});
		if (hasCleanup) return () => {
			for (let i = 0; i < cleanups.length; i++) {
				const cleanup = cleanups[i];
				if (typeof cleanup == "function") cleanup();
				else setRef(refs[i], null);
			}
		};
	};
}
function useComposedRefs(...refs) {
	return import_react.useCallback(composeRefs(...refs), refs);
}
var __defProp = Object.defineProperty;
var __exportAll = (all, no_symbols) => {
	let target = {};
	for (var name in all) __defProp(target, name, {
		get: all[name],
		enumerable: true
	});
	if (!no_symbols) __defProp(target, Symbol.toStringTag, { value: "Module" });
	return target;
};
// @__NO_SIDE_EFFECTS__
function createSlot(ownerName) {
	const Slot2 = import_react.forwardRef((props, forwardedRef) => {
		let { children, ...slotProps } = props;
		let slottableElement = null;
		let hasSlottable = false;
		const newChildren = [];
		if (isLazyComponent(children) && typeof use === "function") children = use(children._payload);
		import_react.Children.forEach(children, (maybeSlottable) => {
			if (isSlottable(maybeSlottable)) {
				hasSlottable = true;
				const slottable = maybeSlottable;
				let child = "child" in slottable.props ? slottable.props.child : slottable.props.children;
				if (isLazyComponent(child) && typeof use === "function") child = use(child._payload);
				slottableElement = getSlottableElementFromSlottable(slottable, child);
				newChildren.push(slottableElement?.props?.children);
			} else newChildren.push(maybeSlottable);
		});
		if (slottableElement) slottableElement = import_react.cloneElement(slottableElement, void 0, newChildren);
		else if (!hasSlottable && import_react.Children.count(children) === 1 && import_react.isValidElement(children)) slottableElement = children;
		const slottableElementRef = slottableElement ? getElementRef(slottableElement) : void 0;
		const composedRef = useComposedRefs(forwardedRef, slottableElementRef);
		if (!slottableElement) {
			if (children || children === 0) throw new Error(hasSlottable ? createSlottableError(ownerName) : createSlotError(ownerName));
			return children;
		}
		const mergedProps = mergeProps(slotProps, slottableElement.props ?? {});
		if (slottableElement.type !== import_react.Fragment) mergedProps.ref = forwardedRef ? composedRef : slottableElementRef;
		return import_react.cloneElement(slottableElement, mergedProps);
	});
	Slot2.displayName = `${ownerName}.Slot`;
	return Slot2;
}
var Slot = /* @__PURE__ */ createSlot("Slot");
var SLOTTABLE_IDENTIFIER = Symbol.for("radix.slottable");
// @__NO_SIDE_EFFECTS__
function createSlottable(ownerName) {
	const Slottable2 = (props) => "child" in props ? props.children(props.child) : props.children;
	Slottable2.displayName = `${ownerName}.Slottable`;
	Slottable2.__radixId = SLOTTABLE_IDENTIFIER;
	return Slottable2;
}
var getSlottableElementFromSlottable = (slottable, child) => {
	if ("child" in slottable.props) {
		const child2 = slottable.props.child;
		if (!import_react.isValidElement(child2)) return null;
		return import_react.cloneElement(child2, void 0, slottable.props.children(child2.props.children));
	}
	return import_react.isValidElement(child) ? child : null;
};
function mergeProps(slotProps, childProps) {
	const overrideProps = { ...childProps };
	for (const propName in childProps) {
		const slotPropValue = slotProps[propName];
		const childPropValue = childProps[propName];
		if (/^on[A-Z]/.test(propName)) {
			if (slotPropValue && childPropValue) overrideProps[propName] = (...args) => {
				const result = childPropValue(...args);
				slotPropValue(...args);
				return result;
			};
			else if (slotPropValue) overrideProps[propName] = slotPropValue;
		} else if (propName === "style") overrideProps[propName] = {
			...slotPropValue,
			...childPropValue
		};
		else if (propName === "className") overrideProps[propName] = [slotPropValue, childPropValue].filter(Boolean).join(" ");
	}
	return {
		...slotProps,
		...overrideProps
	};
}
function getElementRef(element) {
	let getter = Object.getOwnPropertyDescriptor(element.props, "ref")?.get;
	let mayWarn = getter && "isReactWarning" in getter && getter.isReactWarning;
	if (mayWarn) return element.ref;
	getter = Object.getOwnPropertyDescriptor(element, "ref")?.get;
	mayWarn = getter && "isReactWarning" in getter && getter.isReactWarning;
	if (mayWarn) return element.props.ref;
	return element.props.ref || element.ref;
}
function isSlottable(child) {
	return import_react.isValidElement(child) && typeof child.type === "function" && "__radixId" in child.type && child.type.__radixId === SLOTTABLE_IDENTIFIER;
}
var REACT_LAZY_TYPE = Symbol.for("react.lazy");
function isLazyComponent(element) {
	return element != null && typeof element === "object" && "$$typeof" in element && element.$$typeof === REACT_LAZY_TYPE && "_payload" in element && isPromiseLike(element._payload);
}
function isPromiseLike(value) {
	return typeof value === "object" && value !== null && "then" in value;
}
var createSlotError = (ownerName) => {
	return `${ownerName} failed to slot onto its children. Expected a single React element child or \`Slottable\`.`;
};
var createSlottableError = (ownerName) => {
	return `${ownerName} failed to slot onto its \`Slottable\`. Expected \`Slottable\` to receive a single React element child.`;
};
var use = import_react[" use ".trim().toString()];
var Primitive$1 = [
	"a",
	"button",
	"div",
	"form",
	"h2",
	"h3",
	"img",
	"input",
	"label",
	"li",
	"nav",
	"ol",
	"p",
	"select",
	"span",
	"svg",
	"ul"
].reduce((primitive, node) => {
	const Slot = /* @__PURE__ */ createSlot(`Primitive.${node}`);
	const Node = import_react.forwardRef((props, forwardedRef) => {
		const { asChild, ...primitiveProps } = props;
		const Comp = asChild ? Slot : node;
		if (typeof window !== "undefined") window[Symbol.for("radix-ui")] = true;
		return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Comp, {
			...primitiveProps,
			ref: forwardedRef
		});
	});
	Node.displayName = `Primitive.${node}`;
	return {
		...primitive,
		[node]: Node
	};
}, {});
function dispatchDiscreteCustomEvent(target, event) {
	if (target) import_react_dom.flushSync(() => target.dispatchEvent(event));
}
/**
* Thin wrapper around `@radix-ui/react-primitive` that adds `render` prop support.
*
* When `render` is provided, it is converted to the equivalent `asChild` pattern:
*   render={<Comp props />} + children  →  asChild + <Comp props>{children}</Comp>
*
* All prop merging, ref composition, and event handler chaining remain handled
* by Radix's battle-tested Slot implementation — we add zero custom logic for that.
*/
var NODES = [
	"a",
	"button",
	"div",
	"form",
	"h2",
	"h3",
	"img",
	"input",
	"label",
	"li",
	"nav",
	"ol",
	"p",
	"select",
	"span",
	"svg",
	"ul"
];
function withRenderProp(Component) {
	const Wrapped = (0, react_shim_exports.forwardRef)((t0, ref) => {
		const $ = c(17);
		let asChild;
		let children;
		let render;
		let rest;
		if ($[0] !== t0) {
			({render, asChild, children, ...rest} = t0);
			$[0] = t0;
			$[1] = asChild;
			$[2] = children;
			$[3] = render;
			$[4] = rest;
		} else {
			asChild = $[1];
			children = $[2];
			render = $[3];
			rest = $[4];
		}
		const Comp = Component;
		if (render && (0, react_shim_exports.isValidElement)(render)) {
			const renderChildren = children !== void 0 ? children : render.props.children;
			const t1 = rest;
			let t2;
			if ($[5] !== render || $[6] !== renderChildren) {
				t2 = (0, react_shim_exports.cloneElement)(render, void 0, renderChildren);
				$[5] = render;
				$[6] = renderChildren;
				$[7] = t2;
			} else t2 = $[7];
			let t3;
			if ($[8] !== ref || $[9] !== t1 || $[10] !== t2) {
				t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Comp, {
					...t1,
					asChild: true,
					ref,
					children: t2
				});
				$[8] = ref;
				$[9] = t1;
				$[10] = t2;
				$[11] = t3;
			} else t3 = $[11];
			return t3;
		}
		const t1 = rest;
		let t2;
		if ($[12] !== asChild || $[13] !== children || $[14] !== ref || $[15] !== t1) {
			t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Comp, {
				...t1,
				asChild,
				ref,
				children
			});
			$[12] = asChild;
			$[13] = children;
			$[14] = ref;
			$[15] = t1;
			$[16] = t2;
		} else t2 = $[16];
		return t2;
	});
	Wrapped.displayName = typeof Component === "string" ? Component : Component.displayName ?? Component.name ?? "Component";
	return Wrapped;
}
function createPrimitive(node) {
	const RadixComp = Primitive$1[node];
	const Component = withRenderProp(RadixComp);
	Component.displayName = `Primitive.${node}`;
	return Component;
}
var Primitive = NODES.reduce((acc, node) => {
	acc[node] = createPrimitive(node);
	return acc;
}, {});
typeof window !== "undefined" && window.document && window.document.createElement;
function composeEventHandlers(originalEventHandler, ourEventHandler, { checkForDefaultPrevented = true } = {}) {
	return function handleEvent(event) {
		originalEventHandler?.(event);
		if (checkForDefaultPrevented === false || !event.defaultPrevented) return ourEventHandler?.(event);
	};
}
var createActionButton = (displayName, useActionButton, forwardProps = []) => {
	const ActionButton = (0, react_shim_exports.forwardRef)((props, forwardedRef) => {
		const $ = c(6);
		const forwardedProps = {};
		const primitiveProps = {};
		Object.keys(props).forEach((key) => {
			if (forwardProps.includes(key)) forwardedProps[key] = props[key];
			else primitiveProps[key] = props[key];
		});
		const callback = useActionButton(forwardedProps) ?? void 0;
		const t0 = Primitive;
		const t1 = "button";
		const t2 = primitiveProps.disabled || !callback;
		const t3 = composeEventHandlers(primitiveProps.onClick, callback);
		let t4;
		if ($[0] !== forwardedRef || $[1] !== primitiveProps || $[2] !== t0.button || $[3] !== t2 || $[4] !== t3) {
			t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(t0.button, {
				...primitiveProps,
				type: t1,
				ref: forwardedRef,
				disabled: t2,
				onClick: t3
			});
			$[0] = forwardedRef;
			$[1] = primitiveProps;
			$[2] = t0.button;
			$[3] = t2;
			$[4] = t3;
			$[5] = t4;
		} else t4 = $[5];
		return t4;
	});
	ActionButton.displayName = displayName;
	return ActionButton;
};
function useCallbackRef(callback) {
	const callbackRef = import_react.useRef(callback);
	import_react.useEffect(() => {
		callbackRef.current = callback;
	});
	return import_react.useMemo(() => ((...args) => callbackRef.current?.(...args)), []);
}
function useEscapeKeydown(onEscapeKeyDownProp, ownerDocument = globalThis?.document) {
	const onEscapeKeyDown = useCallbackRef(onEscapeKeyDownProp);
	import_react.useEffect(() => {
		const handleKeyDown = (event) => {
			if (event.key === "Escape") onEscapeKeyDown(event);
		};
		ownerDocument.addEventListener("keydown", handleKeyDown, { capture: true });
		return () => ownerDocument.removeEventListener("keydown", handleKeyDown, { capture: true });
	}, [onEscapeKeyDown, ownerDocument]);
}
/**
* The root container component for an attachment.
*
* This component provides the foundational wrapper for attachment-related components
* and content. It serves as the context provider for attachment state and actions.
*
* @example
* ```tsx
* <AttachmentPrimitive.Root>
*   <AttachmentPrimitive.Name />
*   <AttachmentPrimitive.Remove />
* </AttachmentPrimitive.Root>
* ```
*/
var AttachmentPrimitiveRoot = (0, react_shim_exports.forwardRef)((props, ref) => {
	const $ = c(3);
	let t0;
	if ($[0] !== props || $[1] !== ref) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref
		});
		$[0] = props;
		$[1] = ref;
		$[2] = t0;
	} else t0 = $[2];
	return t0;
});
AttachmentPrimitiveRoot.displayName = "AttachmentPrimitive.Root";
var AttachmentPrimitiveThumb = (0, react_shim_exports.forwardRef)((props, ref) => {
	const $ = c(4);
	const ext = useAuiState(_temp$20);
	let t0;
	if ($[0] !== ext || $[1] !== props || $[2] !== ref) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Primitive.div, {
			...props,
			ref,
			children: [".", ext]
		});
		$[0] = ext;
		$[1] = props;
		$[2] = ref;
		$[3] = t0;
	} else t0 = $[3];
	return t0;
});
AttachmentPrimitiveThumb.displayName = "AttachmentPrimitive.unstable_Thumb";
function _temp$20(s) {
	const parts = s.attachment.name.split(".");
	return parts.length > 1 ? parts.pop() : "";
}
var AttachmentPrimitiveName = () => {
	const $ = c(2);
	const name = useAuiState(_temp$19);
	let t0;
	if ($[0] !== name) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_jsx_runtime.Fragment, { children: name });
		$[0] = name;
		$[1] = t0;
	} else t0 = $[1];
	return t0;
};
AttachmentPrimitiveName.displayName = "AttachmentPrimitive.Name";
function _temp$19(s) {
	return s.attachment.name;
}
var useAttachmentRemove = () => {
	const $ = c(2);
	const aui = useAui();
	let t0;
	if ($[0] !== aui) {
		t0 = () => {
			aui.attachment().remove();
		};
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	return t0;
};
var AttachmentPrimitiveRemove = createActionButton("AttachmentPrimitive.Remove", useAttachmentRemove);
var attachment_exports = /* @__PURE__ */ __exportAll({
	Name: () => AttachmentPrimitiveName,
	Remove: () => AttachmentPrimitiveRemove,
	Root: () => AttachmentPrimitiveRoot,
	unstable_Thumb: () => AttachmentPrimitiveThumb
});
var useMessageIf = (props) => {
	const $ = c(12);
	let t0;
	if ($[0] !== props.assistant || $[1] !== props.copied || $[2] !== props.hasAttachments || $[3] !== props.hasBranches || $[4] !== props.hasContent || $[5] !== props.last || $[6] !== props.lastOrHover || $[7] !== props.speaking || $[8] !== props.submittedFeedback || $[9] !== props.system || $[10] !== props.user) {
		t0 = (s) => {
			const { role, attachments, parts, branchCount, isLast, speech, isCopied, isHovering } = s.message;
			if (props.hasBranches === true && branchCount < 2) return false;
			if (props.user && role !== "user") return false;
			if (props.assistant && role !== "assistant") return false;
			if (props.system && role !== "system") return false;
			if (props.lastOrHover === true && !isHovering && !isLast) return false;
			if (props.last !== void 0 && props.last !== isLast) return false;
			if (props.copied === true && !isCopied) return false;
			if (props.copied === false && isCopied) return false;
			if (props.speaking === true && speech == null) return false;
			if (props.speaking === false && speech != null) return false;
			if (props.hasAttachments === true && (role !== "user" || !attachments?.length)) return false;
			if (props.hasAttachments === false && role === "user" && attachments?.length) return false;
			if (props.hasContent === true && parts.length === 0) return false;
			if (props.hasContent === false && parts.length > 0) return false;
			if (props.submittedFeedback !== void 0 && (s.message.metadata.submittedFeedback?.type ?? null) !== props.submittedFeedback) return false;
			return true;
		};
		$[0] = props.assistant;
		$[1] = props.copied;
		$[2] = props.hasAttachments;
		$[3] = props.hasBranches;
		$[4] = props.hasContent;
		$[5] = props.last;
		$[6] = props.lastOrHover;
		$[7] = props.speaking;
		$[8] = props.submittedFeedback;
		$[9] = props.system;
		$[10] = props.user;
		$[11] = t0;
	} else t0 = $[11];
	return useAuiState(t0);
};
/**
* @deprecated Use `<AuiIf condition={(s) => s.message...} />` instead.
*/
var MessagePrimitiveIf = (t0) => {
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
	return useMessageIf(query) ? children : null;
};
MessagePrimitiveIf.displayName = "MessagePrimitive.If";
var ComposerInputPluginRegistryContext = createContext(null);
var useComposerInputPluginRegistryOptional = () => {
	return useContext(ComposerInputPluginRegistryContext);
};
var ComposerInputPluginProvider = (t0) => {
	const $ = c(8);
	const { children } = t0;
	let t1;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = /* @__PURE__ */ new Map();
		$[0] = t1;
	} else t1 = $[0];
	const pluginsRef = useRef(t1);
	let t2;
	if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
		t2 = [];
		$[1] = t2;
	} else t2 = $[1];
	const snapshotRef = useRef(t2);
	let t3;
	if ($[2] === Symbol.for("react.memo_cache_sentinel")) {
		t3 = () => {
			const entries = Array.from(pluginsRef.current.entries());
			entries.sort(_temp$18);
			snapshotRef.current = entries.map(_temp2$5);
		};
		$[2] = t3;
	} else t3 = $[2];
	const refreshSnapshot = t3;
	let t4;
	if ($[3] === Symbol.for("react.memo_cache_sentinel")) {
		t4 = (plugin_0, opts) => {
			const priority = opts?.priority ?? 0;
			pluginsRef.current.set(plugin_0, priority);
			refreshSnapshot();
			return () => {
				pluginsRef.current.delete(plugin_0);
				refreshSnapshot();
			};
		};
		$[3] = t4;
	} else t4 = $[3];
	const register = t4;
	let t5;
	if ($[4] === Symbol.for("react.memo_cache_sentinel")) {
		t5 = () => snapshotRef.current;
		$[4] = t5;
	} else t5 = $[4];
	const getPlugins = t5;
	let t6;
	if ($[5] === Symbol.for("react.memo_cache_sentinel")) {
		t6 = {
			register,
			getPlugins
		};
		$[5] = t6;
	} else t6 = $[5];
	const registry = t6;
	let t7;
	if ($[6] !== children) {
		t7 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ComposerInputPluginRegistryContext.Provider, {
			value: registry,
			children
		});
		$[6] = children;
		$[7] = t7;
	} else t7 = $[7];
	return t7;
};
function _temp$18(a, b) {
	return b[1] - a[1];
}
function _temp2$5(t0) {
	const [plugin] = t0;
	return plugin;
}
var TriggerPopoverRootContext = createContext(null);
var TriggerPopoverAriaPublishContext = createContext(null);
var useTriggerPopoverRootContext = () => {
	const ctx = useContext(TriggerPopoverRootContext);
	if (!ctx) throw new Error("useTriggerPopoverRootContext must be used within ComposerPrimitive.TriggerPopoverRoot");
	return ctx;
};
var useTriggerPopoverRootContextOptional = () => {
	return useContext(TriggerPopoverRootContext);
};
/**
* Internal hook used by `TriggerPopover` children to publish their open and
* highlight state. Not exported from the trigger module.
*/
var useTriggerPopoverAriaPublish = () => {
	const ctx = useContext(TriggerPopoverAriaPublishContext);
	if (!ctx) throw new Error("useTriggerPopoverAriaPublish must be used within ComposerPrimitive.TriggerPopoverRoot");
	return ctx;
};
/**
* Live map of registered triggers, re-rendering on change. Prefer
* `subscribeLifecycle` for incremental add/remove handling.
*/
var useTriggerPopoverTriggers = () => {
	const ctx = useTriggerPopoverRootContext();
	return useSyncExternalStore(ctx.subscribe, ctx.getTriggers, ctx.getTriggers);
};
var EMPTY_TRIGGERS = /* @__PURE__ */ new Map();
var noopSubscribe = () => () => {};
var getEmptyTriggers = () => EMPTY_TRIGGERS;
/** Like `useTriggerPopoverTriggers` but returns an empty map outside a root. */
var useTriggerPopoverTriggersOptional = () => {
	const ctx = useTriggerPopoverRootContextOptional();
	return useSyncExternalStore(ctx ? ctx.subscribe : noopSubscribe, ctx ? ctx.getTriggers : getEmptyTriggers, ctx ? ctx.getTriggers : getEmptyTriggers);
};
var getNullAria = () => null;
/**
* Returns the ARIA descriptor of the currently open trigger popover, or
* `null` if none is open or the consumer is rendered outside a
* `TriggerPopoverRoot`.
*/
var useTriggerPopoverActiveAriaOptional = () => {
	const ctx = useTriggerPopoverRootContextOptional();
	return useSyncExternalStore(ctx ? ctx.subscribeAria : noopSubscribe, ctx ? ctx.getActiveAria : getNullAria, ctx ? ctx.getActiveAria : getNullAria);
};
/**
* Local helper for the simple "notify-all listeners" subscribable pattern.
* Used twice in this file (trigger registry, active ARIA); kept inline to
* avoid pulling a single-use abstraction into the wider tree.
*/
function useSimpleSubscribable() {
	const $ = c(4);
	let t0;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t0 = /* @__PURE__ */ new Set();
		$[0] = t0;
	} else t0 = $[0];
	const listenersRef = useRef(t0);
	let t1;
	if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = () => {
			for (const listener of listenersRef.current) listener();
		};
		$[1] = t1;
	} else t1 = $[1];
	const notify = t1;
	let t2;
	if ($[2] === Symbol.for("react.memo_cache_sentinel")) {
		t2 = (listener_0) => {
			listenersRef.current.add(listener_0);
			return () => {
				listenersRef.current.delete(listener_0);
			};
		};
		$[2] = t2;
	} else t2 = $[2];
	const subscribe = t2;
	let t3;
	if ($[3] === Symbol.for("react.memo_cache_sentinel")) {
		t3 = {
			notify,
			subscribe
		};
		$[3] = t3;
	} else t3 = $[3];
	return t3;
}
var TriggerPopoverRootInner = (t0) => {
	const $ = c(21);
	const { children } = t0;
	let t1;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = /* @__PURE__ */ new Map();
		$[0] = t1;
	} else t1 = $[0];
	const triggersRef = useRef(t1);
	let t2;
	if ($[1] === Symbol.for("react.memo_cache_sentinel")) {
		t2 = /* @__PURE__ */ new Set();
		$[1] = t2;
	} else t2 = $[1];
	const lifecycleListenersRef = useRef(t2);
	const { notify, subscribe } = useSimpleSubscribable();
	let t3;
	if ($[2] !== notify) {
		t3 = (trigger) => {
			const { char } = trigger;
			if (triggersRef.current.has(char)) return _temp$17;
			const next = new Map(triggersRef.current);
			next.set(char, trigger);
			triggersRef.current = next;
			notify();
			for (const l of lifecycleListenersRef.current) l.added(trigger);
			return () => {
				const after = new Map(triggersRef.current);
				after.delete(char);
				triggersRef.current = after;
				notify();
				for (const l_0 of lifecycleListenersRef.current) l_0.removed(char);
			};
		};
		$[2] = notify;
		$[3] = t3;
	} else t3 = $[3];
	const register = t3;
	let t4;
	if ($[4] === Symbol.for("react.memo_cache_sentinel")) {
		t4 = () => triggersRef.current;
		$[4] = t4;
	} else t4 = $[4];
	const getTriggers = t4;
	let t5;
	if ($[5] === Symbol.for("react.memo_cache_sentinel")) {
		t5 = (listener) => {
			lifecycleListenersRef.current.add(listener);
			return () => {
				lifecycleListenersRef.current.delete(listener);
			};
		};
		$[5] = t5;
	} else t5 = $[5];
	const subscribeLifecycle = t5;
	const activeAriaRef = useRef(null);
	const activeAriaCharRef = useRef(null);
	const { notify: notifyAria, subscribe: subscribeAria } = useSimpleSubscribable();
	let t6;
	if ($[6] !== notifyAria) {
		t6 = (char_0, aria) => {
			if (aria === null) {
				if (activeAriaCharRef.current !== char_0) return;
				activeAriaRef.current = null;
				activeAriaCharRef.current = null;
				notifyAria();
				return;
			}
			const prev = activeAriaRef.current;
			if (activeAriaCharRef.current === char_0 && prev !== null && prev.popoverId === aria.popoverId && prev.highlightedItemId === aria.highlightedItemId) return;
			activeAriaRef.current = aria;
			activeAriaCharRef.current = char_0;
			notifyAria();
		};
		$[6] = notifyAria;
		$[7] = t6;
	} else t6 = $[7];
	const setActiveAria = t6;
	let t7;
	if ($[8] === Symbol.for("react.memo_cache_sentinel")) {
		t7 = () => activeAriaRef.current;
		$[8] = t7;
	} else t7 = $[8];
	const getActiveAria = t7;
	let t8;
	if ($[9] !== register || $[10] !== subscribe || $[11] !== subscribeAria) {
		t8 = {
			register,
			getTriggers,
			subscribe,
			subscribeLifecycle,
			getActiveAria,
			subscribeAria
		};
		$[9] = register;
		$[10] = subscribe;
		$[11] = subscribeAria;
		$[12] = t8;
	} else t8 = $[12];
	const value = t8;
	let t9;
	if ($[13] !== setActiveAria) {
		t9 = { setActiveAria };
		$[13] = setActiveAria;
		$[14] = t9;
	} else t9 = $[14];
	const ariaPublishValue = t9;
	let t10;
	if ($[15] !== ariaPublishValue || $[16] !== children) {
		t10 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TriggerPopoverAriaPublishContext.Provider, {
			value: ariaPublishValue,
			children
		});
		$[15] = ariaPublishValue;
		$[16] = children;
		$[17] = t10;
	} else t10 = $[17];
	let t11;
	if ($[18] !== t10 || $[19] !== value) {
		t11 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TriggerPopoverRootContext.Provider, {
			value,
			children: t10
		});
		$[18] = t10;
		$[19] = value;
		$[20] = t11;
	} else t11 = $[20];
	return t11;
};
/**
* Provider that groups one or more `TriggerPopover` declarations. Each trigger
* is identified by its `char` (unique within the root). Behavior is contributed
* by a child `TriggerPopover.Directive` or `TriggerPopover.Action`.
*
* @example
* ```tsx
* <ComposerPrimitive.Unstable_TriggerPopoverRoot>
*   <ComposerPrimitive.Unstable_TriggerPopover char="@" adapter={mentionAdapter}>
*     <ComposerPrimitive.Unstable_TriggerPopover.Directive formatter={formatter} />
*     ...
*   </ComposerPrimitive.Unstable_TriggerPopover>
*
*   <ComposerPrimitive.Unstable_TriggerPopover char="/" adapter={slashAdapter}>
*     <ComposerPrimitive.Unstable_TriggerPopover.Action onExecute={handler} />
*     ...
*   </ComposerPrimitive.Unstable_TriggerPopover>
*
*   <ComposerPrimitive.Root>
*     <ComposerPrimitive.Input />
*   </ComposerPrimitive.Root>
* </ComposerPrimitive.Unstable_TriggerPopoverRoot>
* ```
*/
var ComposerPrimitiveTriggerPopoverRoot = (t0) => {
	const $ = c(4);
	const { children } = t0;
	if (useComposerInputPluginRegistryOptional()) {
		let t1;
		if ($[0] !== children) {
			t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TriggerPopoverRootInner, { children });
			$[0] = children;
			$[1] = t1;
		} else t1 = $[1];
		return t1;
	}
	let t1;
	if ($[2] !== children) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ComposerInputPluginProvider, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TriggerPopoverRootInner, { children }) });
		$[2] = children;
		$[3] = t1;
	} else t1 = $[3];
	return t1;
};
ComposerPrimitiveTriggerPopoverRoot.displayName = "ComposerPrimitive.TriggerPopoverRoot";
function _temp$17() {}
var WHITESPACE_RE = /\s/u;
/**
* Detect a trigger character in text relative to the cursor position.
*
* @internal Exported for testing and for trigger resources.
*/
function detectTrigger(text, triggerChar, cursorPosition) {
	const textUpToCursor = text.slice(0, cursorPosition);
	for (let i = textUpToCursor.length - 1; i >= 0; i--) {
		const char = textUpToCursor[i];
		if (WHITESPACE_RE.test(char)) return null;
		if (textUpToCursor.startsWith(triggerChar, i)) {
			if (i > 0 && !WHITESPACE_RE.test(textUpToCursor[i - 1])) continue;
			return {
				query: textUpToCursor.slice(i + triggerChar.length),
				offset: i
			};
		}
	}
	return null;
}
/** Tracks cursor position and derives the active trigger + query from composer text. */
var useTriggerDetectionResource = (t0) => {
	const $ = c(7);
	const { text, triggerChar } = t0;
	const [cursorPosition, setCursorPosition] = useState(text.length);
	const pos = Math.min(cursorPosition, text.length);
	let t1;
	if ($[0] !== pos || $[1] !== text || $[2] !== triggerChar) {
		t1 = detectTrigger(text, triggerChar, pos);
		$[0] = pos;
		$[1] = text;
		$[2] = triggerChar;
		$[3] = t1;
	} else t1 = $[3];
	const trigger = t1;
	const query = trigger?.query ?? "";
	let t2;
	if ($[4] !== query || $[5] !== trigger) {
		t2 = {
			trigger,
			query,
			setCursorPosition
		};
		$[4] = query;
		$[5] = trigger;
		$[6] = t2;
	} else t2 = $[6];
	return t2;
};
var TriggerDetectionResource = resource(useTriggerDetectionResource);
/** Relies on `Unstable_TriggerCategory` never carrying a `type` field. */
function isTriggerItem(x) {
	return "type" in x;
}
/**
* Owns keyboard-driven highlight state for the popover. Delegates selection,
* category drill-in, back, and close to the callbacks supplied by the parent.
*/
var useTriggerKeyboardResource = (t0) => {
	const $ = c(25);
	const { navigableList, isSearchMode, activeCategoryId, query, popoverId, open, selectItem, selectCategory, goBack, close } = t0;
	const [highlightedIndex, setHighlightedIndex] = useState(0);
	let t1;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = () => {
			setHighlightedIndex(0);
		};
		$[0] = t1;
	} else t1 = $[0];
	let t2;
	if ($[1] !== navigableList) {
		t2 = [navigableList];
		$[1] = navigableList;
		$[2] = t2;
	} else t2 = $[2];
	useEffect(t1, t2);
	let t3;
	if ($[3] === Symbol.for("react.memo_cache_sentinel")) {
		t3 = () => {
			setHighlightedIndex(0);
		};
		$[3] = t3;
	} else t3 = $[3];
	let t4;
	if ($[4] !== activeCategoryId || $[5] !== isSearchMode) {
		t4 = [isSearchMode, activeCategoryId];
		$[4] = activeCategoryId;
		$[5] = isSearchMode;
		$[6] = t4;
	} else t4 = $[6];
	useEffect(t3, t4);
	let t5;
	if ($[7] !== highlightedIndex || $[8] !== navigableList.length) {
		t5 = (index) => {
			if (index < 0 || index >= navigableList.length) return;
			if (index === highlightedIndex) return;
			setHighlightedIndex(index);
		};
		$[7] = highlightedIndex;
		$[8] = navigableList.length;
		$[9] = t5;
	} else t5 = $[9];
	const highlightIndex = useEffectEvent$1(t5);
	let t6;
	if ($[10] !== activeCategoryId || $[11] !== close || $[12] !== goBack || $[13] !== highlightedIndex || $[14] !== navigableList || $[15] !== open || $[16] !== query || $[17] !== selectCategory || $[18] !== selectItem) {
		t6 = (e) => {
			if (!open) return false;
			switch (e.key) {
				case "ArrowDown":
					e.preventDefault();
					setHighlightedIndex((prev_0) => {
						const len_0 = navigableList.length;
						if (len_0 === 0) return 0;
						return prev_0 < len_0 - 1 ? prev_0 + 1 : 0;
					});
					return true;
				case "ArrowUp":
					e.preventDefault();
					setHighlightedIndex((prev) => {
						const len = navigableList.length;
						if (len === 0) return 0;
						return prev > 0 ? prev - 1 : len - 1;
					});
					return true;
				case "Enter":
				case "Tab": {
					if (e.shiftKey) return false;
					e.preventDefault();
					const item = navigableList[highlightedIndex];
					if (!item) return true;
					if (isTriggerItem(item)) selectItem(item);
					else selectCategory(item.id);
					return true;
				}
				case "Escape":
					e.preventDefault();
					close();
					return true;
				case "Backspace":
					if (activeCategoryId && query === "") {
						e.preventDefault();
						goBack();
						return true;
					}
					return false;
				default: return false;
			}
		};
		$[10] = activeCategoryId;
		$[11] = close;
		$[12] = goBack;
		$[13] = highlightedIndex;
		$[14] = navigableList;
		$[15] = open;
		$[16] = query;
		$[17] = selectCategory;
		$[18] = selectItem;
		$[19] = t6;
	} else t6 = $[19];
	const handleKeyDown = useEffectEvent$1(t6);
	const highlightedEntry = navigableList[highlightedIndex];
	const highlightedItemId = open && highlightedEntry ? `${popoverId}-option-${highlightedEntry.id}` : void 0;
	let t7;
	if ($[20] !== handleKeyDown || $[21] !== highlightIndex || $[22] !== highlightedIndex || $[23] !== highlightedItemId) {
		t7 = {
			highlightedIndex,
			highlightedItemId,
			highlightIndex,
			handleKeyDown
		};
		$[20] = handleKeyDown;
		$[21] = highlightIndex;
		$[22] = highlightedIndex;
		$[23] = highlightedItemId;
		$[24] = t7;
	} else t7 = $[24];
	return t7;
};
var TriggerKeyboardResource = resource(useTriggerKeyboardResource);
function matchesQuery$1(item, lower) {
	return item.id.toLowerCase().includes(lower) || item.label.toLowerCase().includes(lower) || (item.description?.toLowerCase().includes(lower) ?? false);
}
/**
* Computes categories, items, search results, and navigation state from the
* adapter + current query. Pure derivation — no side effects on the composer.
*/
var useTriggerNavigationResource = (t0) => {
	const $ = c(38);
	const { adapter, query, open } = t0;
	const [activeCategoryId, setActiveCategoryId] = useState(null);
	let t1;
	let t2;
	if ($[0] !== open) {
		t1 = () => {
			if (!open) setActiveCategoryId(null);
		};
		t2 = [open];
		$[0] = open;
		$[1] = t1;
		$[2] = t2;
	} else {
		t1 = $[1];
		t2 = $[2];
	}
	useEffect(t1, t2);
	let t3;
	bb0: {
		if (!open || !adapter) {
			let t4;
			if ($[3] === Symbol.for("react.memo_cache_sentinel")) {
				t4 = [];
				$[3] = t4;
			} else t4 = $[3];
			t3 = t4;
			break bb0;
		}
		let t4;
		if ($[4] !== adapter) {
			t4 = adapter.categories();
			$[4] = adapter;
			$[5] = t4;
		} else t4 = $[5];
		t3 = t4;
	}
	const categories = t3;
	const effectiveActiveCategoryId = open ? activeCategoryId : null;
	let t4;
	bb1: {
		if (!effectiveActiveCategoryId || !adapter) {
			let t5;
			if ($[6] === Symbol.for("react.memo_cache_sentinel")) {
				t5 = [];
				$[6] = t5;
			} else t5 = $[6];
			t4 = t5;
			break bb1;
		}
		let t5;
		if ($[7] !== adapter || $[8] !== effectiveActiveCategoryId) {
			t5 = adapter.categoryItems(effectiveActiveCategoryId);
			$[7] = adapter;
			$[8] = effectiveActiveCategoryId;
			$[9] = t5;
		} else t5 = $[9];
		t4 = t5;
	}
	const allItems = t4;
	let t5;
	bb2: {
		if (!open || !adapter || effectiveActiveCategoryId) {
			t5 = null;
			break bb2;
		}
		if (!query && categories.length > 0) {
			t5 = null;
			break bb2;
		}
		if (adapter.search) {
			let t6;
			if ($[10] !== adapter || $[11] !== query) {
				t6 = adapter.search(query);
				$[10] = adapter;
				$[11] = query;
				$[12] = t6;
			} else t6 = $[12];
			t5 = t6;
			break bb2;
		}
		let all;
		if ($[13] !== adapter || $[14] !== categories || $[15] !== query) {
			all = [];
			const lower = query.toLowerCase();
			for (const cat of categories) for (const item of adapter.categoryItems(cat.id)) if (matchesQuery$1(item, lower)) all.push(item);
			$[13] = adapter;
			$[14] = categories;
			$[15] = query;
			$[16] = all;
		} else all = $[16];
		t5 = all;
	}
	const searchResults = t5;
	const isSearchMode = searchResults !== null;
	let t6;
	bb3: {
		if (isSearchMode) {
			let t7;
			if ($[17] === Symbol.for("react.memo_cache_sentinel")) {
				t7 = [];
				$[17] = t7;
			} else t7 = $[17];
			t6 = t7;
			break bb3;
		}
		if (!query) {
			t6 = categories;
			break bb3;
		}
		let t7;
		if ($[18] !== categories || $[19] !== query) {
			const lower_0 = query.toLowerCase();
			t7 = categories.filter((cat_0) => cat_0.label.toLowerCase().includes(lower_0));
			$[18] = categories;
			$[19] = query;
			$[20] = t7;
		} else t7 = $[20];
		t6 = t7;
	}
	const filteredCategories = t6;
	let t7;
	bb4: {
		if (isSearchMode) {
			let t8;
			if ($[21] !== searchResults) {
				t8 = searchResults ?? [];
				$[21] = searchResults;
				$[22] = t8;
			} else t8 = $[22];
			t7 = t8;
			break bb4;
		}
		if (!query) {
			t7 = allItems;
			break bb4;
		}
		let t8;
		if ($[23] !== allItems || $[24] !== query) {
			const lower_1 = query.toLowerCase();
			t8 = allItems.filter((item_0) => matchesQuery$1(item_0, lower_1));
			$[23] = allItems;
			$[24] = query;
			$[25] = t8;
		} else t8 = $[25];
		t7 = t8;
	}
	const filteredItems = t7;
	let t8;
	bb5: {
		if (isSearchMode) {
			let t9;
			if ($[26] !== searchResults) {
				t9 = searchResults ?? [];
				$[26] = searchResults;
				$[27] = t9;
			} else t9 = $[27];
			t8 = t9;
			break bb5;
		}
		if (effectiveActiveCategoryId) {
			t8 = filteredItems;
			break bb5;
		}
		t8 = filteredCategories;
	}
	const navigableList = t8;
	let t9;
	if ($[28] === Symbol.for("react.memo_cache_sentinel")) {
		t9 = (categoryId) => {
			setActiveCategoryId(categoryId);
		};
		$[28] = t9;
	} else t9 = $[28];
	const selectCategory = useEffectEvent$1(t9);
	let t10;
	if ($[29] === Symbol.for("react.memo_cache_sentinel")) {
		t10 = () => {
			setActiveCategoryId(null);
		};
		$[29] = t10;
	} else t10 = $[29];
	const goBack = useEffectEvent$1(t10);
	let t11;
	if ($[30] !== effectiveActiveCategoryId || $[31] !== filteredCategories || $[32] !== filteredItems || $[33] !== goBack || $[34] !== isSearchMode || $[35] !== navigableList || $[36] !== selectCategory) {
		t11 = {
			categories: filteredCategories,
			items: filteredItems,
			isSearchMode,
			activeCategoryId: effectiveActiveCategoryId,
			navigableList,
			selectCategory,
			goBack
		};
		$[30] = effectiveActiveCategoryId;
		$[31] = filteredCategories;
		$[32] = filteredItems;
		$[33] = goBack;
		$[34] = isSearchMode;
		$[35] = navigableList;
		$[36] = selectCategory;
		$[37] = t11;
	} else t11 = $[37];
	return t11;
};
var TriggerNavigationResource = resource(useTriggerNavigationResource);
/** Owns composer text mutation + behavior dispatch on item selection. */
var useTriggerSelectionResource = (t0) => {
	const $ = c(15);
	const { behavior, trigger, aui, triggerChar, setCursorPosition, onSelected } = t0;
	const selectItemOverrideRef = useRef(null);
	let t1;
	if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = (fn) => {
			selectItemOverrideRef.current = fn;
			return () => {
				if (selectItemOverrideRef.current === fn) selectItemOverrideRef.current = null;
			};
		};
		$[0] = t1;
	} else t1 = $[0];
	const registerSelectItemOverride = useEffectEvent$1(t1);
	let t2;
	if ($[1] !== aui || $[2] !== behavior || $[3] !== onSelected || $[4] !== trigger || $[5] !== triggerChar) {
		t2 = (item) => {
			if (!trigger || !behavior) return;
			if (selectItemOverrideRef.current?.(item)) {
				onSelected();
				return;
			}
			const currentText = aui.composer().getState().text;
			const before = currentText.slice(0, trigger.offset);
			const after = currentText.slice(trigger.offset + triggerChar.length + trigger.query.length);
			const insertDirective = () => {
				const directive = behavior.formatter.serialize(item);
				aui.composer().setText(before + directive + (after.startsWith(" ") ? after : ` ${after}`));
			};
			if (behavior.kind === "directive") {
				insertDirective();
				behavior.onInserted?.(item);
			} else {
				if (behavior.removeOnExecute) aui.composer().setText(before + (after.startsWith(" ") ? after.slice(1) : after));
				else insertDirective();
				behavior.onExecute(item);
			}
			onSelected();
		};
		$[1] = aui;
		$[2] = behavior;
		$[3] = onSelected;
		$[4] = trigger;
		$[5] = triggerChar;
		$[6] = t2;
	} else t2 = $[6];
	const selectItem = useEffectEvent$1(t2);
	let t3;
	if ($[7] !== onSelected || $[8] !== setCursorPosition || $[9] !== trigger) {
		t3 = () => {
			onSelected();
			if (trigger) setCursorPosition(trigger.offset);
		};
		$[7] = onSelected;
		$[8] = setCursorPosition;
		$[9] = trigger;
		$[10] = t3;
	} else t3 = $[10];
	const close = useEffectEvent$1(t3);
	let t4;
	if ($[11] !== close || $[12] !== registerSelectItemOverride || $[13] !== selectItem) {
		t4 = {
			selectItem,
			close,
			registerSelectItemOverride
		};
		$[11] = close;
		$[12] = registerSelectItemOverride;
		$[13] = selectItem;
		$[14] = t4;
	} else t4 = $[14];
	return t4;
};
var TriggerSelectionResource = resource(useTriggerSelectionResource);
/** Composes detection, navigation, keyboard, and selection sub-resources. */
var useTriggerPopoverResource = (t0) => {
	const $ = c(46);
	const { adapter, text, triggerChar, behavior, aui, popoverId, isLoading } = t0;
	let t1;
	if ($[0] !== text || $[1] !== triggerChar) {
		t1 = TriggerDetectionResource({
			text,
			triggerChar
		});
		$[0] = text;
		$[1] = triggerChar;
		$[2] = t1;
	} else t1 = $[2];
	const detection = useResource(t1);
	const open = detection.trigger !== null && adapter !== void 0 && behavior !== void 0;
	let t2;
	if ($[3] !== adapter || $[4] !== detection.query || $[5] !== open) {
		t2 = TriggerNavigationResource({
			adapter,
			query: detection.query,
			open
		});
		$[3] = adapter;
		$[4] = detection.query;
		$[5] = open;
		$[6] = t2;
	} else t2 = $[6];
	const navigation = useResource(t2);
	let t3;
	if ($[7] !== navigation) {
		t3 = () => {
			navigation.goBack();
		};
		$[7] = navigation;
		$[8] = t3;
	} else t3 = $[8];
	const onSelected = useEffectEvent$1(t3);
	let t4;
	if ($[9] !== aui || $[10] !== behavior || $[11] !== detection.setCursorPosition || $[12] !== detection.trigger || $[13] !== onSelected || $[14] !== triggerChar) {
		t4 = TriggerSelectionResource({
			behavior,
			trigger: detection.trigger,
			aui,
			triggerChar,
			setCursorPosition: detection.setCursorPosition,
			onSelected
		});
		$[9] = aui;
		$[10] = behavior;
		$[11] = detection.setCursorPosition;
		$[12] = detection.trigger;
		$[13] = onSelected;
		$[14] = triggerChar;
		$[15] = t4;
	} else t4 = $[15];
	const selection = useResource(t4);
	let t5;
	if ($[16] !== detection.query || $[17] !== navigation.activeCategoryId || $[18] !== navigation.goBack || $[19] !== navigation.isSearchMode || $[20] !== navigation.navigableList || $[21] !== navigation.selectCategory || $[22] !== open || $[23] !== popoverId || $[24] !== selection.close || $[25] !== selection.selectItem) {
		t5 = TriggerKeyboardResource({
			navigableList: navigation.navigableList,
			isSearchMode: navigation.isSearchMode,
			activeCategoryId: navigation.activeCategoryId,
			query: detection.query,
			popoverId,
			open,
			selectItem: selection.selectItem,
			selectCategory: navigation.selectCategory,
			goBack: navigation.goBack,
			close: selection.close
		});
		$[16] = detection.query;
		$[17] = navigation.activeCategoryId;
		$[18] = navigation.goBack;
		$[19] = navigation.isSearchMode;
		$[20] = navigation.navigableList;
		$[21] = navigation.selectCategory;
		$[22] = open;
		$[23] = popoverId;
		$[24] = selection.close;
		$[25] = selection.selectItem;
		$[26] = t5;
	} else t5 = $[26];
	const keyboard = useResource(t5);
	let t6;
	if ($[27] !== detection.query || $[28] !== detection.setCursorPosition || $[29] !== isLoading || $[30] !== keyboard.handleKeyDown || $[31] !== keyboard.highlightIndex || $[32] !== keyboard.highlightedIndex || $[33] !== keyboard.highlightedItemId || $[34] !== navigation.activeCategoryId || $[35] !== navigation.categories || $[36] !== navigation.goBack || $[37] !== navigation.isSearchMode || $[38] !== navigation.items || $[39] !== navigation.selectCategory || $[40] !== open || $[41] !== popoverId || $[42] !== selection.close || $[43] !== selection.registerSelectItemOverride || $[44] !== selection.selectItem) {
		t6 = {
			open,
			query: detection.query,
			activeCategoryId: navigation.activeCategoryId,
			categories: navigation.categories,
			items: navigation.items,
			highlightedIndex: keyboard.highlightedIndex,
			isSearchMode: navigation.isSearchMode,
			isLoading,
			popoverId,
			highlightedItemId: keyboard.highlightedItemId,
			selectCategory: navigation.selectCategory,
			goBack: navigation.goBack,
			selectItem: selection.selectItem,
			close: selection.close,
			highlightIndex: keyboard.highlightIndex,
			handleKeyDown: keyboard.handleKeyDown,
			setCursorPosition: detection.setCursorPosition,
			registerSelectItemOverride: selection.registerSelectItemOverride
		};
		$[27] = detection.query;
		$[28] = detection.setCursorPosition;
		$[29] = isLoading;
		$[30] = keyboard.handleKeyDown;
		$[31] = keyboard.highlightIndex;
		$[32] = keyboard.highlightedIndex;
		$[33] = keyboard.highlightedItemId;
		$[34] = navigation.activeCategoryId;
		$[35] = navigation.categories;
		$[36] = navigation.goBack;
		$[37] = navigation.isSearchMode;
		$[38] = navigation.items;
		$[39] = navigation.selectCategory;
		$[40] = open;
		$[41] = popoverId;
		$[42] = selection.close;
		$[43] = selection.registerSelectItemOverride;
		$[44] = selection.selectItem;
		$[45] = t6;
	} else t6 = $[45];
	return t6;
};
var TriggerPopoverResource = resource(useTriggerPopoverResource);
var TriggerPopoverScopeContext = createContext(null);
var useTriggerPopoverScopeContext = () => {
	const ctx = useContext(TriggerPopoverScopeContext);
	if (!ctx) throw new Error("useTriggerPopoverScopeContext must be used within ComposerPrimitive.TriggerPopover");
	return ctx;
};
var useTriggerPopoverScopeContextOptional = () => {
	return useContext(TriggerPopoverScopeContext);
};
var TriggerBehaviorRegistrationContext = createContext(null);
/** Obtain the registration handle from the parent `<TriggerPopover>`. */
var useTriggerBehaviorRegistration = () => {
	const ctx = useContext(TriggerBehaviorRegistrationContext);
	if (!ctx) throw new Error("TriggerPopover.Directive / TriggerPopover.Action must be rendered inside ComposerPrimitive.TriggerPopover");
	return ctx;
};
/**
* Declares a trigger and renders its popover container. The popover only
* renders its DOM (and children) when the trigger character is active in the
* composer input and a behavior sub-primitive has been registered.
*
* A behavior is contributed by rendering exactly one of
* `<TriggerPopover.Directive>` or `<TriggerPopover.Action>` as a child. Without
* a behavior the trigger stays closed.
*
* Must be placed inside `ComposerPrimitive.Unstable_TriggerPopoverRoot`.
*
* @example
* ```tsx
* <ComposerPrimitive.Unstable_TriggerPopover
*   char="@"
*   adapter={mentionAdapter}
* >
*   <ComposerPrimitive.Unstable_TriggerPopover.Directive formatter={formatter} />
*   <ComposerPrimitive.Unstable_TriggerPopoverCategories>
*     {(cats) => cats.map(...)}
*   </ComposerPrimitive.Unstable_TriggerPopoverCategories>
*   <ComposerPrimitive.Unstable_TriggerPopoverItems>
*     {(items) => items.map(...)}
*   </ComposerPrimitive.Unstable_TriggerPopoverItems>
* </ComposerPrimitive.Unstable_TriggerPopover>
* ```
*/
var ComposerPrimitiveTriggerPopover$1 = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(61);
	let adapter;
	let ariaLabel;
	let char;
	let children;
	let props;
	let t1;
	if ($[0] !== t0) {
		({char, adapter, isLoading: t1, "aria-label": ariaLabel, children, ...props} = t0);
		$[0] = t0;
		$[1] = adapter;
		$[2] = ariaLabel;
		$[3] = char;
		$[4] = children;
		$[5] = props;
		$[6] = t1;
	} else {
		adapter = $[1];
		ariaLabel = $[2];
		char = $[3];
		children = $[4];
		props = $[5];
		t1 = $[6];
	}
	const isLoading = t1 === void 0 ? false : t1;
	const aui = useAui();
	const text = useAuiState(_temp$16);
	const popoverId = (0, react_shim_exports.useId)();
	const behaviorRef = useRef(null);
	const [behavior, setBehavior] = useState(null);
	const registrationCountRef = useRef(0);
	let t2;
	if ($[7] !== char) {
		t2 = (next) => {
			registrationCountRef.current = registrationCountRef.current + 1;
			behaviorRef.current = next;
			setBehavior(next);
			return () => {
				registrationCountRef.current = Math.max(0, registrationCountRef.current - 1);
				if (behaviorRef.current === next) {
					behaviorRef.current = null;
					setBehavior(null);
				}
			};
		};
		$[7] = char;
		$[8] = t2;
	} else t2 = $[8];
	const register = t2;
	let t3;
	if ($[9] !== register) {
		t3 = { register };
		$[9] = register;
		$[10] = t3;
	} else t3 = $[10];
	const registration = t3;
	const t4 = behavior ?? void 0;
	let t5;
	if ($[11] !== adapter || $[12] !== aui || $[13] !== char || $[14] !== isLoading || $[15] !== popoverId || $[16] !== t4 || $[17] !== text) {
		t5 = TriggerPopoverResource({
			adapter,
			text,
			triggerChar: char,
			behavior: t4,
			aui,
			popoverId,
			isLoading
		});
		$[11] = adapter;
		$[12] = aui;
		$[13] = char;
		$[14] = isLoading;
		$[15] = popoverId;
		$[16] = t4;
		$[17] = text;
		$[18] = t5;
	} else t5 = $[18];
	const resource = useResource(t5);
	let t6;
	if ($[19] !== resource) {
		t6 = () => resource;
		$[19] = resource;
		$[20] = t6;
	} else t6 = $[20];
	const getResource = useEffectEvent$1(t6);
	const root = useTriggerPopoverRootContext();
	let t7;
	if ($[21] !== behavior || $[22] !== char || $[23] !== getResource || $[24] !== root) {
		t7 = () => root.register({
			char,
			...behavior ? { behavior } : {},
			resource: getResource()
		});
		$[21] = behavior;
		$[22] = char;
		$[23] = getResource;
		$[24] = root;
		$[25] = t7;
	} else t7 = $[25];
	let t8;
	if ($[26] !== behavior || $[27] !== char || $[28] !== root) {
		t8 = [
			root,
			char,
			behavior
		];
		$[26] = behavior;
		$[27] = char;
		$[28] = root;
		$[29] = t8;
	} else t8 = $[29];
	useEffect(t7, t8);
	const pluginRegistry = useComposerInputPluginRegistryOptional();
	let t9;
	if ($[30] !== getResource || $[31] !== pluginRegistry) {
		t9 = () => {
			if (!pluginRegistry) return;
			return pluginRegistry.register(getResource());
		};
		$[30] = getResource;
		$[31] = pluginRegistry;
		$[32] = t9;
	} else t9 = $[32];
	let t10;
	if ($[33] !== pluginRegistry) {
		t10 = [pluginRegistry];
		$[33] = pluginRegistry;
		$[34] = t10;
	} else t10 = $[34];
	useEffect(t9, t10);
	const open = behavior !== null && resource.open;
	const aria = useTriggerPopoverAriaPublish();
	let t11;
	let t12;
	if ($[35] !== aria || $[36] !== char || $[37] !== open) {
		t11 = () => {
			if (!open) return;
			return () => {
				aria.setActiveAria(char, null);
			};
		};
		t12 = [
			aria,
			char,
			open
		];
		$[35] = aria;
		$[36] = char;
		$[37] = open;
		$[38] = t11;
		$[39] = t12;
	} else {
		t11 = $[38];
		t12 = $[39];
	}
	useEffect(t11, t12);
	let t13;
	let t14;
	if ($[40] !== aria || $[41] !== char || $[42] !== open || $[43] !== popoverId || $[44] !== resource.highlightedItemId) {
		t13 = () => {
			if (!open) return;
			aria.setActiveAria(char, {
				popoverId,
				highlightedItemId: resource.highlightedItemId
			});
		};
		t14 = [
			aria,
			char,
			popoverId,
			open,
			resource.highlightedItemId
		];
		$[40] = aria;
		$[41] = char;
		$[42] = open;
		$[43] = popoverId;
		$[44] = resource.highlightedItemId;
		$[45] = t13;
		$[46] = t14;
	} else {
		t13 = $[45];
		t14 = $[46];
	}
	useEffect(t13, t14);
	let t15;
	if ($[47] !== ariaLabel || $[48] !== children || $[49] !== forwardedRef || $[50] !== open || $[51] !== popoverId || $[52] !== props || $[53] !== resource.highlightedItemId) {
		t15 = open ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			role: "listbox",
			id: popoverId,
			"aria-label": ariaLabel ?? "Suggestions",
			"aria-activedescendant": resource.highlightedItemId,
			"data-state": "open",
			...props,
			ref: forwardedRef,
			children
		}) : children;
		$[47] = ariaLabel;
		$[48] = children;
		$[49] = forwardedRef;
		$[50] = open;
		$[51] = popoverId;
		$[52] = props;
		$[53] = resource.highlightedItemId;
		$[54] = t15;
	} else t15 = $[54];
	let t16;
	if ($[55] !== resource || $[56] !== t15) {
		t16 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TriggerPopoverScopeContext.Provider, {
			value: resource,
			children: t15
		});
		$[55] = resource;
		$[56] = t15;
		$[57] = t16;
	} else t16 = $[57];
	let t17;
	if ($[58] !== registration || $[59] !== t16) {
		t17 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TriggerBehaviorRegistrationContext.Provider, {
			value: registration,
			children: t16
		});
		$[58] = registration;
		$[59] = t16;
		$[60] = t17;
	} else t17 = $[60];
	return t17;
});
ComposerPrimitiveTriggerPopover$1.displayName = "ComposerPrimitive.TriggerPopover";
function _temp$16(s) {
	return s.composer.text;
}
var useComposerSend = () => {
	const $ = c(2);
	const { disabled, send } = useComposerSend$1();
	let t0;
	if ($[0] !== send) {
		t0 = () => send();
		$[0] = send;
		$[1] = t0;
	} else t0 = $[1];
	const callback = t0;
	if (disabled) return null;
	return callback;
};
/**
* A button component that sends the current message in the composer.
*
* This component automatically handles the send functionality and is disabled
* when sending is not available (e.g., when the thread is running, the composer
* is empty, or not in editing mode).
*
* @example
* ```tsx
* <ComposerPrimitive.Send>
*   Send Message
* </ComposerPrimitive.Send>
* ```
*/
var ComposerPrimitiveSend = createActionButton("ComposerPrimitive.Send", useComposerSend);
/**
* The root form container for message composition.
*
* This component provides a form wrapper that handles message submission when the form
* is submitted (e.g., via Enter key or submit button). It automatically prevents the
* default form submission and triggers the composer's send functionality.
*
* @example
* ```tsx
* <ComposerPrimitive.Root>
*   <ComposerPrimitive.Input placeholder="Type your message..." />
*   <ComposerPrimitive.Send>Send</ComposerPrimitive.Send>
* </ComposerPrimitive.Root>
* ```
*/
var ComposerPrimitiveRoot = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(12);
	let onSubmit;
	let rest;
	if ($[0] !== t0) {
		({onSubmit, ...rest} = t0);
		$[0] = t0;
		$[1] = onSubmit;
		$[2] = rest;
	} else {
		onSubmit = $[1];
		rest = $[2];
	}
	const send = useComposerSend();
	let t1;
	if ($[3] !== send) {
		t1 = (e) => {
			e.preventDefault();
			if (!send) return;
			send();
		};
		$[3] = send;
		$[4] = t1;
	} else t1 = $[4];
	const handleSubmit = t1;
	let t2;
	if ($[5] !== handleSubmit || $[6] !== onSubmit) {
		t2 = composeEventHandlers(onSubmit, handleSubmit);
		$[5] = handleSubmit;
		$[6] = onSubmit;
		$[7] = t2;
	} else t2 = $[7];
	let t3;
	if ($[8] !== forwardedRef || $[9] !== rest || $[10] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.form, {
			...rest,
			ref: forwardedRef,
			onSubmit: t2
		});
		$[8] = forwardedRef;
		$[9] = rest;
		$[10] = t2;
		$[11] = t3;
	} else t3 = $[11];
	return t3;
});
ComposerPrimitiveRoot.displayName = "ComposerPrimitive.Root";
var useOnScrollToBottom = (callback) => {
	const $ = c(4);
	const callbackRef = useCallbackRef(callback);
	const onScrollToBottom = useThreadViewport(_temp$15);
	let t0;
	let t1;
	if ($[0] !== callbackRef || $[1] !== onScrollToBottom) {
		t0 = () => onScrollToBottom(callbackRef);
		t1 = [onScrollToBottom, callbackRef];
		$[0] = callbackRef;
		$[1] = onScrollToBottom;
		$[2] = t0;
		$[3] = t1;
	} else {
		t0 = $[2];
		t1 = $[3];
	}
	useEffect(t0, t1);
};
function _temp$15(vp) {
	return vp.onScrollToBottom;
}
var getServerSnapshot = () => false;
var noopUnsubscribe = () => {};
var useMediaQuery = (query) => {
	const $ = c(4);
	let t0;
	if ($[0] !== query) {
		t0 = (callback) => {
			if (typeof window === "undefined" || query === null) return noopUnsubscribe;
			const mql = window.matchMedia(query);
			mql.addEventListener("change", callback);
			return () => mql.removeEventListener("change", callback);
		};
		$[0] = query;
		$[1] = t0;
	} else t0 = $[1];
	const subscribe = t0;
	let t1;
	if ($[2] !== query) {
		t1 = () => {
			if (typeof window === "undefined" || query === null) return false;
			return window.matchMedia(query).matches;
		};
		$[2] = query;
		$[3] = t1;
	} else t1 = $[3];
	return useSyncExternalStore(subscribe, t1, getServerSnapshot);
};
function _extends() {
	return _extends = Object.assign ? Object.assign.bind() : function(n) {
		for (var e = 1; e < arguments.length; e++) {
			var t = arguments[e];
			for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]);
		}
		return n;
	}, _extends.apply(null, arguments);
}
function _objectWithoutPropertiesLoose(r, e) {
	if (null == r) return {};
	var t = {};
	for (var n in r) if ({}.hasOwnProperty.call(r, n)) {
		if (-1 !== e.indexOf(n)) continue;
		t[n] = r[n];
	}
	return t;
}
var index$1 = import_react.useLayoutEffect;
var useLatest = function useLatest(value) {
	var ref = import_react.useRef(value);
	index$1(function() {
		ref.current = value;
	});
	return ref;
};
var updateRef = function updateRef(ref, value) {
	if (typeof ref === "function") {
		ref(value);
		return;
	}
	ref.current = value;
};
var useComposedRef = function useComposedRef(libRef, userRef) {
	var prevUserRef = import_react.useRef();
	return import_react.useCallback(function(instance) {
		libRef.current = instance;
		if (prevUserRef.current) updateRef(prevUserRef.current, null);
		prevUserRef.current = userRef;
		if (!userRef) return;
		updateRef(userRef, instance);
	}, [userRef]);
};
var HIDDEN_TEXTAREA_STYLE = {
	"min-height": "0",
	"max-height": "none",
	height: "0",
	visibility: "hidden",
	overflow: "hidden",
	position: "absolute",
	"z-index": "-1000",
	top: "0",
	right: "0",
	display: "block"
};
var forceHiddenStyles$1 = function forceHiddenStyles(node) {
	Object.keys(HIDDEN_TEXTAREA_STYLE).forEach(function(key) {
		node.style.setProperty(key, HIDDEN_TEXTAREA_STYLE[key], "important");
	});
};
var hiddenTextarea = null;
var getHeight = function getHeight(node, sizingData) {
	var height = node.scrollHeight;
	if (sizingData.sizingStyle.boxSizing === "border-box") return height + sizingData.borderSize;
	return height - sizingData.paddingSize;
};
function calculateNodeHeight(sizingData, value, minRows, maxRows) {
	if (minRows === void 0) minRows = 1;
	if (maxRows === void 0) maxRows = Infinity;
	if (!hiddenTextarea) {
		hiddenTextarea = document.createElement("textarea");
		hiddenTextarea.setAttribute("tabindex", "-1");
		hiddenTextarea.setAttribute("aria-hidden", "true");
		forceHiddenStyles$1(hiddenTextarea);
	}
	if (hiddenTextarea.parentNode === null) document.body.appendChild(hiddenTextarea);
	var paddingSize = sizingData.paddingSize, borderSize = sizingData.borderSize, sizingStyle = sizingData.sizingStyle;
	var boxSizing = sizingStyle.boxSizing;
	Object.keys(sizingStyle).forEach(function(_key) {
		var key = _key;
		hiddenTextarea.style[key] = sizingStyle[key];
	});
	forceHiddenStyles$1(hiddenTextarea);
	hiddenTextarea.value = value;
	var height = getHeight(hiddenTextarea, sizingData);
	hiddenTextarea.value = value;
	height = getHeight(hiddenTextarea, sizingData);
	hiddenTextarea.value = "x";
	var rowHeight = hiddenTextarea.scrollHeight - paddingSize;
	var minHeight = rowHeight * minRows;
	if (boxSizing === "border-box") minHeight = minHeight + paddingSize + borderSize;
	height = Math.max(minHeight, height);
	var maxHeight = rowHeight * maxRows;
	if (boxSizing === "border-box") maxHeight = maxHeight + paddingSize + borderSize;
	height = Math.min(maxHeight, height);
	return [height, rowHeight];
}
var noop = function noop() {};
var pick = function pick(props, obj) {
	return props.reduce(function(acc, prop) {
		acc[prop] = obj[prop];
		return acc;
	}, {});
};
var SIZING_STYLE = [
	"borderBottomWidth",
	"borderLeftWidth",
	"borderRightWidth",
	"borderTopWidth",
	"boxSizing",
	"fontFamily",
	"fontSize",
	"fontStyle",
	"fontWeight",
	"letterSpacing",
	"lineHeight",
	"paddingBottom",
	"paddingLeft",
	"paddingRight",
	"paddingTop",
	"tabSize",
	"textIndent",
	"textRendering",
	"textTransform",
	"width",
	"wordBreak",
	"wordSpacing",
	"scrollbarGutter"
];
var isIE = !!document.documentElement.currentStyle;
var getSizingData$1 = function getSizingData(node) {
	var style = window.getComputedStyle(node);
	if (style === null) return null;
	var sizingStyle = pick(SIZING_STYLE, style);
	var boxSizing = sizingStyle.boxSizing;
	if (boxSizing === "") return null;
	if (isIE && boxSizing === "border-box") sizingStyle.width = parseFloat(sizingStyle.width) + parseFloat(sizingStyle.borderRightWidth) + parseFloat(sizingStyle.borderLeftWidth) + parseFloat(sizingStyle.paddingRight) + parseFloat(sizingStyle.paddingLeft) + "px";
	return {
		sizingStyle,
		paddingSize: parseFloat(sizingStyle.paddingBottom) + parseFloat(sizingStyle.paddingTop),
		borderSize: parseFloat(sizingStyle.borderBottomWidth) + parseFloat(sizingStyle.borderTopWidth)
	};
};
function useListener(target, type, listener) {
	var latestListener = useLatest(listener);
	import_react.useLayoutEffect(function() {
		var handler = function handler(ev) {
			return latestListener.current(ev);
		};
		if (!target) return;
		target.addEventListener(type, handler);
		return function() {
			return target.removeEventListener(type, handler);
		};
	}, []);
}
var useFormResetListener = function useFormResetListener(libRef, listener) {
	useListener(document.body, "reset", function(ev) {
		if (libRef.current.form === ev.target) listener(ev);
	});
};
var useWindowResizeListener = function useWindowResizeListener(listener) {
	useListener(window, "resize", listener);
};
var useFontsLoadedListener = function useFontsLoadedListener(listener) {
	useListener(document.fonts, "loadingdone", listener);
};
var _excluded = [
	"cacheMeasurements",
	"maxRows",
	"minRows",
	"onChange",
	"onHeightChange"
];
var index = /* #__PURE__ */ import_react.forwardRef(function TextareaAutosize(_ref, userRef) {
	var cacheMeasurements = _ref.cacheMeasurements, maxRows = _ref.maxRows, minRows = _ref.minRows, _ref$onChange = _ref.onChange, onChange = _ref$onChange === void 0 ? noop : _ref$onChange, _ref$onHeightChange = _ref.onHeightChange, onHeightChange = _ref$onHeightChange === void 0 ? noop : _ref$onHeightChange, props = _objectWithoutPropertiesLoose(_ref, _excluded);
	var isControlled = props.value !== void 0;
	var libRef = import_react.useRef(null);
	var ref = useComposedRef(libRef, userRef);
	var heightRef = import_react.useRef(0);
	var measurementsCacheRef = import_react.useRef();
	var resizeTextarea = function resizeTextarea() {
		var node = libRef.current;
		var nodeSizingData = cacheMeasurements && measurementsCacheRef.current ? measurementsCacheRef.current : getSizingData$1(node);
		if (!nodeSizingData) return;
		measurementsCacheRef.current = nodeSizingData;
		var _calculateNodeHeight = calculateNodeHeight(nodeSizingData, node.value || node.placeholder || "x", minRows, maxRows), height = _calculateNodeHeight[0], rowHeight = _calculateNodeHeight[1];
		if (heightRef.current !== height) {
			heightRef.current = height;
			node.style.setProperty("height", height + "px", "important");
			onHeightChange(height, { rowHeight });
		}
	};
	var handleChange = function handleChange(event) {
		if (!isControlled) resizeTextarea();
		onChange(event);
	};
	import_react.useLayoutEffect(resizeTextarea);
	useFormResetListener(libRef, function() {
		if (!isControlled) {
			var currentValue = libRef.current.value;
			requestAnimationFrame(function() {
				var node = libRef.current;
				if (node && currentValue !== node.value) resizeTextarea();
			});
		}
	});
	useWindowResizeListener(resizeTextarea);
	useFontsLoadedListener(resizeTextarea);
	return /*#__PURE__*/ import_react.createElement("textarea", _extends({}, props, {
		onChange: handleChange,
		ref
	}));
});
var TOUCH_PRIMARY_QUERY = "(pointer: coarse) and (not (any-pointer: fine))";
/**
* A text input component for composing messages.
*
* This component provides a rich text input experience with automatic resizing,
* keyboard shortcuts, file paste support, and intelligent focus management.
* It integrates with the composer context to manage message state and submission.
*
* When rendered inside `Unstable_TriggerPopoverRoot` and a popover is open, the
* underlying `<textarea>` automatically receives `aria-controls`,
* `aria-expanded`, `aria-haspopup`, and `aria-activedescendant` for the
* combobox relationship. These computed attributes override user-provided
* values for those four ARIA props while the popover is open.
*
* @example
* ```tsx
* // Ctrl/Cmd+Enter to submit (plain Enter inserts newline)
* <ComposerPrimitive.Input
*   placeholder="Type your message..."
*   submitMode="ctrlEnter"
* />
*
* // Insert a newline on Enter on touch-primary devices.
* <ComposerPrimitive.Input
*   placeholder="Type your message..."
*   unstable_insertNewlineOnTouchEnter
* />
*
* // Old API (deprecated, still supported)
* <ComposerPrimitive.Input
*   placeholder="Type your message..."
*   submitOnEnter={true}
* />
* ```
*/
var ComposerPrimitiveInput = (0, react_shim_exports.forwardRef)(({ autoFocus = false, asChild, render, disabled: disabledProp, onChange, onKeyDown, onPaste, onSelect, submitOnEnter, submitMode, cancelOnEscape = true, unstable_focusOnRunStart = true, unstable_focusOnScrollToBottom = true, unstable_focusOnThreadSwitched = true, unstable_insertNewlineOnTouchEnter = false, addAttachmentOnPaste = true, ...rest }, forwardedRef) => {
	const aui = useAui();
	const pluginRegistry = useComposerInputPluginRegistryOptional();
	const activeAria = useTriggerPopoverActiveAriaOptional();
	const declaredSubmitMode = submitMode ?? (submitOnEnter === false ? "none" : "enter");
	const isTouchPrimary = useMediaQuery(unstable_insertNewlineOnTouchEnter ? TOUCH_PRIMARY_QUERY : null);
	const effectiveSubmitMode = unstable_insertNewlineOnTouchEnter && isTouchPrimary && declaredSubmitMode === "enter" ? "none" : declaredSubmitMode;
	const value = useAuiState((s) => {
		if (!s.composer.isEditing) return "";
		return s.composer.text;
	});
	const isDisabled = useAuiState((s_0) => s_0.thread.isDisabled || s_0.composer.dictation?.inputDisabled) || disabledProp;
	const textareaRef = useRef(null);
	const ref = useComposedRefs(forwardedRef, textareaRef);
	const compositionRef = useRef(false);
	useEscapeKeydown((e) => {
		if (!textareaRef.current?.contains(e.target)) return;
		if (pluginRegistry) {
			for (const plugin of pluginRegistry.getPlugins()) if (plugin.handleKeyDown(e)) return;
		}
		if (!cancelOnEscape) return;
		const composer = aui.composer();
		if (composer.getState().canCancel) {
			composer.cancel();
			e.preventDefault();
		}
	});
	const handleKeyPress = (e_0) => {
		if (isDisabled) return;
		if (e_0.nativeEvent.isComposing) return;
		if (pluginRegistry) {
			for (const plugin_0 of pluginRegistry.getPlugins()) if (plugin_0.handleKeyDown(e_0)) return;
		}
		if (e_0.key === "Enter") {
			const threadState = aui.thread().getState();
			const hasQueue = threadState.capabilities.queue;
			if (e_0.shiftKey && (e_0.ctrlKey || e_0.metaKey) && hasQueue && declaredSubmitMode !== "none" && aui.composer().getState().canSend) {
				e_0.preventDefault();
				aui.composer().send({ steer: true });
				return;
			}
			if (e_0.shiftKey) return;
			if (threadState.isRunning && !hasQueue) return;
			let shouldSubmit = false;
			if (effectiveSubmitMode === "ctrlEnter") shouldSubmit = e_0.ctrlKey || e_0.metaKey;
			else if (effectiveSubmitMode === "enter") shouldSubmit = true;
			if (shouldSubmit) {
				e_0.preventDefault();
				textareaRef.current?.closest("form")?.requestSubmit();
			}
		}
	};
	const handlePaste = async (e_1) => {
		if (!addAttachmentOnPaste) return;
		const threadCapabilities = aui.thread().getState().capabilities;
		const files = Array.from(e_1.clipboardData?.files || []);
		if (threadCapabilities.attachments && files.length > 0) try {
			e_1.preventDefault();
			await Promise.all(files.map((file) => aui.composer().addAttachment(file)));
		} catch (error) {
			console.error("Error adding attachment:", error);
		}
	};
	const autoFocusEnabled = autoFocus && !isDisabled;
	const focus = useCallback(() => {
		const textarea = textareaRef.current;
		if (!textarea || !autoFocusEnabled) return;
		textarea.focus({ preventScroll: true });
		textarea.setSelectionRange(textarea.value.length, textarea.value.length);
	}, [autoFocusEnabled]);
	useEffect(() => focus(), [focus]);
	useOnScrollToBottom(() => {
		if (aui.composer().getState().type === "thread" && unstable_focusOnScrollToBottom) focus();
	});
	useEffect(() => {
		if (aui.composer().getState().type !== "thread" || !unstable_focusOnRunStart) return void 0;
		return aui.on("thread.runStart", focus);
	}, [
		unstable_focusOnRunStart,
		focus,
		aui
	]);
	useEffect(() => {
		if (aui.composer().getState().type !== "thread" || !unstable_focusOnThreadSwitched) return void 0;
		return aui.on("threadListItem.switchedTo", focus);
	}, [
		unstable_focusOnThreadSwitched,
		focus,
		aui
	]);
	const ariaComboboxProps = activeAria ? {
		"aria-controls": activeAria.popoverId,
		"aria-expanded": true,
		"aria-haspopup": "listbox",
		"aria-activedescendant": activeAria.highlightedItemId
	} : {};
	const inputProps = {
		name: "input",
		value,
		...rest,
		...ariaComboboxProps,
		ref,
		disabled: isDisabled,
		onChange: composeEventHandlers(onChange, (e_2) => {
			if (!aui.composer().getState().isEditing) return;
			const nativeIsComposing = e_2.nativeEvent.isComposing === true;
			if (compositionRef.current && !nativeIsComposing) compositionRef.current = false;
			const isComposing = nativeIsComposing || compositionRef.current;
			flushTapSync(() => {
				aui.composer().setText(e_2.target.value);
			});
			if (isComposing) return;
			const pos = e_2.target.selectionStart ?? e_2.target.value.length;
			if (pluginRegistry) for (const plugin_1 of pluginRegistry.getPlugins()) plugin_1.setCursorPosition(pos);
		}),
		onKeyDown: composeEventHandlers(onKeyDown, handleKeyPress),
		onCompositionStart: composeEventHandlers(rest.onCompositionStart, () => {
			compositionRef.current = true;
		}),
		onCompositionEnd: composeEventHandlers(rest.onCompositionEnd, (e_3) => {
			compositionRef.current = false;
			if (!aui.composer().getState().isEditing) return;
			const target = e_3.target;
			flushTapSync(() => {
				aui.composer().setText(target.value);
			});
			const pos_0 = target.selectionStart ?? target.value.length;
			if (pluginRegistry) for (const plugin_2 of pluginRegistry.getPlugins()) plugin_2.setCursorPosition(pos_0);
		}),
		onSelect: composeEventHandlers(onSelect, (e_4) => {
			if (compositionRef.current) return;
			const target_0 = e_4.target;
			const pos_1 = target_0.selectionStart ?? target_0.value.length;
			if (pluginRegistry) for (const plugin_3 of pluginRegistry.getPlugins()) plugin_3.setCursorPosition(pos_1);
		}),
		onPaste: composeEventHandlers(onPaste, handlePaste)
	};
	if (render && (0, react_shim_exports.isValidElement)(render)) {
		const renderChildren = rest.children !== void 0 ? rest.children : render.props.children;
		return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Slot, {
			...inputProps,
			children: (0, react_shim_exports.cloneElement)(render, void 0, renderChildren)
		});
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(asChild ? Slot : index, { ...inputProps });
});
ComposerPrimitiveInput.displayName = "ComposerPrimitive.Input";
var useComposerCancel$1 = () => {
	const { disabled, cancel } = useComposerCancel();
	if (disabled) return null;
	return cancel;
};
/**
* A button component that cancels the current message composition.
*
* This component automatically handles the cancel functionality and is disabled
* when canceling is not available.
*
* @example
* ```tsx
* <ComposerPrimitive.Cancel>
*   Cancel
* </ComposerPrimitive.Cancel>
* ```
*/
var ComposerPrimitiveCancel = createActionButton("ComposerPrimitive.Cancel", useComposerCancel$1);
var useComposerAddAttachment$1 = (t0) => {
	const $ = c(6);
	let t1;
	if ($[0] !== t0) {
		t1 = t0 === void 0 ? {} : t0;
		$[0] = t0;
		$[1] = t1;
	} else t1 = $[1];
	const { multiple: t2 } = t1;
	const multiple = t2 === void 0 ? true : t2;
	const { disabled, addAttachment } = useComposerAddAttachment();
	const aui = useAui();
	let t3;
	if ($[2] !== addAttachment || $[3] !== aui || $[4] !== multiple) {
		t3 = () => {
			const input = document.createElement("input");
			input.type = "file";
			input.multiple = multiple;
			input.hidden = true;
			const attachmentAccept = aui.composer().getState().attachmentAccept;
			if (attachmentAccept !== "*") input.accept = attachmentAccept;
			document.body.appendChild(input);
			input.onchange = (e) => {
				const fileList = e.target.files;
				if (!fileList) return;
				for (const file of fileList) addAttachment(file);
				document.body.removeChild(input);
			};
			input.oncancel = () => {
				if (!input.files || input.files.length === 0) document.body.removeChild(input);
			};
			input.click();
		};
		$[2] = addAttachment;
		$[3] = aui;
		$[4] = multiple;
		$[5] = t3;
	} else t3 = $[5];
	const callback = t3;
	if (disabled) return null;
	return callback;
};
var ComposerPrimitiveAddAttachment = createActionButton("ComposerPrimitive.AddAttachment", useComposerAddAttachment$1, ["multiple"]);
var ComposerPrimitiveAttachmentDropzone = (0, react_shim_exports.forwardRef)((t0, ref) => {
	const $ = c(30);
	const { disabled, asChild: t1, render, children, ...rest } = t0;
	const asChild = t1 === void 0 ? false : t1;
	const [isDragging, setIsDragging] = useState(false);
	const aui = useAui();
	let t2;
	if ($[0] !== disabled) {
		t2 = (e) => {
			if (disabled) return;
			e.preventDefault();
			setIsDragging(true);
		};
		$[0] = disabled;
		$[1] = t2;
	} else t2 = $[1];
	const handleDragEnterCapture = t2;
	let t3;
	if ($[2] !== disabled || $[3] !== isDragging) {
		t3 = (e_0) => {
			if (disabled) return;
			e_0.preventDefault();
			if (!isDragging) setIsDragging(true);
		};
		$[2] = disabled;
		$[3] = isDragging;
		$[4] = t3;
	} else t3 = $[4];
	const handleDragOverCapture = t3;
	let t4;
	if ($[5] !== disabled) {
		t4 = (e_1) => {
			if (disabled) return;
			e_1.preventDefault();
			const next = e_1.relatedTarget;
			if (next && e_1.currentTarget.contains(next)) return;
			setIsDragging(false);
		};
		$[5] = disabled;
		$[6] = t4;
	} else t4 = $[6];
	const handleDragLeaveCapture = t4;
	let t5;
	if ($[7] !== aui || $[8] !== disabled) {
		t5 = async (e_2) => {
			if (disabled) return;
			e_2.preventDefault();
			setIsDragging(false);
			const files = Array.from(e_2.dataTransfer.files);
			await Promise.all(files.map(async (file) => {
				try {
					await aui.composer().addAttachment(file);
				} catch (t6) {
					console.error("Failed to add attachment:", t6);
				}
			}));
		};
		$[7] = aui;
		$[8] = disabled;
		$[9] = t5;
	} else t5 = $[9];
	const handleDrop = t5;
	let t6;
	if ($[10] !== isDragging) {
		t6 = isDragging ? { "data-dragging": "true" } : null;
		$[10] = isDragging;
		$[11] = t6;
	} else t6 = $[11];
	const t7 = composeEventHandlers(rest.onDragEnterCapture, handleDragEnterCapture);
	const t8 = composeEventHandlers(rest.onDragOverCapture, handleDragOverCapture);
	const t9 = composeEventHandlers(rest.onDragLeaveCapture, handleDragLeaveCapture);
	const t10 = composeEventHandlers(rest.onDropCapture, handleDrop);
	let t11;
	if ($[12] !== ref || $[13] !== rest || $[14] !== t10 || $[15] !== t6 || $[16] !== t7 || $[17] !== t8 || $[18] !== t9) {
		t11 = {
			...t6,
			...rest,
			onDragEnterCapture: t7,
			onDragOverCapture: t8,
			onDragLeaveCapture: t9,
			onDropCapture: t10,
			ref
		};
		$[12] = ref;
		$[13] = rest;
		$[14] = t10;
		$[15] = t6;
		$[16] = t7;
		$[17] = t8;
		$[18] = t9;
		$[19] = t11;
	} else t11 = $[19];
	const mergedProps = t11;
	if (render && (0, react_shim_exports.isValidElement)(render)) {
		const t12 = children !== void 0 ? children : render.props.children;
		let t13;
		if ($[20] !== render || $[21] !== t12) {
			t13 = (0, react_shim_exports.cloneElement)(render, void 0, t12);
			$[20] = render;
			$[21] = t12;
			$[22] = t13;
		} else t13 = $[22];
		let t14;
		if ($[23] !== mergedProps || $[24] !== t13) {
			t14 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Slot, {
				...mergedProps,
				children: t13
			});
			$[23] = mergedProps;
			$[24] = t13;
			$[25] = t14;
		} else t14 = $[25];
		return t14;
	}
	const Comp = asChild ? Slot : "div";
	let t12;
	if ($[26] !== Comp || $[27] !== children || $[28] !== mergedProps) {
		t12 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Comp, {
			...mergedProps,
			children
		});
		$[26] = Comp;
		$[27] = children;
		$[28] = mergedProps;
		$[29] = t12;
	} else t12 = $[29];
	return t12;
});
ComposerPrimitiveAttachmentDropzone.displayName = "ComposerPrimitive.AttachmentDropzone";
var useComposerDictate$1 = () => {
	const { disabled, startDictation } = useComposerDictate();
	if (disabled) return null;
	return startDictation;
};
/**
* A button that starts dictation to convert voice to text.
*
* Requires a DictationAdapter to be configured in the runtime.
*
* @example
* ```tsx
* <ComposerPrimitive.Dictate>
*   <MicIcon />
* </ComposerPrimitive.Dictate>
* ```
*/
var ComposerPrimitiveDictate = createActionButton("ComposerPrimitive.Dictate", useComposerDictate$1);
var useComposerStopDictation = () => {
	const $ = c(2);
	const aui = useAui();
	const isDictating = useAuiState(_temp$14);
	let t0;
	if ($[0] !== aui) {
		t0 = () => {
			aui.composer().stopDictation();
		};
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const callback = t0;
	if (!isDictating) return null;
	return callback;
};
/**
* A button that stops the current dictation session.
*
* Only rendered when dictation is active.
*
* @example
* ```tsx
* <ComposerPrimitive.StopDictation>
*   <StopIcon />
* </ComposerPrimitive.StopDictation>
* ```
*/
var ComposerPrimitiveStopDictation = createActionButton("ComposerPrimitive.StopDictation", useComposerStopDictation);
function _temp$14(s) {
	return s.composer.dictation != null;
}
/**
* Renders the current interim (partial) transcript while dictation is active.
*
* This component displays real-time feedback of what the user is saying before
* the transcription is finalized and committed to the composer input.
*
* @example
* ```tsx
* <ComposerPrimitive.If dictation>
*   <div className="dictation-preview">
*     <ComposerPrimitive.DictationTranscript />
*   </div>
* </ComposerPrimitive.If>
* ```
*/
var ComposerPrimitiveDictationTranscript = (0, react_shim_exports.forwardRef)((t0, forwardRef) => {
	const $ = c(7);
	let children;
	let props;
	if ($[0] !== t0) {
		({children, ...props} = t0);
		$[0] = t0;
		$[1] = children;
		$[2] = props;
	} else {
		children = $[1];
		props = $[2];
	}
	const transcript = useAuiState(_temp$13);
	if (!transcript) return null;
	const t1 = children ?? transcript;
	let t2;
	if ($[3] !== forwardRef || $[4] !== props || $[5] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.span, {
			...props,
			ref: forwardRef,
			children: t1
		});
		$[3] = forwardRef;
		$[4] = props;
		$[5] = t1;
		$[6] = t2;
	} else t2 = $[6];
	return t2;
});
ComposerPrimitiveDictationTranscript.displayName = "ComposerPrimitive.DictationTranscript";
function _temp$13(s) {
	return s.composer.dictation?.transcript;
}
/**
* Renders a container for the quoted text preview in the composer.
* Only renders when a quote is set.
*
* @example
* ```tsx
* <ComposerPrimitive.Quote>
*   <ComposerPrimitive.QuoteText />
*   <ComposerPrimitive.QuoteDismiss>×</ComposerPrimitive.QuoteDismiss>
* </ComposerPrimitive.Quote>
* ```
*/
var ComposerPrimitiveQuote = (0, react_shim_exports.forwardRef)((props, forwardedRef) => {
	const $ = c(3);
	if (!useAuiState(_temp$12)) return null;
	let t0;
	if ($[0] !== forwardedRef || $[1] !== props) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref: forwardedRef
		});
		$[0] = forwardedRef;
		$[1] = props;
		$[2] = t0;
	} else t0 = $[2];
	return t0;
});
ComposerPrimitiveQuote.displayName = "ComposerPrimitive.Quote";
/**
* Renders the quoted text content.
*
* @example
* ```tsx
* <ComposerPrimitive.QuoteText />
* ```
*/
var ComposerPrimitiveQuoteText = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(7);
	let children;
	let props;
	if ($[0] !== t0) {
		({children, ...props} = t0);
		$[0] = t0;
		$[1] = children;
		$[2] = props;
	} else {
		children = $[1];
		props = $[2];
	}
	const text = useAuiState(_temp2$4);
	if (!text) return null;
	const t1 = children ?? text;
	let t2;
	if ($[3] !== forwardedRef || $[4] !== props || $[5] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.span, {
			...props,
			ref: forwardedRef,
			children: t1
		});
		$[3] = forwardedRef;
		$[4] = props;
		$[5] = t1;
		$[6] = t2;
	} else t2 = $[6];
	return t2;
});
ComposerPrimitiveQuoteText.displayName = "ComposerPrimitive.QuoteText";
/**
* A button that clears the current quote from the composer.
*
* @example
* ```tsx
* <ComposerPrimitive.QuoteDismiss>×</ComposerPrimitive.QuoteDismiss>
* ```
*/
var ComposerPrimitiveQuoteDismiss = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(12);
	let onClick;
	let props;
	if ($[0] !== t0) {
		({onClick, ...props} = t0);
		$[0] = t0;
		$[1] = onClick;
		$[2] = props;
	} else {
		onClick = $[1];
		props = $[2];
	}
	const aui = useAui();
	let t1;
	if ($[3] !== aui) {
		t1 = () => {
			aui.composer().setQuote(void 0);
		};
		$[3] = aui;
		$[4] = t1;
	} else t1 = $[4];
	const handleDismiss = t1;
	let t2;
	if ($[5] !== handleDismiss || $[6] !== onClick) {
		t2 = composeEventHandlers(onClick, handleDismiss);
		$[5] = handleDismiss;
		$[6] = onClick;
		$[7] = t2;
	} else t2 = $[7];
	let t3;
	if ($[8] !== forwardedRef || $[9] !== props || $[10] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.button, {
			type: "button",
			...props,
			ref: forwardedRef,
			onClick: t2
		});
		$[8] = forwardedRef;
		$[9] = props;
		$[10] = t2;
		$[11] = t3;
	} else t3 = $[11];
	return t3;
});
ComposerPrimitiveQuoteDismiss.displayName = "ComposerPrimitive.QuoteDismiss";
function _temp$12(s) {
	return s.composer.quote;
}
function _temp2$4(s) {
	return s.composer.quote?.text;
}
/**
* Renders the top-level category list via a render function.
* Only renders when no category is active and search mode is off.
*/
var ComposerPrimitiveTriggerPopoverCategories = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(12);
	let ariaLabel;
	let children;
	let props;
	if ($[0] !== t0) {
		({children, "aria-label": ariaLabel, ...props} = t0);
		$[0] = t0;
		$[1] = ariaLabel;
		$[2] = children;
		$[3] = props;
	} else {
		ariaLabel = $[1];
		children = $[2];
		props = $[3];
	}
	const { categories, activeCategoryId, isSearchMode, open } = useTriggerPopoverScopeContext();
	if (!open || activeCategoryId || isSearchMode) return null;
	const t1 = ariaLabel ?? "Categories";
	let t2;
	if ($[4] !== categories || $[5] !== children) {
		t2 = children(categories);
		$[4] = categories;
		$[5] = children;
		$[6] = t2;
	} else t2 = $[6];
	let t3;
	if ($[7] !== forwardedRef || $[8] !== props || $[9] !== t1 || $[10] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			role: "group",
			"aria-label": t1,
			...props,
			ref: forwardedRef,
			children: t2
		});
		$[7] = forwardedRef;
		$[8] = props;
		$[9] = t1;
		$[10] = t2;
		$[11] = t3;
	} else t3 = $[11];
	return t3;
});
ComposerPrimitiveTriggerPopoverCategories.displayName = "ComposerPrimitive.TriggerPopoverCategories";
/**
* A button that selects a category and triggers drill-down navigation.
* Automatically receives `data-highlighted` when keyboard-navigated.
*/
var ComposerPrimitiveTriggerPopoverCategoryItem = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(30);
	let categoryId;
	let onClick;
	let onMouseMove;
	let props;
	if ($[0] !== t0) {
		({categoryId, onClick, onMouseMove, ...props} = t0);
		$[0] = t0;
		$[1] = categoryId;
		$[2] = onClick;
		$[3] = onMouseMove;
		$[4] = props;
	} else {
		categoryId = $[1];
		onClick = $[2];
		onMouseMove = $[3];
		props = $[4];
	}
	const { selectCategory, highlightIndex, categories, highlightedIndex, activeCategoryId, isSearchMode, popoverId } = useTriggerPopoverScopeContext();
	let t1;
	if ($[5] !== categoryId || $[6] !== selectCategory) {
		t1 = () => {
			selectCategory(categoryId);
		};
		$[5] = categoryId;
		$[6] = selectCategory;
		$[7] = t1;
	} else t1 = $[7];
	const handleClick = t1;
	let t2;
	if ($[8] !== categories || $[9] !== categoryId) {
		let t3;
		if ($[11] !== categoryId) {
			t3 = (c) => c.id === categoryId;
			$[11] = categoryId;
			$[12] = t3;
		} else t3 = $[12];
		t2 = categories.findIndex(t3);
		$[8] = categories;
		$[9] = categoryId;
		$[10] = t2;
	} else t2 = $[10];
	const categoryIndex = t2;
	const isHighlighted = !activeCategoryId && !isSearchMode && categoryIndex === highlightedIndex;
	let t3;
	if ($[13] !== categoryIndex || $[14] !== highlightIndex) {
		t3 = () => {
			highlightIndex(categoryIndex);
		};
		$[13] = categoryIndex;
		$[14] = highlightIndex;
		$[15] = t3;
	} else t3 = $[15];
	const handleMouseMove = t3;
	const t4 = `${popoverId}-option-${categoryId}`;
	const t5 = isHighlighted ? "" : void 0;
	let t6;
	if ($[16] !== handleClick || $[17] !== onClick) {
		t6 = composeEventHandlers(onClick, handleClick);
		$[16] = handleClick;
		$[17] = onClick;
		$[18] = t6;
	} else t6 = $[18];
	let t7;
	if ($[19] !== handleMouseMove || $[20] !== onMouseMove) {
		t7 = composeEventHandlers(onMouseMove, handleMouseMove);
		$[19] = handleMouseMove;
		$[20] = onMouseMove;
		$[21] = t7;
	} else t7 = $[21];
	let t8;
	if ($[22] !== forwardedRef || $[23] !== isHighlighted || $[24] !== props || $[25] !== t4 || $[26] !== t5 || $[27] !== t6 || $[28] !== t7) {
		t8 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.button, {
			type: "button",
			role: "option",
			id: t4,
			"aria-selected": isHighlighted,
			"data-highlighted": t5,
			...props,
			ref: forwardedRef,
			onClick: t6,
			onMouseMove: t7
		});
		$[22] = forwardedRef;
		$[23] = isHighlighted;
		$[24] = props;
		$[25] = t4;
		$[26] = t5;
		$[27] = t6;
		$[28] = t7;
		$[29] = t8;
	} else t8 = $[29];
	return t8;
});
ComposerPrimitiveTriggerPopoverCategoryItem.displayName = "ComposerPrimitive.TriggerPopoverCategoryItem";
/**
* Renders the list of items within a category or search results via a render function.
* Only renders when a category is active or search mode is on.
*/
var ComposerPrimitiveTriggerPopoverItems = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(12);
	let ariaLabel;
	let children;
	let props;
	if ($[0] !== t0) {
		({children, "aria-label": ariaLabel, ...props} = t0);
		$[0] = t0;
		$[1] = ariaLabel;
		$[2] = children;
		$[3] = props;
	} else {
		ariaLabel = $[1];
		children = $[2];
		props = $[3];
	}
	const { items, activeCategoryId, isSearchMode, open } = useTriggerPopoverScopeContext();
	if (!open || !activeCategoryId && !isSearchMode) return null;
	const t1 = ariaLabel ?? "Items";
	let t2;
	if ($[4] !== children || $[5] !== items) {
		t2 = children(items);
		$[4] = children;
		$[5] = items;
		$[6] = t2;
	} else t2 = $[6];
	let t3;
	if ($[7] !== forwardedRef || $[8] !== props || $[9] !== t1 || $[10] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			role: "group",
			"aria-label": t1,
			...props,
			ref: forwardedRef,
			children: t2
		});
		$[7] = forwardedRef;
		$[8] = props;
		$[9] = t1;
		$[10] = t2;
		$[11] = t3;
	} else t3 = $[11];
	return t3;
});
ComposerPrimitiveTriggerPopoverItems.displayName = "ComposerPrimitive.TriggerPopoverItems";
/**
* A button that selects a trigger item.
* Automatically receives `data-highlighted` when keyboard-navigated.
*/
var ComposerPrimitiveTriggerPopoverItem = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(30);
	let indexProp;
	let item;
	let onClick;
	let onMouseMove;
	let props;
	if ($[0] !== t0) {
		({item, index: indexProp, onClick, onMouseMove, ...props} = t0);
		$[0] = t0;
		$[1] = indexProp;
		$[2] = item;
		$[3] = onClick;
		$[4] = onMouseMove;
		$[5] = props;
	} else {
		indexProp = $[1];
		item = $[2];
		onClick = $[3];
		onMouseMove = $[4];
		props = $[5];
	}
	const { selectItem, highlightIndex, items, highlightedIndex, activeCategoryId, isSearchMode, popoverId } = useTriggerPopoverScopeContext();
	let t1;
	if ($[6] !== item || $[7] !== selectItem) {
		t1 = () => {
			selectItem(item);
		};
		$[6] = item;
		$[7] = selectItem;
		$[8] = t1;
	} else t1 = $[8];
	const handleClick = t1;
	let t2;
	if ($[9] !== indexProp || $[10] !== item.id || $[11] !== items) {
		t2 = indexProp ?? items.findIndex((i) => i.id === item.id);
		$[9] = indexProp;
		$[10] = item.id;
		$[11] = items;
		$[12] = t2;
	} else t2 = $[12];
	const itemIndex = t2;
	const isHighlighted = (isSearchMode || activeCategoryId !== null) && itemIndex === highlightedIndex;
	let t3;
	if ($[13] !== highlightIndex || $[14] !== itemIndex) {
		t3 = () => {
			highlightIndex(itemIndex);
		};
		$[13] = highlightIndex;
		$[14] = itemIndex;
		$[15] = t3;
	} else t3 = $[15];
	const handleMouseMove = t3;
	const t4 = `${popoverId}-option-${item.id}`;
	const t5 = isHighlighted ? "" : void 0;
	let t6;
	if ($[16] !== handleClick || $[17] !== onClick) {
		t6 = composeEventHandlers(onClick, handleClick);
		$[16] = handleClick;
		$[17] = onClick;
		$[18] = t6;
	} else t6 = $[18];
	let t7;
	if ($[19] !== handleMouseMove || $[20] !== onMouseMove) {
		t7 = composeEventHandlers(onMouseMove, handleMouseMove);
		$[19] = handleMouseMove;
		$[20] = onMouseMove;
		$[21] = t7;
	} else t7 = $[21];
	let t8;
	if ($[22] !== forwardedRef || $[23] !== isHighlighted || $[24] !== props || $[25] !== t4 || $[26] !== t5 || $[27] !== t6 || $[28] !== t7) {
		t8 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.button, {
			type: "button",
			role: "option",
			id: t4,
			"aria-selected": isHighlighted,
			"data-highlighted": t5,
			...props,
			ref: forwardedRef,
			onClick: t6,
			onMouseMove: t7
		});
		$[22] = forwardedRef;
		$[23] = isHighlighted;
		$[24] = props;
		$[25] = t4;
		$[26] = t5;
		$[27] = t6;
		$[28] = t7;
		$[29] = t8;
	} else t8 = $[29];
	return t8;
});
ComposerPrimitiveTriggerPopoverItem.displayName = "ComposerPrimitive.TriggerPopoverItem";
/**
* A button that navigates back from category items to the category list.
* Only renders when a category is active (drill-down view).
*/
var ComposerPrimitiveTriggerPopoverBack = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(10);
	let onClick;
	let props;
	if ($[0] !== t0) {
		({onClick, ...props} = t0);
		$[0] = t0;
		$[1] = onClick;
		$[2] = props;
	} else {
		onClick = $[1];
		props = $[2];
	}
	const { activeCategoryId, isSearchMode, goBack, open } = useTriggerPopoverScopeContext();
	if (!open || !activeCategoryId || isSearchMode) return null;
	let t1;
	if ($[3] !== goBack || $[4] !== onClick) {
		t1 = composeEventHandlers(onClick, goBack);
		$[3] = goBack;
		$[4] = onClick;
		$[5] = t1;
	} else t1 = $[5];
	let t2;
	if ($[6] !== forwardedRef || $[7] !== props || $[8] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.button, {
			type: "button",
			...props,
			ref: forwardedRef,
			onClick: t1
		});
		$[6] = forwardedRef;
		$[7] = props;
		$[8] = t1;
		$[9] = t2;
	} else t2 = $[9];
	return t2;
});
ComposerPrimitiveTriggerPopoverBack.displayName = "ComposerPrimitive.TriggerPopoverBack";
/**
* Configures a `<TriggerPopover>` to fire a handler when an item is selected,
* optionally leaving a directive chip behind as an audit trail. Render exactly
* one behavior sub-primitive per `<TriggerPopover>`.
*
* Exposed as `ComposerPrimitive.Unstable_TriggerPopover.Action`.
*
* @example
* ```tsx
* <ComposerPrimitive.Unstable_TriggerPopover char="/" adapter={slashAdapter}>
*   <ComposerPrimitive.Unstable_TriggerPopover.Action
*     onExecute={(item) => commandHandlers[item.id]?.()}
*     removeOnExecute={false}
*   />
* </ComposerPrimitive.Unstable_TriggerPopover>
* ```
*/
var ComposerPrimitiveTriggerPopoverAction = ({ formatter, onExecute, removeOnExecute }) => {
	const { register } = useTriggerBehaviorRegistration();
	const onExecuteRef = useRef(onExecute);
	onExecuteRef.current = onExecute;
	useEffect(() => {
		return register({
			kind: "action",
			formatter: formatter ?? unstable_defaultDirectiveFormatter,
			onExecute: (item) => onExecuteRef.current(item),
			...removeOnExecute !== void 0 ? { removeOnExecute } : {}
		});
	}, [
		register,
		formatter,
		removeOnExecute
	]);
	return null;
};
ComposerPrimitiveTriggerPopoverAction.displayName = "ComposerPrimitive.TriggerPopoverAction";
/**
* Configures a `<TriggerPopover>` to insert a directive chip when an item is
* selected. Render exactly one behavior sub-primitive per `<TriggerPopover>`.
*
* Exposed as `ComposerPrimitive.Unstable_TriggerPopover.Directive`.
*
* @example
* ```tsx
* <ComposerPrimitive.Unstable_TriggerPopover char="@" adapter={mentionAdapter}>
*   <ComposerPrimitive.Unstable_TriggerPopover.Directive
*     formatter={unstable_defaultDirectiveFormatter}
*     onInserted={(item) => track("mention", item.id)}
*   />
* </ComposerPrimitive.Unstable_TriggerPopover>
* ```
*/
var ComposerPrimitiveTriggerPopoverDirective = ({ formatter, onInserted }) => {
	const { register } = useTriggerBehaviorRegistration();
	const onInsertedRef = useRef(onInserted);
	onInsertedRef.current = onInserted;
	useEffect(() => {
		return register({
			kind: "directive",
			formatter: formatter ?? unstable_defaultDirectiveFormatter,
			onInserted: (item) => onInsertedRef.current?.(item)
		});
	}, [register, formatter]);
	return null;
};
ComposerPrimitiveTriggerPopoverDirective.displayName = "ComposerPrimitive.TriggerPopoverDirective";
var ComposerPrimitiveTriggerPopover = Object.assign(ComposerPrimitiveTriggerPopover$1, {
	Directive: ComposerPrimitiveTriggerPopoverDirective,
	Action: ComposerPrimitiveTriggerPopoverAction
});
var composer_exports = /* @__PURE__ */ __exportAll({
	AddAttachment: () => ComposerPrimitiveAddAttachment,
	AttachmentByIndex: () => ComposerPrimitiveAttachmentByIndex,
	AttachmentDropzone: () => ComposerPrimitiveAttachmentDropzone,
	Attachments: () => ComposerPrimitiveAttachments,
	Cancel: () => ComposerPrimitiveCancel,
	Dictate: () => ComposerPrimitiveDictate,
	DictationTranscript: () => ComposerPrimitiveDictationTranscript,
	If: () => ComposerPrimitiveIf,
	Input: () => ComposerPrimitiveInput,
	Queue: () => ComposerPrimitiveQueue,
	Quote: () => ComposerPrimitiveQuote,
	QuoteDismiss: () => ComposerPrimitiveQuoteDismiss,
	QuoteText: () => ComposerPrimitiveQuoteText,
	Root: () => ComposerPrimitiveRoot,
	Send: () => ComposerPrimitiveSend,
	StopDictation: () => ComposerPrimitiveStopDictation,
	Unstable_TriggerPopover: () => ComposerPrimitiveTriggerPopover,
	Unstable_TriggerPopoverBack: () => ComposerPrimitiveTriggerPopoverBack,
	Unstable_TriggerPopoverCategories: () => ComposerPrimitiveTriggerPopoverCategories,
	Unstable_TriggerPopoverCategoryItem: () => ComposerPrimitiveTriggerPopoverCategoryItem,
	Unstable_TriggerPopoverItem: () => ComposerPrimitiveTriggerPopoverItem,
	Unstable_TriggerPopoverItems: () => ComposerPrimitiveTriggerPopoverItems,
	Unstable_TriggerPopoverRoot: () => ComposerPrimitiveTriggerPopoverRoot,
	unstable_useTriggerPopoverRootContext: () => useTriggerPopoverRootContext,
	unstable_useTriggerPopoverRootContextOptional: () => useTriggerPopoverRootContextOptional,
	unstable_useTriggerPopoverScopeContext: () => useTriggerPopoverScopeContext,
	unstable_useTriggerPopoverScopeContextOptional: () => useTriggerPopoverScopeContextOptional,
	unstable_useTriggerPopoverTriggers: () => useTriggerPopoverTriggers,
	unstable_useTriggerPopoverTriggersOptional: () => useTriggerPopoverTriggersOptional
});
/**
* @deprecated Use {@link useAuiState} to select and narrow `s.part`.
* Return `null` for optional rendering, or throw inside the selector to
* preserve the old hook's strict behavior.
*
* @example
* ```tsx
* const text = useAuiState((s) => {
*   if (s.part.type !== "text" && s.part.type !== "reasoning") return null;
*   return s.part;
* });
* ```
*
* See the {@link https://assistant-ui.com/docs/migrations/v0-12 migration guide}.
*/
var useMessagePartText = () => {
	return useAuiState(_temp$11);
};
function _temp$11(s) {
	if (s.part.type !== "text" && s.part.type !== "reasoning") throw new Error("MessagePartText can only be used inside text or reasoning message parts.");
	return s.part;
}
var SmoothContext = createContext(null);
function useSmoothContext(options) {
	const context = useContext(SmoothContext);
	if (!options?.optional && !context) throw new Error("This component must be used within a SmoothContextProvider.");
	return context;
}
var { useSmoothStatus, useSmoothStatusStore } = createContextStoreHook(useSmoothContext, "useSmoothStatus");
var DEFAULT_DRAIN_MS = 250;
var DEFAULT_MAX_CHAR_INTERVAL_MS = 5;
var TextStreamAnimator = class {
	currentText;
	setText;
	animationFrameId = null;
	lastUpdateTime = Date.now();
	lastCommitTime = 0;
	targetText = "";
	drainMs = DEFAULT_DRAIN_MS;
	maxCharIntervalMs = DEFAULT_MAX_CHAR_INTERVAL_MS;
	maxCharsPerFrame = Infinity;
	minCommitMs = 0;
	constructor(currentText, setText) {
		this.currentText = currentText;
		this.setText = setText;
	}
	start() {
		if (this.animationFrameId !== null) return;
		this.lastUpdateTime = Date.now();
		this.animate();
	}
	stop() {
		if (this.animationFrameId !== null) {
			cancelAnimationFrame(this.animationFrameId);
			this.animationFrameId = null;
		}
	}
	animate = () => {
		const currentTime = Date.now();
		let timeToConsume = currentTime - this.lastUpdateTime;
		const remainingChars = this.targetText.length - this.currentText.length;
		const baseTimePerChar = Math.min(this.maxCharIntervalMs, this.drainMs / remainingChars);
		const frameLimit = Math.min(remainingChars, this.maxCharsPerFrame);
		let charsToAdd = 0;
		while (timeToConsume >= baseTimePerChar && charsToAdd < frameLimit) {
			charsToAdd++;
			timeToConsume -= baseTimePerChar;
		}
		if (charsToAdd === frameLimit && frameLimit === this.maxCharsPerFrame) timeToConsume = 0;
		if (charsToAdd !== remainingChars) this.animationFrameId = requestAnimationFrame(this.animate);
		else this.animationFrameId = null;
		if (charsToAdd === 0) return;
		this.currentText = this.targetText.slice(0, this.currentText.length + charsToAdd);
		this.lastUpdateTime = currentTime - timeToConsume;
		if (charsToAdd === remainingChars || currentTime - this.lastCommitTime >= this.minCommitMs) {
			this.lastCommitTime = currentTime;
			this.setText(this.currentText);
		}
	};
};
var SMOOTH_STATUS = Object.freeze({ type: "running" });
var positiveOr = (value, fallback) => value !== void 0 && value > 0 ? value : fallback;
/**
* Animates streamed message part text with a typewriter-style reveal.
*
* Takes the current part state and a `smooth` argument: `false` disables,
* `true` uses the default rate, and a {@link SmoothOptions} object tunes
* the reveal. Returns the part state with `text` replaced by the revealed
* prefix and `status` reporting `running` until the reveal catches up.
*
* @example
* ```tsx
* const { text, status } = useSmooth(useMessagePartText(), {
*   drainMs: 500,
*   maxCharsPerFrame: 30,
* });
* ```
*/
var useSmooth = (state, smooth = false) => {
	const { text } = state;
	const options = typeof smooth === "object" && smooth !== null ? smooth : void 0;
	const enabled = smooth !== false && smooth !== null;
	const drainMs = positiveOr(options?.drainMs, DEFAULT_DRAIN_MS);
	const maxCharIntervalMs = positiveOr(options?.maxCharIntervalMs, DEFAULT_MAX_CHAR_INTERVAL_MS);
	const maxCharsPerFrame = positiveOr(options?.maxCharsPerFrame, Infinity);
	const minCommitMs = positiveOr(options?.minCommitMs, 0);
	const [displayedText, setDisplayedText] = useState(state.status.type === "running" ? "" : text);
	const aui = useAui();
	const part = useAuiState(() => aui.part());
	const [prevPart, setPrevPart] = useState(part);
	if (part !== prevPart || !text.startsWith(displayedText)) {
		setPrevPart(part);
		setDisplayedText(state.status.type === "running" ? "" : text);
	}
	const smoothStatusStore = useSmoothStatusStore({ optional: true });
	const setText = useCallbackRef((text_0) => {
		setDisplayedText(text_0);
		if (smoothStatusStore) {
			const target = displayedText !== text_0 || state.status.type === "running" ? SMOOTH_STATUS : state.status;
			writableStore(smoothStatusStore).setState(target, true);
		}
	});
	useEffect(() => {
		if (smoothStatusStore) {
			const target_0 = enabled && (displayedText !== text || state.status.type === "running") ? SMOOTH_STATUS : state.status;
			writableStore(smoothStatusStore).setState(target_0, true);
		}
	}, [
		smoothStatusStore,
		enabled,
		text,
		displayedText,
		state.status
	]);
	const [animatorRef] = useState(new TextStreamAnimator(displayedText, setText));
	useEffect(() => {
		animatorRef.drainMs = drainMs;
		animatorRef.maxCharIntervalMs = maxCharIntervalMs;
		animatorRef.maxCharsPerFrame = maxCharsPerFrame;
		animatorRef.minCommitMs = minCommitMs;
	}, [
		animatorRef,
		drainMs,
		maxCharIntervalMs,
		maxCharsPerFrame,
		minCommitMs
	]);
	const animatorPartRef = useRef(part);
	useEffect(() => {
		if (!enabled) {
			animatorRef.stop();
			return;
		}
		const partChanged = animatorPartRef.current !== part;
		animatorPartRef.current = part;
		if (partChanged || !text.startsWith(animatorRef.targetText)) {
			if (state.status.type === "running") {
				animatorRef.currentText = "";
				animatorRef.targetText = text;
				animatorRef.lastCommitTime = 0;
				animatorRef.start();
			} else {
				animatorRef.currentText = text;
				animatorRef.targetText = text;
				animatorRef.stop();
			}
			return;
		}
		animatorRef.targetText = text;
		animatorRef.start();
	}, [
		animatorRef,
		enabled,
		text,
		state.status.type,
		part
	]);
	useEffect(() => {
		return () => {
			animatorRef.stop();
		};
	}, [animatorRef]);
	return useMemo(() => enabled ? {
		...state,
		text: displayedText,
		status: text === displayedText ? state.status : SMOOTH_STATUS
	} : state, [
		enabled,
		displayedText,
		state,
		text
	]);
};
/**
* @deprecated Use {@link useAuiState} to select and narrow `s.part`.
* Return `null` for optional rendering, or throw inside the selector to
* preserve the old hook's strict behavior.
*
* @example
* ```tsx
* const image = useAuiState((s) => {
*   if (s.part.type !== "image") return null;
*   return s.part;
* });
* ```
*
* See the {@link https://assistant-ui.com/docs/migrations/v0-12 migration guide}.
*/
var useMessagePartImage = () => {
	return useAuiState(_temp$10);
};
function _temp$10(s) {
	if (s.part.type !== "image") throw new Error("MessagePartImage can only be used inside image message parts.");
	return s.part;
}
/**
* Renders the text content of a message part with optional smooth streaming.
*
* This component displays text content from the current message part context,
* with support for smooth streaming animation that shows text appearing
* character by character as it's generated.
*
* @example
* ```tsx
* <MessagePartPrimitive.Text
*   smooth={true}
*   component="p"
*   className="message-text"
* />
* ```
*/
var MessagePartPrimitiveText = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(10);
	let rest;
	let t1;
	let t2;
	if ($[0] !== t0) {
		({smooth: t1, component: t2, ...rest} = t0);
		$[0] = t0;
		$[1] = rest;
		$[2] = t1;
		$[3] = t2;
	} else {
		rest = $[1];
		t1 = $[2];
		t2 = $[3];
	}
	const smooth = t1 === void 0 ? true : t1;
	const Component = t2 === void 0 ? "span" : t2;
	const { text, status } = useSmooth(useMessagePartText(), smooth);
	let t3;
	if ($[4] !== Component || $[5] !== forwardedRef || $[6] !== rest || $[7] !== status.type || $[8] !== text) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Component, {
			"data-status": status.type,
			...rest,
			ref: forwardedRef,
			children: text
		});
		$[4] = Component;
		$[5] = forwardedRef;
		$[6] = rest;
		$[7] = status.type;
		$[8] = text;
		$[9] = t3;
	} else t3 = $[9];
	return t3;
});
MessagePartPrimitiveText.displayName = "MessagePartPrimitive.Text";
/**
* Renders an image from the current message part context.
*
* This component displays image content from the current message part,
* automatically setting the src attribute from the message part's image data.
*
* @example
* ```tsx
* <MessagePartPrimitive.Image
*   alt="Generated image"
*   className="message-image"
*   style={{ maxWidth: '100%' }}
* />
* ```
*/
var MessagePartPrimitiveImage = (0, react_shim_exports.forwardRef)((props, forwardedRef) => {
	const $ = c(4);
	const { image } = useMessagePartImage();
	let t0;
	if ($[0] !== forwardedRef || $[1] !== image || $[2] !== props) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.img, {
			src: image,
			...props,
			ref: forwardedRef
		});
		$[0] = forwardedRef;
		$[1] = image;
		$[2] = props;
		$[3] = t0;
	} else t0 = $[3];
	return t0;
});
MessagePartPrimitiveImage.displayName = "MessagePartPrimitive.Image";
var useManagedRef = (callback) => {
	const $ = c(2);
	const cleanupRef = useRef(void 0);
	let t0;
	if ($[0] !== callback) {
		t0 = (el) => {
			if (cleanupRef.current) {
				cleanupRef.current();
				cleanupRef.current = void 0;
			}
			if (el) cleanupRef.current = callback(el);
		};
		$[0] = callback;
		$[1] = t0;
	} else t0 = $[1];
	return t0;
};
/**
* Convert a supported CSS length string (`px`, `em`, `rem`) into pixels,
* resolving font-relative units against the supplied element's computed style.
* Unsupported or malformed values disable the tall-message clamp.
*
* Part of the top-anchor package's public input contract: consumers may pass
* clamp configuration as supported CSS-length strings, and this function is the
* single place that converts them into the pixel values the package operates on.
*/
var parseCssLength = (value, element) => {
	const match = value.trim().match(/^(\d+(?:\.\d+)?|\.\d+)(em|px|rem)$/);
	if (!match) return Number.POSITIVE_INFINITY;
	const num = Number(match[1]);
	const unit = match[2];
	if (unit === "px") return num;
	if (unit === "em") return num * (parseFloat(getComputedStyle(element).fontSize) || 16);
	if (unit === "rem") return num * (parseFloat(getComputedStyle(document.documentElement).fontSize) || 16);
	return Number.POSITIVE_INFINITY;
};
var getAnchorId = (anchor) => anchor.dataset.messageId;
var createReserveElement = () => {
	const reserve = document.createElement("div");
	reserve.dataset.auiTopAnchorReserve = "";
	reserve.style.height = "0px";
	reserve.style.flexShrink = "0";
	reserve.style.pointerEvents = "none";
	reserve.setAttribute("aria-hidden", "true");
	return reserve;
};
var setReserveHeight = (reserve, height) => {
	const nextHeight = `${height}px`;
	if (reserve.style.height !== nextHeight) {
		reserve.style.height = nextHeight;
		return true;
	}
	return false;
};
var snapScrollTop = (top) => {
	const pixelRatio = window.devicePixelRatio || 1;
	return Math.round(top * pixelRatio) / pixelRatio;
};
var useIsHoveringRef = () => {
	const $ = c(4);
	const aui = useAui();
	let t0;
	if ($[0] !== aui) {
		t0 = () => aui.message();
		$[0] = aui;
		$[1] = t0;
	} else t0 = $[1];
	const message = useAuiState(t0);
	let t1;
	if ($[2] !== message) {
		t1 = (el) => {
			const handleMouseEnter = () => {
				message.setIsHovering(true);
			};
			const handleMouseLeave = () => {
				message.setIsHovering(false);
			};
			el.addEventListener("mouseenter", handleMouseEnter);
			el.addEventListener("mouseleave", handleMouseLeave);
			if (el.matches(":hover")) queueMicrotask(() => message.setIsHovering(true));
			return () => {
				el.removeEventListener("mouseenter", handleMouseEnter);
				el.removeEventListener("mouseleave", handleMouseLeave);
				message.setIsHovering(false);
			};
		};
		$[2] = message;
		$[3] = t1;
	} else t1 = $[3];
	return useManagedRef(t1);
};
var useIsTopAnchorUser = () => {
	const $ = c(2);
	const activeAnchorId = useThreadViewport(_temp$9);
	let t0;
	if ($[0] !== activeAnchorId) {
		t0 = (s_0) => s_0.message.role === "user" && s_0.message.index > 0 && s_0.message.index === s_0.thread.messages.length - 2 && s_0.thread.messages.at(-1)?.role === "assistant" && (s_0.message.id === activeAnchorId || s_0.thread.isRunning);
		$[0] = activeAnchorId;
		$[1] = t0;
	} else t0 = $[1];
	return useAuiState(t0);
};
var useIsTopAnchorTarget = () => {
	const $ = c(2);
	const activeTargetId = useThreadViewport(_temp2$3);
	let t0;
	if ($[0] !== activeTargetId) {
		t0 = (s_0) => s_0.message.isLast && s_0.message.role === "assistant" && s_0.message.index >= 1 && s_0.thread.messages.at(s_0.message.index - 1)?.role === "user" && (s_0.message.id === activeTargetId || s_0.thread.isRunning);
		$[0] = activeTargetId;
		$[1] = t0;
	} else t0 = $[1];
	return useAuiState(t0);
};
var useTopAnchorUserRef = (active, threadViewportStore) => {
	const $ = c(3);
	let t0;
	if ($[0] !== active || $[1] !== threadViewportStore) {
		t0 = (el) => {
			if (!active) return;
			return threadViewportStore.getState().registerAnchorElement(el);
		};
		$[0] = active;
		$[1] = threadViewportStore;
		$[2] = t0;
	} else t0 = $[2];
	return useManagedRef(t0);
};
var useTopAnchorTargetRef = (t0) => {
	const $ = c(3);
	const { active, threadViewportStore } = t0;
	let t1;
	if ($[0] !== active || $[1] !== threadViewportStore) {
		t1 = (el) => {
			if (!active) return;
			const state = threadViewportStore.getState();
			const clamp = state.topAnchorMessageClamp;
			return state.registerAnchorTargetElement(el, {
				tallerThan: parseCssLength(clamp.tallerThan, el),
				visibleHeight: parseCssLength(clamp.visibleHeight, el)
			});
		};
		$[0] = active;
		$[1] = threadViewportStore;
		$[2] = t1;
	} else t1 = $[2];
	return useManagedRef(t1);
};
var MessagePrimitiveRootDefault = (t0) => {
	const $ = c(7);
	let forwardedRef;
	let props;
	if ($[0] !== t0) {
		({forwardedRef, ...props} = t0);
		$[0] = t0;
		$[1] = forwardedRef;
		$[2] = props;
	} else {
		forwardedRef = $[1];
		props = $[2];
	}
	const isHoveringRef = useIsHoveringRef();
	const ref = useComposedRefs(forwardedRef, isHoveringRef);
	const messageId = useAuiState(_temp3$2);
	let t1;
	if ($[3] !== messageId || $[4] !== props || $[5] !== ref) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref,
			"data-message-id": messageId
		});
		$[3] = messageId;
		$[4] = props;
		$[5] = ref;
		$[6] = t1;
	} else t1 = $[6];
	return t1;
};
var MessagePrimitiveRootTopAnchor = (t0) => {
	const $ = c(13);
	let forwardedRef;
	let props;
	let threadViewportStore;
	if ($[0] !== t0) {
		({forwardedRef, threadViewportStore, ...props} = t0);
		$[0] = t0;
		$[1] = forwardedRef;
		$[2] = props;
		$[3] = threadViewportStore;
	} else {
		forwardedRef = $[1];
		props = $[2];
		threadViewportStore = $[3];
	}
	const isHoveringRef = useIsHoveringRef();
	const isTopAnchorUser = useIsTopAnchorUser();
	const isTopAnchorTarget = useIsTopAnchorTarget();
	const topAnchorUserRef = useTopAnchorUserRef(isTopAnchorUser, threadViewportStore);
	let t1;
	if ($[4] !== isTopAnchorTarget || $[5] !== threadViewportStore) {
		t1 = {
			active: isTopAnchorTarget,
			threadViewportStore
		};
		$[4] = isTopAnchorTarget;
		$[5] = threadViewportStore;
		$[6] = t1;
	} else t1 = $[6];
	const topAnchorTargetRef = useTopAnchorTargetRef(t1);
	const ref = useComposedRefs(forwardedRef, isHoveringRef, topAnchorUserRef, topAnchorTargetRef);
	const messageId = useAuiState(_temp4$1);
	const t2 = isTopAnchorUser ? "" : void 0;
	const t3 = isTopAnchorTarget ? "" : void 0;
	let t4;
	if ($[7] !== messageId || $[8] !== props || $[9] !== ref || $[10] !== t2 || $[11] !== t3) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref,
			"data-message-id": messageId,
			"data-aui-top-anchor-user": t2,
			"data-aui-top-anchor-target": t3
		});
		$[7] = messageId;
		$[8] = props;
		$[9] = ref;
		$[10] = t2;
		$[11] = t3;
		$[12] = t4;
	} else t4 = $[12];
	return t4;
};
/**
* The root container component for a message.
*
* This component provides the foundational wrapper for message content and handles
* hover state management for the message. It automatically tracks when the user
* is hovering over the message, which can be used by child components like action bars.
*
* When `turnAnchor="top"` is set on the viewport, this component automatically
* registers itself as the top-anchor user message (when it's the previous user
* message) or as the top-anchor target (when it's the streaming assistant
* response). No additional component is required.
*
* @example
* ```tsx
* <MessagePrimitive.Root>
*   <MessagePrimitive.Content />
*   <ActionBarPrimitive.Root>
*     <ActionBarPrimitive.Copy />
*     <ActionBarPrimitive.Edit />
*   </ActionBarPrimitive.Root>
* </MessagePrimitive.Root>
* ```
*/
var MessagePrimitiveRoot = (0, react_shim_exports.forwardRef)((props, forwardedRef) => {
	const $ = c(7);
	const threadViewportStore = useThreadViewportStore();
	if (threadViewportStore.getState().turnAnchor === "top") {
		let t0;
		if ($[0] !== forwardedRef || $[1] !== props || $[2] !== threadViewportStore) {
			t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitiveRootTopAnchor, {
				...props,
				forwardedRef,
				threadViewportStore
			});
			$[0] = forwardedRef;
			$[1] = props;
			$[2] = threadViewportStore;
			$[3] = t0;
		} else t0 = $[3];
		return t0;
	}
	let t0;
	if ($[4] !== forwardedRef || $[5] !== props) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitiveRootDefault, {
			...props,
			forwardedRef
		});
		$[4] = forwardedRef;
		$[5] = props;
		$[6] = t0;
	} else t0 = $[6];
	return t0;
});
MessagePrimitiveRoot.displayName = "MessagePrimitive.Root";
function _temp$9(s) {
	return s.topAnchorTurn?.anchorId;
}
function _temp2$3(s) {
	return s.topAnchorTurn?.targetId;
}
function _temp3$2(s) {
	return s.message.id;
}
function _temp4$1(s) {
	return s.message.id;
}
var webDefaultComponents = {
	...defaultComponents$1,
	Text: () => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
		style: { whiteSpace: "pre-line" },
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartPrimitiveText, {}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartPrimitiveInProgress, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			style: { fontFamily: "revert" },
			children: " ●"
		}) })]
	}),
	Image: () => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartPrimitiveImage, {})
};
/**
* Renders the parts of a message with web-specific default components.
*/
var MessagePrimitiveParts = (props) => {
	const $ = c(10);
	if ("children" in props) {
		let t0;
		if ($[0] !== props.children) {
			t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitiveParts$1, { children: props.children });
			$[0] = props.children;
			$[1] = t0;
		} else t0 = $[1];
		return t0;
	}
	let components;
	let rest;
	if ($[2] !== props) {
		({components, ...rest} = props);
		$[2] = props;
		$[3] = components;
		$[4] = rest;
	} else {
		components = $[3];
		rest = $[4];
	}
	let t0;
	if ($[5] !== components) {
		t0 = components ? {
			Text: components.Text ?? webDefaultComponents.Text,
			Image: components.Image ?? webDefaultComponents.Image,
			Reasoning: components.Reasoning ?? defaultComponents$1.Reasoning,
			Source: components.Source ?? defaultComponents$1.Source,
			File: components.File ?? defaultComponents$1.File,
			Unstable_Audio: components.Unstable_Audio ?? defaultComponents$1.Unstable_Audio,
			..."ChainOfThought" in components ? { ChainOfThought: components.ChainOfThought } : {
				tools: components.tools,
				data: components.data,
				ToolGroup: components.ToolGroup ?? defaultComponents$1.ToolGroup,
				ReasoningGroup: components.ReasoningGroup ?? defaultComponents$1.ReasoningGroup
			},
			Empty: components.Empty,
			Quote: components.Quote,
			generativeUI: components.generativeUI
		} : webDefaultComponents;
		$[5] = components;
		$[6] = t0;
	} else t0 = $[6];
	const t1 = t0;
	let t2;
	if ($[7] !== rest || $[8] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitiveParts$1, {
			components: t1,
			...rest
		});
		$[7] = rest;
		$[8] = t1;
		$[9] = t2;
	} else t2 = $[9];
	return t2;
};
MessagePrimitiveParts.displayName = "MessagePrimitive.Parts";
var MessagePrimitiveError = (t0) => {
	const { children } = t0;
	return useMessageError() !== void 0 ? children : null;
};
MessagePrimitiveError.displayName = "MessagePrimitive.Error";
/**
* Groups message parts by their parent ID.
* Parts without a parent ID appear in their chronological position as individual groups.
* Parts with the same parent ID are grouped together at the position of their first occurrence.
*/
var groupMessagePartsByParentId = (parts) => {
	const groupMap = /* @__PURE__ */ new Map();
	for (let i = 0; i < parts.length; i++) {
		const groupId = parts[i]?.parentId ?? `__ungrouped_${i}`;
		const indices = groupMap.get(groupId) ?? [];
		indices.push(i);
		groupMap.set(groupId, indices);
	}
	const groups = [];
	for (const [groupId, indices] of groupMap) {
		const groupKey = groupId.startsWith("__ungrouped_") ? void 0 : groupId;
		groups.push({
			groupKey,
			indices
		});
	}
	return groups;
};
var useMessagePartsGrouped = (groupingFunction) => {
	const $ = c(4);
	const parts = useAuiState(_temp$8);
	let t0;
	bb0: {
		if (parts.length === 0) {
			let t1;
			if ($[0] === Symbol.for("react.memo_cache_sentinel")) {
				t1 = [];
				$[0] = t1;
			} else t1 = $[0];
			t0 = t1;
			break bb0;
		}
		let t1;
		if ($[1] !== groupingFunction || $[2] !== parts) {
			t1 = groupingFunction(parts);
			$[1] = groupingFunction;
			$[2] = parts;
			$[3] = t1;
		} else t1 = $[3];
		t0 = t1;
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
		t1 = (s) => {
			const Render = s.tools.tools[props.toolName] ?? Fallback;
			if (Array.isArray(Render)) return Render[0] ?? Fallback;
			return Render;
		};
		$[3] = Fallback;
		$[4] = props.toolName;
		$[5] = t1;
	} else t1 = $[5];
	const Render_0 = useAuiState(t1);
	if (!Render_0) return null;
	let t2;
	if ($[6] !== Render_0 || $[7] !== props) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Render_0, { ...props });
		$[6] = Render_0;
		$[7] = props;
		$[8] = t2;
	} else t2 = $[8];
	return t2;
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
		t1 = (s) => {
			const Render = s.dataRenderers.renderers[props.name] ?? Fallback;
			if (Array.isArray(Render)) return Render[0] ?? Fallback;
			return Render;
		};
		$[3] = Fallback;
		$[4] = props.name;
		$[5] = t1;
	} else t1 = $[5];
	const Render_0 = useAuiState(t1);
	if (!Render_0) return null;
	let t2;
	if ($[6] !== Render_0 || $[7] !== props) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Render_0, { ...props });
		$[6] = Render_0;
		$[7] = props;
		$[8] = t2;
	} else t2 = $[8];
	return t2;
};
var defaultComponents = {
	Text: () => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
		style: { whiteSpace: "pre-line" },
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartPrimitiveText, {}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartPrimitiveInProgress, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			style: { fontFamily: "revert" },
			children: " ●"
		}) })]
	}),
	Reasoning: () => null,
	Source: () => null,
	Image: () => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartPrimitiveImage, {}),
	File: () => null,
	Unstable_Audio: () => null,
	Group: ({ children }) => children
};
var MessagePartComponent = (t0) => {
	const $ = c(43);
	const { components: t1 } = t0;
	let t2;
	if ($[0] !== t1) {
		t2 = t1 === void 0 ? {} : t1;
		$[0] = t1;
		$[1] = t2;
	} else t2 = $[1];
	const { Text: t3, Reasoning: t4, Image: t5, Source: t6, File: t7, Unstable_Audio: t8, tools: t9, data } = t2;
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
	const part = useAuiState(_temp2$2);
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
		default:
			console.warn(`Unknown message part type: ${type}`);
			return null;
	}
};
var MessagePartImpl = (t0) => {
	const $ = c(5);
	const { partIndex, components } = t0;
	let t1;
	if ($[0] !== components) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePartComponent, { components });
		$[0] = components;
		$[1] = t1;
	} else t1 = $[1];
	let t2;
	if ($[2] !== partIndex || $[3] !== t1) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PartByIndexProvider, {
			index: partIndex,
			children: t1
		});
		$[2] = partIndex;
		$[3] = t1;
		$[4] = t2;
	} else t2 = $[4];
	return t2;
};
var MessagePart = (0, react_shim_exports.memo)(MessagePartImpl, (prev, next) => prev.partIndex === next.partIndex && prev.components?.Text === next.components?.Text && prev.components?.Reasoning === next.components?.Reasoning && prev.components?.Source === next.components?.Source && prev.components?.Image === next.components?.Image && prev.components?.File === next.components?.File && prev.components?.Unstable_Audio === next.components?.Unstable_Audio && prev.components?.tools === next.components?.tools && prev.components?.data === next.components?.data && prev.components?.Group === next.components?.Group);
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
var COMPLETE_STATUS = Object.freeze({ type: "complete" });
var EmptyPartsImpl = (t0) => {
	const $ = c(6);
	const { components } = t0;
	const status = useAuiState(_temp3$1);
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
/**
* Renders the parts of a message grouped by a custom grouping function.
*
* This component allows you to group message parts based on any criteria you define.
* The grouping function receives all message parts and returns an array of groups,
* where each group has a key and an array of part indices.
*
* @deprecated Prefer `<MessagePrimitive.GroupedParts>` for adjacent
* grouping — it dispatches all rendering through one `switch (part.type)`
* and supports nested group paths. Keep this primitive only for
* non-adjacent clustering (e.g., gathering parts with the same parent-id
* across the message).
*
* @example
* ```tsx
* // Group by parent ID (default behavior)
* <MessagePrimitive.Unstable_PartsGrouped
*   components={{
*     Text: ({ text }) => <p className="message-text">{text}</p>,
*     Image: ({ image }) => <img src={image} alt="Message image" />,
*     Group: ({ groupKey, indices, children }) => {
*       if (!groupKey) return <>{children}</>;
*       return (
*         <div className="parent-group border rounded p-4">
*           <h4>Parent ID: {groupKey}</h4>
*           {children}
*         </div>
*       );
*     }
*   }}
* />
* ```
*/
var MessagePrimitiveUnstable_PartsGrouped = (t0) => {
	const $ = c(9);
	const { groupingFunction, components } = t0;
	const contentLength = useAuiState(_temp4);
	const messageGroups = useMessagePartsGrouped(groupingFunction);
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
		if ($[2] !== components || $[3] !== messageGroups) {
			let t3;
			if ($[5] !== components) {
				t3 = (group, groupIndex) => {
					return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(components?.Group ?? defaultComponents.Group, {
						groupKey: group.groupKey,
						indices: group.indices,
						children: group.indices.map((partIndex) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePart, {
							partIndex,
							components
						}, partIndex))
					}, `group-${groupIndex}-${group.groupKey ?? "ungrouped"}`);
				};
				$[5] = components;
				$[6] = t3;
			} else t3 = $[6];
			t2 = messageGroups.map(t3);
			$[2] = components;
			$[3] = messageGroups;
			$[4] = t2;
		} else t2 = $[4];
		t1 = t2;
	}
	const partsElements = t1;
	let t2;
	if ($[7] !== partsElements) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(import_jsx_runtime.Fragment, { children: partsElements });
		$[7] = partsElements;
		$[8] = t2;
	} else t2 = $[8];
	return t2;
};
MessagePrimitiveUnstable_PartsGrouped.displayName = "MessagePrimitive.Unstable_PartsGrouped";
/**
* Renders the parts of a message grouped by their parent ID.
* This is a convenience wrapper around Unstable_PartsGrouped with parent ID grouping.
*
* @deprecated Use MessagePrimitive.Unstable_PartsGrouped instead for more flexibility
*/
var MessagePrimitiveUnstable_PartsGroupedByParentId = (t0) => {
	const $ = c(6);
	let components;
	let props;
	if ($[0] !== t0) {
		({components, ...props} = t0);
		$[0] = t0;
		$[1] = components;
		$[2] = props;
	} else {
		components = $[1];
		props = $[2];
	}
	let t1;
	if ($[3] !== components || $[4] !== props) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MessagePrimitiveUnstable_PartsGrouped, {
			...props,
			components,
			groupingFunction: groupMessagePartsByParentId
		});
		$[3] = components;
		$[4] = props;
		$[5] = t1;
	} else t1 = $[5];
	return t1;
};
MessagePrimitiveUnstable_PartsGroupedByParentId.displayName = "MessagePrimitive.Unstable_PartsGroupedByParentId";
function _temp$8(s) {
	return s.message.parts;
}
function _temp2$2(s) {
	return s.part;
}
function _temp3$1(s) {
	return s.message.status ?? COMPLETE_STATUS;
}
function _temp4(s) {
	return s.message.parts.length;
}
var message_exports = /* @__PURE__ */ __exportAll({
	AttachmentByIndex: () => MessagePrimitiveAttachmentByIndex,
	Attachments: () => MessagePrimitiveAttachments,
	Content: () => MessagePrimitiveParts,
	Error: () => MessagePrimitiveError,
	GenerativeUI: () => MessagePrimitiveGenerativeUI,
	GroupedParts: () => MessagePrimitiveGroupedParts,
	If: () => MessagePrimitiveIf,
	PartByIndex: () => MessagePrimitivePartByIndex,
	Parts: () => MessagePrimitiveParts,
	Quote: () => MessagePrimitiveQuote,
	Root: () => MessagePrimitiveRoot,
	Unstable_PartsGrouped: () => MessagePrimitiveUnstable_PartsGrouped,
	Unstable_PartsGroupedByParentId: () => MessagePrimitiveUnstable_PartsGroupedByParentId
});
var useOnResizeContent = (callback) => {
	const $ = c(2);
	const callbackRef = useCallbackRef(callback);
	let t0;
	if ($[0] !== callbackRef) {
		t0 = (el) => {
			const resizeObserver = new ResizeObserver(() => {
				callbackRef();
			});
			const mutationObserver = new MutationObserver((mutations) => {
				if (mutations.some(_temp$7)) callbackRef();
			});
			resizeObserver.observe(el);
			mutationObserver.observe(el, {
				childList: true,
				subtree: true,
				attributes: true,
				characterData: true
			});
			return () => {
				resizeObserver.disconnect();
				mutationObserver.disconnect();
			};
		};
		$[0] = callbackRef;
		$[1] = t0;
	} else t0 = $[1];
	return useManagedRef(t0);
};
function _temp$7(m) {
	return m.type !== "attributes" || m.attributeName !== "style";
}
var useThreadViewportAutoScroll = ({ autoScroll, scrollToBottomOnRunStart = true, scrollToBottomOnInitialize = true, scrollToBottomOnThreadSwitch = true }) => {
	const divRef = useRef(null);
	const hasMessages = useAuiState((s) => s.thread.messages.length > 0);
	const initializeScrollRequestedRef = useRef(false);
	const scheduledFrameRef = useRef(null);
	const threadViewportStore = useThreadViewportStore();
	if (autoScroll === void 0) autoScroll = threadViewportStore.getState().turnAnchor !== "top";
	const lastScrollTop = useRef(0);
	const lastScrollHeight = useRef(0);
	const lastObservedScrollHeight = useRef(0);
	const lastObservedClientHeight = useRef(0);
	const scrollingToBottomBehaviorRef = useRef(null);
	const scrollToBottom = useCallback((behavior) => {
		const div = divRef.current;
		if (!div) return;
		scrollingToBottomBehaviorRef.current = behavior;
		div.scrollTo({
			top: div.scrollHeight,
			behavior
		});
	}, []);
	const scheduleScrollToBottom = useCallback((behavior_0) => {
		scrollingToBottomBehaviorRef.current = behavior_0;
		if (scheduledFrameRef.current !== null) cancelAnimationFrame(scheduledFrameRef.current);
		scheduledFrameRef.current = requestAnimationFrame(() => {
			scheduledFrameRef.current = null;
			scrollToBottom(behavior_0);
		});
	}, [scrollToBottom]);
	useLayoutEffect$1(() => () => {
		if (scheduledFrameRef.current !== null) cancelAnimationFrame(scheduledFrameRef.current);
	}, []);
	const hasActiveTopAnchor = useCallback(() => {
		const state = threadViewportStore.getState();
		return state.turnAnchor === "top" && state.element.viewport === divRef.current && state.element.anchor !== null;
	}, [threadViewportStore]);
	const handleScroll = () => {
		const div_0 = divRef.current;
		if (!div_0) return;
		const isAtBottom = threadViewportStore.getState().isAtBottom;
		const newIsAtBottom = Math.abs(div_0.scrollHeight - div_0.scrollTop - div_0.clientHeight) <= 1 || div_0.scrollHeight <= div_0.clientHeight;
		if (!newIsAtBottom && lastScrollTop.current < div_0.scrollTop) {} else {
			if (newIsAtBottom) {
				if (div_0.scrollHeight > div_0.clientHeight + 1) scrollingToBottomBehaviorRef.current = null;
			} else if (lastScrollTop.current > div_0.scrollTop && lastScrollHeight.current === div_0.scrollHeight) scrollingToBottomBehaviorRef.current = null;
			if ((newIsAtBottom || scrollingToBottomBehaviorRef.current === null) && newIsAtBottom !== isAtBottom) writableStore(threadViewportStore).setState({ isAtBottom: newIsAtBottom });
		}
		lastScrollTop.current = div_0.scrollTop;
		lastScrollHeight.current = div_0.scrollHeight;
	};
	const resizeRef = useOnResizeContent(() => {
		const div_1 = divRef.current;
		if (!div_1) return;
		const { scrollHeight, clientHeight } = div_1;
		if (scrollHeight === lastObservedScrollHeight.current && clientHeight === lastObservedClientHeight.current) return;
		lastObservedScrollHeight.current = scrollHeight;
		lastObservedClientHeight.current = clientHeight;
		const scrollBehavior = scrollingToBottomBehaviorRef.current;
		if (scrollBehavior && hasActiveTopAnchor()) scrollingToBottomBehaviorRef.current = null;
		else if (scrollBehavior) scrollToBottom(scrollBehavior);
		else if (autoScroll && threadViewportStore.getState().isAtBottom) scrollToBottom("instant");
		handleScroll();
	});
	const scrollRef = useManagedRef((el) => {
		const cancelPendingScrollToBottom = () => {
			scrollingToBottomBehaviorRef.current = null;
		};
		el.addEventListener("scroll", handleScroll);
		el.addEventListener("pointerdown", cancelPendingScrollToBottom);
		return () => {
			el.removeEventListener("scroll", handleScroll);
			el.removeEventListener("pointerdown", cancelPendingScrollToBottom);
		};
	});
	useLayoutEffect$1(() => {
		if (!scrollToBottomOnInitialize) return;
		if (!hasMessages) {
			initializeScrollRequestedRef.current = false;
			return;
		}
		if (initializeScrollRequestedRef.current) return;
		initializeScrollRequestedRef.current = true;
		if (scrollingToBottomBehaviorRef.current !== null) return;
		scheduleScrollToBottom("instant");
	}, [
		hasMessages,
		scheduleScrollToBottom,
		scrollToBottomOnInitialize
	]);
	useOnScrollToBottom(({ behavior: behavior_1 }) => {
		scrollToBottom(behavior_1);
	});
	useAuiEvent("thread.runStart", () => {
		if (!scrollToBottomOnRunStart) return;
		if (threadViewportStore.getState().turnAnchor === "top") return;
		scheduleScrollToBottom("auto");
	});
	useAuiEvent("threadListItem.switchedTo", () => {
		if (!scrollToBottomOnThreadSwitch) return;
		scheduleScrollToBottom("instant");
	});
	return useComposedRefs(resizeRef, scrollRef, divRef);
};
/**
* The root container component for a thread.
*
* This component serves as the foundational wrapper for all thread-related components.
* It provides the basic structure and context needed for thread functionality.
*
* @example
* ```tsx
* <ThreadPrimitive.Root>
*   <ThreadPrimitive.Viewport>
*     <ThreadPrimitive.Messages>
*       {() => <MyMessage />}
*     </ThreadPrimitive.Messages>
*   </ThreadPrimitive.Viewport>
* </ThreadPrimitive.Root>
* ```
*/
var ThreadPrimitiveRoot = (0, react_shim_exports.forwardRef)((props, ref) => {
	const $ = c(3);
	let t0;
	if ($[0] !== props || $[1] !== ref) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref
		});
		$[0] = props;
		$[1] = ref;
		$[2] = t0;
	} else t0 = $[2];
	return t0;
});
ThreadPrimitiveRoot.displayName = "ThreadPrimitive.Root";
/**
* @deprecated Use `<AuiIf condition={(s) => s.thread.isEmpty} />` instead.
*/
var ThreadPrimitiveEmpty = (t0) => {
	const { children } = t0;
	return useAuiState(_temp$6) ? children : null;
};
ThreadPrimitiveEmpty.displayName = "ThreadPrimitive.Empty";
function _temp$6(s) {
	return s.thread.isEmpty;
}
var useThreadIf = (props) => {
	const $ = c(4);
	let t0;
	if ($[0] !== props.disabled || $[1] !== props.empty || $[2] !== props.running) {
		t0 = (s) => {
			if (props.empty === true && !s.thread.isEmpty) return false;
			if (props.empty === false && s.thread.isEmpty) return false;
			if (props.running === true && !s.thread.isRunning) return false;
			if (props.running === false && s.thread.isRunning) return false;
			if (props.disabled === true && !s.thread.isDisabled) return false;
			if (props.disabled === false && s.thread.isDisabled) return false;
			return true;
		};
		$[0] = props.disabled;
		$[1] = props.empty;
		$[2] = props.running;
		$[3] = t0;
	} else t0 = $[3];
	return useAuiState(t0);
};
/**
* @deprecated Use `<AuiIf condition={(s) => s.thread...} />` instead.
*/
var ThreadPrimitiveIf = (t0) => {
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
	return useThreadIf(query) ? children : null;
};
ThreadPrimitiveIf.displayName = "ThreadPrimitive.If";
/**
* Hook that creates a ref for tracking element size via a SizeHandle.
* Automatically sets up ResizeObserver and reports height changes.
*
* @param register - Function that returns a SizeHandle (e.g., registerContentInset)
* @param getHeight - Optional function to compute height (defaults to el.offsetHeight)
* @returns A ref callback to attach to the element
*/
var useSizeHandle = (register, getHeight) => {
	const $ = c(3);
	let t0;
	if ($[0] !== getHeight || $[1] !== register) {
		t0 = (el) => {
			if (!register) return;
			const sizeHandle = register();
			const updateHeight = () => {
				const height = getHeight ? getHeight(el) : el.offsetHeight;
				sizeHandle.setHeight(height);
			};
			const ro = new ResizeObserver(updateHeight);
			ro.observe(el);
			updateHeight();
			return () => {
				ro.disconnect();
				sizeHandle.unregister();
			};
		};
		$[0] = getHeight;
		$[1] = register;
		$[2] = t0;
	} else t0 = $[2];
	return useManagedRef(t0);
};
var getDocumentOffsetTop = (element) => {
	let top = 0;
	let current = element;
	while (current) {
		top += current.offsetTop;
		current = current.offsetParent;
	}
	return top;
};
var getLayoutOffsetTop = (element, ancestor) => {
	let top = 0;
	let current = element;
	while (current && current !== ancestor) {
		top += current.offsetTop;
		current = current.offsetParent;
	}
	if (current === ancestor) return top;
	return getDocumentOffsetTop(element) - getDocumentOffsetTop(ancestor);
};
/**
* Compute the scroll position that pins the anchor (last user message) to the
* top of the viewport. For tall user messages the anchor is intentionally
* over-scrolled so only `visibleHeight` of it remains visible, leaving room
* for the assistant message below.
*
* Depends only on the anchor's offset within the scroll content; never reads
* `viewport.scrollHeight` (which is volatile while the assistant message
* streams in).
*/
var computeTopAnchorTargetScrollTop = ({ viewport, anchor, tallerThan, visibleHeight }) => {
	const anchorTop = getLayoutOffsetTop(anchor, viewport);
	const anchorHeight = anchor.offsetHeight;
	return anchorTop + Math.max(0, anchorHeight - (anchorHeight <= tallerThan ? anchorHeight : visibleHeight));
};
var computeTopAnchorSlack = ({ scrollHeight, ...targetOptions }) => {
	const { viewport } = targetOptions;
	const targetScrollHeight = computeTopAnchorTargetScrollTop(targetOptions) + viewport.clientHeight;
	return Math.max(0, targetScrollHeight - scrollHeight);
};
var computeTopAnchorReserve = ({ viewport, reserve, ...targetOptions }) => {
	return computeTopAnchorSlack({
		viewport,
		...targetOptions,
		scrollHeight: viewport.scrollHeight - reserve.offsetHeight
	});
};
var createReserveObservers = (onChange) => {
	const resizeObserver = new ResizeObserver(onChange);
	const mutationObserver = new MutationObserver(onChange);
	let observedViewport = null;
	let observedAnchor = null;
	let observedTarget = null;
	const disconnect = () => {
		resizeObserver.disconnect();
		mutationObserver.disconnect();
		observedViewport = null;
		observedAnchor = null;
		observedTarget = null;
	};
	return {
		target: (viewport, anchor, target) => {
			if (observedViewport === viewport && observedAnchor === anchor && observedTarget === target) return;
			disconnect();
			resizeObserver.observe(viewport);
			resizeObserver.observe(anchor);
			resizeObserver.observe(target);
			mutationObserver.observe(target, {
				childList: true,
				subtree: true,
				characterData: true
			});
			observedViewport = viewport;
			observedAnchor = anchor;
			observedTarget = target;
		},
		disconnect
	};
};
var createFrameScheduler = (fn) => {
	let frame = null;
	return {
		schedule: () => {
			if (frame !== null) return;
			frame = requestAnimationFrame(() => {
				frame = null;
				fn();
			});
		},
		cancel: () => {
			if (frame !== null) {
				cancelAnimationFrame(frame);
				frame = null;
			}
		}
	};
};
var mountTopAnchorReserve = (store) => {
	let reserve = null;
	let lastScrolledAnchorId;
	function apply() {
		const state = store.getState();
		const { viewport, anchor, target } = state.element;
		const clamp = state.targetConfig;
		if (state.turnAnchor !== "top" || !viewport || !anchor || !target || !clamp) {
			observers.disconnect();
			if (reserve) {
				setReserveHeight(reserve, 0);
				reserve.remove();
			}
			return;
		}
		reserve ??= createReserveElement();
		if (reserve.parentElement !== target.parentElement || reserve.previousElementSibling !== target) target.after(reserve);
		observers.target(viewport, anchor, target);
		if (setReserveHeight(reserve, computeTopAnchorReserve({
			viewport,
			anchor,
			reserve,
			...clamp
		}))) {
			scheduler.schedule();
			return;
		}
		const anchorId = getAnchorId(anchor);
		if (anchorId !== void 0 && lastScrolledAnchorId === anchorId) return;
		const targetScrollTop = snapScrollTop(computeTopAnchorTargetScrollTop({
			viewport,
			anchor,
			...clamp
		}));
		if (Math.abs(viewport.scrollTop - targetScrollTop) > 1) viewport.scrollTo({
			top: targetScrollTop,
			behavior: "smooth"
		});
		if (anchorId !== void 0) lastScrolledAnchorId = anchorId;
	}
	const scheduler = createFrameScheduler(apply);
	const observers = createReserveObservers(scheduler.schedule);
	scheduler.schedule();
	const unsubscribe = store.subscribe(scheduler.schedule);
	return () => {
		scheduler.cancel();
		unsubscribe();
		observers.disconnect();
		reserve?.remove();
	};
};
/**
* Mounts the top-turn-anchor reserve element against the active
* `ThreadViewport` store. Call this from inside the scrollable viewport so
* the reserve `<div>` is appended next to the streaming assistant message.
*/
var useTopAnchorReserve = (enabled) => {
	const $ = c(4);
	const threadViewportStore = useThreadViewportStore();
	let t0;
	let t1;
	if ($[0] !== enabled || $[1] !== threadViewportStore) {
		t0 = () => {
			if (!enabled) return;
			return mountTopAnchorReserve(threadViewportStore);
		};
		t1 = [enabled, threadViewportStore];
		$[0] = enabled;
		$[1] = threadViewportStore;
		$[2] = t0;
		$[3] = t1;
	} else {
		t0 = $[2];
		t1 = $[3];
	}
	useLayoutEffect$1(t0, t1);
};
var getActiveTopAnchorTurn = ({ isRunning, messages }) => {
	if (!isRunning) return null;
	const target = messages.at(-1);
	const anchor = messages.at(-2);
	if (anchor?.role !== "user" || target?.role !== "assistant") return null;
	return {
		anchorId: anchor.id,
		targetId: target.id
	};
};
var getActiveTopAnchorAnchorId = (options) => getActiveTopAnchorTurn(options)?.anchorId;
var getActiveTopAnchorTargetId = (options) => getActiveTopAnchorTurn(options)?.targetId;
var useViewportSizeRef = () => {
	return useSizeHandle(useThreadViewport(_temp$5), _temp2$1);
};
var useViewportElementRef = () => {
	return useManagedRef(useThreadViewport(_temp3));
};
var useTopAnchorTurn = (enabled) => {
	const $ = c(13);
	const threadViewportStore = useThreadViewportStore();
	let t0;
	if ($[0] !== enabled) {
		t0 = (s) => {
			if (!enabled) return;
			return getActiveTopAnchorAnchorId(s.thread);
		};
		$[0] = enabled;
		$[1] = t0;
	} else t0 = $[1];
	const activeAnchorId = useAuiState(t0);
	let t1;
	if ($[2] !== enabled) {
		t1 = (s_0) => {
			if (!enabled) return;
			return getActiveTopAnchorTargetId(s_0.thread);
		};
		$[2] = enabled;
		$[3] = t1;
	} else t1 = $[3];
	const activeTargetId = useAuiState(t1);
	let t2;
	bb0: {
		if (!activeAnchorId || !activeTargetId) {
			t2 = null;
			break bb0;
		}
		let t3;
		if ($[4] !== activeAnchorId || $[5] !== activeTargetId) {
			t3 = {
				anchorId: activeAnchorId,
				targetId: activeTargetId
			};
			$[4] = activeAnchorId;
			$[5] = activeTargetId;
			$[6] = t3;
		} else t3 = $[6];
		t2 = t3;
	}
	const activeTurn = t2;
	let t3;
	let t4;
	if ($[7] !== activeTurn || $[8] !== threadViewportStore) {
		t3 = () => {
			if (!activeTurn) return;
			const state = threadViewportStore.getState();
			const current = state.topAnchorTurn;
			if (current?.anchorId === activeTurn.anchorId && current.targetId === activeTurn.targetId) return;
			state.setTopAnchorTurn(activeTurn);
		};
		t4 = [activeTurn, threadViewportStore];
		$[7] = activeTurn;
		$[8] = threadViewportStore;
		$[9] = t3;
		$[10] = t4;
	} else {
		t3 = $[9];
		t4 = $[10];
	}
	useLayoutEffect$1(t3, t4);
	let t5;
	if ($[11] !== threadViewportStore) {
		t5 = () => {
			threadViewportStore.getState().setTopAnchorTurn(null);
		};
		$[11] = threadViewportStore;
		$[12] = t5;
	} else t5 = $[12];
	const clearTopAnchorTurn = t5;
	useAuiEvent("thread.initialize", clearTopAnchorTurn);
	useAuiEvent("threadListItem.switchedTo", clearTopAnchorTurn);
};
var ThreadPrimitiveViewportScrollable = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(18);
	let autoScroll;
	let children;
	let rest;
	let scrollToBottomOnInitialize;
	let scrollToBottomOnRunStart;
	let scrollToBottomOnThreadSwitch;
	if ($[0] !== t0) {
		({autoScroll, scrollToBottomOnRunStart, scrollToBottomOnInitialize, scrollToBottomOnThreadSwitch, children, ...rest} = t0);
		$[0] = t0;
		$[1] = autoScroll;
		$[2] = children;
		$[3] = rest;
		$[4] = scrollToBottomOnInitialize;
		$[5] = scrollToBottomOnRunStart;
		$[6] = scrollToBottomOnThreadSwitch;
	} else {
		autoScroll = $[1];
		children = $[2];
		rest = $[3];
		scrollToBottomOnInitialize = $[4];
		scrollToBottomOnRunStart = $[5];
		scrollToBottomOnThreadSwitch = $[6];
	}
	let t1;
	if ($[7] !== autoScroll || $[8] !== scrollToBottomOnInitialize || $[9] !== scrollToBottomOnRunStart || $[10] !== scrollToBottomOnThreadSwitch) {
		t1 = {
			autoScroll,
			scrollToBottomOnRunStart,
			scrollToBottomOnInitialize,
			scrollToBottomOnThreadSwitch
		};
		$[7] = autoScroll;
		$[8] = scrollToBottomOnInitialize;
		$[9] = scrollToBottomOnRunStart;
		$[10] = scrollToBottomOnThreadSwitch;
		$[11] = t1;
	} else t1 = $[11];
	const autoScrollRef = useThreadViewportAutoScroll(t1);
	const viewportSizeRef = useViewportSizeRef();
	const viewportElementRef = useViewportElementRef();
	const threadViewportStore = useThreadViewportStore();
	let t2;
	if ($[12] !== threadViewportStore) {
		t2 = threadViewportStore.getState();
		$[12] = threadViewportStore;
		$[13] = t2;
	} else t2 = $[13];
	const topAnchorEnabled = t2.turnAnchor === "top";
	useTopAnchorTurn(topAnchorEnabled);
	useTopAnchorReserve(topAnchorEnabled);
	const ref = useComposedRefs(forwardedRef, autoScrollRef, viewportSizeRef, viewportElementRef);
	let t3;
	if ($[14] !== children || $[15] !== ref || $[16] !== rest) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...rest,
			ref,
			children
		});
		$[14] = children;
		$[15] = ref;
		$[16] = rest;
		$[17] = t3;
	} else t3 = $[17];
	return t3;
});
ThreadPrimitiveViewportScrollable.displayName = "ThreadPrimitive.ViewportScrollable";
/**
* A scrollable viewport container for thread messages.
*
* This component provides a scrollable area for displaying thread messages with
* automatic scrolling capabilities. It manages the viewport state and provides
* context for child components to access viewport-related functionality.
*
* @example
* ```tsx
* <ThreadPrimitive.Viewport turnAnchor="top">
*   <ThreadPrimitive.Messages>
*     {() => <MyMessage />}
*   </ThreadPrimitive.Messages>
* </ThreadPrimitive.Viewport>
* ```
*/
var ThreadPrimitiveViewport = (0, react_shim_exports.forwardRef)((t0, ref) => {
	const $ = c(13);
	let props;
	let topAnchorMessageClamp;
	let turnAnchor;
	if ($[0] !== t0) {
		({turnAnchor, topAnchorMessageClamp, ...props} = t0);
		$[0] = t0;
		$[1] = props;
		$[2] = topAnchorMessageClamp;
		$[3] = turnAnchor;
	} else {
		props = $[1];
		topAnchorMessageClamp = $[2];
		turnAnchor = $[3];
	}
	let t1;
	if ($[4] !== topAnchorMessageClamp || $[5] !== turnAnchor) {
		t1 = {
			turnAnchor,
			topAnchorMessageClamp
		};
		$[4] = topAnchorMessageClamp;
		$[5] = turnAnchor;
		$[6] = t1;
	} else t1 = $[6];
	let t2;
	if ($[7] !== props || $[8] !== ref) {
		t2 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadPrimitiveViewportScrollable, {
			...props,
			ref
		});
		$[7] = props;
		$[8] = ref;
		$[9] = t2;
	} else t2 = $[9];
	let t3;
	if ($[10] !== t1 || $[11] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ThreadPrimitiveViewportProvider, {
			options: t1,
			children: t2
		});
		$[10] = t1;
		$[11] = t2;
		$[12] = t3;
	} else t3 = $[12];
	return t3;
});
ThreadPrimitiveViewport.displayName = "ThreadPrimitive.Viewport";
function _temp$5(s) {
	return s.registerViewport;
}
function _temp2$1(el) {
	return el.clientHeight;
}
function _temp3(s) {
	return s.registerViewportElement;
}
/**
* A footer container that measures its height for scroll calculations.
*
* This component measures its height and provides it to the viewport context
* so the auto-scroll system can account for any sticky footer overlapping the
* message list.
*
* Multiple ViewportFooter components can be used - their heights are summed.
*
* Typically used with `className="sticky bottom-0"` to keep the footer
* visible at the bottom of the viewport while scrolling.
*
* @example
* ```tsx
* <ThreadPrimitive.Viewport>
*   <ThreadPrimitive.Messages>
*     {() => <MyMessage />}
*   </ThreadPrimitive.Messages>
*   <ThreadPrimitive.ViewportFooter className="sticky bottom-0">
*     <Composer />
*   </ThreadPrimitive.ViewportFooter>
* </ThreadPrimitive.Viewport>
* ```
*/
var ThreadPrimitiveViewportFooter = (0, react_shim_exports.forwardRef)((props, forwardedRef) => {
	const $ = c(3);
	const ref = useComposedRefs(forwardedRef, useSizeHandle(useThreadViewport(_temp$4), _temp2));
	let t0;
	if ($[0] !== props || $[1] !== ref) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref
		});
		$[0] = props;
		$[1] = ref;
		$[2] = t0;
	} else t0 = $[2];
	return t0;
});
ThreadPrimitiveViewportFooter.displayName = "ThreadPrimitive.ViewportFooter";
function _temp$4(s) {
	return s.registerContentInset;
}
function _temp2(el) {
	const marginTop = parseFloat(getComputedStyle(el).marginTop) || 0;
	return el.offsetHeight + marginTop;
}
var useThreadScrollToBottom = (t0) => {
	const $ = c(5);
	let t1;
	if ($[0] !== t0) {
		t1 = t0 === void 0 ? {} : t0;
		$[0] = t0;
		$[1] = t1;
	} else t1 = $[1];
	const { behavior } = t1;
	const isAtBottom = useThreadViewport(_temp$3);
	const threadViewportStore = useThreadViewportStore();
	let t2;
	if ($[2] !== behavior || $[3] !== threadViewportStore) {
		t2 = () => {
			threadViewportStore.getState().scrollToBottom({ behavior });
		};
		$[2] = behavior;
		$[3] = threadViewportStore;
		$[4] = t2;
	} else t2 = $[4];
	const handleScrollToBottom = t2;
	if (isAtBottom) return null;
	return handleScrollToBottom;
};
var ThreadPrimitiveScrollToBottom = createActionButton("ThreadPrimitive.ScrollToBottom", useThreadScrollToBottom, ["behavior"]);
function _temp$3(s) {
	return s.isAtBottom;
}
var useThreadSuggestion = (t0) => {
	const $ = c(4);
	const { prompt, send, clearComposer, autoSend } = t0;
	const resolvedSend = send ?? autoSend ?? false;
	let t1;
	if ($[0] !== clearComposer || $[1] !== prompt || $[2] !== resolvedSend) {
		t1 = {
			prompt,
			send: resolvedSend,
			clearComposer
		};
		$[0] = clearComposer;
		$[1] = prompt;
		$[2] = resolvedSend;
		$[3] = t1;
	} else t1 = $[3];
	const { disabled, trigger } = useSuggestionTrigger(t1);
	if (disabled) return null;
	return trigger;
};
var ThreadPrimitiveSuggestion = createActionButton("ThreadPrimitive.Suggestion", useThreadSuggestion, [
	"prompt",
	"send",
	"clearComposer",
	"autoSend",
	"method"
]);
var thread_exports = /* @__PURE__ */ __exportAll({
	Empty: () => ThreadPrimitiveEmpty,
	If: () => ThreadPrimitiveIf,
	MessageByIndex: () => ThreadPrimitiveMessageByIndex,
	Messages: () => ThreadPrimitiveMessages,
	Root: () => ThreadPrimitiveRoot,
	ScrollToBottom: () => ThreadPrimitiveScrollToBottom,
	Suggestion: () => ThreadPrimitiveSuggestion,
	SuggestionByIndex: () => ThreadPrimitiveSuggestionByIndex,
	Suggestions: () => ThreadPrimitiveSuggestions,
	Viewport: () => ThreadPrimitiveViewport,
	ViewportFooter: () => ThreadPrimitiveViewportFooter,
	ViewportProvider: () => ThreadPrimitiveViewportProvider
});
var ThreadListPrimitiveNew = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(15);
	let disabled;
	let onClick;
	let props;
	if ($[0] !== t0) {
		({onClick, disabled, ...props} = t0);
		$[0] = t0;
		$[1] = disabled;
		$[2] = onClick;
		$[3] = props;
	} else {
		disabled = $[1];
		onClick = $[2];
		props = $[3];
	}
	const isMain = useAuiState(_temp$2);
	const { switchToNewThread } = useThreadListNew();
	let t1;
	if ($[4] !== isMain) {
		t1 = isMain ? {
			"data-active": "true",
			"aria-current": "true"
		} : null;
		$[4] = isMain;
		$[5] = t1;
	} else t1 = $[5];
	let t2;
	if ($[6] !== onClick || $[7] !== switchToNewThread) {
		t2 = composeEventHandlers(onClick, switchToNewThread);
		$[6] = onClick;
		$[7] = switchToNewThread;
		$[8] = t2;
	} else t2 = $[8];
	let t3;
	if ($[9] !== disabled || $[10] !== forwardedRef || $[11] !== props || $[12] !== t1 || $[13] !== t2) {
		t3 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.button, {
			type: "button",
			...t1,
			...props,
			ref: forwardedRef,
			disabled,
			onClick: t2
		});
		$[9] = disabled;
		$[10] = forwardedRef;
		$[11] = props;
		$[12] = t1;
		$[13] = t2;
		$[14] = t3;
	} else t3 = $[14];
	return t3;
});
ThreadListPrimitiveNew.displayName = "ThreadListPrimitive.New";
function _temp$2(s) {
	return s.threads.newThreadId === s.threads.mainThreadId;
}
var useThreadListLoadMore$1 = () => {
	const { loadMore, disabled } = useThreadListLoadMore();
	if (disabled) return null;
	return loadMore;
};
var ThreadListPrimitiveLoadMore = createActionButton("ThreadListPrimitive.LoadMore", useThreadListLoadMore$1);
var ThreadListPrimitiveRoot = (0, react_shim_exports.forwardRef)((props, ref) => {
	const $ = c(3);
	let t0;
	if ($[0] !== props || $[1] !== ref) {
		t0 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref
		});
		$[0] = props;
		$[1] = ref;
		$[2] = t0;
	} else t0 = $[2];
	return t0;
});
ThreadListPrimitiveRoot.displayName = "ThreadListPrimitive.Root";
var threadList_exports = /* @__PURE__ */ __exportAll({
	ItemByIndex: () => ThreadListPrimitiveItemByIndex,
	Items: () => ThreadListPrimitiveItems,
	LoadMore: () => ThreadListPrimitiveLoadMore,
	New: () => ThreadListPrimitiveNew,
	Root: () => ThreadListPrimitiveRoot
});
var ThreadListItemPrimitiveRoot = (0, react_shim_exports.forwardRef)((props, ref) => {
	const $ = c(6);
	const isMain = useAuiState(_temp$1);
	let t0;
	if ($[0] !== isMain) {
		t0 = isMain ? {
			"data-active": "true",
			"aria-current": "true"
		} : null;
		$[0] = isMain;
		$[1] = t0;
	} else t0 = $[1];
	let t1;
	if ($[2] !== props || $[3] !== ref || $[4] !== t0) {
		t1 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...t0,
			...props,
			ref
		});
		$[2] = props;
		$[3] = ref;
		$[4] = t0;
		$[5] = t1;
	} else t1 = $[5];
	return t1;
});
ThreadListItemPrimitiveRoot.displayName = "ThreadListItemPrimitive.Root";
function _temp$1(s) {
	return s.threads.mainThreadId === s.threadListItem.id;
}
var useThreadListItemArchive$1 = () => {
	const { archive } = useThreadListItemArchive();
	return archive;
};
var ThreadListItemPrimitiveArchive = createActionButton("ThreadListItemPrimitive.Archive", useThreadListItemArchive$1);
var useThreadListItemUnarchive$1 = () => {
	const { unarchive } = useThreadListItemUnarchive();
	return unarchive;
};
var ThreadListItemPrimitiveUnarchive = createActionButton("ThreadListItemPrimitive.Unarchive", useThreadListItemUnarchive$1);
var useThreadListItemDelete$1 = () => {
	const { delete: deleteThread } = useThreadListItemDelete();
	return deleteThread;
};
var ThreadListItemPrimitiveDelete = createActionButton("ThreadListItemPrimitive.Delete", useThreadListItemDelete$1);
var useThreadListItemTrigger$1 = () => {
	const { switchTo } = useThreadListItemTrigger();
	return switchTo;
};
var ThreadListItemPrimitiveTrigger = createActionButton("ThreadListItemPrimitive.Trigger", useThreadListItemTrigger$1);
var threadListItem_exports = /* @__PURE__ */ __exportAll({
	Archive: () => ThreadListItemPrimitiveArchive,
	Delete: () => ThreadListItemPrimitiveDelete,
	Root: () => ThreadListItemPrimitiveRoot,
	Title: () => ThreadListItemPrimitiveTitle,
	Trigger: () => ThreadListItemPrimitiveTrigger,
	Unarchive: () => ThreadListItemPrimitiveUnarchive
});
var findMessageId = (node) => {
	let el = node instanceof HTMLElement ? node : node?.parentElement ?? null;
	while (el) {
		const id = el.getAttribute("data-message-id");
		if (id) return id;
		el = el.parentElement;
	}
	return null;
};
var getSelectionMessageId = (selection) => {
	const { anchorNode, focusNode } = selection;
	if (!anchorNode || !focusNode) return null;
	const anchorId = findMessageId(anchorNode);
	const focusId = findMessageId(focusNode);
	if (!anchorId || anchorId !== focusId) return null;
	return anchorId;
};
var SelectionToolbarContext = createContext(null);
var useSelectionToolbarInfo = () => {
	return useContext(SelectionToolbarContext);
};
/**
* A floating toolbar that appears when text is selected within a message.
*
* Listens for mouse and keyboard selection events, validates that the
* selection is within a single message, and renders a positioned portal
* near the selection. Prevents mousedown from clearing the selection.
*
* @example
* ```tsx
* <SelectionToolbarPrimitive.Root>
*   <SelectionToolbarPrimitive.Quote>Quote</SelectionToolbarPrimitive.Quote>
* </SelectionToolbarPrimitive.Root>
* ```
*/
var SelectionToolbarPrimitiveRoot = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(20);
	let onMouseDown;
	let props;
	let style;
	if ($[0] !== t0) {
		({onMouseDown, style, ...props} = t0);
		$[0] = t0;
		$[1] = onMouseDown;
		$[2] = props;
		$[3] = style;
	} else {
		onMouseDown = $[1];
		props = $[2];
		style = $[3];
	}
	const [info, setInfo] = useState(null);
	let t1;
	let t2;
	if ($[4] === Symbol.for("react.memo_cache_sentinel")) {
		t1 = () => {
			const checkSelection = () => {
				requestAnimationFrame(() => {
					const sel = window.getSelection();
					if (!sel || sel.isCollapsed) {
						setInfo(null);
						return;
					}
					const text = sel.toString().trim();
					if (!text) {
						setInfo(null);
						return;
					}
					const messageId = getSelectionMessageId(sel);
					if (!messageId) {
						setInfo(null);
						return;
					}
					setInfo({
						text,
						messageId,
						rect: sel.getRangeAt(0).getBoundingClientRect()
					});
				});
			};
			const handleSelectionCollapse = () => {
				const sel_0 = window.getSelection();
				if (!sel_0 || sel_0.isCollapsed) setInfo(null);
			};
			const handleScroll = () => {
				setInfo(null);
			};
			document.addEventListener("mouseup", checkSelection);
			document.addEventListener("keyup", checkSelection);
			document.addEventListener("selectionchange", handleSelectionCollapse);
			document.addEventListener("scroll", handleScroll, true);
			return () => {
				document.removeEventListener("mouseup", checkSelection);
				document.removeEventListener("keyup", checkSelection);
				document.removeEventListener("selectionchange", handleSelectionCollapse);
				document.removeEventListener("scroll", handleScroll, true);
			};
		};
		t2 = [];
		$[4] = t1;
		$[5] = t2;
	} else {
		t1 = $[4];
		t2 = $[5];
	}
	useEffect(t1, t2);
	if (!info) return null;
	const t3 = `${info.rect.top - 8}px`;
	const t4 = `${info.rect.left + info.rect.width / 2}px`;
	let t5;
	if ($[6] !== style || $[7] !== t3 || $[8] !== t4) {
		t5 = {
			position: "fixed",
			top: t3,
			left: t4,
			transform: "translate(-50%, -100%)",
			zIndex: 50,
			...style
		};
		$[6] = style;
		$[7] = t3;
		$[8] = t4;
		$[9] = t5;
	} else t5 = $[9];
	const positionStyle = t5;
	let t6;
	if ($[10] !== onMouseDown) {
		t6 = (e) => {
			e.preventDefault();
			onMouseDown?.(e);
		};
		$[10] = onMouseDown;
		$[11] = t6;
	} else t6 = $[11];
	let t7;
	if ($[12] !== forwardedRef || $[13] !== positionStyle || $[14] !== props || $[15] !== t6) {
		t7 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.div, {
			...props,
			ref: forwardedRef,
			style: positionStyle,
			onMouseDown: t6
		});
		$[12] = forwardedRef;
		$[13] = positionStyle;
		$[14] = props;
		$[15] = t6;
		$[16] = t7;
	} else t7 = $[16];
	let t8;
	if ($[17] !== info || $[18] !== t7) {
		t8 = (0, import_react_dom.createPortal)(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectionToolbarContext.Provider, {
			value: info,
			children: t7
		}), document.body);
		$[17] = info;
		$[18] = t7;
		$[19] = t8;
	} else t8 = $[19];
	return t8;
});
SelectionToolbarPrimitiveRoot.displayName = "SelectionToolbarPrimitive.Root";
/**
* A button that quotes the currently selected text.
*
* Must be placed inside `SelectionToolbarPrimitive.Root`. Reads the
* selection info from context (captured by the Root), sets it as a
* quote in the thread composer, and clears the selection.
*
* @example
* ```tsx
* <SelectionToolbarPrimitive.Quote>
*   <QuoteIcon /> Quote
* </SelectionToolbarPrimitive.Quote>
* ```
*/
var SelectionToolbarPrimitiveQuote = (0, react_shim_exports.forwardRef)((t0, forwardedRef) => {
	const $ = c(15);
	let disabled;
	let onClick;
	let props;
	if ($[0] !== t0) {
		({onClick, disabled, ...props} = t0);
		$[0] = t0;
		$[1] = disabled;
		$[2] = onClick;
		$[3] = props;
	} else {
		disabled = $[1];
		onClick = $[2];
		props = $[3];
	}
	const aui = useAui();
	const info = useSelectionToolbarInfo();
	let t1;
	if ($[4] !== aui || $[5] !== info) {
		t1 = () => {
			if (!info) return;
			aui.thread().composer().setQuote({
				text: info.text,
				messageId: info.messageId
			});
			window.getSelection()?.removeAllRanges();
		};
		$[4] = aui;
		$[5] = info;
		$[6] = t1;
	} else t1 = $[6];
	const handleClick = t1;
	const t2 = disabled || !info;
	let t3;
	if ($[7] !== handleClick || $[8] !== onClick) {
		t3 = composeEventHandlers(onClick, handleClick);
		$[7] = handleClick;
		$[8] = onClick;
		$[9] = t3;
	} else t3 = $[9];
	let t4;
	if ($[10] !== forwardedRef || $[11] !== props || $[12] !== t2 || $[13] !== t3) {
		t4 = /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.button, {
			type: "button",
			...props,
			ref: forwardedRef,
			disabled: t2,
			onClick: t3
		});
		$[10] = forwardedRef;
		$[11] = props;
		$[12] = t2;
		$[13] = t3;
		$[14] = t4;
	} else t4 = $[14];
	return t4;
});
SelectionToolbarPrimitiveQuote.displayName = "SelectionToolbarPrimitive.Quote";
var selectionToolbar_exports = /* @__PURE__ */ __exportAll({
	Quote: () => SelectionToolbarPrimitiveQuote,
	Root: () => SelectionToolbarPrimitiveRoot
});
/**
* @deprecated Use {@link useAuiState} to select and narrow `s.part`.
* Return `null` for optional rendering, or throw inside the selector to
* preserve the old hook's strict behavior.
*
* @example
* ```tsx
* const reasoning = useAuiState((s) => {
*   if (s.part.type !== "reasoning") return null;
*   return s.part;
* });
* ```
*
* See the {@link https://assistant-ui.com/docs/migrations/v0-12 migration guide}.
*/
var useMessagePartReasoning = () => {
	return useAuiState(_temp);
};
function _temp(s) {
	if (s.part.type !== "reasoning") throw new Error("MessagePartReasoning can only be used inside reasoning message parts.");
	return s.part;
}
/**
* @deprecated Under active development and may change without notice.
*
* Bundles slash command definitions (with inline `execute` callbacks) into
* `{adapter, action}` that plug directly into `ComposerTriggerPopover`.
* `execute` stays in the hook closure and is never attached to the returned
* `TriggerItem`, keeping items serializable.
*
* @example
* ```tsx
* const slash = unstable_useSlashCommandAdapter({
*   commands: [
*     { id: "summarize", execute: () => runSummarize(), icon: "FileText" },
*     { id: "translate", execute: () => runTranslate(), icon: "Languages" },
*   ],
* });
*
* <ComposerTriggerPopover char="/" {...slash} />
* ```
*/
function unstable_useSlashCommandAdapter(options) {
	const { commands, removeOnExecute } = options;
	const commandsRef = useRef(commands);
	commandsRef.current = commands;
	return useMemo(() => {
		return {
			adapter: {
				categories: () => [],
				categoryItems: () => [],
				search: (query) => {
					const lower = query.toLowerCase();
					return commandsRef.current.filter((c) => matchesQuery(c, lower)).map(toItem);
				}
			},
			action: {
				onExecute: (item) => {
					commandsRef.current.find((c) => c.id === item.id)?.execute();
				},
				...removeOnExecute !== void 0 ? { removeOnExecute } : {}
			},
			...options.iconMap ? { iconMap: options.iconMap } : {},
			...options.fallbackIcon ? { fallbackIcon: options.fallbackIcon } : {}
		};
	}, [
		removeOnExecute,
		options.iconMap,
		options.fallbackIcon
	]);
}
function toItem(cmd) {
	return {
		id: cmd.id,
		type: "command",
		label: cmd.label ?? `/${cmd.id}`,
		...cmd.description !== void 0 ? { description: cmd.description } : {},
		...cmd.icon !== void 0 ? { metadata: { icon: cmd.icon } } : {}
	};
}
function matchesQuery(cmd, lower) {
	if (!lower) return true;
	if (cmd.id.toLowerCase().includes(lower)) return true;
	if (cmd.label?.toLowerCase().includes(lower)) return true;
	if (cmd.description?.toLowerCase().includes(lower)) return true;
	return false;
}
export { AssistantRuntimeProvider as C, useComposerRuntime as S, dispatchDiscreteCustomEvent as _, threadList_exports as a, useComposedRefs as b, useSmooth as c, useTriggerPopoverScopeContext as d, attachment_exports as f, Primitive$1 as g, composeEventHandlers as h, threadListItem_exports as i, useMessagePartText as l, useCallbackRef as m, useMessagePartReasoning as n, thread_exports as o, useEscapeKeydown as p, selectionToolbar_exports as r, message_exports as s, unstable_useSlashCommandAdapter as t, composer_exports as u, createSlot as v, require_react_dom as w, useMessage as x, createSlottable as y };
