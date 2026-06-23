// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.mac

import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.Foundation.NSRect
import com.intellij.ui.mac.foundation.Foundation.addMethod
import com.intellij.ui.mac.foundation.Foundation.addProtocol
import com.intellij.ui.mac.foundation.Foundation.allocateObjcClassPair
import com.intellij.ui.mac.foundation.Foundation.createSelector
import com.intellij.ui.mac.foundation.Foundation.getObjcClass
import com.intellij.ui.mac.foundation.Foundation.getProtocol
import com.intellij.ui.mac.foundation.Foundation.invoke
import com.intellij.ui.mac.foundation.Foundation.isNil
import com.intellij.ui.mac.foundation.Foundation.nsString
import com.intellij.ui.mac.foundation.Foundation.registerObjcClassPair
import com.intellij.ui.mac.foundation.Foundation.toStringViaUTF8
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.webview.impl.WebViewEditCommand
import com.intellij.ui.webview.impl.WebViewAssetResponse
import com.intellij.ui.webview.impl.WebViewApplicationModeScripts
import com.intellij.ui.webview.impl.WebViewLogger
import com.intellij.ui.webview.impl.WEBVIEW_ASSET_CUSTOM_SCHEME
import com.sun.jna.Callback
import com.sun.jna.Memory
import com.sun.jna.Pointer
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

/**
 * Low-level JNA bridge to macOS `WKWebView` via the existing [Foundation] ObjC runtime.
 *
 * All methods in this object **must** be called on the macOS main thread.
 * The caller (typically [MacWebViewEngine]) is responsible for dispatching via
 * [com.intellij.ui.webview.impl.MacMainThreadDispatcher].
 *
 * Uses `Foundation.invoke()` for all Objective-C message sends — no separate native library.
 */
@ApiStatus.Internal
@Suppress("JSUnresolvedVariable")
internal object WKWebViewBridge {

  // region ObjC class names
  private const val CLS_WKWEBVIEW = "WKWebView"
  private const val CLS_WKWEBVIEW_CONFIGURATION = "WKWebViewConfiguration"
  private const val CLS_NSURL = "NSURL"
  private const val CLS_NSURLREQUEST = "NSURLRequest"
  private const val CLS_NSHTTPURL_RESPONSE = "NSHTTPURLResponse"
  private const val CLS_NSDATA = "NSData"
  private const val CLS_NSMUTABLE_DICTIONARY = "NSMutableDictionary"
  private const val CLS_NSOBJECT = "NSObject"
  private const val CLS_NSAPPLICATION = "NSApplication"
  private const val CLS_WKUSER_SCRIPT = "WKUserScript"
  // endregion

  // region ObjC selectors (centralized, no scattered magic strings)
  private val SEL_ALLOC = createSelector("alloc")
  private val SEL_INIT = createSelector("init")
  private val SEL_RELEASE = createSelector("release")

  // WKWebViewConfiguration
  private val SEL_PREFERENCES = createSelector("preferences")
  private val SEL_USER_CONTENT_CONTROLLER = createSelector("userContentController")
  private val SEL_SET_URL_SCHEME_HANDLER_FOR_URL_SCHEME = createSelector("setURLSchemeHandler:forURLScheme:")

  // WKPreferences
  private val SEL_SET_JAVA_SCRIPT_ENABLED = createSelector("setJavaScriptEnabled:")
  private val SEL_SET_JAVA_SCRIPT_CAN_OPEN_WINDOWS_AUTOMATICALLY = createSelector("setJavaScriptCanOpenWindowsAutomatically:")

