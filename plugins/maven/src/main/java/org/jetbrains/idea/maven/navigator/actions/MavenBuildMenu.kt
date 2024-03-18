// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator.actions

import com.intellij.execution.Executor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import org.jetbrains.idea.maven.project.actions.RunBuildAction
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector.trigger

class MavenBuildMenu : DefaultActionGroup(), DumbAware {
  private val actionManager = ActionManager.getInstance()
  override fun update(e: AnActionEvent) {
    val project = AnAction.getEventProject(e) ?: return

    childActionsOrStubs
      .filter { it is MyDelegatingAction || it is RunBuildAction }
      .forEach { remove(it) }

    Executor.EXECUTOR_EXTENSION_NAME.extensionList
      .filter { it.isApplicable(project) }
      .reversed()
      .forEach {
        val contextAction = actionManager.getAction(it.contextActionId)
        if (contextAction != null) {
          add(wrap(contextAction, it), Constraints.FIRST)
        }
      }

    ActionManager.getInstance().getAction("Maven.RunBuild")?.let {
      add(it, Constraints.FIRST)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private interface MyDelegatingAction

  private class DelegatingActionGroup(action: ActionGroup, private val executor: Executor) :
    ActionGroupWrapper(action), MyDelegatingAction {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val children = super.getChildren(e)
      return children.map { wrap(it, executor) }.toTypedArray()
    }

    override fun actionPerformed(e: AnActionEvent) {
      reportUsage(e, executor)
      super.actionPerformed(e)
    }
  }

  private class DelegatingAction(action: AnAction, private val executor: Executor) :
    AnActionWrapper(action), MyDelegatingAction {

    override fun actionPerformed(e: AnActionEvent) {
      reportUsage(e, executor)
      super.actionPerformed(e)
    }
  }

  companion object {
    private fun wrap(action: AnAction, executor: Executor): AnAction = if (action is ActionGroup) DelegatingActionGroup(action, executor)
    else DelegatingAction(action, executor)

    private fun reportUsage(e: AnActionEvent, executor: Executor) {
      trigger(e.project, MavenActionsUsagesCollector.RUN_BUILD_ACTION, e.place, e.isFromContextMenu, executor)
    }
  }
}