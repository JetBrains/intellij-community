// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.python.sdk.backend.ActivationScript
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * @deprecated Use PythonEnvironment.activationScript(Shell.Type) which returns [ActivationScript].
 */
@Deprecated("Use PythonEnvironment.activationScript(Shell.Type)", ReplaceWith("PythonEnvironment.activationScript(Shell.Type)"))
@ApiStatus.Internal
fun findActivateScript(sdkPath: String?, shellPath: String?): Pair<String, String?>? {
  if (sdkPath == null) return null
  val environment = Path.of(sdkPath).detectPythonEnvironment().getOr { return null }
  val shellType = shellPath?.let { Shell.Type.resolve(Path.of(it)) } ?: Shell.Type.UNKNOWN
  return environment.activationScript(shellType)?.let {
    Pair(it.scriptPath.absolutePathString(), it.args?.firstOrNull())
  }
}
