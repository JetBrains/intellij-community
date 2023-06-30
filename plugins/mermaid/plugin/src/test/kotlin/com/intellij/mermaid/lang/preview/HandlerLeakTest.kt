package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.preview.*
import com.intellij.mermaid.preview.WaitForLoadHandlerAdapter
import com.intellij.mermaid.preview.createBrowser
import com.intellij.mermaid.preview.waitForPageLoad
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.cef.handler.CefLoadHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@PreviewTest
@WithJcef
@TestApplication
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
        return 2 + 2;
      })();
      """.trimIndent()
      val result = browser.executeCancellableJavaScript(code)
      checkNotNull(result)
      Assertions.assertEquals(4, result.toInt())
    }
    LeakHunter.checkLeak(browser, JBCefJSQuery::class.java)
    ensureLoadHandlerIsNotLeaked(browser)
  }

  private fun ensureLoadHandlerIsNotLeaked(browser: JBCefBrowser) {
    LeakHunter.checkLeak(browser, CefLoadHandler::class.java) { it is WaitForLoadHandlerAdapter }
  }
}
