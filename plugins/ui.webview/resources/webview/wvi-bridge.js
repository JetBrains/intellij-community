(function() {
	//#region packages/impl/src/jsonRpc.ts
	var CANCEL_CALL_METHOD = "$/cancelRequest";
	var RUNTIME_INFO_REQUEST_METHOD = "$/webview/runtimeInfoRequest";
	var INVALID_FRAME = -32600;
	var METHOD_NOT_FOUND = -32601;
	var CANCELLED = -32800;
	function callKey(id) {
		return JSON.stringify(id);
	}
	function makeError(code, message, data) {
		const error = {
			code,
			message
		};
		if (typeof data !== "undefined") error.data = data;
		return error;
	}
	//#endregion
	//#region packages/impl/src/runtimeOverlay.ts
	var RUNTIME_OVERLAY_ID = "__wvi-runtime-overlay";
	function applyRuntimeInfo(params) {
		const runtimeInfo = params && typeof params === "object" ? params : void 0;
		if (!runtimeInfo || runtimeInfo.overlayVisible !== true) {
			removeRuntimeOverlay();
			return;
		}
		const displayName = typeof runtimeInfo.displayName === "string" ? runtimeInfo.displayName.trim() : "";
		if (displayName.length === 0) {
			removeRuntimeOverlay();
			return;
		}
		ensureRuntimeOverlay(displayName);
	}
	function ensureRuntimeOverlay(displayName) {
		if (!document.body) {
			document.addEventListener("DOMContentLoaded", function onRuntimeOverlayReady() {
				document.removeEventListener("DOMContentLoaded", onRuntimeOverlayReady);
				ensureRuntimeOverlay(displayName);
			});
			return;
		}
		let overlay = document.getElementById(RUNTIME_OVERLAY_ID);
		if (!overlay) {
			overlay = document.createElement("div");
			overlay.id = RUNTIME_OVERLAY_ID;
			overlay.style.position = "fixed";
			overlay.style.right = "6px";
			overlay.style.bottom = "4px";
			overlay.style.zIndex = "2147483647";
			overlay.style.pointerEvents = "none";
			overlay.style.userSelect = "none";
			overlay.style.padding = "2px 5px";
			overlay.style.borderRadius = "4px";
			overlay.style.background = "rgba(0, 0, 0, 0.52)";
			overlay.style.color = "rgba(255, 255, 255, 0.86)";
			overlay.style.font = "11px/14px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
			overlay.style.letterSpacing = "0";
			overlay.style.whiteSpace = "nowrap";
			document.body.appendChild(overlay);
		}
		overlay.textContent = displayName;
	}
	function removeRuntimeOverlay() {
		const overlay = document.getElementById(RUNTIME_OVERLAY_ID);
		overlay?.parentNode?.removeChild(overlay);
	}
	//#endregion
	//#region packages/impl/src/nativeTransport.ts
	var HANDLER_CHANNEL = "webviewIpc";
	var webViewWindow = window;
	function webkitMessageHandler() {
		return webViewWindow.webkit?.messageHandlers?.[HANDLER_CHANNEL];
	}
	function detectTransport() {
		if (webViewWindow.chrome?.webview) return "webview2";
		if (webkitMessageHandler()) return "webkit";
		if (typeof webViewWindow.__wviJcefQuery === "function") return "jcef";
		return "missing";
	}
	function sendToHost(frame) {
		try {
			const raw = JSON.stringify(frame);
			if (webViewWindow.chrome?.webview) {
				webViewWindow.chrome.webview.postMessage(raw);
				return true;
			}
			const webkitHandler = webkitMessageHandler();
			if (webkitHandler) {
				webkitHandler.postMessage(raw);
				return true;
			}
			if (typeof webViewWindow.__wviJcefQuery === "function") {
				webViewWindow.__wviJcefQuery({
					request: raw,
					onFailure(code, message) {
						console.warn("[__WVI__] JCEF query failed:", code, message);
					}
				});
				return true;
			}
			console.warn("[__WVI__] native host bridge unavailable; dropping", frame);
			return false;
		} catch (err) {
			console.error("[__WVI__] postMessage failed:", err);
			return false;
		}
	}
	//#endregion
	//#region packages/impl/src/messageBus.ts
	var MAX_PENDING_INBOUND = 128;
	function createWebViewBridge() {
		let nextCallId = 1;
		const pendingCalls = Object.create(null);
		const notificationHandlers = Object.create(null);
		const typedApiMethods = Object.create(null);
		const pendingInboundNotifications = [];
		function sendErrorResponse(id, code, message, data) {
			sendToHost({
				jsonrpc: "2.0",
				id,
				error: makeError(code, message, data)
			});
		}
		function requestRuntimeInfo() {
			sendToHost({
				jsonrpc: "2.0",
				method: RUNTIME_INFO_REQUEST_METHOD
			});
		}
		function enqueueInboundNotification(frame) {
			if (pendingInboundNotifications.length >= MAX_PENDING_INBOUND) {
				pendingInboundNotifications.shift();
				console.warn("[__WVI__] dropping queued inbound notification; queueLimit=128");
			}
			pendingInboundNotifications.push(frame);
		}
		function dispatchNotification(method, params) {
			const list = notificationHandlers[method];
			if (!list || list.length === 0) return false;
			const snapshot = list.slice();
			for (const handler of snapshot) try {
				handler(params);
			} catch (err) {
				console.error("[__WVI__] notification handler for \"" + method + "\" threw:", err);
			}
			return true;
		}
		function replayPendingNotifications(method) {
			for (let i = 0; i < pendingInboundNotifications.length;) {
				const frame = pendingInboundNotifications[i];
				if (frame.method !== method) {
					i++;
					continue;
				}
				pendingInboundNotifications.splice(i, 1);
				dispatchNotification(frame.method, frame.params);
			}
		}
		function registerNotificationHandler(method, handler) {
			if (typeof method !== "string" || typeof handler !== "function") throw new Error("WebView notification registration requires a string method and function handler");
			let list = notificationHandlers[method];
			if (!list) {
				list = [];
				notificationHandlers[method] = list;
			}
			list.push(handler);
			replayPendingNotifications(method);
			return function unsubscribe() {
				const current = notificationHandlers[method];
				if (!current) return;
				const idx = current.indexOf(handler);
				if (idx >= 0) current.splice(idx, 1);
				if (current.length === 0) delete notificationHandlers[method];
			};
		}
		function sendCancel(id, message) {
			sendToHost({
				jsonrpc: "2.0",
				method: CANCEL_CALL_METHOD,
				params: {
					id,
					message: message || null
				}
			});
		}
		function rejectPendingCall(id, error) {
			const key = callKey(id);
			const pending = pendingCalls[key];
			if (!pending) return false;
			delete pendingCalls[key];
			if (pending.signal && pending.abortListener) pending.signal.removeEventListener("abort", pending.abortListener);
			pending.reject(error);
			return true;
		}
		function handleCall(frame) {
			sendErrorResponse(frame.id, METHOD_NOT_FOUND, "JavaScript API calls are not supported: " + frame.method);
		}
		function handleResponse(frame) {
			const key = callKey(frame.id);
			const pending = pendingCalls[key];
			if (!pending) {
				console.warn("[__WVI__] dropping response for unknown call", frame.id);
				return;
			}
			delete pendingCalls[key];
			if (pending.signal && pending.abortListener) pending.signal.removeEventListener("abort", pending.abortListener);
			if ("error" in frame) {
				const error = new Error(frame.error.message || "WebView RPC error");
				error.code = frame.error.code;
				error.data = frame.error.data;
				pending.reject(error);
			} else pending.resolve(frame.result);
		}
		function handleNotification(frame) {
			if (frame.method === "$/cancelRequest") {
				const params = frame.params && typeof frame.params === "object" ? frame.params : void 0;
				const id = params && "id" in params ? params.id : void 0;
				if (typeof id !== "undefined") rejectPendingCall(id, makeError(CANCELLED, params && "message" in params && typeof params.message === "string" ? params.message : "Call cancelled"));
				return;
			}
			if (frame.method === "$/webview/runtimeInfo") {
				applyRuntimeInfo(frame.params);
				return;
			}
			if (!dispatchNotification(frame.method, frame.params)) enqueueInboundNotification(frame);
		}
		function handleInboundFrame(frame) {
			if (!frame || typeof frame !== "object" || !("jsonrpc" in frame) || frame.jsonrpc !== "2.0") {
				console.warn("[__WVI__] dropping invalid JSON-RPC frame", frame);
				return;
			}
			const candidate = frame;
			const hasId = Object.prototype.hasOwnProperty.call(candidate, "id");
			const hasMethod = typeof candidate.method === "string";
			const hasResult = Object.prototype.hasOwnProperty.call(candidate, "result");
			const hasError = Object.prototype.hasOwnProperty.call(candidate, "error");
			if (hasId && hasMethod && !hasResult && !hasError) {
				handleCall(candidate);
				return;
			}
			if (hasId && !hasMethod && hasResult !== hasError) {
				handleResponse(candidate);
				return;
			}
			if (!hasId && hasMethod && !hasResult && !hasError) {
				handleNotification(candidate);
				return;
			}
			if (hasId) sendErrorResponse(candidate.id ?? null, INVALID_FRAME, "Invalid JSON-RPC frame shape");
			else console.warn("[__WVI__] dropping invalid JSON-RPC notification", frame);
		}
		function callHostMethod(method, params, options) {
			if (typeof method !== "string") throw new Error("WebView host API method must be a string");
			const id = nextCallId++;
			const signal = options?.signal;
			return new Promise((resolve, reject) => {
				if (signal?.aborted) {
					reject(makeError(CANCELLED, "Call cancelled"));
					return;
				}
				const key = callKey(id);
				let abortListener;
				if (signal) {
					abortListener = function onAbort() {
						delete pendingCalls[key];
						sendCancel(id, "Call cancelled");
						reject(makeError(CANCELLED, "Call cancelled"));
					};
					signal.addEventListener("abort", abortListener, { once: true });
				}
				pendingCalls[key] = {
					resolve,
					reject,
					signal,
					abortListener
				};
				const frame = {
					jsonrpc: "2.0",
					id,
					method
				};
				if (typeof params !== "undefined") frame.params = params;
				if (!sendToHost(frame)) {
					delete pendingCalls[key];
					if (signal && abortListener) signal.removeEventListener("abort", abortListener);
					reject(/* @__PURE__ */ new Error("WebView native transport is unavailable"));
				}
			});
		}
		function sendNotification(method, params) {
			const frame = {
				jsonrpc: "2.0",
				method
			};
			if (typeof params !== "undefined") frame.params = params;
			if (!sendToHost(frame)) return Promise.reject(/* @__PURE__ */ new Error("WebView native transport is unavailable"));
			return Promise.resolve();
		}
		function notificationMethod(descriptor) {
			const method = descriptor?.method;
			if (typeof method !== "string" || method.length === 0) throw new Error("WebView notification descriptor requires a non-empty method");
			return method;
		}
		function validateNamespace(namespace, owner) {
			if (typeof namespace !== "string" || namespace.length === 0) throw new Error(owner + " requires a non-empty namespace");
			if (namespace.charAt(0) === "." || namespace.charAt(namespace.length - 1) === "." || namespace.charAt(0) === "/" || namespace.charAt(namespace.length - 1) === "/") throw new Error(owner + " namespace must not start or end with '.' or '/': " + namespace);
			if (!/^[A-Za-z0-9_.-]+$/.test(namespace)) throw new Error(owner + " namespace contains unsupported characters: " + namespace);
			return namespace;
		}
		function apiIdNamespace(id) {
			if (!id || typeof id !== "object") throw new Error("__WVI__ typed API id is required");
			return validateNamespace(id.namespace, "__WVI__ typed API id");
		}
		function wireMethod(namespace, methodName) {
			if (methodName.length === 0) throw new Error("WebView typed API method name must not be empty: " + namespace);
			return namespace + "/" + methodName;
		}
		function typedApiMethodSource(method) {
			const stack = (/* @__PURE__ */ new Error()).stack;
			if (typeof stack !== "string") return method;
			const caller = stack.split("\n").slice(1).map((line) => line.trim()).find((line) => {
				return line.length > 0 && !line.includes("typedApiMethodSource") && !line.includes("typedImplementationMethod") && !line.includes("typedImplementationMethods") && !line.includes("reserveTypedApiMethods") && !line.includes("implementApi") && !line.includes("Object.implement");
			});
			return caller ? method + " at " + caller : method;
		}
		function typedImplementationMethod(namespace, methodName, member) {
			if (typeof member !== "function") return;
			const method = wireMethod(namespace, methodName);
			return {
				registration: {
					method,
					source: typedApiMethodSource(method)
				},
				member
			};
		}
		function implementationMethodNames(implementation) {
			const names = [];
			const seen = Object.create(null);
			let current = implementation;
			while (current && current !== Object.prototype) {
				for (const name of Object.getOwnPropertyNames(current)) {
					if (name === "constructor" || seen[name]) continue;
					seen[name] = true;
					names.push(name);
				}
				current = Object.getPrototypeOf(current);
			}
			return names;
		}
		function typedImplementationMethods(namespace, implementation) {
			const methods = [];
			for (const methodName of implementationMethodNames(implementation)) {
				const method = typedImplementationMethod(namespace, methodName, implementation[methodName]);
				if (method) methods.push(method);
			}
			return methods;
		}
		function reserveTypedApiMethods(methods) {
			const reservedMethods = [];
			try {
				for (const method of methods) {
					const previous = typedApiMethods[method.method];
					if (previous) throw new Error("WebView typed API method is already registered: " + method.method + ". Existing: " + previous.source + ". New: " + method.source);
					typedApiMethods[method.method] = method;
					reservedMethods.push(method);
				}
			} catch (err) {
				for (const method of reservedMethods) if (typedApiMethods[method.method] === method) delete typedApiMethods[method.method];
				throw err;
			}
			let closed = false;
			return { close() {
				if (closed) return;
				closed = true;
				for (const method of methods) if (typedApiMethods[method.method] === method) delete typedApiMethods[method.method];
			} };
		}
		function implementApi(namespace, implementation) {
			const methods = typedImplementationMethods(namespace, implementation);
			const registrations = [];
			try {
				registrations.push(reserveTypedApiMethods(methods.map((method) => method.registration)));
				for (const method of methods) registrations.push(createNotificationBinding({ method: method.registration.method }).on(function dispatchTypedNotification(params) {
					method.member.call(implementation, params);
				}));
			} catch (err) {
				for (let i = registrations.length - 1; i >= 0; i--) registrations[i].close();
				throw err;
			}
			let closed = false;
			return { close() {
				if (closed) return;
				closed = true;
				for (let i = registrations.length - 1; i >= 0; i--) registrations[i].close();
			} };
		}
		function createNotificationBinding(descriptor) {
			const method = notificationMethod(descriptor);
			return {
				send(params) {
					return sendNotification(method, params);
				},
				on(handler) {
					if (typeof handler !== "function") throw new Error("WebView notification handler must be a function: " + method);
					return { close: registerNotificationHandler(method, handler) };
				}
			};
		}
		const api = {
			__installed: true,
			transport() {
				return detectTransport();
			},
			hostApi(namespace) {
				if (typeof Proxy !== "function") throw new Error("__WVI__.hostApi requires JavaScript Proxy support");
				const validatedNamespace = validateNamespace(namespace, "__WVI__.hostApi(namespace)");
				return new Proxy({}, { get(_target, property) {
					if (typeof property !== "string") return;
					return function proxyCall(params, options) {
						return callHostMethod(wireMethod(validatedNamespace, property), params, options);
					};
				} });
			},
			callable(id, options) {
				if (options?.bridge && options.bridge !== api) return options.bridge.callable(id);
				return api.hostApi(apiIdNamespace(id));
			},
			implement(id, implementation, options) {
				if (options?.bridge && options.bridge !== api) return options.bridge.implement(id, implementation);
				if (!implementation || typeof implementation !== "object") throw new Error("__WVI__.implement(id, implementation) requires an implementation object");
				return implementApi(apiIdNamespace(id), implementation);
			},
			notification(descriptor) {
				return createNotificationBinding(descriptor);
			},
			notifications(descriptors) {
				if (!descriptors || typeof descriptors !== "object") throw new Error("__WVI__.notifications(descriptors) requires an object map");
				const result = {};
				Object.keys(descriptors).forEach((key) => {
					result[key] = createNotificationBinding(descriptors[key]);
				});
				return result;
			},
			__deliver(raw) {
				if (typeof raw !== "string") return;
				let frame;
				try {
					frame = JSON.parse(raw);
				} catch (err) {
					console.error("[__WVI__] failed to parse inbound frame:", err);
					return;
				}
				handleInboundFrame(frame);
			}
		};
		requestRuntimeInfo();
		return api;
	}
	//#endregion
	//#region packages/impl/src/bridge.ts
	function installWebViewBridge() {
		if (window.__WVI__?.__installed) return window.__WVI__;
		const bridge = createWebViewBridge();
		window.__WVI__ = bridge;
		return bridge;
	}
	//#endregion
	//#region packages/impl/src/entry.ts
	installWebViewBridge();
	//#endregion
})();