  // WKWebView
  private val SEL_INIT_WITH_FRAME_CONFIGURATION = createSelector("initWithFrame:configuration:")
  private val SEL_LOAD_REQUEST = createSelector("loadRequest:")
  private val SEL_LOAD_HTML_STRING_BASE_URL = createSelector("loadHTMLString:baseURL:")
  private val SEL_EVALUATE_JAVASCRIPT = createSelector("evaluateJavaScript:completionHandler:")
  private val SEL_WINDOW = createSelector("window")
  private val SEL_SET_FRAME = createSelector("setFrame:")
  private val SEL_SET_HIDDEN = createSelector("setHidden:")
  private val SEL_SET_AUTORESIZING_MASK = createSelector("setAutoresizingMask:")
  private val SEL_SET_ALLOWS_BACK_FORWARD_NAVIGATION_GESTURES = createSelector("setAllowsBackForwardNavigationGestures:")
  private val SEL_SET_ALLOWS_MAGNIFICATION = createSelector("setAllowsMagnification:")
  private val SEL_SET_PAGE_ZOOM = createSelector("setPageZoom:")
  private val SEL_SET_INSPECTABLE = createSelector("setInspectable:")
  private val SEL_SET_CAN_USE_CREDENTIAL_STORAGE = createSelector("_setCanUseCredentialStorage:")
  private val SEL_SET_RUBBER_BANDING_ENABLED = createSelector("_setRubberBandingEnabled:")
  private val SEL_REMOVE_FROM_SUPERVIEW = createSelector("removeFromSuperview")
  private val SEL_COPY = createSelector("copy:")
  private val SEL_PASTE = createSelector("paste:")
  private val SEL_CUT = createSelector("cut:")
  private val SEL_SELECT_ALL = createSelector("selectAll:")
  private val SEL_UNDO = createSelector("undo:")
  private val SEL_REDO = createSelector("redo:")

  // NSWindow
  private val SEL_FIRST_RESPONDER = createSelector("firstResponder")
  private val SEL_MAKE_FIRST_RESPONDER = createSelector("makeFirstResponder:")

  // NSApplication
  private val SEL_SHARED_APPLICATION = createSelector("sharedApplication")
  private val SEL_SEND_ACTION_TO_FROM = createSelector("sendAction:to:from:")

  // NSView
  private val SEL_ADD_SUBVIEW = createSelector("addSubview:")
  private val SEL_IS_DESCENDANT_OF = createSelector("isDescendantOf:")

  // NSObject
  private val SEL_RESPONDS_TO_SELECTOR = createSelector("respondsToSelector:")
  private val SEL_DESCRIPTION = createSelector("description")

  // NSURL / NSURLRequest
  private val SEL_URL_WITH_STRING = createSelector("URLWithString:")
  private val SEL_REQUEST_WITH_URL = createSelector("requestWithURL:")
  private val SEL_REQUEST = createSelector("request")
  private val SEL_URL = createSelector("URL")
  private val SEL_ABSOLUTE_STRING = createSelector("absoluteString")

  // NSURLResponse / NSData / WKURLSchemeTask
  private val SEL_INIT_WITH_URL_STATUS_CODE_HTTP_VERSION_HEADER_FIELDS = createSelector("initWithURL:statusCode:HTTPVersion:headerFields:")
  private val SEL_DATA_WITH_BYTES_LENGTH = createSelector("dataWithBytes:length:")
  private val SEL_DICTIONARY = createSelector("dictionary")
  private val SEL_SET_OBJECT_FOR_KEY = createSelector("setObject:forKey:")
  private val SEL_DID_RECEIVE_RESPONSE = createSelector("didReceiveResponse:")
  private val SEL_DID_RECEIVE_DATA = createSelector("didReceiveData:")
  private val SEL_DID_FINISH = createSelector("didFinish")

  // WKUserContentController
  private val SEL_ADD_USER_SCRIPT = createSelector("addUserScript:")
  private val SEL_ADD_SCRIPT_MESSAGE_HANDLER = createSelector("addScriptMessageHandler:name:")
  private val SEL_REMOVE_SCRIPT_MESSAGE_HANDLER = createSelector("removeScriptMessageHandlerForName:")

  // WKUserScript
  private val SEL_INIT_WITH_SOURCE_INJECTION_TIME_FOR_MAIN_FRAME_ONLY = createSelector("initWithSource:injectionTime:forMainFrameOnly:")

  // WKScriptMessage
  private val SEL_BODY = createSelector("body")
  // endregion

