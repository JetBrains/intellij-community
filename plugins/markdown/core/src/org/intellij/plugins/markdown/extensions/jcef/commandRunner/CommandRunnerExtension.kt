// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingRunConfigurationProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingRecentProjectProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AppUIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownUsageCollector.RUNNER_EXECUTED
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.PreviewClickConfirmation
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class CommandRunnerExtension(
  val panel: MarkdownHtmlPanel,
  private val provider: Provider
): MarkdownBrowserPreviewExtension {
  private val sessionKey = UUID.randomUUID().toString()
  override val scripts: List<String> = listOf("commandRunner/commandRunner.js")
  override val styles: List<String> = listOf("commandRunner/commandRunner.css")
  private val hash2Cmd = ConcurrentHashMap<String, String>()

  fun resetCommands() {
    hash2Cmd.clear()
  }

  init {
    val runLineHandler = createRunLineHandler()
    val runBlockHandler = createRunBlockHandler()
    panel.browserPipe?.subscribe(RUN_LINE_EVENT, runLineHandler)
    panel.browserPipe?.subscribe(RUN_BLOCK_EVENT, runBlockHandler)
    Disposer.register(this) {
      panel.browserPipe?.removeSubscription(RUN_LINE_EVENT, runLineHandler)
      panel.browserPipe?.removeSubscription(RUN_BLOCK_EVENT, runBlockHandler)
    }
  }

  override val resourceProvider: ResourceProvider = ResourceProvider.aggregating(
    CommandRunnerResourceProvider(),
    CommandRunnerIconsResourceProvider()
  )

  private inner class CommandRunnerResourceProvider: ResourceProvider {
    override fun canProvide(resourceName: String): Boolean {
      return resourceName in scripts || resourceName in styles
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
      return ResourceProvider.loadInternalResource<CommandRunnerResourceProvider>(resourceName)
    }
  }

  private class CommandRunnerIconsResourceProvider: ResourceProvider {
    override fun canProvide(resourceName: String): Boolean {
      return resourceName in icons
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
      val icon = when (resourceName) {
        RUN_LINE_ICON -> AllIcons.RunConfigurations.TestState.Run
        RUN_BLOCK_ICON -> AllIcons.RunConfigurations.TestState.Run_run
        else -> return null
      }
      val format = resourceName.substringAfterLast(".")
      return ResourceProvider.Resource(MarkdownExtensionsUtil.loadIcon(icon, format))
    }

    companion object {
      private val icons = setOf(RUN_LINE_ICON, RUN_BLOCK_ICON)
    }
  }

  fun processCodeLine(rawCodeLine: String, insideFence: Boolean): String {
    processLine(rawCodeLine, !insideFence)?.let { hash ->
      return getHtmlForLineRunner(insideFence, hash)
    }
    return ""
  }

  private fun processLine(rawCodeLine: String, allowRunConfigurations: Boolean): String? {
    try {
      val project = panel.project
      val file = panel.virtualFile
      if (project != null && file != null
          && PreviewCommandRunnability.getInstance().isRunnable(project, file, rawCodeLine.trim(), allowRunConfigurations)
      ) {
        val hash = MarkdownUtil.md5(rawCodeLine, sessionKey)
        hash2Cmd[hash] = rawCodeLine
        return hash
      }
      else return null
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)

      LOG.warn(e)
      return null
    }
  }

  private fun getHtmlForLineRunner(insideFence: Boolean, hash: String): String {
    val cssClass = "run-icon-line" + if (insideFence) " code-block" else ""
    return "<a class='$cssClass' href='#' role='button' data-command='${DefaultRunExecutor.EXECUTOR_ID}:$hash'>" +
           "<img src='$RUN_LINE_ICON'>" +
           "</a>"
  }

  fun processCodeBlock(codeFenceRawContent: String, language: String): String {
    try {
      val lang = CodeFenceLanguageGuesser.guessLanguageForInjection(language)
      val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull { it.isApplicable(lang) }
      if (runner == null) return ""

      val hash = MarkdownUtil.md5(codeFenceRawContent, sessionKey)
      hash2Cmd[hash] = codeFenceRawContent
      val lines = codeFenceRawContent.trimEnd().lines()
      val firstLineHash = if (lines.size > 1) processLine(lines[0], false) else null
      val firstLineData = if (firstLineHash.isNullOrBlank()) "" else "data-firstLine='$firstLineHash'"
      val cssClass = "run-icon code-block"
      return "<a class='${cssClass}' href='#' role='button' " +
             "data-command='${DefaultRunExecutor.EXECUTOR_ID}:$hash' " +
             "data-commandtype='block'" +
             firstLineData +
             ">" +
             "<img src='$RUN_BLOCK_ICON'>" +
             "</a>"
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)

      LOG.warn(e)
      return ""
    }
  }

  private fun createRunLineHandler() = object : BrowserPipe.Handler {
    override fun processMessageReceived(data: String): Boolean {
      val parts = data.split(":")
      val executorId = parts[0]
      val cmdHash: String = parts.getOrElse(1) { "" }
      val x = parts.getOrNull(2)?.toIntOrNull() ?: 0
      val y = parts.getOrNull(3)?.toIntOrNull() ?: 0
      val needsConfirmation = parts.getOrNull(4) == PreviewClickConfirmation.NEEDS_CONFIRMATION
      val command = hash2Cmd[cmdHash]
      if (command == null) {
        LOG.error("Command index not found. Please attach .md file to error report.")
        return true
      }
      runWithConfirmationIfNeeded(needsConfirmation, command) {
        executeLineCommand(command, executorId, x, y)
      }
      return false
    }
  }

  private fun executeLineCommand(command: String, executorId: String, x: Int, y: Int) {
    val project = panel.project ?: return
    val virtualFile = panel.virtualFile ?: return
    withMarkdownCommandWorkingDirectory(project, virtualFile, panel.component, x, y) { workingDirectory ->
      PreviewCommandRunnability.getInstance().execute(project, virtualFile, command, executorId, workingDirectory)
    }
  }

  private fun executeBlock(command: String, executorId: String, x: Int, y: Int) {
    val project = panel.project ?: return
    val virtualFile = panel.virtualFile ?: return
    withMarkdownCommandWorkingDirectory(project, virtualFile, panel.component, x, y) { workingDirectory ->
      launchBlockRunner(project, command, executorId, workingDirectory)
    }
  }

  private fun runWithConfirmationIfNeeded(needsConfirmation: Boolean, command: String, runAction: () -> Unit) {
    if (needsConfirmation) confirmThenRun(command, runAction) else runAction()
  }

  private fun confirmThenRun(command: String, runAction: () -> Unit) {
    ApplicationManager.getApplication().invokeLater {
      if (confirmPreviewCommandExecution(command)) {
        runAction()
      }
    }
  }

  private fun confirmPreviewCommandExecution(command: String): Boolean {
    return MessageDialogBuilder
      .yesNo(
        MarkdownBundle.message("markdown.runner.preview.confirm.title"),
        MarkdownBundle.message("markdown.runner.preview.confirm.message", command.trim())
      )
      .icon(Messages.getWarningIcon())
      .yesText(MarkdownBundle.message("markdown.runner.preview.confirm.run"))
      .noText(Messages.getCancelButton())
      .ask(panel.project)
  }

  private fun createRunBlockHandler() = object : BrowserPipe.Handler{
    override fun processMessageReceived(data: String): Boolean {
      val args = data.split(":")
      val executorId = args[0]
      val cmdHash: String = args[1]
      val command = hash2Cmd[cmdHash]
      val firstLineCommand = hash2Cmd[args[2]]
      if (command == null) {
        LOG.error("Command hash not found. Please attach .md file to error report.")
        return true
      }
      val trimmedCmd = trimPrompt(command)
      val x = args[3].toDoubleOrNull()?.toInt() ?: 0
      val y = args[4].toDoubleOrNull()?.toInt() ?: 0
      val needsConfirmation = args.getOrNull(5) == PreviewClickConfirmation.NEEDS_CONFIRMATION
      if (needsConfirmation) {
        confirmThenRun(trimmedCmd) {
          executeBlock(trimmedCmd, executorId, x, y)
        }
        return false
      }
      if (firstLineCommand == null) {
        ApplicationManager.getApplication().invokeLater {
          executeBlock(trimmedCmd, executorId, x, y)
        }
        return false
      }
      val actionManager = ActionManager.getInstance()
      val actionGroup = DefaultActionGroup()

      val runBlockAction = object : AnAction({ MarkdownBundle.message("markdown.runner.launch.block") },
                                             AllIcons.RunConfigurations.TestState.Run_run) {
        override fun actionPerformed(e: AnActionEvent) {
          ApplicationManager.getApplication().invokeLater {
            executeBlock(trimmedCmd, executorId, x, y)
          }
        }
      }
      val runLineAction = object : AnAction({ MarkdownBundle.message("markdown.runner.launch.line") },
                                            AllIcons.RunConfigurations.TestState.Run) {
        override fun actionPerformed(e: AnActionEvent) {
          ApplicationManager.getApplication().invokeLater {
            executeLineCommand(firstLineCommand, executorId, x, y)
          }
        }
      }

      actionGroup.add(runBlockAction)
      actionGroup.add(runLineAction)
      AppUIUtil.invokeOnEdt {
        actionManager.createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, actionGroup)
          .component.show(panel.component, x, y)
      }

      return false
    }
  }

  override fun dispose() {
    provider.extensions.remove(panel.virtualFile)
  }

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    val extensions = ConcurrentHashMap<VirtualFile, CommandRunnerExtension>()

    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      val virtualFile = panel.virtualFile ?: return null
      if (!isExtensionEnabled()) {
        return null
      }
      return extensions.computeIfAbsent(virtualFile) { CommandRunnerExtension(panel, this) }
    }
  }

  companion object {
    private const val RUN_LINE_EVENT = "runLine"
    private const val RUN_BLOCK_EVENT = "runBlock"
    private const val RUN_LINE_ICON = "commandRunner/run.png"
    private const val RUN_BLOCK_ICON = "commandRunner/runrun.png"

    const val extensionId = "MarkdownCommandRunnerExtension"

    fun isExtensionEnabled(): Boolean {
      return MarkdownExtensionsSettings.getInstance().extensionsEnabledState[extensionId] ?: true
    }

    fun getRunnerByFile(file: VirtualFile) : CommandRunnerExtension? {
      val provider = MarkdownExtensionsUtil.findBrowserExtensionProvider<Provider>()
      return provider?.extensions?.get(file)
    }

    @ApiStatus.Internal
    fun launchBlockRunner(project: Project, command: String, executorId: String, workingDirectory: String): Boolean {
      val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull()
      if (runner == null) {
        LOG.warn("No Markdown runner is registered, the block is not executed.")
        return false
      }
      val trusted = TrustedProjectUtil.executeIfTrusted(project) {
        startRunner(project, command, executorId, runner, workingDirectory)
      }
      if (!trusted) {
        LOG.info("Markdown block is not executed: the project is not trusted.")
      }
      return trusted
    }

    private fun startRunner(project: Project, command: String, executorId: String, runner: MarkdownRunner, workingDirectory: String) {
      val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
      LOG.info("Markdown block run: ${runner.javaClass.name} in '$workingDirectory'.")
      invokeLater {
        if (runner.run(command, project, workingDirectory, executor)) {
          RUNNER_EXECUTED.log(project, RunnerPlace.PREVIEW, RunnerType.BLOCK, runner.javaClass)
        }
        else {
          LOG.warn("Markdown block run: ${runner.javaClass.name} declined to run the command.")
        }
      }
    }

    @ApiStatus.Internal
    fun matches(project: Project, workingDirectories: List<String>, localSession: Boolean,
                command: String,
                allowRunConfigurations: Boolean = false): Boolean {
      val trimmedCmd = trimPrompt(command).trim()
      if (trimmedCmd.isEmpty()) return false

      val candidateDirectories: List<String?> = workingDirectories.ifEmpty { listOf(null) }
      return candidateDirectories.any { workingDirectory ->
        val dataContext = createDataContext(project, localSession, workingDirectory)
        ReadAction.nonBlocking<Boolean> {
          RunAnythingProvider.EP_NAME.extensionList.asSequence()
            .filter { checkForCLI(it, allowRunConfigurations) }
            .any { provider -> provider.findMatchingValue(dataContext, trimmedCmd) != null }
        }.executeSynchronously()
      }
    }

    @ApiStatus.Internal
    fun executeByExecutorId(
      project: Project,
      workingDirectory: String?,
      localSession: Boolean,
      command: String,
      executorId: String,
      place: RunnerPlace
    ): Boolean {
      val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
      return execute(project, workingDirectory, localSession, command, executor, place)
    }

    @ApiStatus.Internal
    fun execute(
      project: Project,
      workingDirectory: String?,
      localSession: Boolean,
      command: String,
      executor: Executor,
      place: RunnerPlace
    ): Boolean {
      val dataContext = createDataContext(project, localSession, workingDirectory, executor)
      val trimmedCmd = trimPrompt(command).trim()
      return runReadAction {
        for (provider in RunAnythingProvider.EP_NAME.extensionList) {
          val value = provider.findMatchingValue(dataContext, trimmedCmd) ?: continue
          return@runReadAction TrustedProjectUtil.executeIfTrusted(project) {
            RUNNER_EXECUTED.log(project, place, RunnerType.LINE, provider.javaClass)
            invokeLater {
              provider.execute(dataContext, value)
            }
          }
        }
        return@runReadAction false
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

    private fun checkForCLI(it: RunAnythingProvider<*>?, allowRunConfigurations: Boolean): Boolean {
      return (it !is RunAnythingCommandProvider
              && it !is RunAnythingRecentProjectProvider
              && (it !is RunAnythingRunConfigurationProvider || allowRunConfigurations))
    }

    private val LOG = logger<CommandRunnerExtension>()

    @ApiStatus.Internal
    fun trimPrompt(cmd: String): String {
      return cmd.lines()
        .map { line ->
          val withoutPrompt = if (line.startsWith("$")) line.substringAfter("$") else line
          stripTrailingShellComment(withoutPrompt)
        }
        .filter { line -> line.isNotEmpty() }
        .joinToString("\n")
    }

    private fun stripTrailingShellComment(line: String): String {
      var inSingle = false
      var inDouble = false
      for (i in line.indices) {
        when (line[i]) {
          '\'' -> if (!inDouble) inSingle = !inSingle
          '"' -> if (!inSingle) inDouble = !inDouble
          '#' -> if (!inSingle && !inDouble && (i == 0 || line[i - 1].isWhitespace())) {
            return line.substring(0, i).trimEnd()
          }
        }
      }
      return line
    }
  }
}

enum class RunnerPlace {
  EDITOR, PREVIEW
}

enum class RunnerType {
  BLOCK, LINE
}

@ApiStatus.Internal
fun getMarkdownCommandWorkingDirectory(project: Project, virtualFile: VirtualFile?): String? {
  val fileDirectory = virtualFile?.parent?.canonicalPath ?: return null
  val projectDirectory = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(virtualFile)?.canonicalPath ?: fileDirectory
  return when (MarkdownSettings.getInstance(project).useFileDirectoryForCommands) {
    true -> fileDirectory
    false -> projectDirectory
    null -> null
  }
}

@ApiStatus.Internal
fun getMarkdownCommandWorkingDirectories(project: Project, virtualFile: VirtualFile?): List<String> {
  // Not `virtualFile.parent`: it is null for the previewed file on the JetBrains Client, and an empty list here means
  // no line or span is ever offered a run icon (IJPL-250078).
  if (virtualFile == null) return emptyList()
  val fileDirectory = markdownCommandFileDirectory(virtualFile) ?: return emptyList()
  val projectDirectory = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(virtualFile)?.canonicalPath ?: fileDirectory
  return when (MarkdownSettings.getInstance(project).useFileDirectoryForCommands) {
    true -> listOf(fileDirectory)
    false -> listOf(projectDirectory)
    null -> listOf(projectDirectory, fileDirectory).distinct()
  }
}
