// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.execution.target.value.TargetEnvironmentFunction
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to execute the code in Python console. In comparison with [PyCodeExecutor] this interface allows to execute code with parts that
 * depend on the target execution environment associated with the console.
 *
 * @see PyCodeExecutor
 * @see PythonConsoleView
 */
@ApiStatus.Experimental
interface PyTargetedCodeExecutor {
  fun executeCode(code: TargetEnvironmentFunction<String>)
}