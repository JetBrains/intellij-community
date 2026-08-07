// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.sdk.backend.service.ActivatableEnvironmentService.Companion.nonActivationEnvVars
import com.intellij.util.EnvironmentUtil
import com.intellij.util.ShellEnvironmentReader
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.Activatable
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.terminal.Shell
import com.jetbrains.python.sdk.terminal.Shell.Companion.systemDefaultShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Reads and caches the environment set up by an interpreter's activation script (venv `activate`, conda
 * `activate.bat`, …), so it is computed once per interpreter and shared by every caller.
 *
 * Concurrency-safe: [Cache.get] runs the computation at most once per key even when several callers request the
 * same interpreter at once (the others wait for that single computation). A failed read is not cached (the loader
 * throws, so Caffeine records nothing) and is surfaced to the caller as a [PyResult] failure, so a transient failure
 * (e.g. a timed-out conda activation) is retried on the next call; the bounded TTL lets environment changes such as
 * a newly installed package eventually take effect.
 *
 * Not called directly: use the `Sdk.activationEnvironment` / `PythonEnvironment.activationEnvironment` /
 * `Path.activationEnvironment` extensions.
 */
@Service(Service.Level.APP)
internal class ActivatableEnvironmentService {
  private val cache: Cache<Path, Map<String, String>> = Caffeine.newBuilder()
    .expireAfterWrite(24, TimeUnit.HOURS)
    .maximumSize(1024)
    .build()

  /**
   * Timeout for reading the shell activation environment. Generous so that a slow interpreter activation (notably
   * conda activation on Windows, which can take tens of seconds) is not cut short — when the read is cut short the
   * activation env is lost and the launched process misses variables, crashing on missing DLLs (PY-91371). The
   * default lives in the `python.activate.env.reader.timeout.ms` registry key, tunable for slower machines.
   */
  private fun activationEnvReaderTimeoutMs(): Long = Registry.intValue("python.activate.env.reader.timeout.ms").toLong()

  suspend fun activationEnvironment(pythonBinaryPath: Path): PyResult<Map<String, String>> {
    // A cache hit avoids the (interpreter-spawning) environment detection below.
    cache.getIfPresent(pythonBinaryPath)?.let { return PyResult.success(it) }
    val environment = withContext(Dispatchers.IO) { pythonBinaryPath.detectPythonEnvironment() }.getOr { return it }
    return activationEnvironment(environment)
  }

  suspend fun activationEnvironment(environment: PythonEnvironment): PyResult<Map<String, String>> = withContext(Dispatchers.IO) {
    try {
      // Only a successful read is cached: a throwing loader records nothing in Caffeine, so a transient failure
      // (e.g. a timed-out conda activation) is retried next call instead of being remembered as an empty map.
      val value = cache.get(environment.pythonBinaryPath) {
        if (environment is Activatable) readActivationEnvironment(environment) else emptyMap()
      }
      PyResult.success(value)
    }
    catch (e: IOException) {
      thisLogger().warn("Failed to read the activation environment of ${environment.pythonBinaryPath}", e)
      @NlsSafe val message = e.localizedMessage
      PyResult.localizedError(message)
    }
  }

  /**
   * Reads the environment produced by activating [environment].
   *
   * The login shell is read twice — once plain, once after sourcing the activation script — and only the variables
   * the script added or changed are returned (see [activationEnvDelta]). Diffing against a reference shell keeps
   * whatever the script exports (conda `activate.d` hooks routinely set arbitrary package-specific variables,
   * PY-71917) while not leaking the reader shell's own variables into the target process.
   */
  @OptIn(LowLevelLocalMachineAccess::class)
  private fun readActivationEnvironment(environment: Activatable): Map<String, String> {
    val shellType = systemDefaultShell?.type ?: Shell.Type.UNKNOWN
    val script = environment.activation(shellType) ?: return emptyMap()
    val isWindows = script.scriptPath.getEelDescriptor().osFamily == EelOsFamily.Windows

    fun readShellEnv(sourced: Activatable.Script?): Map<String, String> {
      val command = if (isWindows) {
        ShellEnvironmentReader.winShellCommand(sourced?.scriptPath, sourced?.args)
      }
      else {
        ShellEnvironmentReader.shellCommand(systemDefaultShell?.path?.toString(), sourced?.scriptPath, false, sourced?.args)
      }
      command.environment().putAll(EnvironmentUtil.getEnvironmentMap())
      return ShellEnvironmentReader.readEnvironment(command, activationEnvReaderTimeoutMs()).first
    }

    val referenceEnv = readShellEnv(null)
    val activatedEnv = readShellEnv(script)
    val envDelta = activationEnvDelta(referenceEnv = referenceEnv, activatedEnv = activatedEnv)
    return script.postProcessEnv(envDelta)
  }


  companion object {
    /**
     * Variables that differ between the reference and the activated shell for reasons unrelated to activation, so
     * they must not be mistaken for part of the activation delta: `PWD`/`OLDPWD` are working-directory-derived,
     * `_`/`SHLVL` are per-invocation shell bookkeeping. `cmd.exe` needs no entries (it exports no such bookkeeping,
     * and its per-drive current-directory variables are filtered out by [com.intellij.util.ReadEnv]).
     */
    private val nonActivationEnvVars = setOf("_", "SHLVL", "PWD", "OLDPWD")

    /**
     * The variables the activation script is responsible for: those [activatedEnv] adds or changes relative to
     * [referenceEnv], minus [nonActivationEnvVars] noise. A pure function (no application needed) so the diff can
     * be unit-tested without spawning a shell.
     */
    internal fun activationEnvDelta(referenceEnv: Map<String, String>, activatedEnv: Map<String, String>): Map<String, String> =
      activatedEnv.filter { (key, value) -> key !in nonActivationEnvVars && referenceEnv[key] != value }
  }
}
