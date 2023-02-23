package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
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
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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
    val job = CompletableFuture<Unit>()
    val connection = project.messageBus.simpleConnect()
    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val scopeRef = Ref<Scope>()
    val projectPath = project.basePath
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonFinished() {
        try {
          connection.disconnect()
          context.message(PerformanceTestingBundle.message("command.file.opened", file.name), line)
        }
        finally {
          spanRef.get()?.end()
          scopeRef.get()?.close()
          job.complete(Unit)
        }
      }
    })
    withContext(Dispatchers.EDT) {
      spanRef.set(span.startSpan())
      scopeRef.set(spanRef.get().makeCurrent())
      setFilePath(projectPath, spanRef, file)
      FileEditorManager.getInstance(project).openFile(file, true)
    }
    try {
      if (timeout > 0) {
        job.orTimeout(timeout, TimeUnit.SECONDS)
      }
      job.join()
    } catch (e: Exception) {
      spanRef.get()?.setAttribute("timeout", "true")
      spanRef.get()?.end()
      scopeRef.get()?.close()
      if (!suppressErrors)
        throw IllegalStateException("Timeout on open file ${file.path} more than $timeout seconds", e)
    }
  }

  private fun setFilePath(projectPath: @SystemIndependent @NonNls String?,
                        spanRef: Ref<Span>,
                        file: VirtualFile) {
    if (projectPath != null) {
      spanRef.get().setAttribute("filePath", file.path.replaceFirst(projectPath, ""))
    }
    else {
      spanRef.get().setAttribute("filePath", file.path)
    }
  }
}