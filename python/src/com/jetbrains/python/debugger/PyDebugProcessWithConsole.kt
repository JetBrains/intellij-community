// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.jetbrains.python.console.pydev.PydevCompletionVariant
import com.jetbrains.python.debugger.pydev.PyDebugCallback
import org.jetbrains.annotations.ApiStatus


/**
 * Interface representing a Python debug process with an integrated console interface
 * for executing commands and retrieving debug-related information.
 */
@ApiStatus.Internal
interface PyDebugProcessWithConsole {
  @Throws(Exception::class)
  fun getCompletions(prefix: String?): MutableList<PydevCompletionVariant?>

  @Throws(Exception::class)
  fun getDescription(prefix: String?): String

  fun consoleExec(command: String?, callback: PyDebugCallback<String?>)

  fun interruptDebugConsole()
}