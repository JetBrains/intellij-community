package com.intellij.python.processOutput.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class ProcessOutputToolWindowFactory : SdkAwareToolWindowFactory(), DumbAware {
  override fun init(toolWindow: ToolWindow) {
    toolWindow.setStripeTitleProvider { message("process.output.title") }
    toolWindow.setStripeShortTitleProvider { message("process.output.title") }
  }

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    val project = toolWindow.project

    // create the controller off the EDT to warm up the logged processes flow
    val controller = project.serviceAsync<ProcessOutputControllerService>().controller

    supervisorScope {
      // the controller drops the UI events that nobody collects, so keep them until the UI exists
      val uiEvents = Channel<UiEvent>(Channel.UNLIMITED)
      launch(start = CoroutineStart.UNDISPATCHED) {
        controller.uiEvents.collect { uiEvents.send(it) }
      }

      val toolWindowService = project.serviceAsync<ProcessOutputToolWindowService>()
      launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val component = toolWindowService.getOrCreate(toolWindow)
        UIEventListener(component.uiContext, toolWindow).listen(uiEvents.receiveAsFlow())
      }

      super.manage(toolWindow, toolWindowManager)
    }
  }

  override fun createToolWindowContent(
    project: Project,
    toolWindow: ToolWindow,
  ) {
    val content =
      ContentFactory
        .getInstance()
        .createContent(
          project.service<ProcessOutputToolWindowService>().getOrCreate(toolWindow).component,
          null,
          false
        )

    toolWindow.contentManager.addContent(content)
  }
}

@Service(Service.Level.PROJECT)
private class ProcessOutputToolWindowService {
  private var toolWindow: ToolWindow? = null
  private var component: ProcessOutputToolWindow? = null

  @RequiresEdt
  fun getOrCreate(toolWindow: ToolWindow): ProcessOutputToolWindow {
    component?.takeIf { this.toolWindow === toolWindow }?.let { return it }

    val component = ProcessOutputToolWindow(toolWindow)
    this.toolWindow = toolWindow
    this.component = component
    return component
  }
}