  /** Name used for the JS→JVM postMessage channel. JS calls: `window.webkit.messageHandlers.webviewIpc.postMessage(...)` */
  const val IPC_HANDLER_NAME = "webviewIpc"

  private const val WK_RECT_EDGE_NONE = 0L
  private const val WK_USER_SCRIPT_INJECTION_TIME_AT_DOCUMENT_START = 0L

  /**
   * Registered ObjC class acting as WKScriptMessageHandler. Created once, reused across instances.
   * The class name must be unique to avoid collisions with other ObjC runtime registrations.
   */
  private var messageHandlerClass: ID = ID.NIL

  /**
   * Callback reference kept alive to prevent GC while native code holds a function pointer.
   */
  @Suppress("unused") // prevent GC
  private var messageHandlerCallback: Callback? = null

  private var urlSchemeHandlerClass: ID = ID.NIL

  @Suppress("unused") // prevent GC
  private var urlSchemeStartCallback: Callback? = null

  @Suppress("unused") // prevent GC
  private var urlSchemeStopCallback: Callback? = null

  /**
   * Per-webview callback registry. Key = the ObjC `self` pointer of the handler instance.
   * Value = callback invoked with the message body string.
   */
  private val messageHandlerCallbacks = java.util.concurrent.ConcurrentHashMap<Long, (String) -> Unit>()

  private val urlSchemeHandlerCallbacks = java.util.concurrent.ConcurrentHashMap<Long, (String) -> WebViewAssetResponse?>()

  /**
   * Creates and configures a new `WKWebView` instance.
   *
   * @param onMessage callback invoked on the main thread when JS calls `postMessage`
   * @param resolveAssetUrl callback invoked by the private URL scheme handler.
   * @return handles that must be passed to [release].
   */
  fun createWKWebView(
    onMessage: (String) -> Unit,
    resolveAssetUrl: (String) -> WebViewAssetResponse?,
  ): WebViewHandles {
    // 1. Create WKWebViewConfiguration
    val configuration = invoke(invoke(getObjcClass(CLS_WKWEBVIEW_CONFIGURATION), SEL_ALLOC), SEL_INIT)

    // 2. Configure preferences
    val preferences = invoke(configuration, SEL_PREFERENCES)
    invoke(preferences, SEL_SET_JAVA_SCRIPT_ENABLED, true)
    invoke(preferences, SEL_SET_JAVA_SCRIPT_CAN_OPEN_WINDOWS_AUTOMATICALLY, false)

    // 3. Set up user content controller with message handler
    val userContentController = invoke(configuration, SEL_USER_CONTENT_CONTROLLER)
    installApplicationModeUserScript(userContentController)
    val handlerInstance = createAndRegisterMessageHandler(onMessage)
    invoke(userContentController, SEL_ADD_SCRIPT_MESSAGE_HANDLER, handlerInstance, nsString(IPC_HANDLER_NAME))

    val urlSchemeHandlerInstance = createAndRegisterUrlSchemeHandler(resolveAssetUrl)
    invoke(configuration, SEL_SET_URL_SCHEME_HANDLER_FOR_URL_SCHEME, urlSchemeHandlerInstance, nsString(WEBVIEW_ASSET_CUSTOM_SCHEME))

    // 4. Allocate WKWebView with zero frame (will be set when attached)
    val webView = invoke(getObjcClass(CLS_WKWEBVIEW), SEL_ALLOC)
    val initializedWebView = invoke(webView, SEL_INIT_WITH_FRAME_CONFIGURATION,
                                    NSRect(0.0, 0.0, 0.0, 0.0), configuration)
    configureWebViewApplicationMode(initializedWebView)

    // 5. Keep Swing host geometry as the only frame source. The WebView is attached
    // to the window content view, so AppKit autoresizing would follow the whole window.
    invoke(initializedWebView, SEL_SET_AUTORESIZING_MASK, 0)

    // 6. Release configuration (webview retains it)
    invoke(configuration, SEL_RELEASE)

    return WebViewHandles(
      webView = initializedWebView,
      messageHandler = handlerInstance,
      urlSchemeHandler = urlSchemeHandlerInstance,
    )
  }

