package com.intellij.python.processOutput.frontend

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.python.community.common.sdk.SdkAwareToolWindowFactory
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.components.ProcessOutputToolWindow
import com.intellij.ui.content.ContentFactory

internal const val TOOL_WINDOW_ID = "PythonProcessOutput"

internal class ProcessOutputToolWindowFactory : SdkAwareToolWindowFactory(), DumbAware {
  override fun init(toolWindow: ToolWindow) {
    // pre-initialize the service to warm up the logged processes flow
    toolWindow.project.service<ProcessOutputControllerService>()

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
          ProcessOutputToolWindow(project, toolWindow).component,
          null,
          false
        )

    toolWindow.contentManager.addContent(content)
  }
}
