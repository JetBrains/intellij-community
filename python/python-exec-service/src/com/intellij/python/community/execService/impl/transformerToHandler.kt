// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.processSemiInteractiveHandler
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

@ApiStatus.Internal
fun <T> transformerToHandler(
  procListener: PyProcessListener? = null,
  dataForStdIn: ByteArray? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): ProcessInteractiveHandler<T> = processSemiInteractiveHandler(procListener) { stdin, result ->
  if (dataForStdIn != null) {
    try {
      stdin.sendWholeBuffer(ByteBuffer.wrap(dataForStdIn))
      // The process waits for `EOF` on `stdin`, so it never ends until the channel is closed.
      stdin.close(null)
    }
    catch (e: EelSendChannelException) {
      // A broken channel needs no close. See EelSendChannelException.
      logger.warn("Error writing to process stdin", e)
      return@processSemiInteractiveHandler PyResult.failure(PyExecBundle.message("py.exec.stdin.error", e.localizedMessage ?: e.toString()))
    }
  }
  processOutputTransformer(result.await())
}

private val logger = fileLogger()