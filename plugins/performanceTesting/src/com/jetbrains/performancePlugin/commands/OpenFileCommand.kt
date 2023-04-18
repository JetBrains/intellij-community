package com.jetbrains.performancePlugin.commands

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerListener
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent

class OpenFileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openFile"
    const val SPAN_NAME: @NonNls String = "firstCodeAnalysis"
    const val SUPPRESS_ERROR: @NonNls String = "SUPPRESS_ERROR"

    @JvmStatic
    fun findFile(filePath: String, project: Project): VirtualFile? {
      return when {
        filePath.contains(JarFileSystem.JAR_SEPARATOR) -> JarFileSystem.getInstance().findFileByPath(filePath)
        FileUtil.isAbsolute(filePath) -> LocalFileSystem.getInstance().findFileByPath(filePath)
        else -> project.guessProjectDir()?.findFileByRelativePath(filePath)
      }
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val params = text.split(' ', limit = 4)
    val filePath = params[1]
    val timeout = if (params.size > 2) params[2].toLong() else 0
    val suppressErrors = text.contains(SUPPRESS_ERROR)
    val project = context.project
    val file = findFile(filePath, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
    val connection = project.messageBus.simpleConnect()
    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val scopeRef = Ref<Scope>()
    val projectPath = project.basePath
    val job = DaemonCodeAnalyzerListener.listen(connection, spanRef, scopeRef, timeout)
    if (suppressErrors) {
      job.suppressErrors()
    }
    spanRef.set(span.startSpan())
    scopeRef.set(spanRef.get().makeCurrent())
    setFilePath(projectPath = projectPath, span = spanRef.get(), file = file)
    // focus window
    ProjectUtil.focusProjectWindow(project)
    FileEditorManagerEx.getInstanceEx(project).openFile(file = file,
                                                        options = FileEditorOpenOptions(requestFocus = true)).allEditors

    job.onError {
      spanRef.get()?.setAttribute("timeout", "true")
    }
    job.withErrorMessage("Timeout on open file ${file.path} more than $timeout seconds")
    job.waitForComplete()
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