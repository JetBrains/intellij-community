// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language

internal class PyDebuggerBackendToolWindowSetup : ProjectActivity {
  override suspend fun execute(project: Project) {
    withContext(Dispatchers.EDT) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG)
      if (toolWindow != null) {
        setupTitleAction(toolWindow)
      }
      else {
        val connection = project.messageBus.connect()
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
          override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
            if (ToolWindowId.DEBUG in ids) {
              connection.disconnect()
              PythonDebuggerScope.childScope(project, "PyDebuggerBackendToolWindowSetup").launch(Dispatchers.EDT) {
                toolWindowManager.getToolWindow(ToolWindowId.DEBUG)?.let { setupTitleAction(it) }
              }
            }
          }
        })
      }
    }
  }

  @RequiresEdt
  private fun setupTitleAction(toolWindow: ToolWindow) {
    val action = ActionManager.getInstance().getAction(SWITCHER_ACTION_ID) ?: return
    val existing = ((toolWindow as? ToolWindowEx)?.decorator as? InternalDecoratorImpl)?.headerToolbarActions
                     ?.let { group -> (group as? DefaultActionGroup)?.getChildActionsOrStubs()?.filter { it !is Separator } }
                   ?: emptyList()
    // The header can hold the switcher either as the action itself or as an unresolved stub, and a
    // dynamic plugin reload replaces the cached instance. Matching by identity therefore misses the
    // entry that is already there and appends a second switcher, so drop every copy by id first.
    val desired = existing.filterNot { it.isBackendSwitcher() } + action
    if (desired != existing) {
      toolWindow.setTitleActions(desired)
    }
  }

  // Compared by class name rather than with `is`: a switcher left behind by a previous plugin
  // generation comes from a classloader that is already dead, so its class is not identical to ours.
  private fun AnAction.isBackendSwitcher(): Boolean =
    javaClass.name == PyDebuggerBackendSwitcherAction::class.java.name ||
    (this as? ActionStubBase)?.id == SWITCHER_ACTION_ID
}

@Language("devkit-action-id")
private const val SWITCHER_ACTION_ID = "Python.DebuggerBackendSwitcher"