  fun attachToParent(webView: ID, parentNSView: ID) {
    invoke(parentNSView, SEL_ADD_SUBVIEW, webView)
  }

  fun detachFromParent(webView: ID) {
    invoke(webView, SEL_REMOVE_FROM_SUPERVIEW)
  }

  fun loadUrl(webView: ID, url: String) {
    val nsUrl = invoke(getObjcClass(CLS_NSURL), SEL_URL_WITH_STRING, nsString(url))
    val request = invoke(getObjcClass(CLS_NSURLREQUEST), SEL_REQUEST_WITH_URL, nsUrl)
    invoke(webView, SEL_LOAD_REQUEST, request)
  }

  fun loadHtml(webView: ID, html: String, baseUrl: String?) {
    val nsBaseUrl = if (baseUrl != null) {
      invoke(getObjcClass(CLS_NSURL), SEL_URL_WITH_STRING, nsString(baseUrl))
    }
    else {
      ID.NIL
    }
    invoke(webView, SEL_LOAD_HTML_STRING_BASE_URL, nsString(html), nsBaseUrl)
  }

  /**
   * Evaluates JavaScript in the WebView and reports the result through the message handler channel.
   *
   * The message payload format is one of:
   * - `__eval__:<evalId>:<value>`
   * - `__eval_err__:<evalId>:<error>`
   *
   * [evalId] is provided by the engine and scoped per WebView instance.
   */
  fun evaluateJavaScript(webView: ID, script: String, evalId: Long) {
    @Language("JavaScript")
    val taggedScript = """
      (function() {
        try {
          const __result = eval(${escapeJsString(script)});
          window.webkit.messageHandlers[${escapeJsString(IPC_HANDLER_NAME)}].postMessage('__eval__:$evalId:' + String(__result));
        } catch(e) {
          window.webkit.messageHandlers[${escapeJsString(IPC_HANDLER_NAME)}].postMessage('__eval_err__:$evalId:' + e.message);
        }
      })();
    """.trimIndent()

    executeJavaScript(webView, taggedScript)
  }

  /**
   * Executes JavaScript without routing result/error back into the bridge channel.
   */
  fun executeJavaScript(webView: ID, script: String) {
    invoke(webView, SEL_EVALUATE_JAVASCRIPT, nsString(script), ID.NIL)
  }

  /**
   * Transfers raw JSON-RPC frame into the JS runtime ingress.
   */
  fun transferToJs(webView: ID, rawJson: String) {
    @Language("JavaScript")
    val script = "window.__WVI__ && window.__WVI__.__deliver(${escapeJsString(rawJson)});"
    executeJavaScript(webView, script)
  }

  fun setFrame(webView: ID, x: Double, y: Double, w: Double, h: Double) {
    invoke(webView, SEL_SET_FRAME, NSRect(x, y, w, h))
  }

  fun setHidden(webView: ID, hidden: Boolean) {
    invoke(webView, SEL_SET_HIDDEN, hidden)
  }

  fun requestFocus(webView: ID) {
    makeFirstResponder(webView)
  }

  fun makeFirstResponder(view: ID) {
    val window = invoke(view, SEL_WINDOW)
    if (!isNil(window)) {
      invoke(window, SEL_MAKE_FIRST_RESPONDER, view)
    }
  }

  fun clearFocus(webView: ID) {
    val window = invoke(webView, SEL_WINDOW)
    if (!isNil(window)) {
      invoke(window, SEL_MAKE_FIRST_RESPONDER, ID.NIL)
    }
  }

  /**
   * Dispatches an AppKit edit action through `NSApplication.sendAction(_:to:from:)`.
   *
   * `to = nil` preserves normal responder-chain routing, including WebKit's private editor
   * responders. The first-responder containment check prevents a command from leaking to another
   * native control in the same window when Swing focus state is stale.
   */
  fun performEditCommand(webView: ID, command: WebViewEditCommand): Boolean {
    val selector = editCommandSelector(command) ?: return false
    if (!firstResponderIsInsideWebView(webView)) return false
    val application = invoke(getObjcClass(CLS_NSAPPLICATION), SEL_SHARED_APPLICATION)
    return invoke(application, SEL_SEND_ACTION_TO_FROM, selector, ID.NIL, ID.NIL).booleanValue()
  }

