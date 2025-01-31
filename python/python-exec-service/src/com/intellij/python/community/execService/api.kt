// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.platform.eel.EelApi
import com.intellij.python.community.execService.impl.ExecServiceImpl
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError.ExecException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Service is a thin wrapper over [EelApi] to execute python tools on local or remote Eel.
 * to obtain service, use function with same name.
 */
@ApiStatus.Internal
interface ExecService {
  /**
   * Execute [whatToExec] with [args] and get stdout if `errorCode != 0`. Get error otherwise.
   *
   * Process gets killed after [timeout], and might have optional [processDescription] (to be displayed to user).
   * If you want to show a modal window with progress, use `withModalProgress`.
   * @return stdout or error. It is recommended to put this error into [com.jetbrains.python.errorProcessing.ErrorSink], but feel free to match and process it.
   */
  @ApiStatus.Internal
  @CheckReturnValue
  suspend fun execGetStdout(whatToExec: WhatToExec, args: List<String> = emptyList<String>(), processDescription: @Nls String? = null, timeout: Duration = 1.minutes): Result<String, ExecException>
}

sealed interface WhatToExec {
  /**
   * [binary] (can reside on local or remote Eel, [EelApi] is calculated out of it)
   */
  data class Binary(val binary: Path) : WhatToExec

  /**
   * Execute [helper] on [python]. If [python] resides on remote Eel -- helper is copied there.
   * Note, that only **one** helper file is copied, not all helpers.
   */
  data class Helper(val python: PythonBinary, val helper: HelperName) : WhatToExec

  /**
   * Random command on [eel]. [EelApi] will look for it in the path
   */
  data class Command(val eel: EelApi, val command: String) : WhatToExec
}

/**
 * Default server implementation
 */
fun ExecService(): ExecService = ExecServiceImpl