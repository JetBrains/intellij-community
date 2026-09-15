package com.intellij.markdown.jcef.preview.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandler
import java.util.EnumSet
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

private val logger = fileLogger()

/** How many times [waitForLoad] starts a load that failed for a transient reason. */
private const val RETRY_LIMITS = 3

private val LOAD_RETRY_DELAY = 500.milliseconds

/**
 * The error codes that the environment produces, and not the loaded page.
 *
 * The browser reports [CefLoadHandler.ErrorCode.ERR_NETWORK_CHANGED] when the operating system changes its network
 * configuration during a load, and [CefLoadHandler.ErrorCode.ERR_ABORTED] when something supersedes the load. The same
 * URL loads after the cause is gone.
 */
private val retryableErrorCodes: Set<CefLoadHandler.ErrorCode> = EnumSet.of(
  CefLoadHandler.ErrorCode.ERR_NETWORK_CHANGED,
  CefLoadHandler.ErrorCode.ERR_ABORTED,
)

internal fun JBCefClient.addRequestHandler(handler: CefRequestHandler, browser: CefBrowser, parentDisposable: Disposable) {
  Disposer.register(parentDisposable) { removeRequestHandler(handler, browser) }
  addRequestHandler(handler, browser)
}

internal fun JBCefClient.addLoadHandler(handler: CefLoadHandler, browser: CefBrowser, parentDisposable: Disposable) {
  Disposer.register(parentDisposable) { removeLoadHandler(handler, browser) }
  addLoadHandler(handler, browser)
}

internal class LoadErrorException(
  val code: CefLoadHandler.ErrorCode,
  text: String,
  url: String,
) : IllegalStateException("Failed to load $url:\n$code: $text") {
  /** `true` when the environment stopped the load, so a new load of the same URL can succeed. */
  val isRetryable: Boolean
    get() = code in retryableErrorCodes
}

/**
 * Runs [content] and suspends until the browser ends the load that [content] starts.
 *
 * A transient failure (see [LoadErrorException.isRetryable]) runs [content] again, up to [RETRY_LIMITS] times.
 * Every other failure, and the failure of the last attempt, throws [LoadErrorException].
 */
internal suspend fun JBCefBrowser.waitForLoad(content: JBCefBrowser.(attempt: Int) -> Unit) {
  for (attempt in 1..RETRY_LIMITS) {
    try {
      awaitLoad { content(attempt) }
      return
    }
    catch (e: LoadErrorException) {
      if (!e.isRetryable || attempt == RETRY_LIMITS) {
        throw e
      }
      logger.warn("Attempt $attempt of $RETRY_LIMITS failed. The browser loads the page again.", e)
    }
    delay(LOAD_RETRY_DELAY)
  }
}

private suspend fun JBCefBrowser.awaitLoad(content: JBCefBrowser.() -> Unit) {
  var handlerReference: CefLoadHandler? = null
  try {
    suspendCancellableCoroutine { continuation ->
      val handler = object : CefLoadHandlerAdapter() {
        @Volatile
        private var handlerWasCalled = false

        override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
          if (handlerWasCalled) {
            return
          }
          handlerWasCalled = true
          continuation.resume(Unit)
        }

        override fun onLoadError(
          browser: CefBrowser,
          frame: CefFrame,
          errorCode: CefLoadHandler.ErrorCode,
          errorText: String,
          failedUrl: String,
        ) {
          if (handlerWasCalled) {
            return
          }
          handlerWasCalled = true
          continuation.resumeWithException(LoadErrorException(errorCode, errorText, failedUrl))
        }
      }
      handlerReference = handler
      jbCefClient.addLoadHandler(handler, cefBrowser)
      content.invoke(this)
    }
  } finally {
    handlerReference?.let { jbCefClient.removeLoadHandler(it, cefBrowser) }
  }
}

/**
 * Loads [url] and suspends until the browser ends the load.
 *
 * The first attempt calls [JBCefBrowser.loadURL], because that method defers the load until the native browser exists,
 * and it also skips a request for the URL that is loaded last, so a later attempt calls [CefBrowser.loadURL] instead.
 * That call starts a load every time, and the native browser exists at that point
 * because otherwise we wouldn't have gotten a retryable exception in the previous attempts.
 */
internal suspend fun JBCefBrowser.waitForPageLoad(url: String) {
  waitForLoad { attempt ->
    if (attempt == 1) loadURL(url) else cefBrowser.loadURL(url)
  }
}

/**
 * Reloads the current page bypassing the browser cache. Unlike re-navigating to the same URL (which may hit a stale
 * `304` or a back-forward-cache restore), this re-fetches every resource, refreshing settings-dependent resources
 * served from stable URLs (e.g. the mermaid theme definition).
 */
internal suspend fun JBCefBrowser.waitForReloadIgnoringCache() {
  waitForLoad {
    cefBrowser.reloadIgnoreCache()
  }
}

internal fun queryHandler(handler: (String?) -> Unit): (String?) -> JBCefJSQuery.Response? {
  return {
    handler.invoke(it)
    JBCefJSQuery.Response("")
  }
}
