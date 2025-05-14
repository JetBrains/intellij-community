// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.process.*
import com.intellij.openapi.util.Key
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.coroutines.CoroutineContext

/**
 * Wraps process handler and provides three coroutine channels for [stdErr], [stdOut] and error code: [result]
 *
 * You could either start [ProcessHandler.startNotify] manually, use constructor that wraps process or use [runProcessAndGetError].
 */
internal class ProcessHandlerReader(processHandler: ProcessHandler) {
  private val stdOutImpl: Channel<String> = Channel()
  private val stdErrImpl: Channel<String> = Channel()
  private val processResult: Channel<Int> = Channel()
  private val scope = CoroutineScope(Dispatchers.IO)
  private val stdOut: ReceiveChannel<String> = stdOutImpl
  private val stdErr: ReceiveChannel<String> = stdErrImpl
  val result: ReceiveChannel<Int> = processResult

  constructor(process: Process) : this(BaseOSProcessHandler(process, "some command line", null).apply { startNotify() })

  init {
    processHandler.addProcessListener(object : ProcessListener {
      override fun startNotified(event: ProcessEvent) = Unit

      override fun processTerminated(event: ProcessEvent) {
        stdErrImpl.close()
        stdOutImpl.close()
        scope.async {
          processResult.send(event.exitCode)
          processResult.close()
        }
      }

      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val where = if (outputType == ProcessOutputTypes.STDOUT) stdOutImpl else stdErrImpl
        scope.launch(Dispatchers.IO) {
          where.send(event.text)
        }
      }
    })
  }


  /**
   * Runs process, prints its output to [reporter] (if provided) and returns either null or error
   */
  @Suppress("HardCodedStringLiteral")
  suspend fun runProcessAndGetError(uiContext: CoroutineContext, reporter: RawProgressReporter?): String? {

    val jobs = mutableListOf<Job>()
    jobs += withContext(uiContext) {
      launch {
        for (message in stdOut) {
          reporter?.text(message.filter { it != UIUtil.MNEMONIC })
        }
      }
    }
    val errors = withContext(Dispatchers.IO) {
      async {
        val errors = mutableListOf<String>()
        for (error in stdErr) {
          errors += error
        }
        errors
      }
    }.apply { jobs += this }

    val result = withContext(Dispatchers.IO) {
      result.receive()
    }

    jobs.forEach { it.join() }
    return if (result != 0) {
      errors.await().joinToString("")
    }
    else null
  }
}