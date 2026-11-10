// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.recordStartupSpan
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val LOG = logger<WaitForReOpenedFileCommand>()

/**
 * System property with the absolute path of the file [WaitForReOpenedFileCommand] waits for. The IDE records the opening of this file only.
 * The path is absolute because the file opens before the project may know its base directories.
 */
const val REOPENED_FILE_PROPERTY: @NonNls String = "performance.testing.reopened.file"

/**
 * [System.nanoTime] when the first opening of the file named by [REOPENED_FILE_PROPERTY] began,
 * `null` until then and in an IDE started without the property.
 */
@VisibleForTesting
internal val reopenedFileOpenedAt: MutableStateFlow<Long?> = MutableStateFlow(null)

/**
 * Records the span `reopenFileAfterIdeRestart` from the moment the given file starts to open after the IDE starts to the moment the code
 * analysis finished, also when it finished before this command waits. After a timeout, or when no analysis finished after the opening,
 * it records no span.
 */
open class WaitForReOpenedFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "waitForReOpenedFile"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val myOptions = OpenFileCommand.getOptions(extractCommandArgument(PREFIX))
    val filePath = myOptions!!.file.replace("SPACE_SYMBOL", " ")
    val expectedFile = OpenFileCommand.findFile(filePath, context.project)
                       ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
    check(FileUtil.pathsEqual(expectedFile.path, System.getProperty(REOPENED_FILE_PROPERTY))) {
      "Start the IDE with -D$REOPENED_FILE_PROPERTY=${expectedFile.path}, otherwise the opening of the file is not recorded"
    }
    val timeout = if (myOptions.timeout == 0L) 5.minutes else myOptions.timeout.seconds
    val deadline = TimeSource.Monotonic.markNow() + timeout
    val openedAt = withTimeoutOrNull(timeout) { reopenedFileOpenedAt.filterNotNull().first() }
                   ?: error("${expectedFile.path} did not reopen within $timeout")
    val remaining = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
    val analysisFinishedAt = context.project.service<CodeAnalysisStateListener>().waitAnalysisToFinish(remaining)
    if (analysisFinishedAt == null || analysisFinishedAt - openedAt < 0) {
      LOG.warn("No code analysis finished after ${expectedFile.path} reopened, so no reopenFileAfterIdeRestart span is recorded")
      return
    }
    recordStartupSpan("reopenFileAfterIdeRestart", openedAt, analysisFinishedAt) { it.setNoParent() }
  }

  override fun getName(): String = NAME

}

internal class BeforeFileOpenLoggerListener : FileEditorManagerListener.Before {
  override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
    logger<BeforeFileOpenLoggerListener>().info("beforeFileOpened ${file.name}")
    val openedAt = System.nanoTime()
    if (reopenedFileOpenedAt.value != null) return
    val reopenedFilePath = System.getProperty(REOPENED_FILE_PROPERTY) ?: return
    if (FileUtil.pathsEqual(file.path, reopenedFilePath)) {
      reopenedFileOpenedAt.compareAndSet(null, openedAt)
    }
  }
}

internal class FileOpenLoggerListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    logger<BeforeFileOpenLoggerListener>().info("fileOpened ${file.name}")
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    logger<BeforeFileOpenLoggerListener>().info("fileClosed ${file.name}")
  }

}
