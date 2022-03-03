// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RegistryKeyRule
import com.intellij.testFramework.statement
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefTestHelper
import com.intellij.ui.scale.TestScaleHelper
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.jetbrains.ide.BuiltInServerManager
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.BorderLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * These methods are very specific to how our [MarkdownJCEFHtmlPanel] works and are expected to be called
 * only from dedicated preview (with full-blown jcef) tests, since there is a lot of expensive synchronization going on.
 *
 * Note: since [MarkdownJCEFHtmlPanel] is not using any of parsing/VFS infrastructure, it should be safe to use
 * explicit [SwingUtilities.invokeLater] kind methods.
 */
internal object MarkdownJCEFPreviewTestUtil {
  fun setupPreviewPanel(html: String, parentDisposable: Disposable, beforeLoad: (MarkdownJCEFHtmlPanel.() -> Unit)? = null): MarkdownJCEFHtmlPanel {
    BuiltInServerManager.getInstance().waitForStart()
    val panel = MarkdownJCEFHtmlPanel()
    Disposer.register(parentDisposable, panel)
    beforeLoad?.invoke(panel)
    panel.addConsoleMessageHandler { level, message, _, _ ->
      println("[${level.name}] $message")
    }
    setupViewComponent(panel, parentDisposable)
    // setHtml() delegates to JavaScript, we can't listen for it with CefLoadHandler, just perform
    SwingUtilities.invokeAndWait {
      panel.setHtml(html, 0)
    }
    return panel
  }

  fun JBCefBrowser.collectPageSource(): String? {
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

  fun JBCefBrowser.addConsoleMessageHandler(handler: (level: CefSettings.LogSeverity, message: String, source: String, line: Int) -> Unit) {
    jbCefClient.addDisplayHandler(object: CefDisplayHandlerAdapter() {
      override fun onConsoleMessage(browser: CefBrowser, level: CefSettings.LogSeverity, message: String, source: String, line: Int): Boolean {
        handler.invoke(level, message, source, line)
        return false
      }
    }, cefBrowser)
  }

  private fun setupViewComponent(browser: MarkdownJCEFHtmlPanel, parentDisposable: Disposable) {
    // MarkdownJCEFHtmlPanel loads some html on init,
    // the native browser is not created immediately but on show,
    // so loading is started after showing and we can listen/wait for it
    JBCefTestHelper.invokeAndWaitForLoad(browser) {
      SwingUtilities.invokeLater {
        val frame = JFrame(MarkdownContentEscapingTest::class.java.name)
        Disposer.register(parentDisposable) {
          frame.isVisible = false
          frame.dispose()
        }
        frame.apply {
          setSize(640, 480)
          setLocationRelativeTo(null)
          add(browser.component, BorderLayout.CENTER)
          isVisible = true
        }
      }
    }
  }

  private class StandaloneRule: TestRule {
    override fun apply(base: Statement, description: Description): Statement {
      return statement {
        TestScaleHelper.assumeStandalone()
        base.evaluate()
      }
    }
  }

  fun createJcefTestRule(enableOsr: Boolean): TestRule {
    return RuleChain.outerRule(StandaloneRule())
      .around(RegistryKeyRule("ide.browser.jcef.testMode.enabled", true))
      .around(RegistryKeyRule("ide.browser.jcef.markdownView.osr.enabled", enableOsr))
  }
}
