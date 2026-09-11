// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.markdown

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.Executor
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.sh.ShBundle
import com.intellij.sh.ShLanguage
import com.intellij.sh.run.ShCommandTitleUtil
import com.intellij.sh.run.ShSelectedTerminalRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.service.MarkdownCommandRunnerRemoteApi
import org.intellij.plugins.markdown.service.confirmBackendProjectIsTrusted
import org.intellij.plugins.markdown.util.MarkdownPluginScope

internal class ShMarkdownRunner : MarkdownRunner {
  override fun isApplicable(language: Language?): Boolean = language != null && language.`is`(ShLanguage.INSTANCE)

  override fun run(command: String, project: Project, workingDirectory: String?, executor: Executor): Boolean {
    val terminalRunner = project.getService(ShSelectedTerminalRunner::class.java) ?: return false
    val directory = workingDirectory ?: return false
    return runInTrustedProject(project) {
      val title = ShCommandTitleUtil.getTitle(command)
      val runInNewTerminal = Runnable {
        terminalRunner.createNewTerminal(project, command, toTerminalWorkingDirectory(project, directory), title, null)
      }
      if (!terminalRunner.run(project, command, title, runInNewTerminal)) {
        runInNewTerminal.run()
      }
    }
  }

  override fun run(command: String, project: Project, executor: Executor, context: MarkdownRunnerContext): Boolean {
    val selectedTerminalRunner = project.getService(ShSelectedTerminalRunner::class.java) ?: return false
    return runInTrustedProject(project) {
      val title = ShCommandTitleUtil.getTitle(command)
      val sourceFileUrl = context.sourceFileUrl
      val runInNewTerminal = Runnable {
        context.withWorkingDirectory(project) { directory ->
          selectedTerminalRunner.createNewTerminal(project, command, toTerminalWorkingDirectory(project, directory), title, sourceFileUrl)
        }
      }
      if ((context.showTargetChooser || selectedTerminalRunner.hasTerminalStartedFrom(project, sourceFileUrl)) &&
          selectedTerminalRunner.showChooser(
            project, command, title, context.component, context.x, context.y, runInNewTerminal,
          )) {
        return@runInTrustedProject
      }
      if (!selectedTerminalRunner.run(project, command, title, runInNewTerminal)) {
        runInNewTerminal.run()
      }
    }
  }

  override fun title(): String = ShBundle.message("sh.markdown.runner.title")

  private fun runInTrustedProject(project: Project, action: () -> Unit): Boolean {
    val api = MarkdownCommandRunnerRemoteApi.tryGetInstance() ?: return false
    MarkdownPluginScope.scope(project).launch {
      try {
        if (!confirmBackendProjectIsTrusted(project, api)) return@launch
        withContext(Dispatchers.EDT) {
          if (!project.isDisposed) action()
        }
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        logger<ShMarkdownRunner>().warn("Failed to run a Markdown code fence.", e)
      }
    }
    return true
  }

  private fun toTerminalWorkingDirectory(project: Project, directory: String): String {
    return EelPath.parse(directory, project.getEelDescriptor()).asNioPath().toString()
  }
}
