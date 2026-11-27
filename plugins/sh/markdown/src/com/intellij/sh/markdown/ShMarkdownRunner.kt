// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.markdown

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.sh.ShBundle
import com.intellij.sh.ShLanguage
import com.intellij.sh.run.ShCommandTitleUtil
import com.intellij.sh.run.ShSelectedTerminalRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCodeFenceRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.service.runMarkdownCommandIfTrusted

internal class ShMarkdownRunner : MarkdownCodeFenceRunner {
  override fun isApplicable(language: Language?): Boolean =
    language != null && (language.`is`(ShLanguage.INSTANCE) || language.id == "PowerShell")

  override fun run(command: String, project: Project, context: MarkdownRunnerContext): Boolean {
    val selectedTerminalRunner = project.getService(ShSelectedTerminalRunner::class.java) ?: return false
    return runMarkdownCommandIfTrusted(project) {
      val title = ShCommandTitleUtil.getTitle(command)
      val sourceFileUrl = context.sourceFileUrl
      val shell = context.fenceLanguage?.trim()?.takeWhile { !it.isWhitespace() }?.lowercase()
        ?.takeIf { it == "bash" || it == "sh" || it == "zsh" }
      val runInNewTerminal = Runnable {
        context.withWorkingDirectory(project) { directory ->
          selectedTerminalRunner.createNewTerminal(
            project, command, toTerminalWorkingDirectory(project, directory), title, sourceFileUrl, shell,
          )
        }
      }
      if ((context.showTargetChooser || selectedTerminalRunner.hasTerminalStartedFrom(project, sourceFileUrl)) &&
          selectedTerminalRunner.showChooser(
            project, command, title, context.component, context.x, context.y, runInNewTerminal, shell,
          )) {
        return@runMarkdownCommandIfTrusted true
      }
      if (!selectedTerminalRunner.run(project, command, title, runInNewTerminal, shell)) {
        runInNewTerminal.run()
      }
      true
    }
  }

  override fun title(): String = ShBundle.message("sh.markdown.runner.title")

  private fun toTerminalWorkingDirectory(project: Project, directory: String): String {
    return EelPathUtils.expandUserHome(project.getEelDescriptor(), directory).toString()
  }
}