  fun firstResponderState(webView: ID): MacWebViewFirstResponderState {
    val window = invoke(webView, SEL_WINDOW)
    if (isNil(window)) {
      return MacWebViewFirstResponderState(hasResponder = false, isInsideWebView = false, responderDescription = "<no window>")
    }

    val firstResponder = invoke(window, SEL_FIRST_RESPONDER)
    if (isNil(firstResponder)) {
      return MacWebViewFirstResponderState(hasResponder = false, isInsideWebView = false, responderDescription = "<nil>")
    }

    val isWebViewResponder = firstResponder == webView
    val isDescendantOfWebView = isDescendantOfWebView(firstResponder, webView)
    return MacWebViewFirstResponderState(
      hasResponder = true,
      isInsideWebView = isWebViewResponder || isDescendantOfWebView,
      responderDescription = describeResponder(firstResponder),
    )
  }

  /**
   * Releases the native WKWebView and its associated message handler.
   * Must be called on the macOS main thread.
   */
  fun release(handles: WebViewHandles) {
    // 1. Remove the message handler from user content controller to break retain cycle
    val configuration = invoke(handles.webView, "configuration")
    val ucc = invoke(configuration, SEL_USER_CONTENT_CONTROLLER)
    invoke(ucc, SEL_REMOVE_SCRIPT_MESSAGE_HANDLER, nsString(IPC_HANDLER_NAME))

    // 2. Detach from superview
    invoke(handles.webView, SEL_REMOVE_FROM_SUPERVIEW)

    // 3. Unregister message callback
    messageHandlerCallbacks.remove(handles.messageHandler.toLong())
    urlSchemeHandlerCallbacks.remove(handles.urlSchemeHandler.toLong())

    // 4. Release native objects
    invoke(handles.messageHandler, SEL_RELEASE)
    invoke(handles.urlSchemeHandler, SEL_RELEASE)
    invoke(handles.webView, SEL_RELEASE)
  }

  // region Message handler class registration

  private fun installApplicationModeUserScript(userContentController: ID) {
    val userScript = invoke(
      invoke(getObjcClass(CLS_WKUSER_SCRIPT), SEL_ALLOC),
      SEL_INIT_WITH_SOURCE_INJECTION_TIME_FOR_MAIN_FRAME_ONLY,
      nsString(WebViewApplicationModeScripts.DOM_HARDENING_SCRIPT),
      WK_USER_SCRIPT_INJECTION_TIME_AT_DOCUMENT_START,
      false,
    )
    invoke(userContentController, SEL_ADD_USER_SCRIPT, userScript)
    invoke(userScript, SEL_RELEASE)
  }

  private fun configureWebViewApplicationMode(webView: ID) {
    invoke(webView, SEL_SET_ALLOWS_BACK_FORWARD_NAVIGATION_GESTURES, false)
    invoke(webView, SEL_SET_ALLOWS_MAGNIFICATION, false)
    invokeIfResponds(webView, SEL_SET_PAGE_ZOOM, 1.0, "setPageZoom:")
    invokeIfResponds(webView, SEL_SET_INSPECTABLE, false, "setInspectable:")
    invokeIfResponds(webView, SEL_SET_CAN_USE_CREDENTIAL_STORAGE, false, "_setCanUseCredentialStorage:")
    invokeIfResponds(webView, SEL_SET_RUBBER_BANDING_ENABLED, WK_RECT_EDGE_NONE, "_setRubberBandingEnabled:")
  }

  private fun invokeIfResponds(target: ID, selector: Pointer, value: Any, settingName: String) {
    if (!respondsTo(target, selector)) return
    try {
      invoke(target, selector, value)
    }
    catch (t: Throwable) {
      WebViewLogger.LOG.debug("Failed to apply WKWebView application-mode setting $settingName", t)
    }
  }

