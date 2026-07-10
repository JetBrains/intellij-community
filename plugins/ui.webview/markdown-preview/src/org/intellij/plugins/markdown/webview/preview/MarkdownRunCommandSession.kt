// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.TrustedProjectUtil
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.getMarkdownCommandWorkingDirectory
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser

internal class MarkdownRunCommandSession private constructor(
  private val project: Project?,
  private val workingDirectory: String?,
  private val commandsById: Map<String, MarkdownRunCommand>,
) {
  val descriptors: List<MarkdownCommandDescriptor> = commandsById.values.map { it.descriptor }

  fun command(id: String): MarkdownRunCommand? {
    return commandsById[id]
  }

  fun lineCommand(id: String?): MarkdownRunCommand.Line? {
    if (id == null) return null
    return commandsById[id] as? MarkdownRunCommand.Line
  }

  fun executeLine(
    command: MarkdownRunCommand.LineCommand,
    place: RunnerPlace,
    executor: Executor = DefaultRunExecutor.getRunExecutorInstance(),
  ): Boolean {
    val project = project ?: return false
    return CommandRunnerExtension.execute(project, workingDirectory, true, command.rawCommand, executor, place)
  }

  fun executeBlock(
    command: MarkdownRunCommand.Block,
    executor: Executor = DefaultRunExecutor.getRunExecutorInstance(),
  ): Boolean {
    val project = project ?: return false
    return TrustedProjectUtil.executeIfTrusted(project) {
      command.runner.run(CommandRunnerExtension.trimPrompt(command.rawCommand), project, workingDirectory, executor)
    }
  }

  companion object {
    val EMPTY: MarkdownRunCommandSession = MarkdownRunCommandSession(null, null, emptyMap())

    fun resolve(project: Project?, virtualFile: VirtualFile?, candidates: List<MarkdownCommandCandidate>): MarkdownRunCommandSession {
      if (project == null || !CommandRunnerExtension.isExtensionEnabled()) return EMPTY
      return Resolver(project, virtualFile, candidates).resolve()
    }
  }

  private class Resolver(
    private val project: Project,
    virtualFile: VirtualFile?,
    private val candidates: List<MarkdownCommandCandidate>,
  ) {
    private val workingDirectory = virtualFile?.let { getMarkdownCommandWorkingDirectory(project, it) }
    private val commands = LinkedHashMap<String, MarkdownRunCommand>()
    private val lineCommands = LinkedHashMap<String, MarkdownRunCommand.Line>()

    fun resolve(): MarkdownRunCommandSession {
      for (candidate in candidates) {
        when (candidate.kind) {
          MarkdownPreviewCommandKind.LINE -> resolveLine(candidate, allowRunConfigurations = false)?.let {
            lineCommands[it.descriptor.id] = it
          }
          MarkdownPreviewCommandKind.INLINE -> resolveLine(candidate, allowRunConfigurations = true)?.let {
            lineCommands[it.descriptor.id] = it
          }
          MarkdownPreviewCommandKind.BLOCK -> {}
        }
      }

      for (candidate in candidates) {
        val command = when (candidate.kind) {
          MarkdownPreviewCommandKind.LINE,
          MarkdownPreviewCommandKind.INLINE -> lineCommands[candidate.id]
          MarkdownPreviewCommandKind.BLOCK -> resolveBlock(candidate)
        } ?: continue
        commands[command.descriptor.id] = command
      }
      return MarkdownRunCommandSession(project, workingDirectory, commands)
    }

    private fun resolveLine(candidate: MarkdownCommandCandidate, allowRunConfigurations: Boolean): MarkdownRunCommand.Line? {
      val command = candidate.rawCommand.trim()
      if (!CommandRunnerExtension.matches(project, workingDirectory, true, command, allowRunConfigurations)) return null
      val title = MarkdownBundle.message("markdown.runner.launch.command", command)
      return MarkdownRunCommand.Line(
        descriptor = candidate.toDescriptor(title),
        command = MarkdownRunCommand.LineCommand(
          rawCommand = candidate.rawCommand,
          command = command,
          title = title,
        ),
      )
    }

    private fun resolveBlock(candidate: MarkdownCommandCandidate): MarkdownRunCommand.Block? {
      val runner = candidate.language?.let(::findBlockRunner) ?: return null
      return MarkdownRunCommand.Block(
        descriptor = candidate.toDescriptor(
          title = runner.title(),
          firstLineCommandId = candidate.firstLineCommandId?.takeIf { lineCommands[it] != null },
        ),
        rawCommand = candidate.rawCommand,
        runner = runner,
      )
    }

    private fun findBlockRunner(language: String): MarkdownRunner? {
      val lang = CodeFenceLanguageGuesser.guessLanguageForInjection(language)
      return MarkdownRunner.EP_NAME.extensionList.firstOrNull { it.isApplicable(lang) }
    }
  }
}

internal sealed interface MarkdownRunCommand {
  val descriptor: MarkdownCommandDescriptor

  data class Block(
    override val descriptor: MarkdownCommandDescriptor,
    val rawCommand: String,
    val runner: MarkdownRunner,
  ) : MarkdownRunCommand

  data class Line(
    override val descriptor: MarkdownCommandDescriptor,
    val command: LineCommand,
  ) : MarkdownRunCommand

  data class LineCommand(
    val rawCommand: String,
    val command: String,
    val title: String,
  )
}

private fun MarkdownCommandCandidate.toDescriptor(title: String, firstLineCommandId: String? = null): MarkdownCommandDescriptor {
  return MarkdownCommandDescriptor(
    id = id,
    kind = kind,
    startLine = startLine,
    startColumn = startColumn,
    endLine = endLine,
    endColumn = endColumn,
    title = title,
    firstLineCommandId = firstLineCommandId,
  )
}
