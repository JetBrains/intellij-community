package com.intellij.lambda.testFramework.testApi

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


fun LambdaIdeContext.getToolWindow(toolWindowName: String): ToolWindow =
   ToolWindowManager.getInstance(getProject()).getToolWindow(toolWindowName) ?: error("Have not found tool window '${toolWindowName}'}")

suspend fun LambdaIdeContext.waitForToolWindow(toolWindowName: String, timeout: Duration = 20.seconds): ToolWindow =
  waitSuspendingNotNull("Got '${toolWindowName}' tool window", timeout) {
    ToolWindowManager.getInstance(getProject()).getToolWindow(toolWindowName)
  }

context(ac: LambdaIdeContext)
suspend fun showAndActivateToolWindow(toolWindowName: String, timeout: Duration = 20.seconds, waitFocused: Boolean = true): ToolWindow {
  val toolWindow = ac.waitForToolWindow(toolWindowName, timeout)
  withContext(Dispatchers.EDT) {
    toolWindow.show {
      toolWindow.activate {}
    }
  }
  waitSuspending("Tool window '${toolWindowName}' is activated", timeout) {
    toolWindow.isActive
  }
  waitSuspending("Tool window '${toolWindowName}' is not empty", timeout) {
    !toolWindow.contentManager.isEmpty
  }
  if (waitFocused) {
    waitSuspending("Tool window '${toolWindowName}' is focused", timeout) {
      toolWindow.component.isFocusAncestor()
    }
  }
  return toolWindow
}

context(_: LambdaIdeContext)
fun activeToolWindowId() = ToolWindowManager.getInstance(getProject()).activeToolWindowId
                           ?: error("There is no active tool window id")

context(ac: LambdaIdeContext)
suspend fun hideActiveToolWindow() {
  val activeToolWindowId = activeToolWindowId()
  val toolWindow = ac.getToolWindow(activeToolWindowId)
  callActionByShortcut("HideActiveWindow")
  waitSuspending("Tool window '${activeToolWindowId}' is disactivated", 10.seconds) {
    !toolWindow.isActive
  }
}
