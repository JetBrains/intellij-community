// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.div

/**
 * Measure VFS update time.
 * Command creates a file with random name in the project root and waits till VFS noticed the files
 * Syntax: %measureVFSUpdate [path inside the project]
 */
class MeasureVFSUpdateCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line) {

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "measureVFSUpdate"
    const val SPAN_NAME: @NonNls String = "vfsUpdate"
  }

  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val path = extractCommandArgument(PREFIX)
    val disposer = Disposer.newDisposable()
    if (context.project.basePath == null) {
      callback.reject("Project path is empty")
      return
    }
    val projectDir = Path(context.project.basePath!!)
    val testFile = (projectDir / path / UUID.randomUUID().toString()).toFile()
    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).startSpan()
    val isFileCreated = FileUtil.createIfNotExists(testFile)
    if (!isFileCreated) {
      callback.reject("File ${testFile.path} wasn't created")
      return
    }
    VirtualFileManager.getInstance().addAsyncFileListener(
      { events ->
        events.forEach {
          if (it.path == testFile.path) {
            span.end()
            FileUtil.delete(testFile)
            callback.setDone()
          }
        }
        null
      }, disposer)
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
  }

}