  private fun respondsTo(target: ID, selector: Pointer): Boolean {
    return !isNil(target) && invoke(target, SEL_RESPONDS_TO_SELECTOR, selector).booleanValue()
  }

  private fun createAndRegisterMessageHandler(onMessage: (String) -> Unit): ID {
    ensureMessageHandlerClassRegistered()

    val instance = invoke(invoke(messageHandlerClass, SEL_ALLOC), SEL_INIT)
    messageHandlerCallbacks[instance.toLong()] = onMessage
    return instance
  }

  @Synchronized
  private fun ensureMessageHandlerClassRegistered() {
    if (!ID.NIL.equals(messageHandlerClass)) return

    val superclass = getObjcClass(CLS_NSOBJECT)
    val cls = allocateObjcClassPair(superclass, "IdeaWKMessageHandler")

    val protocol = getProtocol("WKScriptMessageHandler")
    if (!isNil(protocol)) {
      addProtocol(cls, protocol)
    }

    // Implement userContentController:didReceiveScriptMessage:
    // Type encoding: v@:@@ (void, self, _cmd, WKUserContentController, WKScriptMessage)
    val callback = object : Callback {
      @Suppress("unused", "UNUSED_PARAMETER") // called from native
      fun callback(self: ID, selector: String, controller: ID, message: ID) {
        val body = invoke(message, SEL_BODY)
        val bodyString = toStringViaUTF8(body)
        if (bodyString != null) {
          val handler = messageHandlerCallbacks[self.toLong()]
          handler?.invoke(bodyString)
        }
      }
    }
    messageHandlerCallback = callback // prevent GC

    addMethod(cls, createSelector("userContentController:didReceiveScriptMessage:"), callback, "v@:@@")

    registerObjcClassPair(cls)
    messageHandlerClass = cls
  }

  // endregion

  // region URL scheme handler class registration

  private fun createAndRegisterUrlSchemeHandler(resolve: (String) -> WebViewAssetResponse?): ID {
    ensureUrlSchemeHandlerClassRegistered()

    val instance = invoke(invoke(urlSchemeHandlerClass, SEL_ALLOC), SEL_INIT)
    urlSchemeHandlerCallbacks[instance.toLong()] = resolve
    return instance
  }

  @Synchronized
  private fun ensureUrlSchemeHandlerClassRegistered() {
    if (!ID.NIL.equals(urlSchemeHandlerClass)) return

    val superclass = getObjcClass(CLS_NSOBJECT)
    val cls = allocateObjcClassPair(superclass, "IdeaWKUrlSchemeHandler")

    val protocol = getProtocol("WKURLSchemeHandler")
    if (!isNil(protocol)) {
      addProtocol(cls, protocol)
    }

    val startCallback = object : Callback {
      @Suppress("unused", "UNUSED_PARAMETER") // called from native
      fun callback(self: ID, selector: String, webView: ID, task: ID) {
        val response = urlFromSchemeTask(task)?.let { url -> urlSchemeHandlerCallbacks[self.toLong()]?.invoke(url) }
                       ?: WebViewAssetResponse.notFound("WebView asset URL not found")
        sendSchemeTaskResponse(task, response)
      }
    }
    val stopCallback = object : Callback {
      @Suppress("unused", "UNUSED_PARAMETER") // called from native
      fun callback(self: ID, selector: String, webView: ID, task: ID) {
      }
    }
    urlSchemeStartCallback = startCallback
    urlSchemeStopCallback = stopCallback

    addMethod(cls, createSelector("webView:startURLSchemeTask:"), startCallback, "v@:@@")
    addMethod(cls, createSelector("webView:stopURLSchemeTask:"), stopCallback, "v@:@@")

    registerObjcClassPair(cls)
    urlSchemeHandlerClass = cls
  }

  private fun urlFromSchemeTask(task: ID): String? {
    val request = invoke(task, SEL_REQUEST)
    if (isNil(request)) return null
    val url = invoke(request, SEL_URL)
    if (isNil(url)) return null
    return toStringViaUTF8(invoke(url, SEL_ABSOLUTE_STRING))
  }

