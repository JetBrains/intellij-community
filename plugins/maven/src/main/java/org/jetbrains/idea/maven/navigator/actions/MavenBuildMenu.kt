// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator.actions

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.RunContextAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import org.jetbrains.idea.maven.project.actions.RunBuildAction
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector

class MavenBuildMenu : DefaultActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val project = AnAction.getEventProject(e) ?: return

    childActionsOrStubs
      .filter { it is MyDelegatingAction || it is RunBuildAction }
      .forEach { remove(it) }

    ExecutorRegistry.getInstance().registeredExecutors
      .filter { it.isApplicable(project) }
      .reversed()
      .forEach { add(wrap(RunContextAction(it), it), Constraints.FIRST) }

    ActionManager.getInstance().getAction("Maven.RunBuild")?.let {
      add(it, Constraints.FIRST)
    }
  }

  private interface MyDelegatingAction

  private class DelegatingActionGroup internal constructor(action: ActionGroup, private val executor: Executor) :
    EmptyAction.MyDelegatingActionGroup(action), MyDelegatingAction {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val children = super.getChildren(e)
      return children.map { wrap(it, executor) }.toTypedArray()
    }

    override fun actionPerformed(e: AnActionEvent) {
      reportUsage(e, executor)
      super.actionPerformed(e)
    }
  }

  private class DelegatingAction internal constructor(action: AnAction, private val executor: Executor) :
    EmptyAction.MyDelegatingAction(action), MyDelegatingAction {

    override fun actionPerformed(e: AnActionEvent) {
      reportUsage(e, executor)
      super.actionPerformed(e)
    }
  }

  companion object {
    private fun wrap(action: AnAction, executor: Executor): AnAction = if (action is ActionGroup) DelegatingActionGroup(action, executor)
    else DelegatingAction(action, executor)

    private fun reportUsage(e: AnActionEvent, executor: Executor) {
      MavenActionsUsagesCollector.trigger(e.project, MavenActionsUsagesCollector.ActionID.RunBuildAction, e, executor)
    }
  }
}