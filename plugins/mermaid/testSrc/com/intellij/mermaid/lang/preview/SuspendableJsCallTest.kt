package com.intellij.mermaid.lang.preview

import com.intellij.idea.IJIgnore
import com.intellij.mermaid.markdown.preview.JsCallExecutionException
import com.intellij.mermaid.markdown.preview.createBrowser
import com.intellij.mermaid.markdown.preview.executeCancellableJavaScript
import com.intellij.mermaid.markdown.preview.waitForPageLoad
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

@PreviewTest
@WithJcef
@ExtendWith(WaitForBuiltInServerExtension::class)
@TestApplication
@IJIgnore(issue = "IJPL-245868")
class SuspendableJsCallTest {
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  @Test
  fun `suspendable js call should work`() {
    Disposer.newCheckedDisposable().use { disposable ->
      val browser = runBlocking(Dispatchers.EDT) {
        val browser = createBrowser()
        Disposer.register(disposable, browser)
        browser.waitForPageLoad("about:blank")
        browser.executeCancellableJavaScript("""
        (function() {
          return new Promise(resolve => {
            window["marker"] = true;
            resolve();
          });
        })();
        """.trimIndent())
        val marker = browser.executeCancellableJavaScript("""
        (function() {
          return new Promise(resolve => resolve(window["marker"]));
        })();
        """.trimIndent())
        Assertions.assertTrue(marker.toBoolean())
        browser.waitForPageLoad("about:blank")
        val undefinedMarker = browser.executeCancellableJavaScript("""
        (function() {
          return new Promise(resolve => resolve(window["marker"]));
        })();
        """.trimIndent())
        Assertions.assertEquals("undefined", undefinedMarker)
        return@runBlocking browser
      }
      HandlerLeakTest.ensureLoadHandlerIsNotLeaked(browser)
    }
    PreviewHandlerLeakTest.ensureNoJcefObjectsLeaks(JBCefApp.getInstance())
  }

  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  @Test
  fun `suspendable js call should throw exception on js error`() {
    assertThrows<JsCallExecutionException> {
      try {
        Disposer.newCheckedDisposable().use { disposable ->
          runBlocking(Dispatchers.EDT) {
            val browser = createBrowser()
            Disposer.register(disposable, browser)
            browser.waitForPageLoad("about:blank")
            val code = """
            (function() { 
              return new Promise(resolve => { 
                throw new Error("Expected Error");
                resolve();
              });
            })();
            """.trimMargin()
            try {
              browser.executeCancellableJavaScript(code)
            } finally {
              HandlerLeakTest.ensureLoadHandlerIsNotLeaked(browser)
            }
          }
        }
      } finally {
        PreviewHandlerLeakTest.ensureNoJcefObjectsLeaks(JBCefApp.getInstance())
      }
    }
  }

  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  @Test
  fun `suspendable js call is not throwing on cancellation`() {
    Disposer.newCheckedDisposable().use { disposable ->
      val browser = runBlocking(Dispatchers.EDT) {
        createBrowser()
      }
      Disposer.register(disposable, browser)
      runBlocking(Dispatchers.EDT) {
        browser.waitForPageLoad("about:blank")
        val result = async {
          val code = """
          (function() { 
            return new Promise(resolve => {
              window.setTimeout(() => resolve(42), 2000);
            });
          })();
          """.trimIndent()
          return@async browser.executeCancellableJavaScript(code)
        }
        delay(1000)
        result.cancel()
      }
      HandlerLeakTest.ensureLoadHandlerIsNotLeaked(browser)
    }
    PreviewHandlerLeakTest.ensureNoJcefObjectsLeaks(JBCefApp.getInstance())
  }
}
