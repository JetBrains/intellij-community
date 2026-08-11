// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo.acp

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.webview.demo.WebViewDemoBundle
import com.intellij.ui.webview.demo.WebViewDemoProjectService

/** Hosts the agentic ACP chat in its own dedicated tool window. */
internal class AcpChatToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val demoContent = WebViewDemoProjectService.getInstance(project).createAcpChatContent()
    val content = ContentFactory.getInstance()
      .createContent(demoContent.component, WebViewDemoBundle.message("toolwindow.tab.acp.chat"), false)
    content.setDisposer(demoContent.disposer)
    toolWindow.contentManager.addContent(content)
  }
}
