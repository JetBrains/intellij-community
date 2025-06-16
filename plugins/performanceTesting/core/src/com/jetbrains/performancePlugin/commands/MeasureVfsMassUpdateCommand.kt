// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.appendText
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * Measure VFS update time.
 * Command creates the given number of files with different extensions and measures how much each refresh takes
 * Syntax: %measureVfsMassUpdate ACTION [fileExtension] [numberOfFiles]
 *  ACTION:
 *   - CREATE fileExtension numberOfFiles
 *   - MODIFY
 *   - DELETE
 *   - REFRESH MassVfsRefreshSpan.CREATE|MODIFY|DELETE
 *   - REFRESH CREATE|MODIFY|DELETE
 * Example: @measureVfsMassUpdate CREATE java 200000
 * Example: @measureVfsMassUpdate REFRESH MassVfsRefreshSpan.CREATE
 */
@Suppress("KDocUnresolvedReference")
class MeasureVfsMassUpdateCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "measureVfsMassUpdate"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val pathForFiles = Path(context.project.basePath!! + "/src")

    val arguments = extractCommandArgument(PREFIX).split(" ")
    val disposer = Disposer.newDisposable()

    if (arguments.isEmpty()) throw IllegalArgumentException("The action should be either CREATE or MODIFY or DELETE or REFRESH")
    val action = arguments[0]

    if (action == "CREATE") {
      if (arguments.size < 3) throw IllegalArgumentException("CREATE takes two parameters, a file extension and a number of files")
      val extension = arguments[1]
      val numberOfFiles = arguments[2].toIntOrNull() ?: throw IllegalArgumentException("Third parameter must be a valid number")

      PerformanceTestSpan.TRACER.spanBuilder("massCreateFiles").use {
        createFiles(extension, numberOfFiles, pathForFiles)
      }
      return

    } else if (action == "MODIFY") {
      ensureCreateHasBeenRun(pathForFiles)
      PerformanceTestSpan.TRACER.spanBuilder("massModifyFiles").use {
        modifyFilesContent(pathForFiles)
      }

    } else if (action == "DELETE") {
      ensureCreateHasBeenRun(pathForFiles)
      PerformanceTestSpan.TRACER.spanBuilder("massDeleteFiles").use {
        deleteFiles(pathForFiles)
      }
      return

    } else if (action == "REFRESH") {
      if (arguments.size < 2) throw IllegalArgumentException("REFRESH takes MassVfsRefreshSpan.spanName as a parameter")

      val span = PerformanceTestSpan.TRACER.spanBuilder(arguments[1]).startSpan()
      val mutex = Mutex(true)

      VirtualFileManager.getInstance().addAsyncFileListener(
        { events ->
          object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
              try {
                span.end()
                mutex.unlock()
              } finally {
                Disposer.dispose(disposer)
              }
            }
          }
        }, disposer)

      VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
      mutex.lock()

    } else throw IllegalArgumentException("The action should be either CREATE or MODIFY or DELETE or REFRESH")
  }

  private fun createFiles(extension: String, numberOfFiles: Int, projectPath: Path) {
    val filesPerFolder = 1000
    val foldersPerParent = 100
    for (i in 0 until numberOfFiles) {
      val currentFolderNum = (i / filesPerFolder) % foldersPerParent
      val parentFolderNum = (i / (filesPerFolder * foldersPerParent))

      val classSuffix = i.toString().padStart(numberOfFiles.toString().length, '0')
      val folderPath = projectPath
        .resolve("TempFolderParent$parentFolderNum")
        .resolve("TempFolderChild$currentFolderNum")

      Files.createDirectories(folderPath)

      (folderPath / "TempClass$classSuffix.$extension").writeText(
        "class TempClass$classSuffix {}\n",
        options = arrayOf(StandardOpenOption.APPEND, StandardOpenOption.CREATE)
      )
    }
  }

  private fun modifyFilesContent(pathForFiles: Path) {
    Files.walk(pathForFiles)
      .filter { !Files.isDirectory(it) && it.fileName.toString().startsWith("TempClass") }
      .forEach { file ->
        file.appendText("\n// Modified")
      }
  }

  private fun deleteFiles(path: Path) {
    Files.list(path)
      .filter { it.fileName.toString().startsWith("TempFolder") }
      .forEach { FileUtil.delete(it) }
  }
}

private fun ensureCreateHasBeenRun(pathForFiles: Path) {
  if (Files.walk(pathForFiles).filter { Files.isDirectory(it) }.noneMatch { it.fileName.toString().startsWith("TempFolder") }) {
    throw IllegalStateException("No TempFolder directories found. Have you run the CREATE action first?")
  }
}
