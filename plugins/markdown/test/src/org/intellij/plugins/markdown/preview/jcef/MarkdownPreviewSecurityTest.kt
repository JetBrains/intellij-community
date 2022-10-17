// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
class MarkdownPreviewSecurityTest(enableOsr: Boolean): MarkdownJcefTestCase(enableOsr) {
  @Before
  fun disablePreviewExtensions() {
    ExtensionTestUtil.maskExtensions(MarkdownBrowserPreviewExtension.Provider.EP, emptyList(), disposable)
  }

  @After
  fun ensureEdtIsPumped() {
    // wait until com.intellij.ui.jcef.JBCefOsrHandler.onPaint is called which call invokeLater()
    Thread.sleep(500)
    UIUtil.pump()
  }

  @Test
  fun `test meta redirects are not allowed`() {
    val maliciousUrl = "https://evil.example.com/poc.html"
    // language=HTML
    val content = """
      <html>
        <body>
          <meta http-equiv="refresh" content="0;url=$maliciousUrl">
        </body>
      </html>
    """.trimIndent()
    val latch = CountDownLatch(2)
    var canceledUrl: String? = null
    setupPreview(content) { browser ->
      browser.addLoadHandler(object: CefLoadHandlerAdapter() {
        override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
          latch.countDown()
        }

        override fun onLoadError(browser: CefBrowser, frame: CefFrame, errorCode: CefLoadHandler.ErrorCode, errorText: String, failedUrl: String) {
          canceledUrl = failedUrl
          latch.countDown()
        }
      })
    }
    assertTrue(latch.await(latchAwaitTimeout, TimeUnit.SECONDS))
    assertEquals(maliciousUrl, canceledUrl)
  }

  @Test
  fun `test panel won't reload on manual location change`() {
    val maliciousUrl = "https://google.com/"
    // language=HTML
    val content = "<html><body></body></html>"
    val latch = CountDownLatch(1)
    var canceledUrl: String? = null
    setupPreview(content) { browser ->
      browser.addLoadHandler(object: CefLoadHandlerAdapter() {
        override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
          browser.executeJavaScript("window.location.replace('$maliciousUrl')", null, 0)
        }

        override fun onLoadError(browser: CefBrowser, frame: CefFrame, errorCode: CefLoadHandler.ErrorCode, errorText: String, failedUrl: String) {
          canceledUrl = failedUrl
          latch.countDown()
        }
      })
    }
    assertTrue(latch.await(latchAwaitTimeout, TimeUnit.SECONDS))
    assertEquals(maliciousUrl, canceledUrl)
  }

  @Test
  fun `test external scripts are not allowed`() {
    val maliciousUrl = "https://evil.example.com/some-script.js"
    // language=HTML
    val content = "<html><body><script src='$maliciousUrl'></body></html>"
    val latch = CountDownLatch(1)
    setupPreview(content) { browser ->
      browser.addConsoleMessageHandler { _, message, _, _ ->
        if (message.contains("Refused to load the script 'https://evil.example.com/some-script.js'")) {
          latch.countDown()
        }
      }
    }
    assertTrue(latch.await(latchAwaitTimeout, TimeUnit.SECONDS))
  }

  @Test
  fun `test dynamically added inline scripts are not allowed`() {
    // language=HTML
    val content = "<html><body><script>console.log('Ops!');</script></body></html>"
    val latch = CountDownLatch(1)
    setupPreview(content) { browser ->
      browser.addLoadHandler(object: CefLoadHandlerAdapter() {
        override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
          // language=JavaScript
          val scriptContent = """
            const element = document.createElement("script");
            element.innerHTML = "console.log('Ops!!')";
            document.body.appendChild(element);
          """.trimIndent()
          browser.executeJavaScript(scriptContent, null, 0)
        }
      })
      browser.addConsoleMessageHandler { _, message, _, _ ->
        if (message.contains("Refused to execute inline script because it violates the following Content Security Policy directive")) {
          latch.countDown()
        }
      }
    }
    assertTrue(latch.await(latchAwaitTimeout, TimeUnit.SECONDS))
  }

  companion object {
    private const val latchAwaitTimeout = 10L

    @Suppress("unused")
    @JvmStatic
    @get:Parameterized.Parameters(name = "enableOsr = {0}")
    val modes = listOf(true)
  }
}
