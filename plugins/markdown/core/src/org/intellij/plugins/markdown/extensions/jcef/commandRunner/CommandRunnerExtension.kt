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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.fileActions.utils.MarkdownFileEditorUtils
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.settings.MarkdownSettings
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
    try {
      val project = panel.project
      val file = panel.virtualFile
      if (project != null && file != null && file.parent != null
          && matches(project, file.parent.canonicalPath, true, rawCodeLine.trim(), allowRunConfigurations = !insideFence)
      ) {
        val hash = MarkdownUtil.md5(rawCodeLine, "")
        hash2Cmd[hash] = rawCodeLine
        val cssClass = "run-icon hidden" + if (insideFence) " code-block" else ""
        return "<a class='${cssClass}' href='#' role='button' data-command='${DefaultRunExecutor.EXECUTOR_ID}:$hash'>" +
               "<img src='${PreviewStaticServer.getStaticUrl(provider, RUN_LINE_ICON)}'>" +
               "</a>"
      }
      else return ""
    }
    catch (e: Exception) {
      LOG.warn(e)
      return ""
    }
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
      val cssClass = "run-icon hidden code-block"

      return "<a class='${cssClass}' href='#' role='button' " +
             "data-command='${DefaultRunExecutor.EXECUTOR_ID}:$hash' " +
             "data-commandtype='block'" +
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
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    val project = panel.project
    val virtualFile = panel.virtualFile
    if (project !=null && virtualFile != null) {
      execute(project, virtualFile.parent.canonicalPath, true, command, executor)
    }
  }

  private fun runBlock(encodedLine: String) {
    val executorId = encodedLine.substringBefore(":")
    val cmdHash: String = encodedLine.substringAfter(":")
    val command = hash2Cmd[cmdHash]
    if (command == null) {
      LOG.error("Command hash $cmdHash not found. Please attach .md file to error report.\n${hash2Cmd}")
      return
    }
    val runner = MarkdownRunner.EP_NAME.extensionList.first()
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    val project = panel.project
    val virtualFile = panel.virtualFile
    if (project != null && virtualFile != null) {
      ApplicationManager.getApplication().invokeLater {
        TrustedProjectUtil.executeIfTrusted(project) {
          runner.run(command, project, virtualFile.parent.canonicalPath, executor)
        }
      }
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
      if (panel.project?.let(MarkdownSettings::getInstance)?.isRunnerEnabled == false) {
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

      return RunAnythingProvider.EP_NAME.extensionList
        .asSequence()
        .filter { checkForCLI(it, allowRunConfigurations) }
        .any { provider -> provider.findMatchingValue(dataContext, trimmedCmd) != null }
    }

    fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executor: Executor): Boolean {
      val dataContext = createDataContext(project, localSession, workingDirectory, executor)
      val trimmedCmd = command.trim()
      for (provider in RunAnythingProvider.EP_NAME.extensionList) {
        val value = provider.findMatchingValue(dataContext, trimmedCmd) ?: continue
        return TrustedProjectUtil.executeIfTrusted(project) {
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
  }
}
