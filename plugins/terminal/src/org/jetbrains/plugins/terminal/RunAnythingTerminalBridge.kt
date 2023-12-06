// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.execution.Executor
import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingRunConfigurationProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingRecentProjectProvider
import com.intellij.internal.statistic.collectors.fus.TerminalFusAwareHandler
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.terminal.TerminalShellCommandHandler

internal class RunAnythingTerminalBridge : TerminalShellCommandHandler, TerminalFusAwareHandler {

  override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
    val dataContext = createDataContext(project, localSession, workingDirectory)
    return RunAnythingProvider.EP_NAME.extensionList
      .asSequence()
      .filter { checkForCLI(it) }
      .any { provider -> provider.findMatchingValue(dataContext, command) != null }
  }

  override fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executor: Executor): Boolean {
    val dataContext = createDataContext(project, localSession, workingDirectory, executor)
    return RunAnythingProvider.EP_NAME.extensionList
      .asSequence()
      .filter { checkForCLI(it) }
      .any { provider ->
        provider.findMatchingValue(dataContext, command)?.let { provider.execute(dataContext, it); return true } ?: false
      }
  }

  private fun createDataContext(project: Project,
                                localSession: Boolean,
                                workingDirectory: String?,
                                executor: Executor? = null): DataContext {
    val virtualFile = if (localSession && workingDirectory != null)
      LocalFileSystem.getInstance().findFileByPath(workingDirectory)
    else null

    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(RunAnythingAction.EXECUTOR_KEY, executor)
      .apply {
        if (virtualFile != null) {
          add(CommonDataKeys.VIRTUAL_FILE, virtualFile)
          add(RunAnythingProvider.EXECUTING_CONTEXT, RunAnythingContext.RecentDirectoryContext(virtualFile.path))
        }
      }
      .build()
  }

  private fun checkForCLI(it: RunAnythingProvider<*>?): Boolean {
    return (it !is RunAnythingCommandProvider
            && it !is RunAnythingRecentProjectProvider
            && it !is RunAnythingRunConfigurationProvider)
  }

  override fun fillData(project: Project,
                        workingDirectory: String?,
                        localSession: Boolean,
                        command: String,
                        data: MutableList<EventPair<*>>) {
    val dataContext = createDataContext(project, localSession, workingDirectory)
    val runAnythingProvider = RunAnythingProvider.EP_NAME.extensionList
      .filter { checkForCLI(it) }
      .ifEmpty { return }
      .first { provider -> provider.findMatchingValue(dataContext, command) != null }

    data.add(EventFields.Class("runAnythingProvider").with(runAnythingProvider::class.java))
  }
}