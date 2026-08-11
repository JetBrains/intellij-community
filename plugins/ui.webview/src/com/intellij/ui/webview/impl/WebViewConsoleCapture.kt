// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.webview.api.WebViewMessageRegistration
import com.intellij.ui.webview.api.WebViewNotification
import com.intellij.ui.webview.impl.engine.WebViewScript
import com.intellij.ui.webview.impl.rpc.WebViewMessageBusImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.time.Instant

@ApiStatus.Internal
internal class WebViewConsoleCapture(consoleLogCategory: String = CONSOLE_LOG_CATEGORY) {
  private val consoleLogCategory: String = consoleLogCategory.trim().ifEmpty { CONSOLE_LOG_CATEGORY }

  @Volatile
  private var viewId: String? = null

  fun register(bus: WebViewMessageBusImpl): WebViewMessageRegistration {
    return bus.registerNotificationHandler(ConsoleNotification) { payload, _ ->
      log(payload)
    }
  }

  fun setViewId(viewId: String?) {
    this.viewId = viewId
  }

  private fun log(payload: WebViewConsolePayload) {
    val message = formatMessage(payload)
    val logger = logger()
    when (payload.method.lowercase()) {
      "error", "assert" -> logger.error(message)
      "warn", "warning" -> logger.warn(message)
      "debug" -> logger.debug(message)
      "trace", "group", "groupcollapsed", "groupend", "profile", "profileend", "clear" -> logger.trace(message)
      else -> logger.info(message)
    }
  }

  internal fun formatMessage(payload: WebViewConsolePayload): String {
    val prefix = buildString {
      append("[js=")
      append(Instant.ofEpochMilli(payload.jsTimeEpochMs))
      append(']')
    }
    val body = payload.args.joinToString(" ").limitLength(MAX_LOG_MESSAGE_CHARS - prefix.length - 1)
    return if (body.isEmpty()) prefix else "$prefix $body"
  }

  internal fun loggerCategory(): String {
    val viewPart = viewId?.trim()?.takeIf { it.isNotEmpty() }?.let(::sanitizeCategoryPart)
    return if (viewPart == null) consoleLogCategory else "$consoleLogCategory.$viewPart"
  }

  private fun logger(): Logger {
    return Logger.getInstance(loggerCategory())
  }

  private fun String.limitLength(maxChars: Int): String {
    if (maxChars <= 0) return ""
    if (length <= maxChars) return this
    if (maxChars <= TRUNCATION_MARKER.length) return take(maxChars)
    return take(maxChars - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
  }

  private fun sanitizeCategoryPart(value: String): String {
    return buildString(value.length) {
      for (ch in value) {
        append(if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_')
      }
    }
  }

  companion object {
    private const val MAX_LOG_MESSAGE_CHARS = 16_384
    private const val TRUNCATION_MARKER = "…"

    val DOCUMENT_START_SCRIPT: WebViewScript = WebViewScript("""
      (function() {
        const marker = "__WVI_CONSOLE_CAPTURE_INSTALLED__";
        if (window[marker]) {
          return;
        }
        try {
          Object.defineProperty(window, marker, { value: true });
        }
        catch (_) {
          window[marker] = true;
        }

        const consoleObject = window.console;
        if (!consoleObject) {
          return;
        }

        const jsonStringify = JSON.stringify.bind(JSON);
        const dateNow = Date.now ? Date.now.bind(Date) : function() { return new Date().getTime(); };
        const objectToString = Object.prototype.toString;
        const arrayIsArray = Array.isArray;
        const maxArgs = 32;
        const maxArgChars = 2048;
        const methods = [
          "log", "debug", "info", "warn", "warning", "error", "trace", "assert",
          "dir", "table", "count", "time", "timeEnd", "group", "groupCollapsed",
          "groupEnd", "profile", "profileEnd", "clear"
        ];

        function limit(value) {
          if (value.length <= maxArgChars) {
            return value;
          }
          return value.slice(0, maxArgChars - 1) + "…";
        }

        function preview(value) {
          try {
            if (value === null) {
              return "null";
            }
            const type = typeof value;
            if (type === "string") {
              return limit(value);
            }
            if (type === "undefined" || type === "number" || type === "boolean" || type === "bigint" || type === "symbol") {
              return String(value);
            }
            if (type === "function") {
              return "[function " + (value.name || "anonymous") + "]";
            }
            if (typeof Error !== "undefined" && value instanceof Error) {
              return limit(value.stack || (value.name + ": " + value.message));
            }
            if (arrayIsArray(value)) {
              return "[Array(" + value.length + ")]";
            }
            const constructorName = value && value.constructor && value.constructor.name;
            if (constructorName && constructorName !== "Object") {
              return "[" + constructorName + "]";
            }
            return objectToString.call(value);
          }
          catch (_) {
            return "[unserializable]";
          }
        }

        function sendToHost(frame) {
          try {
            const raw = jsonStringify(frame);
            if (window.chrome && window.chrome.webview && typeof window.chrome.webview.postMessage === "function") {
              window.chrome.webview.postMessage(raw);
              return;
            }
            const webkitHandlers = window.webkit && window.webkit.messageHandlers;
            const webkitHandler = webkitHandlers && webkitHandlers.webviewIpc;
            if (webkitHandler && typeof webkitHandler.postMessage === "function") {
              webkitHandler.postMessage(raw);
              return;
            }
            if (typeof window.__wviJcefQuery === "function") {
              window.__wviJcefQuery({ request: raw });
            }
          }
          catch (_) {
          }
        }

        function report(method, args, jsTimeEpochMs) {
          sendToHost({
            jsonrpc: "2.0",
            method: "${ConsoleNotification.METHOD}",
            params: {
              method: method,
              jsTimeEpochMs: jsTimeEpochMs,
              args: Array.prototype.slice.call(args, 0, maxArgs).map(preview)
            }
          });
        }

        methods.forEach(function(method) {
          const original = consoleObject[method];
          if (typeof original !== "function") {
            return;
          }
          const wrapped = function() {
            const jsTimeEpochMs = dateNow();
            try {
              original.apply(consoleObject, arguments);
            }
            finally {
              if (method === "assert" && arguments[0]) {
                return;
              }
              report(method, arguments, jsTimeEpochMs);
            }
          };
          try {
            Object.defineProperty(consoleObject, method, {
              configurable: true,
              writable: true,
              value: wrapped
            });
          }
          catch (_) {
            try {
              consoleObject[method] = wrapped;
            }
            catch (__) {
            }
          }
        });
      })();
    """.trimIndent())
  }
}

internal const val CONSOLE_LOG_CATEGORY: String = "#com.intellij.ui.webview.console"
internal const val WEBVIEW_CONSOLE_NOTIFICATION_METHOD: String = "$/webview/console"

@Serializable
internal data class WebViewConsolePayload(
  val method: String,
  val jsTimeEpochMs: Long,
  val args: List<String> = emptyList(),
)

private object ConsoleNotification : WebViewNotification<WebViewConsolePayload> {
  const val METHOD: String = WEBVIEW_CONSOLE_NOTIFICATION_METHOD
  override val method: String = METHOD
  override val paramsSerializer: KSerializer<WebViewConsolePayload> = WebViewConsolePayload.serializer()
}
