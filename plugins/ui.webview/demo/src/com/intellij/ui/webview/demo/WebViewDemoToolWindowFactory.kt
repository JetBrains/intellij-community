package com.intellij.ui.webview.demo

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

internal class WebViewDemoToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    toolWindowManager.invokeLater {
      if (!toolWindow.project.isDisposed) {
        toolWindow.activate(null, true)
      }
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = WebViewDemoProjectService.getInstance(project)
    toolWindow.addContent(service.createSamplePanelContent(), WebViewDemoBundle.message("toolwindow.tab.sample.panel"))
    toolWindow.addContent(service.createControlsShowcaseContent(), WebViewDemoBundle.message("toolwindow.tab.controls.showcase"))
    toolWindow.addContent(service.createMarkdownLinkGraphContent(), WebViewDemoBundle.message("toolwindow.tab.markdown.link.graph"))
  }

  private fun ToolWindow.addContent(demoContent: WebViewDemoContent, displayName: String) {
    val content = ContentFactory.getInstance().createContent(demoContent.component, displayName, false)
    content.setDisposer(demoContent.disposer)
    contentManager.addContent(content)
  }
}
