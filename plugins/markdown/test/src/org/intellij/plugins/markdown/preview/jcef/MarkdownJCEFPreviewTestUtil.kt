// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefTestHelper
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.jetbrains.ide.BuiltInServerManager
import java.awt.BorderLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

object MarkdownJCEFPreviewTestUtil {
  fun setupPreviewPanel(html: String): MarkdownJCEFHtmlPanel {
    BuiltInServerManager.getInstance().waitForStart()
    val panel = MarkdownJCEFHtmlPanel()
    setupViewComponent(panel)
    JBCefTestHelper.invokeAndWaitForLoad(panel) {
      SwingUtilities.invokeLater {
        panel.setHtml(html, 0)
      }
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

  fun setupViewComponent(browser: MarkdownJCEFHtmlPanel) {
    JBCefTestHelper.invokeAndWaitForLoad(browser) {
      SwingUtilities.invokeLater {
        with(JFrame(MarkdownContentEscapingTest::class.java.name)) {
          setSize(640, 480)
          setLocationRelativeTo(null)
          add(browser.component, BorderLayout.CENTER)
          isVisible = true
        }
      }
    }
  }
}
