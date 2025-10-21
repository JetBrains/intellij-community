package org.intellij.plugins.markdown.ui.preview.jcef.impl

import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.suspendCancellableCoroutine
import org.intellij.lang.annotations.Language
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [code] is expected to be **valid** js expression that returns an instance of Promise.
 * Check [wrapWithErrorHandling] to see how this expression will be used.
 * Also, consider wrapping your expression in a `(function() { ... })();`.
 */
@Throws(JsCallExecutionException::class)
suspend fun JBCefBrowser.executeCancellableJavaScript(@Language("JavaScript") code: String): String? {
  return executeCancellableJsCall(browser = this, code)
}

@Throws(JsCallExecutionException::class)
internal suspend fun executeCancellableJsCall(browser: JBCefBrowser, @Language("JavaScript") code: String): String? {
  // Will be disposed on either normal finish, cancellation or related browser disposal
  val disposable = Disposer.newCheckedDisposable()
  Disposer.register(browser, disposable)
  if (disposable.isDisposed) {
    throw AlreadyDisposedException("The related browser is already disposed")
  }
  try {
    return suspendCancellableCoroutine { continuation ->
      Disposer.register(disposable) {
        // Ensures that the current coroutine was not resumed before, which means that there were no any results
        // yielded from our js call, and we are still waiting for it
        if (!continuation.isCompleted) {
          continuation.resumeWithException(AlreadyDisposedException("The related browser was disposed during the call"))
        }
      }
      val resultQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
      // Handlers should not throw because it won't be possible to catch an exception
      val resultQueryHandler = queryHandler { result ->
        if (!continuation.isCompleted) {
          continuation.resume(result)
        }
      }
      resultQuery.addHandler(resultQueryHandler)
      // Remove handler first and only then dispose query instance
      Disposer.register(disposable) {
        resultQuery.removeHandler(resultQueryHandler)
      }
      Disposer.register(disposable, resultQuery)
      val errorQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
      val errorQueryHandler = queryHandler { error ->
        if (!continuation.isCompleted) {
          val message = error ?: "Unknown error"
          continuation.resumeWithException(JsCallExecutionException(message))
        }
      }
      errorQuery.addHandler(errorQueryHandler)
      Disposer.register(disposable) {
        errorQuery.removeHandler(errorQueryHandler)
      }
      Disposer.register(disposable, errorQuery)
      continuation.invokeOnCancellation {
        Disposer.dispose(disposable)
      }
      try {
        val wrappedCode = wrapWithErrorHandling(code, resultQuery, errorQuery)
        browser.runJavaScript(wrappedCode)
      } catch (exception: Exception) {
        continuation.resumeWithException(exception)
      }
    }
  } finally {
    Disposer.dispose(disposable)
  }
}

@Suppress("JSVoidFunctionReturnValueUsed")
@Language("JavaScript")
private fun wrapWithErrorHandling(
  @Language("JavaScript") code: String,
  resultQuery: JBCefJSQuery,
  errorQuery: JBCefJSQuery
): String {
  // language=JavaScript
  return """
  (function() {
    // Should return promise
    function payload() {
      return $code;
    }
    payload().then(result => {
      // call back the related JBCefJSQuery
      window.${resultQuery.funcName}({
        request: "" + result,
        onSuccess: (response) => {}, 
        onFailure: (error_code, error_message) => {}
      });
    }).catch(error => {
      // call back the related error handling JBCefJSQuery
      window.${errorQuery.funcName}({
        request: "" + error, 
        onSuccess: (response) => {}, 
        onFailure: (error_code, error_message) => {}
      });
    });
  })();
  """.trimIndent()
}
