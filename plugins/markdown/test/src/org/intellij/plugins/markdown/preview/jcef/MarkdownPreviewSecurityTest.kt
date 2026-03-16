// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ExtensionTestUtil
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@EnabledIfSystemProperty(named = "intellij.test.standalone", matches = "^true$")
@MarkdownPreviewTest
class MarkdownPreviewSecurityTest: MarkdownJcefTestCase() {

  @BeforeEach
  fun disablePreviewExtensions() {
    ExtensionTestUtil.maskExtensions(MarkdownBrowserPreviewExtension.Provider.EP, emptyList(), disposable)
  }

  @Timeout(TIMEOUT)
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

    val canceledUrl = runBlocking(Dispatchers.EDT) {
      coroutineScope {
        val panel = createPreview()
        panel.setupPreview()

        val canceledUrl = async(start = CoroutineStart.UNDISPATCHED) {
          val channel = Channel<String>(capacity = 1)
          val handler = object : CefLoadHandlerAdapter() {
            override fun onLoadError(browser: CefBrowser, frame: CefFrame, errorCode: CefLoadHandler.ErrorCode, errorText: String, failedUrl: String) {
              check(channel.trySend(failedUrl).isSuccess)
            }
          }
          panel.addLoadHandler(handler)
          return@async channel.receive()
        }
        panel.setHtmlAndWait(content)
        return@coroutineScope canceledUrl.await()
      }
    }

    assertEquals(maliciousUrl, canceledUrl)
  }

  @Timeout(TIMEOUT)
  @Test
  fun `test panel won't reload on manual location change`() {
    val maliciousUrl = "https://google.com/"
    // language=HTML
    val content = "<html><body></body></html>"

    val canceledUrl = runBlocking(Dispatchers.EDT) {
      coroutineScope {
        val panel = createPreview()
        panel.setupPreview()

        val canceledUrl = async(start = CoroutineStart.UNDISPATCHED) {
          val channel = Channel<String>(capacity = 1)
          val handler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
              browser.executeJavaScript("window.location.replace('$maliciousUrl')", null, 0)
            }

            override fun onLoadError(browser: CefBrowser, frame: CefFrame, errorCode: CefLoadHandler.ErrorCode, errorText: String, failedUrl: String) {
              check(channel.trySend(failedUrl).isSuccess)
            }
          }
          panel.addLoadHandler(handler)
          return@async channel.receive()
        }
        panel.setHtmlAndWait(content)
        return@coroutineScope canceledUrl.await()
      }
    }

    assertEquals(maliciousUrl, canceledUrl)
  }

  @Timeout(TIMEOUT)
  @Test
  fun `test external scripts are not allowed`() {
    val maliciousUrl = "https://evil.example.com/some-script.js"
    // language=HTML
    val content = "<html><body><script src='$maliciousUrl'></body></html>"

    val message = runBlocking(Dispatchers.EDT) {
      coroutineScope {
        val panel = createPreview()
        panel.setupPreview()

        val message = async(start = CoroutineStart.UNDISPATCHED) {
          val channel = Channel<String>(capacity = 1)
          panel.addConsoleMessageHandler { _, message, _, _ ->
            check(channel.trySend(message).isSuccess)
          }
          return@async channel.receive()
        }
        panel.setHtmlAndWait(content)
        return@coroutineScope message.await()
      }
    }

    assertTrue(message.contains("Refused to load the script 'https://evil.example.com/some-script.js'"))
  }

  @Timeout(TIMEOUT)
  @Test
  fun `test dynamically added inline scripts are not allowed`() {
    // language=HTML
    val content = "<html><body><script>console.log('Ops!');</script></body></html>"

    val message = runBlocking(Dispatchers.EDT) {
      coroutineScope {
        val panel = createPreview()
        panel.setupPreview()

        val canceledUrl = async(start = CoroutineStart.UNDISPATCHED) {
          val handler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
              // language=JavaScript
              val scriptContent = """
                const element = document.createElement("script");
                element.innerHTML = "console.log('Ops!!')";
                document.body.appendChild(element);
              """.trimIndent()
              browser.executeJavaScript(scriptContent, null, 0)
            }
          }
          panel.addLoadHandler(handler)

          val channel = Channel<String>(capacity = 1)
          panel.addConsoleMessageHandler { _, message, _, _ ->
            check(channel.trySend(message).isSuccess)
          }

          return@async channel.receive()
        }
        panel.setHtmlAndWait(content)
        return@coroutineScope canceledUrl.await()
      }
    }

    assertTrue(message.contains("Refused to execute inline script because it violates the following Content Security Policy directive"))
  }

  companion object {
    private const val TIMEOUT = 10L
  }
}
