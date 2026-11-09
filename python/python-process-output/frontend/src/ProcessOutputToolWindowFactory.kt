package com.intellij.python.processOutput.frontend

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.community.common.sdk.SdkAwareToolWindowFactory
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.UIEventListener
import com.intellij.python.processOutput.frontend.ui.components.ProcessOutputToolWindow
import com.intellij.ui.content.ContentFactory

internal class ProcessOutputToolWindowFactory : SdkAwareToolWindowFactory(), DumbAware {
  override fun init(toolWindow: ToolWindow) {
    toolWindow.setStripeTitleProvider { message("process.output.title") }
    toolWindow.setStripeShortTitleProvider { message("process.output.title") }
  }

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    // create the controller off the EDT to warm up the logged processes flow
    toolWindow.project.serviceAsync<ProcessOutputControllerService>()

    super.manage(toolWindow, toolWindowManager)
  }

  override fun createToolWindowContent(
    project: Project,
    toolWindow: ToolWindow,
  ) {
    val processOutputToolWindow = ProcessOutputToolWindow(toolWindow)

    // start listening to UI events
    UIEventListener(processOutputToolWindow.uiContext, toolWindow).launch()
    
    val content =
      ContentFactory
        .getInstance()
        .createContent(
          processOutputToolWindow.component,
          null,
          false
        )

    toolWindow.contentManager.addContent(content)
  }
}
