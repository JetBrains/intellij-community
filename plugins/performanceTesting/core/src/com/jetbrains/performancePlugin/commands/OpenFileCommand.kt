package com.jetbrains.performancePlugin.commands

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.waitForFullyCompleted
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerListener
import com.sampullara.cli.Args
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent

/**
 * Command opens file.
 * Example: %openFile -file <filename from the root of the project> [-suppressErrors] [-timeout <in seconds>] [WARMUP] [-disableCodeAnalysis(-dsa) by default false]
 */
class OpenFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: @NonNls String = "openFile"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
    const val SPAN_NAME: @NonNls String = "firstCodeAnalysis"

    @JvmStatic
    fun findFile(filePath: String, project: Project): VirtualFile? {
      return when {
        filePath.contains(JarFileSystem.JAR_SEPARATOR) -> JarFileSystem.getInstance().findFileByPath(filePath)
        FileUtil.isAbsolute(filePath) -> LocalFileSystem.getInstance().findFileByPath(filePath)
        else -> project.getBaseDirectories().firstNotNullOfOrNull { it.findFileByRelativePath(filePath) }
      }
    }

    fun getOptions(arguments: String): OpenFileCommandOptions? {
      val myOptions = runCatching {
        OpenFileCommandOptions().apply { Args.parse(this, arguments.split(" ").toTypedArray()) }
      }.getOrNull()
      return myOptions
    }
  }
  
  override fun getName(): String = NAME

  override suspend fun doExecute(context: PlaybackContext) {
    val myOptions = getOptions(extractCommandArgument(PREFIX))
    val filePath = myOptions?.file ?: text.split(' ', limit = 4)[1]
    val timeout = myOptions?.timeout ?: 0
    val suppressErrors = myOptions?.suppressErrors ?: false

    val project = context.project
    val file = findFile(filePath, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
    val connection = project.messageBus.simpleConnect()
    val spanRef = Ref<Span>()
    val projectPath = project.basePath
    val job = DaemonCodeAnalyzerListener.listen(connection, spanRef, timeout)
    if (suppressErrors) {
      job.suppressErrors()
    }
    spanRef.set(startSpan(SPAN_NAME))
    setFilePath(projectPath = projectPath, span = spanRef.get(), file = file)

    // focus window
    withContext(Dispatchers.EDT) {
      ProjectUtil.focusProjectWindow(project, stealFocusIfAppInactive = true)
    }

    val fileEditor = (project.serviceAsync<FileEditorManager>() as FileEditorManagerEx)
      .openFile(file = file, options = FileEditorOpenOptions(requestFocus = true))
    if (myOptions != null && !myOptions.disableCodeAnalysis) {
      waitForFullyCompleted(fileEditor)
    }

    job.onError {
      spanRef.get()?.setAttribute("timeout", "true")
    }
    job.withErrorMessage("Timeout on open file ${file.path} more than $timeout seconds")

    if (myOptions != null && !myOptions.disableCodeAnalysis) {
      job.waitForComplete()
    }
  }

  private fun setFilePath(projectPath: @SystemIndependent @NonNls String?, span: Span, file: VirtualFile) {
    if (projectPath != null) {
      span.setAttribute("filePath", file.path.replaceFirst(projectPath, ""))
    }
    else {
      span.setAttribute("filePath", file.path)
    }
  }
}