  private fun sendSchemeTaskResponse(task: ID, responseData: WebViewAssetResponse) {
    val request = invoke(task, SEL_REQUEST)
    val url = invoke(request, SEL_URL)
    val response = invoke(
      invoke(getObjcClass(CLS_NSHTTPURL_RESPONSE), SEL_ALLOC),
      SEL_INIT_WITH_URL_STATUS_CODE_HTTP_VERSION_HEADER_FIELDS,
      url,
      responseData.statusCode.toLong(),
      nsString("HTTP/1.1"),
      responseHeaders(responseData),
    )
    val data = dataWithBytes(responseData.bytes)
    invoke(task, SEL_DID_RECEIVE_RESPONSE, response)
    invoke(task, SEL_DID_RECEIVE_DATA, data)
    invoke(task, SEL_DID_FINISH)
    invoke(response, SEL_RELEASE)
  }

  private fun dataWithBytes(bytes: ByteArray): ID {
    if (bytes.isEmpty()) {
      return invoke(getObjcClass(CLS_NSDATA), SEL_DATA_WITH_BYTES_LENGTH, ID.NIL, 0L)
    }
    val memory = Memory(bytes.size.toLong())
    memory.write(0, bytes, 0, bytes.size)
    return invoke(getObjcClass(CLS_NSDATA), SEL_DATA_WITH_BYTES_LENGTH, memory, bytes.size.toLong())
  }

  private fun responseHeaders(responseData: WebViewAssetResponse): ID {
    val headers = invoke(getObjcClass(CLS_NSMUTABLE_DICTIONARY), SEL_DICTIONARY)
    invoke(headers, SEL_SET_OBJECT_FOR_KEY, nsString(responseData.contentType), nsString("Content-Type"))
    for ((name, value) in responseData.headers) {
      invoke(headers, SEL_SET_OBJECT_FOR_KEY, nsString(value), nsString(name))
    }
    return headers
  }

  // endregion

  // region Utilities

  private fun escapeJsString(s: String): String {
    val escaped = s.replace("\\", "\\\\")
      .replace("'", "\\'")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    return "'$escaped'"
  }

  private fun describeResponder(responder: ID): String {
    return toStringViaUTF8(invoke(responder, SEL_DESCRIPTION)) ?: "<unknown>"
  }

  private fun editCommandSelector(command: WebViewEditCommand): Pointer? {
    return when (command) {
      WebViewEditCommand.COPY -> SEL_COPY
      WebViewEditCommand.PASTE -> SEL_PASTE
      WebViewEditCommand.CUT -> SEL_CUT
      WebViewEditCommand.SELECT_ALL -> SEL_SELECT_ALL
      WebViewEditCommand.UNDO -> SEL_UNDO
      WebViewEditCommand.REDO -> SEL_REDO
      else -> null
    }
  }

  /**
   * Requires AppKit's current first responder to be this WebView or one of its private subviews
   * before we send a responder-chain edit action on behalf of the Swing host.
   */
  private fun firstResponderIsInsideWebView(webView: ID): Boolean {
    val window = invoke(webView, SEL_WINDOW)
    if (isNil(window)) return false

    val firstResponder = invoke(window, SEL_FIRST_RESPONDER)
    return !isNil(firstResponder) && isInsideWebView(firstResponder, webView)
  }

  private fun isInsideWebView(responder: ID, webView: ID): Boolean {
    return responder == webView || isDescendantOfWebView(responder, webView)
  }

  private fun isDescendantOfWebView(responder: ID, webView: ID): Boolean {
    return responder != webView &&
           invoke(responder, SEL_RESPONDS_TO_SELECTOR, SEL_IS_DESCENDANT_OF).booleanValue() &&
           invoke(responder, SEL_IS_DESCENDANT_OF, webView).booleanValue()
  }

  // endregion

  data class WebViewHandles(
    val webView: ID,
    val messageHandler: ID,
    val urlSchemeHandler: ID,
  )
}
