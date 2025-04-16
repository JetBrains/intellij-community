// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectCreation

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.*
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.withProgressText
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.jetbrains.python.*
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val logger = fileLogger()

/**
 * Create a venv in a [project] (or in [explicitProjectPath]) and SDK out of it (existing venv will be used if valid).
 * The best python os chosen automatically using [SystemPythonService], but if there is no python one will be installed with
 * [confirmInstallation].
 *  If a project has no module -- one will be created.
 *
 *  Use this function as a high-level API for various quick project creation wizards like Misc and Tour.
 *
 *  If you only need venv (no SDK), use [createVenv]
 */
suspend fun createVenvAndSdk(
  project: Project,
  confirmInstallation: suspend () -> Boolean = { true },
  systemPythonService: SystemPythonService = SystemPythonService(),
  explicitProjectPath: VirtualFile? = null,
): Result<Sdk, PyError> {
  val vfsProjectPath = withContext(Dispatchers.IO) {
    explicitProjectPath
    ?: (project.modules.firstOrNull()?.let { module -> ModuleRootManager.getInstance(module).contentRoots.firstOrNull() }
        ?: project.guessProjectDir()
        ?: error("no path provided and can't guess path for $project"))
  }


  val projectPath = vfsProjectPath.toNioPath()
  val venvDirPath = vfsProjectPath.toNioPath().resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)


  // Find venv in a project
  var venvPython: PythonBinary? = findExistingVenv(venvDirPath)

  if (venvPython == null) {
    // No venv found -- find system python to create venv
    val systemPythonBinary = getSystemPython(confirmInstallation = confirmInstallation, systemPythonService).getOr { return it }
    logger.info("no venv in $venvDirPath, using system python $systemPythonBinary to create venv")
    // create venv using this system python
    venvPython = createVenv(systemPythonBinary, venvDir = venvDirPath).getOr {
      return it
    }
  }

  logger.info("using venv python $venvPython")
  val sdk = getSdk(venvPython, project)
  if (project.modules.isEmpty()) {
    writeAction {
      val file = projectPath.resolve("${projectPath.fileName}.iml")
      ModuleManager.getInstance(project).newModule(file, PythonModuleTypeBase.getInstance().id)
    }
  }
  val module = project.modules.first()
  ensureModuleHasRoot(module, vfsProjectPath)
  withContext(Dispatchers.IO) {
    // generated files should be readable by VFS
    VfsUtil.markDirtyAndRefresh(false, true, true, vfsProjectPath)
  }
  configurePythonSdk(project, module, sdk)
  sdk.getOrCreateAdditionalData().associateWithModule(module)
  module.pythonSdk
  return Result.success(sdk)
}

/**
 * Search for existing venv in [venvDirPath] and make sure it is usable.
 * `null` means no venv or venv is broken (it doesn't report its version)
 */
private suspend fun findExistingVenv(
  venvDirPath: Path,
): PythonBinary? = withContext(Dispatchers.IO) {
  val pythonPath = VirtualEnvReader.Instance.findPythonInPythonRoot(venvDirPath) ?: return@withContext null
  val flavor = PythonSdkFlavor.tryDetectFlavorByLocalPath(pythonPath.toString())
  if (flavor == null) {
    logger.warn("No flavor found for $pythonPath")
    return@withContext null
  }
  return@withContext when (val p = pythonPath.validatePythonAndGetVersion()) {
    is Result.Success -> pythonPath
    is Result.Failure -> {
      logger.warn("No version string. python seems to be broken: $pythonPath. ${p.error}")
      null
    }
  }
}

private suspend fun getSystemPython(
  confirmInstallation: suspend () -> Boolean,
  pythonService: SystemPythonService,
): Result<PythonBinary, PyError.Message> {


  // First, find the latest python according to strategy
  var systemPythonBinary = pythonService.findSystemPythons().firstOrNull()

  // No python found?
  if (systemPythonBinary == null) {
    // Install it
    val installer = pythonService.getInstaller()
                    ?: return failure(PyBundle.message("project.error.install.not.supported"))
    if (confirmInstallation()) {
      // Install
      when (val r = installer.installLatestPython()) {
        is Result.Failure -> {
          val error = r.error
          logger.warn("Python installation failed $error")
          return failure(
            PyBundle.message("project.error.install.python", error))
        }
        is Result.Success -> {
          // Find the latest python again, after installation
          systemPythonBinary = pythonService.findSystemPythons(forceRefresh = true).firstOrNull()
        }
      }
    }
  }

  return if (systemPythonBinary == null) {
    return failure(PyBundle.message("project.error.all.pythons.bad"))
  }
  else {
    Result.Success(systemPythonBinary.pythonBinary)
  }
}


private suspend fun ensureModuleHasRoot(module: Module, root: VirtualFile): Unit = edtWriteAction {
  with(module.rootManager.modifiableModel) {
    try {
      if (root in contentRoots) return@edtWriteAction
      addContentEntry(root)
    }
    finally {
      commit()
    }
  }
}

private suspend fun getSdk(pythonPath: PythonBinary, project: Project): Sdk =
  withProgressText(ProjectBundle.message("progress.text.configuring.sdk")) {
    val allJdks = ProjectJdkTable.getInstance().allJdks
    val currentSdk = allJdks.firstOrNull { sdk -> sdk.homeDirectory?.toNioPath() == pythonPath }
    if (currentSdk != null) return@withProgressText currentSdk

    val localPythonVfs = withContext(Dispatchers.IO) { VfsUtil.findFile(pythonPath, true)!! }
    return@withProgressText createSdk(localPythonVfs, project.basePath?.let { Path.of(it) }, allJdks)
  }
