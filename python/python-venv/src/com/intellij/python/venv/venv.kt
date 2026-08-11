// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.venv

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.HelperName
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.workingDirectory
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CheckReturnValue
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

/**
 * Create virtual env in [venvDir] using [python].
 * If [python] resides on remote eel, [VIRTUALENV_ZIPAPP_NAME] helper will be copied there.
 * [inheritSitePackages] inherits [python] packages into newly created virtual env.
 * @return either error or [PythonBinary] path of a python binary in newly created virtual env.
 */
@Internal
@CheckReturnValue
suspend fun createVenv(
  python: PythonBinary,
  venvDir: Directory,
  inheritSitePackages: Boolean = false,
  envReader: VirtualEnvReader = VirtualEnvReader(),
): PyResult<PythonBinary> {
  createVenv(python.asBinToExec(), venvDir.asEelPath().toString(), inheritSitePackages).getOr { return it }

  val venvPython = withContext(Dispatchers.IO) {
    envReader.findPythonInPythonRoot(venvDir)
  } ?: return PyResult.localizedError(PyVenvBundle.message("py.venv.error.after.creation", venvDir))
  fileLogger().info("Venv created: $venvPython")

  withContext(Dispatchers.EDT) {
    PySdkSettings.instance.preferredVirtualEnvBaseSdk = python.pathString
  }
  // A new venv was just created, we need to clear cache to make sure it isn't marked as "broken" to prevent inspections
  PythonSdkFlavor.clearExecutablesCache()
  return Result.success(venvPython)
}

@Internal
@CheckReturnValue
suspend fun createVenv(
  python: BinaryToExec,
  venvDir: String,
  inheritSitePackages: Boolean = false,
): PyResult<Unit> {
  val execService = ExecService()
  val args = buildList {
    if (inheritSitePackages) {
      add("--system-site-packages")
    }
    add(venvDir)
  }
  val version = python.validatePythonAndGetInfo().getOr(PyVenvBundle.message("py.venv.error.cant.base.version")) { return it }.languageLevel
  if (!version.isAtLeast(MINIMUM_SUPPORTED_VENV_PYTHON_VERSION)) {
    return PyResult.localizedError(PyVenvBundle.message("py.venv.error.unsupported.version",
                                                        version.toPythonVersion(),
                                                        MINIMUM_SUPPORTED_VENV_PYTHON_VERSION.toPythonVersion()))
  }
  execService.executeHelper(python, VIRTUALENV_ZIPAPP_NAME, args, ExecOptions(timeout = 3.minutes))
    .getOr(PyVenvBundle.message("py.venv.error.executing.script", VIRTUALENV_ZIPAPP_NAME)) { return it }

  return Result.success(Unit)
}

// venv helper, update from https://bootstrap.pypa.io/virtualenv.pyz
@Internal
const val VIRTUALENV_ZIPAPP_NAME: HelperName = "virtualenv-py3.pyz"

/**
 * Minimum Python version supported by the IDE's bundled environment tooling: the [VIRTUALENV_ZIPAPP_NAME]
 * zipapp and the bundled pip/setuptools wheels all require Python >= 3.8. Older interpreters stay selectable,
 * but the IDE can neither create a virtual environment for them nor provision packaging tools.
 */
@Internal
val MINIMUM_SUPPORTED_VENV_PYTHON_VERSION: LanguageLevel = LanguageLevel.PYTHON38

/**
 * Creates [PythonSdkAdditionalData] for virtual env using working directory
 */
@Internal
fun createVenvAdditionalData(workingDirectory: Path): PythonSdkAdditionalData =
  PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()), workingDirectory)

/**
 * Creates [PythonSdkAdditionalData] for virtual env using module baseDir as working directory
 */
@Internal
fun createVenvAdditionalData(module: Module): PyResult<PythonSdkAdditionalData> {
  return ModuleOrProject.ModuleAndProject(module).workingDirectory?.let {
    PyResult.success(createVenvAdditionalData(it))
  } ?: PyResult.localizedError(PySdkBundle.message("python.sdk.cannot.create.working.directory.empty"))
}