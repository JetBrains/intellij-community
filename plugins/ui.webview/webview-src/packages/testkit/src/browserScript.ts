// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export function mockWebViewBridgeBrowserScript(): string {
  return String.raw`(function () {
  if (window.__WVI_MOCK__ && window.__WVI__) return;

  var hostMethods = new Map();
  var pageMethods = new Map();
  var pageImplementationWaiters = new Map();
  var notificationHandlers = new Map();
  var callLog = [];
  var defaultMockFonts = {
    ui: {
      families: ["Inter", "Segoe UI"],
      size: 13,
      lineHeight: 16,
      sizes: { h0: 25, h1: 22, h2: 18, h3: 16, h4: 14, regular: 13, medium: 12, small: 11, mini: 9 }
    },
    editor: {
      families: ["JetBrains Mono"],
      size: 13,
      lineHeight: 1.2,
      ligatures: true,
      fontFeatureSettings: []
    }
  };
  var currentTheme = { theme: "dark", fonts: defaultMockFonts };

  function apiId(namespace) {
    return { namespace: namespace };
  }

  function validateNamespace(id) {
    if (!id || typeof id.namespace !== "string" || id.namespace.length === 0) {
      throw new Error("Mock WebView API id requires a non-empty namespace");
    }
    return id.namespace;
  }

  function wireMethod(namespace, methodName) {
    return namespace + "/" + methodName;
  }

  function implementationMethodNames(implementation) {
    var names = [];
    var seen = new Set();
    var current = implementation;
    while (current && current !== Object.prototype) {
      Object.getOwnPropertyNames(current).forEach(function (name) {
        if (name === "constructor" || seen.has(name)) return;
        seen.add(name);
        names.push(name);
      });
      current = Object.getPrototypeOf(current);
    }
    return names;
  }

  function hasImplementationForNamespace(methods, namespace) {
    var prefix = namespace + "/";
    for (var method of methods.keys()) {
      if (method.indexOf(prefix) === 0) return true;
    }
    return false;
  }

  function notifyPageImplementationWaiters(namespace) {
    var waiters = pageImplementationWaiters.get(namespace);
    if (!waiters) return;
    Array.from(waiters).forEach(function (waiter) { waiter(); });
  }

  function registerMethods(target, id, implementation) {
    var namespace = validateNamespace(id);
    var registeredMethods = [];
    implementationMethodNames(implementation).forEach(function (methodName) {
      var member = implementation[methodName];
      if (typeof member !== "function") return;
      var method = wireMethod(namespace, methodName);
      if (target.has(method)) throw new Error("Mock WebView API method is already registered: " + method);
      target.set(method, function (params) { return member.call(implementation, params); });
      registeredMethods.push(method);
    });
    notifyPageImplementationWaiters(namespace);
    return {
      close: function () {
        registeredMethods.forEach(function (method) { target.delete(method); });
      }
    };
  }

  function mockRpcError(code, message) {
    var error = new Error(message);
    error.code = code;
    return error;
  }

  function createCallable(target, side, id) {
    var namespace = validateNamespace(id);
    return new Proxy({}, {
      get: function (_target, property) {
        if (typeof property !== "string") return undefined;
        return async function (params) {
          var method = wireMethod(namespace, property);
          callLog.push({ side: side, method: method, params: params });
          var implementation = target.get(method);
          if (!implementation) throw mockRpcError(-32601, "Method not found: " + method);
          return implementation(params);
        };
      }
    });
  }

  function notificationMethod(descriptor) {
    var method = descriptor && descriptor.method;
    if (typeof method !== "string" || method.length === 0) {
      throw new Error("Mock WebView notification descriptor requires a non-empty method");
    }
    return method;
  }

  function createNotificationBinding(descriptor) {
    var method = notificationMethod(descriptor);
    return {
      send: function (params) {
        var handlers = notificationHandlers.get(method);
        if (handlers) Array.from(handlers).forEach(function (handler) { handler(params); });
        return Promise.resolve();
      },
      on: function (handler) {
        var handlers = notificationHandlers.get(method);
        if (!handlers) {
          handlers = new Set();
          notificationHandlers.set(method, handlers);
        }
        handlers.add(handler);
        return { close: function () { handlers.delete(handler); } };
      }
    };
  }

  function sendThemeChanged() {
    context.page.callable(apiId("webview.theme")).themeChanged(currentTheme).catch(function () {});
  }

  var bridge = {
    __installed: true,
    transport: function () { return "browser-test"; },
    callable: function (id) { return createCallable(hostMethods, "host", id); },
    implement: function (id, implementation) { return registerMethods(pageMethods, id, implementation); },
    notification: createNotificationBinding,
    notifications: function (descriptors) {
      var result = {};
      Object.keys(descriptors).forEach(function (key) {
        result[key] = createNotificationBinding(descriptors[key]);
      });
      return result;
    }
  };

  var context = {
    bridge: bridge,
    host: {
      implement: function (id, implementation) { return registerMethods(hostMethods, id, implementation); }
    },
    page: {
      callable: function (id) { return createCallable(pageMethods, "page", id); },
      whenImplemented: function (id, callback) {
        var namespace = validateNamespace(id);
        var closed = false;
        var notify = function () {
          if (!closed) callback(createCallable(pageMethods, "page", id));
        };
        var waiters = pageImplementationWaiters.get(namespace);
        if (!waiters) {
          waiters = new Set();
          pageImplementationWaiters.set(namespace, waiters);
        }
        waiters.add(notify);
        if (hasImplementationForNamespace(pageMethods, namespace)) queueMicrotask(notify);
        return {
          close: function () {
            closed = true;
            waiters.delete(notify);
          }
        };
      }
    },
    calls: {
      all: function () { return callLog.slice(); },
      byMethod: function (method) { return callLog.filter(function (call) { return call.method === method; }); },
      clear: function () { callLog.length = 0; }
    },
    theme: {
      set: function (theme, fonts) {
        currentTheme = { theme: theme, fonts: fonts || defaultMockFonts };
        sendThemeChanged();
      }
    },
    apply: function (mock) { return mock.setup(context); }
  };

  hostMethods.set("webview.theme/themeRequest", function () { sendThemeChanged(); });
  hostMethods.set("webview.focus/activated", function () {});
  hostMethods.set("webview.focus/exit", function () {});
  hostMethods.set("$/webview/runtimeInfoRequest", function () {});
  context.page.whenImplemented(apiId("webview.theme"), sendThemeChanged);

  window.__WVI__ = bridge;
  window.__WVI_MOCK__ = context;
})();`
}
