// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.jetbrains.python.run

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.EnvironmentUtil
import com.intellij.util.ShellEnvironmentReader
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.jetbrains.python.sdk.Activatable
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.terminal.Shell
import com.jetbrains.python.sdk.terminal.Shell.Companion.systemDefaultShell
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Suppress("SpellCheckingInspection")
private val virtualEnvVars = listOf(
  "PATH", "PS1", "VIRTUAL_ENV", "PYTHONHOME", "PROMPT", "_OLD_VIRTUAL_PROMPT",
  "_OLD_VIRTUAL_PYTHONHOME", "_OLD_VIRTUAL_PATH", "CONDA_SHLVL", "CONDA_PROMPT_MODIFIER",
  "CONDA_PREFIX", "CONDA_DEFAULT_ENV",
  "GDAL_DATA", "PROJ_LIB", "JAVA_HOME", "JAVA_LD_LIBRARY_PATH"
)

/**
 * Filter envs that are set up by the activate script, adding other variables from the different shell can break the actual shell.
 */
@ApiStatus.Internal
internal fun Activatable.Script.readPythonEnvironment(): Map<String, String> {
  val command = if (scriptPath.getEelDescriptor().osFamily == EelOsFamily.Windows) {
    ShellEnvironmentReader.winShellCommand(scriptPath, args)
  }
  else {
    ShellEnvironmentReader.shellCommand(systemDefaultShell?.path?.toString(), scriptPath, false, args)
  }
  command.environment().putAll(EnvironmentUtil.getEnvironmentMap())

  val env = try {
    ShellEnvironmentReader.readEnvironment(command, 0).first
  }
  catch (e: IOException) {
    logger<Activatable.Script>().warn("Couldn't read shell environment: ${e.message}")
    mutableMapOf()
  }

  return env.filterKeys { k -> virtualEnvVars.any { it.equals(k, true) } }
}


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
