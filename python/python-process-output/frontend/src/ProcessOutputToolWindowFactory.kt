package com.intellij.python.processOutput.frontend

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.python.community.common.sdk.SdkAwareToolWindowFactory
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.UIEventListener
import com.intellij.python.processOutput.frontend.ui.components.ProcessOutputToolWindow
import com.intellij.ui.content.ContentFactory

internal class ProcessOutputToolWindowFactory : SdkAwareToolWindowFactory(), DumbAware {
  private lateinit var processOutputToolWindow: ProcessOutputToolWindow

  override fun init(toolWindow: ToolWindow) {
    processOutputToolWindow = ProcessOutputToolWindow(toolWindow)

    // start listening to UI events
    // also pre-initialize the service to warm up the logged processes flow
    UIEventListener(processOutputToolWindow.uiContext, toolWindow).launch()

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
          processOutputToolWindow.component,
          null,
          false
        )

    toolWindow.contentManager.addContent(content)
  }
}
