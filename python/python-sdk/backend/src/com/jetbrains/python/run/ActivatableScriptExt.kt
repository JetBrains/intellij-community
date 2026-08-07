// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.jetbrains.python.sdk.Activatable
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * @deprecated Use PythonEnvironment.activation(Shell.Type) which returns [Activatable.Script].
 */
@Deprecated("Use PythonEnvironment.activation(Shell.Type)", ReplaceWith("PythonEnvironment.activation(Shell.Type)"))
@ApiStatus.Internal
fun findActivateScript(sdkPath: String?, shellPath: String?): Pair<String, String?>? {
  if (sdkPath == null) return null
  val activatable = Path.of(sdkPath).detectPythonEnvironment().getOr { return null } as? Activatable
                    ?: return null
  val shellType = shellPath?.let { Shell.Type.resolve(Path.of(it)) } ?: Shell.Type.UNKNOWN
  return activatable.activation(shellType)?.let {
    Pair(it.scriptPath.absolutePathString(), it.args?.firstOrNull())
  }
}
