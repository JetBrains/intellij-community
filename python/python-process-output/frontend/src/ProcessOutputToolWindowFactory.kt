package com.intellij.python.processOutput.frontend

import androidx.compose.runtime.remember
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.components.ToolWindow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.addComposeTab

internal const val TOOL_WINDOW_ID = "PythonProcessOutput"

@ApiStatus.Internal
class ProcessOutputToolWindowFactory : ToolWindowFactory, DumbAware {
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
        toolWindow.addComposeTab(focusOnClickInside = true) {
            val service = remember { project.service<ProcessOutputControllerService>() }

            ToolWindow(service)
        }
    }
}
