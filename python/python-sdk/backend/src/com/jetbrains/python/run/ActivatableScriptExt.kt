// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.python.sdk.backend.ShellActivation
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.jetbrains.python.sdk.ShellType
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

/**
 * @deprecated Use PythonEnvironment.activationScript(ShellType?), which returns [ShellActivation].
 */
@Deprecated("Use PythonEnvironment.activationScript(ShellType?)", ReplaceWith("PythonEnvironment.activationScript(ShellType?)"))
@ApiStatus.Internal
fun findActivateScript(sdkPath: String?, shellPath: String?): Pair<String, String?>? {
  if (sdkPath == null) return null
  val environment = Path.of(sdkPath).detectPythonEnvironment().getOr { return null }
  val shellType = shellPath?.let { ShellType.resolve(Path.of(it).name) }
  return environment.activationScript(shellType)?.let {
    when (it) {
      is ShellActivation.Snippet -> null
      is ShellActivation.SourceScript -> {
        Pair(it.scriptPath.absolutePathString(), it.args?.firstOrNull())
      }
    }
  }
}
