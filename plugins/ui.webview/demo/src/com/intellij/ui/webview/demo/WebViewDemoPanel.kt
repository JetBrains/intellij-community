// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.components.JBLabel
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.api.createWebViewPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

internal class WebViewDemoPanel(
  private val scope: CoroutineScope,
) {
  companion object {
    private const val RESOURCE_ROOT = "webview/views/sample-panel"
    private const val DEMO_PREFERRED_WIDTH = 900
    private const val DEMO_PREFERRED_HEIGHT = 520
    private const val DEMO_MINIMUM_WIDTH = 320
    private const val DEMO_MINIMUM_HEIGHT = 240
    private const val NO_ENGINE_MESSAGE_PREFIX = "No WebView engine satisfies"
    private val LOG = logger<WebViewDemoPanel>()
    private val ASSET_ROOT = WebViewAssetRoot.fromClasspath(WebViewDemoPanel::class.java, WebViewAssetPath.of(RESOURCE_ROOT))

    private fun demoPreferredSize(): Dimension = Dimension(DEMO_PREFERRED_WIDTH, DEMO_PREFERRED_HEIGHT)

    private fun demoMinimumSize(): Dimension = Dimension(DEMO_MINIMUM_WIDTH, DEMO_MINIMUM_HEIGHT)

  }

  @Volatile private var panel: WebViewPanel? = null

  val component: JComponent = JPanel(BorderLayout()).apply {
    preferredSize = demoPreferredSize()
    minimumSize = demoMinimumSize()
  }

  init {
    loadSampleFromResources()
  }

  fun dispose() {
    scope.launch {
      panel?.close()
    }
  }

  private fun loadSampleFromResources() {
    scope.launch {
      try {
        LOG.info("Loading WebView sample assets: $RESOURCE_ROOT")
        val createdPanel = withContext(Dispatchers.EDT) {
          createWebViewPanel(
            scope = scope,
            options = WebViewPanelOptions(
              assetRoot = ASSET_ROOT,
              debugName = "WebView demo",
            ),
          )
            .also { panel ->
              panel.component.preferredSize = demoPreferredSize()
              panel.component.minimumSize = demoMinimumSize()
              component.add(panel.component, BorderLayout.CENTER)
              component.revalidate()
              component.repaint()
            }
        }
        panel = createdPanel
        val bus = createdPanel.interop.messageBus
        DemoBoardProducer(scope, bus).start()
      }
      catch (t: Throwable) {
        LOG.warn("Failed to load WebView sample", t)
        withContext(Dispatchers.EDT) {
          showFailure(t)
        }
      }
    }
  }

  private fun showFailure(t: Throwable) {
    component.removeAll()
    component.add(createFailureComponent(t), BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
  }

  private fun createFailureComponent(t: Throwable): JComponent {
    val noEngine = t.message?.startsWith(NO_ENGINE_MESSAGE_PREFIX) == true
    val titleKey = if (noEngine) "panel.no.engine.title" else "panel.fallback.title"
    val descriptionKey = if (noEngine) "panel.no.engine.description" else "panel.fallback.description"

    val content = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
    }

    content.add(JBLabel(WebViewDemoBundle.message(titleKey), SwingConstants.CENTER).apply {
      alignmentX = Component.CENTER_ALIGNMENT
      font = font.deriveFont(Font.BOLD)
    })
    content.add(Box.createVerticalStrut(JBUI.scale(8)))
    content.add(JBLabel(WebViewDemoBundle.message(descriptionKey), SwingConstants.CENTER).apply {
      alignmentX = Component.CENTER_ALIGNMENT
    })
    content.add(Box.createVerticalStrut(JBUI.scale(8)))
    content.add(JTextArea(WebViewDemoBundle.message("panel.failure.details", t.message ?: t.javaClass.name)).apply {
      alignmentX = Component.CENTER_ALIGNMENT
      isEditable = false
      isFocusable = false
      isOpaque = false
      lineWrap = true
      wrapStyleWord = true
      font = UIUtil.getLabelFont()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty()
    })

    return JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(16)
      add(content, BorderLayout.CENTER)
    }
  }

}
