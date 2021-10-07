// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

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
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.fileActions.utils.MarkdownFileEditorUtils
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.injection.alias.LanguageGuesser
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.Icon

private const val RUN_LINE_EVENT = "runLine"
private const val RUN_BLOCK_EVENT = "runBlock"

internal class CommandRunnerExtension(val panel: MarkdownHtmlPanel) : MarkdownBrowserPreviewExtension, ResourceProvider {

  override val scripts: List<String> = listOf("commandRunner/commandRunner.js")
  override val styles: List<String> = listOf("commandRunner/commandRunner.css")
  val icons: List<String> = listOf("run.png", "runrun.png")

  private var splitEditor: MarkdownEditorWithPreview? = null

  init {
    panel.browserPipe?.subscribe(RUN_LINE_EVENT, this::runLine)
    panel.browserPipe?.subscribe(RUN_BLOCK_EVENT, this::runBlock)
    invokeLater {
      splitEditor = MarkdownFileEditorUtils.findMarkdownSplitEditor(panel.project!!, panel.virtualFile!!)
    }

    val resourceProviderRegistration = PreviewStaticServer.instance.registerResourceProvider(this)
    Disposer.register(this, resourceProviderRegistration)

    Disposer.register(this) {
      panel.browserPipe?.removeSubscription(RUN_LINE_EVENT, ::runLine)
      panel.browserPipe?.removeSubscription(RUN_BLOCK_EVENT, ::runBlock)
    }
  }

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in scripts || resourceName in icons || resourceName in styles

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    if (resourceName in icons) {
      val icon = when (resourceName) {
        "run.png" -> AllIcons.RunConfigurations.TestState.Run
        "runrun.png" -> AllIcons.RunConfigurations.TestState.Run_run
        else -> return null
      }
      val format = resourceName.substringAfterLast(".")
      return ResourceProvider.Resource(icon2Stream(icon, format))
    }
    return ResourceProvider.loadInternalResource(this::class, resourceName)
  }

  private fun icon2Stream(icon: Icon, format: String): ByteArray {
    val output = ByteArrayOutputStream()
    val fontSize = JBCefApp.normalizeScaledSize(EditorUtil.getEditorFont().size + 1).toFloat()
    //MarkdownExtension.currentProjectSettings.fontSize.toFloat()
    val scaledIcon = IconUtil.scaleByFont(icon, null, fontSize)
    val image = ImageUtil.createImage(ScaleContext.create(), scaledIcon.iconWidth.toDouble(), scaledIcon.iconHeight.toDouble(),
      BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.FLOOR)
    scaledIcon.paintIcon(null, image.graphics, 0, 0)

    //val image = IconUtil.toBufferedImage(scaledIcon, true)
    ImageIO.write(image, format, output)
    return output.toByteArray()

  }


  fun processCodeLine(rawCodeLine: String, inBlock: Boolean): String {
    val project = panel.project
    val file = panel.virtualFile
    if (project != null && file != null && previewProcessingEnabled() && matches(project, file.parent.canonicalPath, true, rawCodeLine.trim())) {
      val cssClass = "run-icon" + if (inBlock) " code-block" else ""
      return "<a class='${cssClass}' href='#' role='button' data-command='${DefaultRunExecutor.EXECUTOR_ID}:$rawCodeLine'>" +
             "<img src='${PreviewStaticServer.getStaticUrl(this,"run.png")}'>" +
             "</a>"
    }
    else return ""
  }

  fun processCodeBlock(codeFenceRawContent: String, language: String): String {
    if (!previewProcessingEnabled()) return ""
    val lang = LanguageGuesser.guessLanguageForInjection(language)
    val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull {
      it.isApplicable(lang)
    }
    if (runner == null) return ""

    val cssClass = "run-icon code-block" // todo: check possible xss
    val html = "<a class='${cssClass}' href='#' role='button' " +
               "data-command='${DefaultRunExecutor.EXECUTOR_ID}:echo BLOCK' " +
               "data-commandtype='block'" +
               ">" +
               "<img src='${PreviewStaticServer.getStaticUrl(this,"runrun.png")}'>" +
               "</a>"

    return html
  } // free resources related to md file



  // fixme: dynamic layout change
  private fun previewProcessingEnabled(): Boolean {
    return splitEditor?.layout  == TextEditorWithPreview.Layout.SHOW_PREVIEW
  }

  private fun runLine(command: String) {
    val executorId = command.substringBefore(":")
    val shellCommand = command.substringAfter(":")
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    val project = panel.project
    val virtualFile = panel.virtualFile
    if (project !=null && virtualFile != null) {
      execute(project, virtualFile.parent.canonicalPath, true, shellCommand, executor)
    }
  }

  private fun runBlock(command: String) {
    val executorId = command.substringBefore(":")
    val shellCommand = command.substringAfter(":")
    val runner = MarkdownRunner.EP_NAME.extensionList.first()
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    val project = panel.project
    val virtualFile = panel.virtualFile
    if (project !=null && virtualFile != null) {
      ApplicationManager.getApplication().invokeLater {
        runner.run(shellCommand, project, virtualFile.parent.canonicalPath, executor)
      }
    }
  }

  override fun dispose() {
    MarkdownExtensionsUtil.findBrowserExtensionProvider<Provider>()?.extensions?.remove(panel.virtualFile)
  }


  class Provider: MarkdownBrowserPreviewExtension.Provider, MarkdownConfigurableExtension {

    val extensions = mutableMapOf<VirtualFile, CommandRunnerExtension>()

    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      if (!isEnabled || panel.virtualFile == null || panel.project == null) return null

      extensions.computeIfAbsent(panel.virtualFile!!) { CommandRunnerExtension(panel) }
      return extensions[panel.virtualFile]
    }

    override val displayName: String
      get() = MarkdownBundle.message("markdown.extensions.commandrunner.display.name")

    override val description: String
      get() = MarkdownBundle.message("markdown.extensions.commandrunner.description")

    override val id: String
      get() = "CommandRunnerExtension"
  }



  companion object {

    fun getRunnerByFile(file: VirtualFile?) : CommandRunnerExtension? {
      return MarkdownExtensionsUtil.findBrowserExtensionProvider<Provider>()?.extensions?.get(file)
    }

    fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
      val dataContext = createDataContext(project, localSession, workingDirectory)
      val trimmedCmd = command.trim()
      if (trimmedCmd.isEmpty()) return false

      return RunAnythingProvider.EP_NAME.extensionList
        .asSequence()
        .filter { checkForCLI(it) }
        .any { provider -> provider.findMatchingValue(dataContext, trimmedCmd) != null }
    }

    fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executor: Executor): Boolean {
      val dataContext = createDataContext(project, localSession, workingDirectory, executor)
      val trimmedCmd = command.trim()
      return RunAnythingProvider.EP_NAME.extensionList
        .any { provider ->
          provider.findMatchingValue(dataContext, trimmedCmd)?.let { provider.execute(dataContext, it); return true } ?: false
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
