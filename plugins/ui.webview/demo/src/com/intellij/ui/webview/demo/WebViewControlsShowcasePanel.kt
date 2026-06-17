// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.api.createWebViewPanel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private const val SHOWCASE_SOURCE_PATH = "community/plugins/ui.webview/demo/webview-src/views/controls-showcase/src/main.ts"

private val LOG = logger<WebViewControlsShowcasePanel>()
private val SHOWCASE_ASSET_ROOT = WebViewAssetRoot.forView("controls-showcase")

private val SHOWCASE_TABS = listOf(
  ShowcaseTab("components", "webview.controls.showcase.tab.components", "webview.controls.showcase.tab.components.description"),
  ShowcaseTab("labels-help", "webview.controls.showcase.tab.labels.help", "webview.controls.showcase.tab.labels.help.description"),
  ShowcaseTab("validation", "webview.controls.showcase.tab.validation", "webview.controls.showcase.tab.validation.description"),
  ShowcaseTab("states", "webview.controls.showcase.tab.states", "webview.controls.showcase.tab.states.description"),
  ShowcaseTab("groups-disclosure", "webview.controls.showcase.tab.groups.disclosure", "webview.controls.showcase.tab.groups.disclosure.description"),
  ShowcaseTab("tabs-segmented", "webview.controls.showcase.tab.tabs.segmented", "webview.controls.showcase.tab.tabs.segmented.description"),
  ShowcaseTab("spacing-density", "webview.controls.showcase.tab.spacing.density", "webview.controls.showcase.tab.spacing.density.description"),
  ShowcaseTab("theme-rendering", "webview.controls.showcase.tab.theme.rendering", "webview.controls.showcase.tab.theme.rendering.description"),
)

internal class WebViewControlsShowcasePanel(
  private val project: Project?,
  private val scope: CoroutineScope,
) {
  private val panels = linkedMapOf<String, WebViewPanel>()
  private val loadingTabs = mutableSetOf<String>()
  private val contentRoots = mutableMapOf<String, JPanel>()
  private lateinit var tabbedPane: JBTabbedPane

  val component: JComponent = createComponent()

  private fun createComponent(): JComponent {
    tabbedPane = JBTabbedPane().apply {
      minimumSize = Dimension(400, 300)
      preferredSize = Dimension(900, 650)
      tabPlacement = SwingConstants.TOP
    }

    for (tab in SHOWCASE_TABS) {
      tabbedPane.add(tab.title(), createTabContent(tab))
    }

    tabbedPane.addChangeListener { loadSelectedTab() }
    SwingUtilities.invokeLater { loadSelectedTab() }
    return tabbedPane
  }

  fun dispose() {
    val panelsToClose = panels.values.toList()
    panels.clear()
    runBlocking {
      for (panel in panelsToClose) {
        runCatching { panel.close() }
          .onFailure { LOG.warn("Failed to close WebView controls showcase panel", it) }
      }
    }
  }

  private fun createTabContent(tab: ShowcaseTab): JComponent {
    val root = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      border = JBUI.Borders.empty(8)
    }

    val header = JPanel(BorderLayout(JBUI.scale(8), 0))
    header.add(JBLabel(tab.description()), BorderLayout.CENTER)
    header.add(ActionLink(WebViewDemoBundle.message("webview.controls.showcase.view.source")) {
      openShowcaseSource(project)
    }.apply {
      isEnabled = project?.basePath != null
    }, BorderLayout.EAST)
    root.add(header, BorderLayout.NORTH)

    val contentRoot = JPanel(BorderLayout())
    contentRoot.add(JBLabel(WebViewDemoBundle.message("webview.controls.showcase.loading"), SwingConstants.CENTER), BorderLayout.CENTER)
    contentRoots[tab.id] = contentRoot
    root.add(contentRoot, BorderLayout.CENTER)
    return root
  }

  private fun loadSelectedTab() {
    val selectedIndex = tabbedPane.selectedIndex
    if (selectedIndex !in SHOWCASE_TABS.indices) {
      return
    }

    val tab = SHOWCASE_TABS[selectedIndex]
    if (tab.id in panels || !loadingTabs.add(tab.id)) {
      return
    }

    val contentRoot = contentRoots[tab.id] ?: return
    scope.launch {
      try {
        val panel = withContext(Dispatchers.EDT) {
          createWebViewPanel(
            scope = scope,
            options = WebViewPanelOptions(
              assetRoot = SHOWCASE_ASSET_ROOT,
              query = "section=${tab.id}",
              debugName = "WebView controls showcase: ${tab.id}",
            ),
          )
        }

        withContext(Dispatchers.EDT) {
          panels[tab.id] = panel
          contentRoot.removeAll()
          contentRoot.add(panel.component, BorderLayout.CENTER)
          contentRoot.revalidate()
          contentRoot.repaint()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Failed to load WebView controls showcase tab: ${tab.id}", t)
        withContext(Dispatchers.EDT) {
          contentRoot.removeAll()
          contentRoot.add(JBLabel(WebViewDemoBundle.message("webview.controls.showcase.load.failed"), SwingConstants.CENTER), BorderLayout.CENTER)
          contentRoot.revalidate()
          contentRoot.repaint()
        }
      }
      finally {
        withContext(Dispatchers.EDT) {
          loadingTabs.remove(tab.id)
        }
      }
    }
  }
}

private data class ShowcaseTab(
  val id: String,
  private val titleKey: String,
  private val descriptionKey: String,
) {
  fun title(): String = WebViewDemoBundle.message(titleKey)
  fun description(): String = WebViewDemoBundle.message(descriptionKey)
}

private fun openShowcaseSource(project: Project?) {
  val basePath = project?.basePath ?: return
  val sourcePath = Path.of(basePath).resolve(SHOWCASE_SOURCE_PATH)
  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourcePath)
  if (virtualFile == null) {
    LOG.warn("WebView controls showcase source file not found: $sourcePath")
    return
  }
  OpenFileDescriptor(project, virtualFile).navigate(true)
}
