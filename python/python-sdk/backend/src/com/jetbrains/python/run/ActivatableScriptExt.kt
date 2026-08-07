// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.jetbrains.python.run

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
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

/**
 * Variables that differ between the reference and the activated shell read for reasons unrelated to
 * activation, so they must not be mistaken for part of the activation delta:
 *  - `PWD`/`OLDPWD` are working-directory-derived (the activated read runs in the script's directory);
 *  - `_`/`SHLVL` are per-invocation shell bookkeeping.
 *
 * This cannot catch a genuinely non-deterministic value exported by a user's shell rc (a timestamp, a
 * random token): no static list can, and the whitelist this replaced dropped such variables anyway.
 *
 * No Windows entries are needed: `cmd.exe` exports no such bookkeeping, and its hidden per-drive
 * current-directory variables (`=C:`, …) are filtered out by [com.intellij.util.ReadEnv] before the diff.
 */
private val nonActivationEnvVars = setOf("_", "SHLVL", "PWD", "OLDPWD")

/**
 * Reads the shell environment produced by [command], applying a generous timeout so that a slow interpreter
 * activation (notably conda activation on Windows, which can take tens of seconds) is not cut short — when the
 * read is cut short the activation env is lost and the launched process misses variables, crashing on missing
 * DLLs (PY-91371). This is the single place that timeout is applied.
 *
 * The default lives in the `python.activate.env.reader.timeout.ms` registry key, tunable for slower machines.
 */
@ApiStatus.Internal
fun readActivationEnvironment(command: ProcessBuilder): Map<String, String> =
  ShellEnvironmentReader.readEnvironment(command, activationEnvReaderTimeoutMs()).first

private fun activationEnvReaderTimeoutMs(): Long =
  Registry.intValue("python.activate.env.reader.timeout.ms").toLong()

/**
 * Reads the environment set up by the activation [Activatable.Script].
 *
 * The login shell is read twice — once plain, once after sourcing the activation script — and only the
 * variables the script added or changed are returned. Diffing against a reference shell keeps whatever the
 * script exports (conda `activate.d` hooks routinely set arbitrary package-specific variables, PY-71917)
 * while not leaking the reader shell's own variables into the target process, which could break it.
 */
internal fun Activatable.Script.readPythonEnvironment(): Map<String, String> {
  val isWindows = scriptPath.getEelDescriptor().osFamily == EelOsFamily.Windows

  fun readShellEnv(script: Activatable.Script?): Map<String, String> {
    val command = if (isWindows) {
      ShellEnvironmentReader.winShellCommand(script?.scriptPath, script?.args)
    }
    else {
      ShellEnvironmentReader.shellCommand(systemDefaultShell?.path?.toString(), script?.scriptPath, false, script?.args)
    }
    command.environment().putAll(EnvironmentUtil.getEnvironmentMap())
    return readActivationEnvironment(command)
  }

  return try {
    activationEnvDelta(referenceEnv = readShellEnv(null), activatedEnv = readShellEnv(this))
  }
  catch (e: IOException) {
    logger<Activatable.Script>().warn("Couldn't read shell environment: ${e.message}")
    emptyMap()
  }
}

/**
 * The variables the activation script is responsible for: those [activatedEnv] adds or changes relative to
 * [referenceEnv], minus [nonActivationEnvVars] noise. Extracted from [readPythonEnvironment] so the diff can
 * be tested without spawning a shell.
 */
internal fun activationEnvDelta(referenceEnv: Map<String, String>, activatedEnv: Map<String, String>): Map<String, String> =
  activatedEnv.filter { (key, value) -> key !in nonActivationEnvVars && referenceEnv[key] != value }


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
