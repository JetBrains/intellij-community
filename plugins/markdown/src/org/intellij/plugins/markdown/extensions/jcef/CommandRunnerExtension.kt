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
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.Icon

internal class CommandRunnerExtension : MarkdownCodeViewExtension, MarkdownConfigurableExtension, ResourceProvider {

  override val scripts: List<String> = listOf("commandRunner/commandRunner.js")

  override val styles: List<String> = listOf("commandRunner/commandRunner.css")

  val icons: List<String> = listOf("run.png", "runrun.png")

  override val codeEvents: Map<String, (String, Project, VirtualFile) -> Unit> = mapOf("runCommand" to this::runCommand)

  override val resourceProvider: ResourceProvider = this

  override val displayName: String
    get() = MarkdownBundle.message("markdown.extensions.commandrunner.display.name")

  override val description: String
    get() = MarkdownBundle.message("markdown.extensions.commandrunner.description")

  override val id: String
    get() = "CommandRunnerExtension"

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


  override fun processCodeLine(escapedCodeLine: String, project: Project?, file: VirtualFile?, inBlock: Boolean): String {
    if (project != null && file != null && previewProcessingEnabled() && matches(project, file.parent.canonicalPath, true, escapedCodeLine.trim())) {
      val cssClass = "run-icon" + if (inBlock) " code-block" else ""
      return "<a class='${cssClass}' href='#' role='button' data-command='${DefaultRunExecutor.EXECUTOR_ID}:$escapedCodeLine'>" +
             "<img src='${PreviewStaticServer.getStaticUrl("run.png")}'>" +
             "</a>" +
             escapedCodeLine
    }
    else return escapedCodeLine
  }

  // todo: need access to MarkdownEditorWithPreview
  private fun previewProcessingEnabled(): Boolean {
    return isEnabled /*&& MarkdownExtension.currentProjectSettings.splitLayout == TextEditorWithPreview.Layout.SHOW_PREVIEW*/
  }

  private fun runCommand(command: String, project: Project, file: VirtualFile) {
    val executorId = command.substringBefore(":")
    val shellCommand = command.substringAfter(":")
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: DefaultRunExecutor.getRunExecutorInstance()
    execute(project, file.parent.canonicalPath, true, shellCommand, executor)
  }


  companion object {

    fun commandRunnerEnabled() : Boolean {
      return MarkdownCodeViewExtension.allSorted.any {
        it is CommandRunnerExtension && it.isEnabled
      }
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
