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
import java.util.concurrent.CompletableFuture

class OpenFileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openFile"
    const val SPAN_NAME: @NonNls String = "firstCodeAnalysis"

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
    val filePath = text.split(' ', limit = 2)[1]
    val project = context.project
    val file = findFile(filePath, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
    val job = CompletableFuture<Unit>()
    val connection = project.messageBus.simpleConnect()
    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val scopeRef = Ref<Scope>()
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
      FileEditorManager.getInstance(project).openFile(file, true)
    }
    job.join()
  }
}