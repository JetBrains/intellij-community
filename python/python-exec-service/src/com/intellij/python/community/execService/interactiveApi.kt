// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.python.community.execService.impl.ProcessSemiInteractiveHandlerImpl
import com.jetbrains.python.Result
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.Nls
import java.io.IOException


/**
 * Message to be displayed to a user in case of process failure.
 */
typealias CustomErrorMessage = @Nls String


/**
 * In most cases you need [processSemiInteractiveHandler]
 */
fun interface ProcessInteractiveHandler<T> {
  /**
   * Reads output from [process], decides if success or not.
   * In latter case returns [EelProcessExecutionResult] (created out of collected output) and optional error message.
   * If no message returned -- the default one is used.
   */
  suspend fun getResultFromProcess(whatToExec: WhatToExec, args: List<String>, process: EelProcess): Result<T, Pair<EelProcessExecutionResult, CustomErrorMessage?>>
}


/**
 * Process stdout -> result
 */
typealias ProcessSemiInteractiveFun<T> = suspend (EelSendChannel, Deferred<Int>) -> Result<T, CustomErrorMessage?>

/**
 * [ProcessInteractiveHandler], but you do not have to collect output by yourself. You only have access to stdout and exit code.
 * Function collects output lines and reports them to [pyProcessListener] if set
 * So, you can only *write* something to process.
 */
fun <T> processSemiInteractiveHandler(pyProcessListener: PyProcessListener? = null, code: ProcessSemiInteractiveFun<T>): ProcessInteractiveHandler<T> = ProcessSemiInteractiveHandlerImpl(pyProcessListener, code)
