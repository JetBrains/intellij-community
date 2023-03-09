package com.intellij.mermaid.preview

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.jcef.JBCefBrowserBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.intellij.lang.annotations.Language
import org.jetbrains.concurrency.AsyncPromise
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

typealias JsExpression = String
typealias JsExpressionResult = String?
typealias JsExpressionResultPromise = AsyncPromise<JsExpressionResult>

/**
 * Create an instance of [CancellableJcefBrowserJsCall], invoke it's [CancellableJcefBrowserJsCall.performCall]
 * and wait for the returned promise to be resolved or rejected.
 */
internal suspend fun JBCefBrowser.executeCancellableJavaScript(@Language("JavaScript") code: JsExpression): JsExpressionResult {
  return suspendCancellableCoroutine { continuation ->
    // Disposable associated with the call lifetime
    val disposable = Disposer.newCheckedDisposable()
    // Can't be alive longer than a browser
    Disposer.register(this, disposable)
    val call = CancellableJcefBrowserJsCall(code, browser = this)
    // The actual call will be disposed either on [disposable] disposal, or on browser disposal
    val promise = call.performCall(disposable)
    // Same as Promise.await() but with a dispose call on cancellation
    promise.onSuccess { continuation.resume(it) }
    promise.onError { continuation.resumeWithException(it) }
    continuation.invokeOnCancellation {
      Disposer.dispose(disposable)
      promise.setError(CancellationException())
    }
  }
}

private class CancellableJcefBrowserJsCall(
  private val javaScriptExpression: JsExpression,
  private val browser: JBCefBrowser
) {
  fun performCall(parentDisposable: Disposable): AsyncPromise<JsExpressionResult> {
    val disposable = Disposer.newCheckedDisposable("CancellableJcefBrowserJsCallDisposable")
    Disposer.register(parentDisposable, disposable)
    Disposer.register(browser, disposable)
    check(!disposable.isDisposed) { "Failed to execute the requested JS expression. The related browser is disposed." }
    val resultPromise = JsExpressionResultPromise().apply {
      onProcessed {
        Disposer.dispose(disposable)
      }
    }
    Disposer.register(disposable) {
      resultPromise.setError(AlreadyDisposedException("The related browser is disposed during the call."))
    }
    val resultHandlerQuery = createResultHandlerQuery(disposable, resultPromise)
    val errorHandlerQuery = createErrorHandlerQuery(disposable, resultPromise)
    val jsToRun = javaScriptExpression.wrapWithErrorHandling(
      resultQuery = resultHandlerQuery,
      errorQuery = errorHandlerQuery
    )
    try {
      browser.cefBrowser.executeJavaScript(jsToRun, "", 0)
    }
    catch (exception: Exception) {
      resultPromise.setError(exception)
    }
    return resultPromise
  }

  private fun createResultHandlerQuery(parentDisposable: Disposable, resultPromise: JsExpressionResultPromise) =
    createQuery(parentDisposable).apply {
      addHandler { result ->
        resultPromise.setResult(result)
        null
      }
    }

  private fun createErrorHandlerQuery(parentDisposable: Disposable, resultPromise: JsExpressionResultPromise) =
    createQuery(parentDisposable).apply {
      addHandler { errorMessage ->
        resultPromise.setError(errorMessage ?: "Unknown error")
        null
      }
    }

  private fun createQuery(parentDisposable: Disposable): JBCefJSQuery {
    val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
    Disposer.register(parentDisposable, query)
    return query
  }

  private fun JsExpression.asFunctionBody(): JsExpression = let { expression ->
    when {
      StringUtil.containsLineBreak(expression) -> expression
      StringUtil.startsWith(expression, "return") -> expression
      else -> "return $expression"
    }
  }

  @Suppress("JSVoidFunctionReturnValueUsed")
  @Language("JavaScript")
  private fun @receiver:Language("JavaScript") JsExpression.wrapWithErrorHandling(resultQuery: JBCefJSQuery, errorQuery: JBCefJSQuery) = """
      function payload() {
          ${asFunctionBody()}
      }
    
      try {
          let result = payload();

          // call back the related JBCefJSQuery
          window.${resultQuery.funcName}({
              request: "" + result,
              onSuccess: function (response) {
                  // do nothing
              }, onFailure: function (error_code, error_message) {
                  // do nothing
              }
          });
      } catch (e) {
          // call back the related error handling JBCefJSQuery
          window.${errorQuery.funcName}({
              request: "" + e, 
              onSuccess: function (response) {
                  // do nothing
              }, onFailure: function (error_code, error_message) {
                  // do nothing
              }
          });
      }      
    """.trimIndent()
}
