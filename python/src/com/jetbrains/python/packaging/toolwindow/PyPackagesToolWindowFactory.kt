// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class PyPackagesToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = project.service<PyPackagingToolWindowService>()
    val toolWindowPanel = PyPackagingToolWindowPanel(service, toolWindow)
    service.initialize(toolWindowPanel)
    val content = ContentFactory.SERVICE.getInstance().createContent(toolWindowPanel.component, null, false)
    toolWindow.contentManager.addContent(content)
  }
}


