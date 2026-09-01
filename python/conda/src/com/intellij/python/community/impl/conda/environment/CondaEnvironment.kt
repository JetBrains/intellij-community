// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.conda.environment

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.impl.conda.PyCondaBundle.message
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.packaging.findCondaExecutableRelativeToEnv
import com.jetbrains.python.sdk.ActivationScript
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.PythonEnvironmentProvider
import com.jetbrains.python.sdk.ShellActivation
import com.jetbrains.python.sdk.impl.resolvePythonHome
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

/** A conda environment, marked by its `conda-meta` directory. */
@ApiStatus.Internal
data class CondaEnvironment(
  override val version: @NlsSafe String?,
  override val pythonBinaryPath: PythonBinary,
  override val pythonHomePath: PythonHomePath,
  /** Path to the environment's `conda-meta/` directory — the marker that identified it as a conda env. */
  val condaMetaPath: Path,
  /** `true` if this is the base conda installation (has `condabin/` or `envs/` subdirectory). */
  val isBase: Boolean,
  /** Path to the `conda` executable resolved relative to this environment, or `null` if not found. */
  val condaExecutable: Path? = null,
) : PythonEnvironment {
  override val isActivatable: Boolean = true

  override fun activationScript(shellType: Shell.Type): ActivationScript? {
    val osFamily = pythonBinaryPath.getEelDescriptor().osFamily
    val isWindows = osFamily == EelOsFamily.Windows

    val (baseSubdirs, scriptName) = when {
      isWindows -> listOf("Scripts", "condabin") to "activate.bat"
      else -> listOf("bin") to "activate"
    }
    // First existing `activate` script: next to the conda executable, then under the base install.
    val activate = (listOfNotNull(condaExecutable?.resolveSibling(scriptName)) +
                    baseSubdirs.mapNotNull { condaBaseDir?.resolve(it)?.resolve(scriptName) })
      .firstOrNull { it.exists() }

    return activate?.let { activateScript ->
      // conda runs base python under the hood; its MKL DLLs live in the base install's `Library\bin`, which
      // activating a (non-base) env does not add. Append it *after* the activated env's own dirs so base python
      // can load them while the env still takes precedence (PY-57146). Windows-only.
      val additionalPaths = listOfNotNull(
        condaBaseDir?.takeIf { isWindows && !isBase }?.resolve("Library")?.resolve("bin")?.takeIf { it.isDirectory() }
      )

      val postProcessEnv: (Map<String, String>) -> Map<String, String> =
        if (additionalPaths.isEmpty()) { env -> env }
        else { env ->
          val patched = LinkedHashMap(env)
          patched.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.let { (k, v) ->
            patched[k] = (listOf(v) + additionalPaths.map { it.pathString })
              .filter { it.isNotEmpty() }
              .joinToString(osFamily.pathSeparator)
          }
          patched
        }

      ActivationScript(activateScript, listOf(pythonHomePath.pathString), postProcessEnv)
    }
  }

  /**
   * PowerShell activates through the `conda init` hook, so it gets a snippet. Every other shell sources the script.
   *
   * `conda init` writes the hook into the user profile. The IDE runs the hook by hand, because it cannot ask the
   * user to install the hook and then restart the terminal. When the conda executable is missing, the snippet tells
   * the user to run `conda init` instead.
   */
  override fun shellActivation(shellType: Shell.Type): ShellActivation? =
    if (shellType == Shell.Type.POWERSHELL) ShellActivation.Snippet(powerShellActivationCode())
    else super.shellActivation(shellType)

  private fun powerShellActivationCode(): String {
    val condaPath = condaExecutable?.takeIf { it.isExecutable() }
    if (condaPath == null) {
      fileLogger().warn("Can't find $condaExecutable, will not activate conda")
      return "echo '${message("powershell.conda.not.activated", "conda")}'"
    }

    // The quotes are inside "Write-Host".
    val errorMessage = message("powershell.conda.not.activated", condaPath).replace('\'', '"')

    // The conda path needs no escape for a space, because conda cannot have one.
    return """
      & '${StringUtil.escapeChar(condaPath.toString(), '\'')}' shell.powershell hook | Out-String | Invoke-Expression ;
      try {
        conda activate '${StringUtil.escapeChar(pythonHomePath.toString(), '\'')}'
      } catch {
        Write-Host('${StringUtil.escapeChar(errorMessage, '\'')}')
      }
      """.trimIndent()
  }

  /** Root of the base conda installation (contains `condabin`/`Scripts`, `Library`, …); the conda executable lives two levels down. */
  private val condaBaseDir: Path? get() = condaExecutable?.parent?.parent
}

/** A conda environment, marked by a `conda-meta` directory. */
internal class CondaEnvironmentProvider : PythonEnvironmentProvider {
  override val environmentClass: Class<out PythonEnvironment> = CondaEnvironment::class.java

  override fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment>? {
    val pythonHome = pythonBinary.resolvePythonHome()
    val condaMeta = pythonHome.resolve("conda-meta")
    if (!condaMeta.isDirectory()) return null

    return CondaEnvironment(
      version = condaPythonVersion(condaMeta),
      pythonBinaryPath = pythonBinary,
      pythonHomePath = pythonHome,
      condaMetaPath = condaMeta,
      isBase = pythonHome.resolve("condabin").isDirectory() || pythonHome.resolve("envs").isDirectory(),
      // Prefer the per-env conda executable when the layout exposes one (base conda or
      // <root>/envs/<name> envs); fall back to the user-configured / system-wide conda so that
      // venv-style conda envs still get a usable handle for activation pipelines.
      condaExecutable = findCondaExecutableRelativeToEnv(pythonBinary) ?: PyCondaPackageService.getCondaExecutable(),
    ).let { PyResult.success(it) }
  }
}

/**
 * The version of the `python` package conda recorded in [condaMeta], read from the name of its entry.
 *
 * conda writes one JSON file per installed package, named `<package>-<version>-<build>.json`. The name carries the
 * version, so the directory listing is the whole of the work and the file is never opened.
 */
private fun condaPythonVersion(condaMeta: Path): String? =
  try {
    condaMeta.listDirectoryEntries("python-*.json")
      .firstNotNullOfOrNull { it.fileName.toString().removePrefix("python-").substringBefore('-').takeIf(String::isNotBlank) }
  }
  catch (e: IOException) {
    fileLogger().warn("Failed to list $condaMeta", e)
    null
  }
