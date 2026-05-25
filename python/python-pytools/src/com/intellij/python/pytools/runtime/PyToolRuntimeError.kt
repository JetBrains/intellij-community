// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.runtime

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pytools.PyToolsBundle
import com.jetbrains.python.errorProcessing.MessageError
import java.nio.file.Path

/**
 * Base error type for failures raised by [PyToolRuntime] helpers.
 * Tool-specific error hierarchies (e.g. `HatchError`) may extend this
 * to interop with the generic runtime API.
 */
sealed class PyToolRuntimeError(message: @NlsSafe String) : MessageError(message)

class WorkingDirectoryNotFoundError(pathString: String?) : PyToolRuntimeError(
  PyToolsBundle.message("python.tool.runtime.error.working.directory.not.found", pathString.toString())
) {
  constructor(path: Path?) : this(path?.toString())
}

class BasePythonExecutableNotFoundError(pathString: String?) : PyToolRuntimeError(
  PyToolsBundle.message("python.tool.runtime.error.base.python.executable.not.found", pathString.toString())
) {
  constructor(path: Path?) : this(path?.toString())
}
