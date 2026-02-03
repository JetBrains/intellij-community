// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.random.Random

class CorruptIndexesCommand(text: String, line: Int) : AbstractCommand(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "corruptIndex"
    private val LOG = logger<CorruptIndexesCommand>()
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val args = text.split(" ".toRegex()).toTypedArray()
    val path = when (args.size) {
      2 -> Paths.get(args[1])
      3 -> Paths.get(args[1], args[2])
      else -> error("There are more than 2 args")
    }
    if (path.toFile().isFile) {
      LOG.info("Corrupting file: $path")
      corruptFile(path.toFile())
    }
    else {
      LOG.info("Corrupting files in dir: $path")
      corruptFilesInDir(path)
    }
    actionCallback.setDone()
    return actionCallback.toPromise()
  }

  private fun corruptFilesInDir(dir: Path) {
    val listOfFiles = dir.toFile().walkTopDown().filter { it.isFile }
    listOfFiles.forEach {
      corruptFile(it)
    }
  }

  private fun corruptFile(file: File) {
    LOG.info("Corrupting file: ${file.absolutePath}")
    file.writeBytes(Random(42).nextBytes(100 * 1024 * 1024))
  }
}