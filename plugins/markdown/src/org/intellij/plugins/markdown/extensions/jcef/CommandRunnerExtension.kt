// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingRunConfigurationProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingRecentProjectProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class CommandRunnerExtension : MarkdownCodeViewExtension, ResourceProvider {

  override val scripts: List<String> = listOf("commandRunner/commandRunner.js")

  override val codeEvents: Map<String, (String, Project, VirtualFile) -> Unit> = mapOf("runCommand" to this::runCommand)

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in scripts

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return ResourceProvider.loadInternalResource(this::class, resourceName)
  }

  //override fun openTag(escapedCodeLine: String, project: Project?, file: VirtualFile?): String {
  //  if (project != null && file != null && matches(escapedCodeLine, project, file)) {
  //    return "<a href='#' role='button' data-command='${DefaultRunExecutor.EXECUTOR_ID}:$escapedCodeLine'>"
  //  } else return ""
  //}
  //
  //override fun closeTag(escapedCodeLine: String, project: Project?, file: VirtualFile?): String {
  //  return if (project != null && file != null && matches(escapedCodeLine, project, file)) "</a>" else ""
  //}

  override fun processCodeLine(escapedCodeLine: String, project: Project?, file: VirtualFile?): String {
    if (project != null && file != null && matches(project, file.parent.canonicalPath, true, escapedCodeLine.trim())) {
      return "<a href='#' role='button' data-command='${DefaultRunExecutor.EXECUTOR_ID}:$escapedCodeLine'>$escapedCodeLine</a>"
    }
    else return escapedCodeLine
  }

  private fun runCommand(command: String, project: Project, file: VirtualFile) {
    val executorId = command.substringBefore(":")
    val shellCommand = command.substringAfter(":")
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    execute(project, file.parent.canonicalPath, true, shellCommand, executor)
  }


  companion object {

    fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
      val dataContext = createDataContext(project, localSession, workingDirectory)
      return RunAnythingProvider.EP_NAME.extensionList
        .asSequence()
        .filter { checkForCLI(it) }
        .any { provider -> provider.findMatchingValue(dataContext, command) != null }
    }

    fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executor: Executor): Boolean {
      val dataContext = createDataContext(project, localSession, workingDirectory, executor)
      return RunAnythingProvider.EP_NAME.extensionList
        .asSequence()
        .filter { checkForCLI(it) }
        .any { provider ->
          provider.findMatchingValue(dataContext, command)?.let { provider.execute(dataContext, it); return true } ?: false
        }
    }

    private fun createDataContext(project: Project, localSession: Boolean, workingDirectory: String?, executor: Executor? = null): DataContext {
      val virtualFile = if (localSession && workingDirectory != null)
        LocalFileSystem.getInstance().findFileByPath(workingDirectory) else null

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
  }
}
