package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CompletableFuture

internal class OpenFileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openFile"

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
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }

    val job = CompletableFuture<Unit>()
    val connection = project.messageBus.simpleConnect()
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonFinished() {
        try {
          connection.disconnect()
          context.message(PerformanceTestingBundle.message("command.file.opened", file.name), line)
        }
        finally {
          job.complete(Unit)
        }
      }
    })

    job.join()
  }
}