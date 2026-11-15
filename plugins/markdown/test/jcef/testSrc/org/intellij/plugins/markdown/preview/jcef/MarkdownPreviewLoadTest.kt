// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.util.concurrent.atomic.AtomicInteger

/**
 * Run with the same system properties as [MarkdownContentEscapingTest].
 */
@EnabledIfSystemProperty(named = "intellij.test.standalone", matches = "^true$")
@MarkdownPreviewTest
class MarkdownPreviewLoadTest: MarkdownJcefTestCase() {

  /**
   * The browser ends a load with `ERR_ABORTED` when something outside the preview stops it, and with
   * `ERR_NETWORK_CHANGED` when the operating system changes its network configuration. The preview must load the page
   * again and render the content.
   */
  @Timeout(TIMEOUT)
  @Test
  fun `test preview renders after the browser stops the first load`() {
    // language=HTML
    val content = "<html><body><p>$MARKER</p></body></html>"
    val navigations = AtomicInteger()

    val source = runBlocking(Dispatchers.EDT) {
      val panel = createPreview()
      // A canceled navigation ends the load with ERR_ABORTED, as a change of the network configuration does.
      val handler = object: CefRequestHandlerAdapter() {
        override fun onBeforeBrowse(
          browser: CefBrowser,
          frame: CefFrame,
          request: CefRequest,
          user_gesture: Boolean,
          is_redirect: Boolean,
        ): Boolean = PAGE_URL_PATH in request.url && navigations.incrementAndGet() == 1
      }
      panel.jbCefClient.addRequestHandler(handler, panel.cefBrowser)
      Disposer.register(disposable) { panel.jbCefClient.removeRequestHandler(handler, panel.cefBrowser) }

      panel.setupPreview()
      panel.setHtmlAndWait(content)
      panel.collectPageSource()
    }

    assertEquals(2, navigations.get(), "Expected the stopped load and one more load of the preview page")
    assertTrue(MARKER in source, "Expected the preview content but got: '$source'")
  }

  companion object {
    private const val TIMEOUT = 20L

    private const val MARKER = "The preview loaded the page again."

    /** The path that [org.intellij.plugins.markdown.ui.preview.PreviewStaticServer] serves the preview page from. */
    private const val PAGE_URL_PATH = "/markdownPreview/"
  }
}
