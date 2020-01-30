// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentRequest
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonScriptTargetedCommandLineBuilder {
  /**
   * Takes [pythonScript] and modifies it along with [targetEnvironmentRequest]
   * for specific execution strategy (e.g. debugging, profiling, etc).
   */
  fun build(targetEnvironmentRequest: TargetEnvironmentRequest, pythonScript: PythonExecution): PythonExecution
}