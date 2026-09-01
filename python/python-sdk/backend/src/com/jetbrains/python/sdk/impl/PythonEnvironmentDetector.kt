// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.packaging.findCondaExecutableRelativeToEnv
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.PythonEnvironmentProvider
import com.jetbrains.python.sdk.impl.PySdkBundle.message
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries

/**
 * Detects the Python environment from the file system layout around this binary.
 *
 * Asks each [PythonEnvironmentProvider] in the registered order and takes the first answer. The system provider is
 * registered last and claims any layout, so a caller always gets an environment.
 *
 * Returns an error if the binary is not executable, or if a provider owns the layout but the layout is broken.
 */
@RequiresBackgroundThread
internal fun PythonBinary.detectPythonEnvironmentImpl(): PyResult<PythonEnvironment> {
  if (!isExecutable()) return PyResult.localizedError(message("python.sdk.detect.binary.not.executable", this))

  return PythonEnvironmentProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.instance.detect(this) }
         ?: error("No ${PythonEnvironmentProvider.EP_NAME.name} claimed $this. The system provider claims any layout, so it is not registered.")
}

/** A conda environment, marked by a `conda-meta` directory. */
internal class CondaEnvironmentProvider : PythonEnvironmentProvider {
  override val environmentClass: Class<out PythonEnvironment> = PythonEnvironment.Conda::class.java

  override fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment>? {
    val pythonHome = pythonBinary.resolvePythonHome()
    val condaMeta = pythonHome.resolve("conda-meta")
    if (!condaMeta.isDirectory()) return null

    return PythonEnvironment.Conda(
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
 * A system-wide Python installation: whatever no other provider claims.
 *
 * Register it last, because it answers for any layout.
 */
internal class SystemPythonEnvironmentProvider : PythonEnvironmentProvider {
  override val environmentClass: Class<out PythonEnvironment> = PythonEnvironment.SystemPython::class.java

  override fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment> =
    PythonEnvironment.SystemPython(pythonBinaryPath = pythonBinary).let { PyResult.success(it) }
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
