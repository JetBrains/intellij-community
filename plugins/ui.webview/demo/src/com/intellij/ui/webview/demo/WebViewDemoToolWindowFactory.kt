package com.intellij.ui.webview.demo

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

internal class WebViewDemoToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun shouldBeAvailable(project: Project): Boolean = false

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = WebViewDemoProjectService.getInstance(project)
    toolWindow.addContent(service.createUiDslShowcaseContent(), WebViewDemoBundle.message("toolwindow.tab.ui.dsl.showcase"))
    toolWindow.addContent(service.createControlsShowcaseContent(), WebViewDemoBundle.message("toolwindow.tab.controls.showcase"))
    toolWindow.addContent(service.createSamplePanelContent(), WebViewDemoBundle.message("toolwindow.tab.sample.panel"))
    toolWindow.addContent(service.createReactControlsShowcaseContent(), WebViewDemoBundle.message("toolwindow.tab.react.controls.showcase"))
    toolWindow.addContent(service.createMarkdownLinkGraphContent(), WebViewDemoBundle.message("toolwindow.tab.markdown.link.graph"))
  }

  private fun ToolWindow.addContent(demoContent: WebViewDemoContent, displayName: String) {
    val content = ContentFactory.getInstance().createContent(demoContent.component, displayName, false)
    content.setDisposer(demoContent.disposer)
    contentManager.addContent(content)
  }
}
