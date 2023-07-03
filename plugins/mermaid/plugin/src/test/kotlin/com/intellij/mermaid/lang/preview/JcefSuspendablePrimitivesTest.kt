package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.preview.createBrowser
import com.intellij.mermaid.preview.executeCancellableJavaScript
import com.intellij.mermaid.preview.waitForPageLoad
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@PreviewTest
@WithJcef
@ExtendWith(WaitForBuiltInServerExtension::class)
@TestApplication
class JcefSuspendablePrimitivesTest {
  @Test
  fun `waitForLoad and suspendable js call should work`() {
    Disposer.newCheckedDisposable().use { disposable ->
      val browser = runBlocking(Dispatchers.EDT) {
        val browser = createBrowser()
        Disposer.register(disposable, browser)
        browser.waitForPageLoad("about:blank")
        browser.executeCancellableJavaScript("""window["marker"] = true;""")
        val marker = browser.executeCancellableJavaScript("""window["marker"]""")
        Assertions.assertTrue(marker.toBoolean())
        browser.waitForPageLoad("about:blank")
        val undefinedMarker = browser.executeCancellableJavaScript("""window["marker"]""")
        Assertions.assertEquals("undefined", undefinedMarker)
        return@runBlocking browser
      }
      HandlerLeakTest.ensureLoadHandlerIsNotLeaked(browser)
    }
    PreviewHandlerLeakTest.ensureNoJcefObjectsLeaks(JBCefApp.getInstance())
  }
}
