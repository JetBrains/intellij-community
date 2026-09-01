// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.venv.environment

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.venv.PyVenvBundle.message
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.Activatable
import com.jetbrains.python.sdk.HasOwnLibRoot
import com.jetbrains.python.sdk.HasPythonHome
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.PythonEnvironmentProvider
import com.jetbrains.python.sdk.impl.resolvePythonHome
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/** A virtual environment, with the contents of its `pyvenv.cfg`. */
@ApiStatus.Internal
data class VenvEnvironment(
  override val version: @NlsSafe String?,
  override val pythonBinaryPath: PythonBinary,
  override val pythonHomePath: PythonHomePath,
  /**
   * Key/value pairs parsed from `pyvenv.cfg`. Empty for a legacy `virtualenv` layout (Python 2.7-era)
   * that ships `bin/activate_this.py` instead of `pyvenv.cfg`.
   */
  val config: Map<String, String>,
  /** The `lib/` or `lib/pythonX.Y/` directory of the virtual environment. */
  override val libRoot: Path,
) : PythonEnvironment, HasPythonHome, HasOwnLibRoot, Activatable {
  /**
   * Resolves the venv activation script that fits [Shell.Type] in the directory next to the python
   * binary (`Scripts/` on Windows, `bin/` on Unix). Returns `null` if no matching script exists.
   *
   * On Windows the choice depends on the shell: PowerShell needs `Activate.ps1` (cmd's `activate.bat`
   * cannot mutate the calling PowerShell session), while cmd / unknown shells get `activate.bat`.
   */
  override val activation: (shellType: Shell.Type) -> Activatable.Script? = { shellType ->
    val isWindows = pythonBinaryPath.getEelDescriptor().osFamily == EelOsFamily.Windows
    val scriptName = when (shellType) {
      Shell.Type.POWERSHELL -> "Activate.ps1"
      Shell.Type.FISH -> "activate.fish"
      Shell.Type.CSH -> "activate.csh"
      Shell.Type.BASH, Shell.Type.SH, Shell.Type.ZSH, Shell.Type.UNKNOWN ->
        if (isWindows) "activate.bat" else "activate"
    }
    pythonBinaryPath.resolveSibling(scriptName).takeIf { it.exists() }?.let { Activatable.Script(it) }
  }
}

/**
 * A virtual environment, marked by `pyvenv.cfg` (PEP 405, Python 3.3+).
 *
 * A Python 2.7-era `virtualenv` has no `pyvenv.cfg`. It ships `bin/activate_this.py` or `Scripts/activate_this.py`
 * instead, and it records no version.
 */
internal class VenvEnvironmentProvider : PythonEnvironmentProvider {
  override val environmentClass: Class<out PythonEnvironment> = VenvEnvironment::class.java

  override fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment>? {
    val pythonHome = pythonBinary.resolvePythonHome()
    val pyvenvCfg = pythonHome.resolve("pyvenv.cfg")
    val hasPyvenvCfg = pyvenvCfg.exists()
    if (!hasPyvenvCfg &&
        !pythonHome.resolve("bin").resolve("activate_this.py").exists() &&
        !pythonHome.resolve("Scripts").resolve("activate_this.py").exists()) {
      return null
    }

    val libRoot = resolveVenvLibRoot(pythonHome)
                  ?: return PyResult.localizedError(message("py.venv.error.lib.root.failed", pythonHome))
    val config = if (hasPyvenvCfg) parsePyvenvCfg(pyvenvCfg) else emptyMap()
    return VenvEnvironment(
      version = config.recordedVersion(),
      pythonBinaryPath = pythonBinary,
      pythonHomePath = pythonHome,
      config = config,
      libRoot = libRoot,
    ).let { PyResult.success(it) }
  }
}

/**
 * The interpreter version the `pyvenv.cfg` states, or null when it states none.
 *
 * No PEP defines a version key. [PEP 405](https://peps.python.org/pep-0405/) defines only `home` and
 * `include-system-site-packages`. Each tool adds its own key in its own shape:
 *
 * - `version_info`: `virtualenv` writes the full `sys.version_info` as `major.minor.micro.releaselevel.serial`.
 *   The release level is a word: `alpha`, `beta`, `candidate` or `final`. An example is `3.14.0.candidate.1`.
 *   uv writes the same key as `major.minor.micro`.
 * - `version`: the standard library `venv` and `virtualenv` write `major.minor.micro`. An example is `3.14.4`.
 * - `python-version`: [PEP 838](https://peps.python.org/pep-0838/) adds `major.minor` only. An example is `3.14`.
 *
 * `version_info` comes first, because only that key can carry a pre-release. `python-version` comes last, because it
 * is the least exact. A value the parser does not accept falls through to the next key.
 */
private fun Map<String, String>.recordedVersion(): String? =
  sequenceOf("version_info", "version", "python-version").firstNotNullOfOrNull { key -> this[key]?.let(::parseRecordedVersion) }

/** The `sys.version_info` release level, mapped to its [PEP 440](https://peps.python.org/pep-0440/) suffix. `final` gets no suffix. */
private val PRE_RELEASE_SUFFIX: Map<String, String> = mapOf("alpha" to "a", "beta" to "b", "candidate" to "rc")

/**
 * [raw] as a version to show, or null when [raw] does not start with a number.
 *
 * The parser keeps up to three leading numbers, then adds a pre-release suffix. It drops a tail that it does not know.
 * `3.14.4.final.0` gives `3.14.4`, and `3.14.0.candidate.1` gives `3.14.0rc1`.
 */
private fun parseRecordedVersion(raw: String): String? {
  val parts = raw.trim().split('.')
  val numbers = parts.takeWhile { it.isNotEmpty() && it.all(Char::isDigit) }
  if (numbers.isEmpty()) return null
  val version = numbers.take(3).joinToString(".")
  val suffix = parts.getOrNull(numbers.size)?.let(PRE_RELEASE_SUFFIX::get) ?: return version
  val serial = parts.getOrNull(numbers.size + 1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return version
  return "$version$suffix$serial"
}

private fun parsePyvenvCfg(path: Path): Map<String, String> {
  val result = mutableMapOf<String, String>()
  try {
    for (line in Files.readAllLines(path)) {
      val eq = line.indexOf('=')
      if (eq < 0) continue
      result[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
    }
  }
  catch (e: IOException) {
    fileLogger().warn("Failed to read $path", e)
  }
  return result
}

/**
 * Resolves the library root of a virtual environment.
 * On Windows returns `lib/`, on Unix returns `lib/pythonX.Y/` (or `lib/` if no version subdirectory is found).
 * Returns `null` if the `lib/` directory does not exist or cannot be listed.
 */
private fun resolveVenvLibRoot(home: Path): Path? {
  val libDir = home.resolve("lib")
  if (!libDir.isDirectory()) return null
  if (home.getEelDescriptor().osFamily == EelOsFamily.Windows) return libDir
  return try {
    libDir.listDirectoryEntries("python*").firstOrNull { it.isDirectory() } ?: libDir
  }
  catch (e: IOException) {
    fileLogger().warn("Failed to list $libDir", e)
    null
  }
}
