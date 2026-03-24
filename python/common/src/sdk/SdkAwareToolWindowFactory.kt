package com.intellij.python.common.sdk

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

private val isAvailableAtStartup: Boolean get() = Registry.`is`("python.toolwindows.available.at.startup")

@Internal
abstract class SdkAwareToolWindowFactory : ToolWindowFactory {
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
}
