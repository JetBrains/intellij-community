// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.packaging.findCondaExecutableRelativeToEnv
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.impl.PySdkBundle.message
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries

/**
 * Detects the Python environment type from the file system layout around this binary.
 *
 * Returns an error if the binary does not exist or the environment layout is corrupted.
 *
 * - If `pyvenv.cfg` exists (PEP 405, Python 3.3+), returns [PythonEnvironment.Venv] with the full parsed config map.
 * - If `bin/activate_this.py` or `Scripts/activate_this.py` exists (legacy `virtualenv` for Python 2.7),
 *   returns [PythonEnvironment.Venv] with an empty config map.
 * - If `conda-meta/` directory exists, returns [PythonEnvironment.Conda] with the resolved conda executable.
 * - Otherwise returns [PythonEnvironment.SystemPython].
 */
@RequiresBackgroundThread
internal fun PythonBinary.detectPythonEnvironmentImpl(): PyResult<PythonEnvironment> {
  if (!isExecutable()) return PyResult.localizedError(message("python.sdk.detect.binary.not.executable", this))
  val validationInfo = runBlockingMaybeCancellable { validatePythonAndGetInfo() }

  val home = resolvePythonHome()
  val pyvenvCfg = home.resolve("pyvenv.cfg")
  if (pyvenvCfg.exists()) {
    val venvLibRoot = resolveVenvLibRoot(home)
                      ?: return PyResult.localizedError(message("python.sdk.detect.venv.lib.root.failed", home))
    return PythonEnvironment.Venv(
      validationInfo = validationInfo,
      pythonBinaryPath = this,
      pythonHomePath = home,
      config = parsePyvenvCfg(pyvenvCfg),
      libRoot = venvLibRoot,
    ).let { PyResult.success(it) }
  }

  // Legacy virtualenv (Python 2.7): no pyvenv.cfg but has activate_this.py in bin/ or Scripts/
  if (home.resolve("bin").resolve("activate_this.py").exists() ||
      home.resolve("Scripts").resolve("activate_this.py").exists()) {
    val venvLibRoot = resolveVenvLibRoot(home)
                      ?: return PyResult.localizedError(message("python.sdk.detect.venv.lib.root.failed", home))
    return PythonEnvironment.Venv(
      validationInfo = validationInfo,
      pythonBinaryPath = this,
      pythonHomePath = home,
      config = emptyMap(),
      libRoot = venvLibRoot,
    ).let { PyResult.success(it) }
  }

  val condaMeta = home.resolve("conda-meta")
  if (condaMeta.isDirectory()) {
    val isBase = home.resolve("condabin").isDirectory() || home.resolve("envs").isDirectory()
    return PythonEnvironment.Conda(
      validationInfo = validationInfo,
      pythonBinaryPath = this,
      pythonHomePath = home,
      condaMetaPath = condaMeta,
      isBase = isBase,
      // Prefer the per-env conda executable when the layout exposes one (base conda or
      // <root>/envs/<name> envs); fall back to the user-configured / system-wide conda so that
      // venv-style conda envs still get a usable handle for activation pipelines.
      condaExecutable = findCondaExecutableRelativeToEnv(this) ?: PyCondaPackageService.getCondaExecutable(),
    ).let { PyResult.success(it) }
  }

  return PythonEnvironment.SystemPython(validationInfo, this).let { PyResult.success(it) }
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
