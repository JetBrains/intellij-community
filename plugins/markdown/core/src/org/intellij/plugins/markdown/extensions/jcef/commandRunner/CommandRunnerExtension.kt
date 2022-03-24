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
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AppUIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownUsageCollector.Companion.RUNNER_EXECUTED
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.fileActions.utils.MarkdownFileEditorUtils
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.util.concurrent.ConcurrentHashMap

internal class CommandRunnerExtension(val panel: MarkdownHtmlPanel,
                                      private val provider: Provider)
  : MarkdownBrowserPreviewExtension, ResourceProvider, MarkdownEditorWithPreview.SplitLayoutListener {

  override val scripts: List<String> = listOf("commandRunner/commandRunner.js")
  override val styles: List<String> = listOf("commandRunner/commandRunner.css")
  private val hash2Cmd = mutableMapOf<String, String>()
  private var splitEditor: MarkdownEditorWithPreview? = null

  init {
    panel.browserPipe?.subscribe(RUN_LINE_EVENT, this::runLine)
    panel.browserPipe?.subscribe(RUN_BLOCK_EVENT, this::runBlock)
    panel.browserPipe?.subscribe(PAGE_READY_EVENT, this::onPageReady)
    invokeLater {
      MarkdownFileEditorUtils.findMarkdownSplitEditor(panel.project!!, panel.virtualFile!!)?.let {
        splitEditor = it
        it.addLayoutListener(this)
      }
    }

    Disposer.register(this) {
      panel.browserPipe?.removeSubscription(RUN_LINE_EVENT, ::runLine)
      panel.browserPipe?.removeSubscription(RUN_BLOCK_EVENT, ::runBlock)
      splitEditor?.removeLayoutListener(this)
    }
  }

  override fun onLayoutChange(oldLayout: TextEditorWithPreview.Layout?, newLayout: TextEditorWithPreview.Layout) {
    panel.browserPipe?.send(LAYOUT_CHANGE_EVENT, newLayout.name)
  }

  private fun onPageReady(ready: String) {
    splitEditor?.let {
      panel.browserPipe?.send(LAYOUT_CHANGE_EVENT, it.layout.name)
    }
  }


  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in scripts || resourceName in styles

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return ResourceProvider.loadInternalResource(this::class, resourceName)
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
      if (project != null && file != null && file.parent != null
          && matches(project, file.parent.canonicalPath, true, rawCodeLine.trim(), allowRunConfigurations)
      ) {
        val hash = MarkdownUtil.md5(rawCodeLine, "")
        hash2Cmd[hash] = rawCodeLine
        return hash
      }
      else return null
    }
    catch (e: Exception) {
      LOG.warn(e)
      return null
    }
  }

  private fun getHtmlForLineRunner(insideFence: Boolean, hash: String): String {
    val cssClass = "run-icon hidden" + if (insideFence) " code-block" else ""
    return "<a class='${cssClass}' href='#' role='button' data-command='${DefaultRunExecutor.EXECUTOR_ID}:$hash'>" +
           "<img src='${PreviewStaticServer.getStaticUrl(provider, RUN_LINE_ICON)}'>" +
           "</a>"
  }

  fun processCodeBlock(codeFenceRawContent: String, language: String): String {
    try {
      val lang = CodeFenceLanguageGuesser.guessLanguageForInjection(language)
      val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull {
        it.isApplicable(lang)
      }
      if (runner == null) return ""

      val hash = MarkdownUtil.md5(codeFenceRawContent, "")
      hash2Cmd[hash] = codeFenceRawContent
      val lines = codeFenceRawContent.trimEnd().lines()
      val firstLineHash = if (lines.size > 1) processLine(lines[0], false) else null
      val firstLineData = if (firstLineHash.isNullOrBlank()) "" else "data-firstLine='$firstLineHash'"
      val cssClass = "run-icon hidden code-block"
      return "<a class='${cssClass}' href='#' role='button' " +
             "data-command='${DefaultRunExecutor.EXECUTOR_ID}:$hash' " +
             "data-commandtype='block'" +
             firstLineData +
             ">" +
             "<img src='${PreviewStaticServer.getStaticUrl(provider, RUN_BLOCK_ICON)}'>" +
             "</a>"
    }
    catch (e: Exception) {
      LOG.warn(e)
      return ""
    }
  }


  private fun runLine(encodedLine: String) {
    val executorId = encodedLine.substringBefore(":")
    val cmdHash: String = encodedLine.substringAfter(":")
    val command = hash2Cmd[cmdHash]
    if (command == null) {
      LOG.error("Command index $cmdHash not found. Please attach .md file to error report. commandCache = ${hash2Cmd}")
      return
    }
    executeLineCommand(command, executorId)
  }

  private fun executeLineCommand(command: String, executorId: String) {
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    val project = panel.project
    val virtualFile = panel.virtualFile
    if (project != null && virtualFile != null) {
      execute(project, virtualFile.parent.canonicalPath, true, command, executor, RunnerPlace.PREVIEW)
    }
  }

  private fun executeBlock(command: String, executorId: String) {
    val runner = MarkdownRunner.EP_NAME.extensionList.first()
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    val project = panel.project
    val virtualFile = panel.virtualFile
    if (project != null && virtualFile != null) {
      TrustedProjectUtil.executeIfTrusted(project) {
        RUNNER_EXECUTED.log(project,  RunnerPlace.PREVIEW, RunnerType.BLOCK, runner.javaClass)
        runner.run(command, project, virtualFile.parent.canonicalPath, executor)
      }
    }
  }

  private fun runBlock(encodedLine: String) {
    val args = encodedLine.split(":")
    val executorId = args[0]
    val cmdHash: String = args[1]
    val command = hash2Cmd[cmdHash]
    val firstLineCommand = hash2Cmd[args[2]]
    if (command == null) {
      LOG.error("Command hash $cmdHash not found. Please attach .md file to error report.\n${hash2Cmd}")
      return
    }
    val trimmedCmd = trimPrompt(command)
    if (firstLineCommand == null) {
      ApplicationManager.getApplication().invokeLater {
        executeBlock(trimmedCmd, executorId)
      }
      return
    }
    val x = args[3].toInt()
    val y = args[4].toInt()

    val actionManager = ActionManager.getInstance()
    val actionGroup = DefaultActionGroup()

    val runBlockAction = object : AnAction({ MarkdownBundle.message("markdown.runner.launch.block") },
                                           AllIcons.RunConfigurations.TestState.Run_run) {
      override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
          executeBlock(trimmedCmd, executorId)
        }
      }
    }
    val runLineAction = object : AnAction({ MarkdownBundle.message("markdown.runner.launch.line") },
                                           AllIcons.RunConfigurations.TestState.Run) {
      override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
          executeLineCommand(firstLineCommand, executorId)
        }
      }
    }

    actionGroup.add(runBlockAction)
    actionGroup.add(runLineAction)
    AppUIUtil.invokeOnEdt {
      actionManager.createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, actionGroup)
        .component.show(panel.component, x, y)
    }
  }

  override fun dispose() {
    provider.extensions.remove(panel.virtualFile)
  }


  class Provider: MarkdownBrowserPreviewExtension.Provider, ResourceProvider {
    val extensions = ConcurrentHashMap<VirtualFile, CommandRunnerExtension>()

    init {
      PreviewStaticServer.instance.registerResourceProvider(this)
    }

    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      val virtualFile = panel.virtualFile ?: return null
      if (!isExtensionEnabled()) {
        return null
      }
      return extensions.computeIfAbsent(virtualFile) { CommandRunnerExtension(panel, this) }
    }

    val icons: List<String> = listOf(RUN_LINE_ICON, RUN_BLOCK_ICON)

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
  }



  companion object {
    private const val RUN_LINE_EVENT = "runLine"
    private const val RUN_BLOCK_EVENT = "runBlock"
    private const val PAGE_READY_EVENT = "pageReady"
    private const val LAYOUT_CHANGE_EVENT = "layoutChange"
    private const val RUN_LINE_ICON = "run.png"
    private const val RUN_BLOCK_ICON = "runrun.png"

    const val extensionId = "MarkdownCommandRunnerExtension"

    fun isExtensionEnabled(): Boolean {
      return MarkdownExtensionsSettings.getInstance().extensionsEnabledState[extensionId] ?: true
    }

    fun getRunnerByFile(file: VirtualFile) : CommandRunnerExtension? {
      val provider = MarkdownExtensionsUtil.findBrowserExtensionProvider<Provider>()
      return provider?.extensions?.get(file)
    }

    fun matches(project: Project, workingDirectory: String?, localSession: Boolean,
                command: String,
                allowRunConfigurations: Boolean = false): Boolean {
      val trimmedCmd = command.trim()
      if (trimmedCmd.isEmpty()) return false
      val dataContext = createDataContext(project, localSession, workingDirectory)

      return runReadAction {
        RunAnythingProvider.EP_NAME.extensionList
          .asSequence()
          .filter { checkForCLI(it, allowRunConfigurations) }
          .any { provider -> provider.findMatchingValue(dataContext, trimmedCmd) != null }
      }
    }

    fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executor: Executor, place: RunnerPlace): Boolean {
      val dataContext = createDataContext(project, localSession, workingDirectory, executor)
      val trimmedCmd = command.trim()
      for (provider in RunAnythingProvider.EP_NAME.extensionList) {
        val value = provider.findMatchingValue(dataContext, trimmedCmd) ?: continue
        return TrustedProjectUtil.executeIfTrusted(project) {
          RUNNER_EXECUTED.log(project, place, RunnerType.LINE, provider.javaClass)
          provider.execute(dataContext, value)
        }
      }
      return false
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

    internal fun trimPrompt(cmd: String): String {
      return cmd.lines()
        .filter { line -> line.isNotEmpty() }
        .joinToString("\n") { line ->
          if (line.startsWith("$")) line.substringAfter("$") else line
        }
    }
  }
}

enum class RunnerPlace {
  EDITOR, PREVIEW
}

enum class RunnerType {
  BLOCK, LINE
}