// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.common.sdk.PythonSdkObserverService
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val isAvailableAtStartup: Boolean get() = Registry.`is`("python.toolwindows.available.at.startup")

class PyPackagesToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun shouldBeAvailable(project: Project): Boolean =
    isAvailableAtStartup

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    if (isAvailableAtStartup) {
      return
    }

    toolWindow.project.service<PythonSdkObserverService>().isPythonSdkAvailable.collect {
      withContext(Dispatchers.EDT) {
        toolWindow.isAvailable = it
      }
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = project.service<PyPackagingToolWindowService>()
    val toolWindowPanel = PyPackagingToolWindowPanel(project)
    Disposer.register(toolWindow.disposable, toolWindowPanel)
    service.initialize(toolWindowPanel)
    val content = ContentFactory.getInstance().createContent(toolWindowPanel.component, null, false)
    toolWindow.contentManager.addContent(content)
  }
}
