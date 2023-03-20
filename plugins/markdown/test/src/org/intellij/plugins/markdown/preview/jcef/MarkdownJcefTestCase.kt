package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.*
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.scale.TestScaleHelper
import com.intellij.util.SystemProperties
import com.intellij.util.application
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.jetbrains.ide.BuiltInServerManager
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.BorderLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

abstract class MarkdownJcefTestCase(protected val enableOsr: Boolean) {
  private val disposableRule = DisposableRule()

  protected val disposable: Disposable
    get() = disposableRule.disposable

  @get:Rule
  val ruleChain: TestRule = RuleChain(
    ApplicationRule(),
    createJcefTestRule(enableOsr),
    disposableRule
  )

  private class StandaloneRule: TestRule {
    override fun apply(base: Statement, description: Description): Statement {
      return statement {
        TestScaleHelper.assumeStandalone()
        base.evaluate()
      }
    }
  }

  protected fun createJcefTestRule(enableOsr: Boolean): TestRule {
    return RuleChain(
      StandaloneRule(),
      RegistryKeyRule("ide.browser.jcef.testMode.enabled", true),
      RegistryKeyRule("ide.browser.jcef.markdownView.osr.enabled", enableOsr)
    )
  }

  protected fun setupPreview(content: String, beforeLoad: ((JBCefBrowser) -> Unit)? = null): MarkdownJCEFHtmlPanel {
    BuiltInServerManager.getInstance().waitForStart()
    val panel = MarkdownJCEFHtmlPanel()
    Disposer.register(disposable, panel)
    beforeLoad?.invoke(panel)
    panel.addConsoleMessageHandler { level, message, _, _ ->
      println("[${level.name}] $message")
    }
    invokeAndWaitForLoadOrError(panel) { browser ->
      application.invokeLater {
        val frame = JFrame(MarkdownContentEscapingTest::class.java.name)
        Disposer.register(disposable) {
          frame.isVisible = false
          frame.dispose()
        }
        frame.apply {
          setSize(640, 480)
          setLocationRelativeTo(null)
          add(browser.component, BorderLayout.CENTER)
          isVisible = true
        }
        application.invokeAndWait {
          panel.setHtml(content, 0)
        }
      }
    }
    return panel
  }

  protected fun invokeAndWaitForLoadOrError(browser: JBCefBrowser, block: (JBCefBrowser) -> Unit) {
    val latch = CountDownLatch(1)
    browser.addLoadHandler(object: CefLoadHandlerAdapter() {
      override fun onLoadEnd(targetBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        latch.countDown()
        browser.removeLoadHandler(this)
      }

      override fun onLoadError(
        targetBrowser: CefBrowser,
        frame: CefFrame,
        errorCode: CefLoadHandler.ErrorCode,
        errorText: String,
        failedUrl: String
      ) {
        latch.countDown()
        browser.removeLoadHandler(this)
      }
    })
    block.invoke(browser)
    val timeout = SystemProperties.getLongProperty(LATCH_AWAIT_TIMEOUT_PROPERTY, 20)
    latch.await(timeout, TimeUnit.SECONDS)
  }

  companion object {
    private const val LATCH_AWAIT_TIMEOUT_PROPERTY = "idea.markdown.test.jcef.latch.await.timeout"

    internal fun JBCefBrowser.addLoadHandler(handler: CefLoadHandler) {
      jbCefClient.addLoadHandler(handler, cefBrowser)
    }

    internal fun JBCefBrowser.removeLoadHandler(handler: CefLoadHandler) {
      jbCefClient.removeLoadHandler(handler, cefBrowser)
    }

    internal fun JBCefBrowser.collectPageSource(): String? {
      val latch = CountDownLatch(1)
      var result: String? = null
      cefBrowser.getSource {
        result = it
        latch.countDown()
      }
      return try {
        latch.await(5, TimeUnit.SECONDS)
        result
      } catch (exception: InterruptedException) {
        null
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
