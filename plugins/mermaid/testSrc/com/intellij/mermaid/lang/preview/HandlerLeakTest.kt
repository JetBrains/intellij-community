package com.intellij.mermaid.lang.preview

import com.intellij.idea.IJIgnore
import com.intellij.mermaid.markdown.preview.WaitForLoadHandlerAdapter
import com.intellij.mermaid.markdown.preview.createBrowser
import com.intellij.mermaid.markdown.preview.executeCancellableJavaScript
import com.intellij.mermaid.markdown.preview.waitForPageLoad
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.cef.handler.CefLoadHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@PreviewTest
@WithJcef
@TestApplication
@IJIgnore(issue = "IJPL-245868")
class HandlerLeakTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `waitForPageLoad does not leak browser and its handlers after disposal`() {
    val browser = createBrowser()
    Disposer.register(disposable, browser)
    runBlocking(Dispatchers.EDT) {
      browser.waitForPageLoad("about:blank")
    }
    ensureLoadHandlerIsNotLeaked(browser)
  }

  @Test
  fun `no leaked handlers after js call`() {
    val browser = createBrowser()
    Disposer.register(disposable, browser)
    runBlocking(Dispatchers.EDT) {
      browser.waitForPageLoad("about:blank")
      // language=JavaScript
      val code = """
      (function some() {
        return new Promise(resolve => resolve(2 + 2));
      })();
      """.trimIndent()
      val result = browser.executeCancellableJavaScript(code)
      checkNotNull(result)
      Assertions.assertEquals(4, result.toInt())
    }
    ensureJsQueryHandlerIsNotLeaked(browser)
    ensureLoadHandlerIsNotLeaked(browser)
  }

  companion object {
    fun ensureJsQueryHandlerIsNotLeaked(root: Any) {
      LeakHunter.checkLeak(root, JBCefJSQuery::class.java)
    }

    fun ensureLoadHandlerIsNotLeaked(root: Any) {
      LeakHunter.checkLeak(root, CefLoadHandler::class.java) { it is WaitForLoadHandlerAdapter }
    }
  }
}
