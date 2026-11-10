package com.intellij.python.processOutput.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.python.community.common.sdk.SdkAwareToolWindowFactory
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.UIEventListener
import com.intellij.python.processOutput.frontend.ui.components.ProcessOutputToolWindow
import com.intellij.ui.content.ContentFactory

internal class ProcessOutputToolWindowFactory : SdkAwareToolWindowFactory(), DumbAware {
  override fun init(toolWindow: ToolWindow) {
    val component = ProcessOutputToolWindow(toolWindow)

    // initializing the state
    toolWindow.project.service<ProcessOutputToolWindowService>().component = ProcessOutputToolWindow(toolWindow)

    // start listening to UI events and warming up the processes flow
    UIEventListener(component.uiContext, toolWindow).launch()

    toolWindow.setStripeTitleProvider { message("process.output.title") }
    toolWindow.setStripeShortTitleProvider { message("process.output.title") }
  }

  override fun createToolWindowContent(
    project: Project,
    toolWindow: ToolWindow,
  ) {
    val content =
      ContentFactory
        .getInstance()
        .createContent(
          project.service<ProcessOutputToolWindowService>().component.component,
          null,
          false
        )

    toolWindow.contentManager.addContent(content)
  }
}

@Service(Service.Level.PROJECT)
private class ProcessOutputToolWindowService {
  lateinit var component: ProcessOutputToolWindow
}