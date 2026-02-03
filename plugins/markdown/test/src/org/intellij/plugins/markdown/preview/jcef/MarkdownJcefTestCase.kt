package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.suspendCancellableCoroutine
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.BorderLayout
import javax.swing.JFrame
import kotlin.coroutines.resume

@ExtendWith(WaitForBuiltInServerExtension::class)
@TestApplication
abstract class MarkdownJcefTestCase {

  @TestDisposable
  lateinit var disposable: Disposable

  protected fun createPreview(): MarkdownJCEFHtmlPanel {
    val panel = MarkdownJCEFHtmlPanel()
    Disposer.register(disposable, panel)

    return panel
  }

  protected fun MarkdownJCEFHtmlPanel.setupPreview() {
    addConsoleMessageHandler { level, message, _, _ ->
      println("[${level.name}] $message")
    }

    val frame = JFrame(MarkdownContentEscapingTest::class.java.name)
    Disposer.register(disposable) {
      frame.isVisible = false
      frame.dispose()
    }
    frame.apply {
      setSize(640, 480)
      setLocationRelativeTo(null)
      add(component, BorderLayout.CENTER)
      isVisible = true
    }
  }

  companion object {
    internal fun JBCefBrowser.addLoadHandler(handler: CefLoadHandler) {
      jbCefClient.addLoadHandler(handler, cefBrowser)
    }

    internal fun JBCefBrowser.removeLoadHandler(handler: CefLoadHandler) {
      jbCefClient.removeLoadHandler(handler, cefBrowser)
    }

    internal suspend fun JBCefBrowser.collectPageSource(): String {
      return suspendCancellableCoroutine { continuation ->
        cefBrowser.getSource {
          continuation.resume(it)
        }
      }
    }

    internal fun JBCefBrowser.addConsoleMessageHandler(
      handler: (level: CefSettings.LogSeverity, message: String, source: String, line: Int) -> Unit
    ) {
      jbCefClient.addDisplayHandler(object: CefDisplayHandlerAdapter() {
        override fun onConsoleMessage(
          browser: CefBrowser,
          level: CefSettings.LogSeverity,
          message: String,
          source: String,
          line: Int
        ): Boolean {
          handler.invoke(level, message, source, line)
          return false
        }
      }, cefBrowser)
    }
  }
}
