// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectCreation

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.withProgressText
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.createVenvFromSystemPython
import com.intellij.python.venv.createVenv
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.intellij.python.community.services.systemPython.findMatchingPython
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
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
  moduleOrProject: ModuleOrProject,
  confirmInstallation: suspend () -> Boolean = { true },
  systemPythonService: SystemPythonService = SystemPythonService(),
  explicitPath: VirtualFile? = null,
): PyResult<Sdk> {
  val project = moduleOrProject.project
  val vfsPath = run {
    explicitPath?.let { return@run explicitPath }

    val module = moduleOrProject.moduleIfExists ?: project.modules.firstOrNull()
    val contentRoot = module?.let {
      ModuleRootManager.getInstance(it).contentRoots.firstOrNull()
    }

    contentRoot ?: withContext(Dispatchers.IO) {
      project.guessProjectDir()
    } ?: error("No path provided and can't guess path for $moduleOrProject")
  }

  val venvDirPath = vfsPath.toNioPath().resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)

  // Find venv in a project
  var venvPython: PythonBinary? = findExistingVenv(venvDirPath)

  if (venvPython == null) {
    // No venv found -- find system python to create venv
    val systemPythonBinary = getSystemPython(confirmInstallation = confirmInstallation, systemPythonService).getOr { return it }
    logger.info("no venv in $venvDirPath, using system python $systemPythonBinary to create venv")
    // create venv using this system python
    venvPython = createVenvFromSystemPython(systemPythonBinary,
                                            venvDir = venvDirPath).getOr(PyBundle.message("action.AnActionButton.text.show.early.releases")) {
      return it
    }
  }

  logger.info("using venv python $venvPython")
  val sdkBasePath = moduleOrProject.moduleIfExists?.baseDir?.path ?: project.basePath
  val sdk = getSdk(venvPython, sdkBasePath?.let { Path.of(it) })
  if (moduleOrProject.moduleIfExists == null && project.modules.isEmpty()) {
    writeAction {
      val projectPath = vfsPath.toNioPath()
      val file = projectPath.resolve("${projectPath.fileName}.iml")
      ModuleManager.getInstance(project).newModule(file, PythonModuleTypeBase.getInstance().id)
    }
  }
  val module = moduleOrProject.moduleIfExists ?: project.modules.first()
  ensureModuleHasRoot(module, vfsPath)
  withContext(Dispatchers.IO) {
    // generated files should be readable by VFS
    VfsUtil.markDirtyAndRefresh(false, true, true, vfsPath)
  }
  configurePythonSdk(project, module, sdk)
  sdk.setAssociationToModule(module)
  project.pySdkService.persistSdk(sdk)
  return Result.success(sdk)
}

/**
 * Search for existing venv in [venvDirPath] and make sure it is usable.
 * `null` means no venv or venv is broken (it doesn't report its version)
 */
private suspend fun findExistingVenv(
  venvDirPath: Path,
): PythonBinary? = withContext(Dispatchers.IO) {
  val pythonPath = VirtualEnvReader().findPythonInPythonRoot(venvDirPath) ?: return@withContext null
  val flavor = PythonSdkFlavor.tryDetectFlavorByLocalPath(pythonPath.toString())
  if (flavor == null) {
    logger.warn("No flavor found for $pythonPath")
    return@withContext null
  }
  return@withContext when (val p = pythonPath.validatePythonAndGetInfo()) {
    is Result.Success -> pythonPath
    is Result.Failure -> {
      logger.warn("No version string. python seems to be broken: $pythonPath. ${p.error}")
      null
    }
  }
}

internal suspend fun getSystemPython(
  confirmInstallation: suspend () -> Boolean,
  pythonService: SystemPythonService,
  versionSpecifiers: PyVersionSpecifiers = PyVersionSpecifiers.ANY_SUPPORTED,
): Result<SystemPython, MessageError> {
  // First, find the latest python according to strategy
  var systemPythonBinary = pythonService.findSystemPythons(forceRefresh = true).findMatchingPython(versionSpecifiers)

  // No python found?
  if (systemPythonBinary == null) {
    // Install it
    val installer = pythonService.getInstaller()
                    ?: return PyResult.localizedError(PyBundle.message("project.error.install.not.supported"))
    if (confirmInstallation()) {
      // Install
      when (val r = installer.installLatestPython(versionSpecifiers)) {
        is Result.Failure -> {
          val error = r.error
          logger.warn("Python installation failed $error")
          return PyResult.localizedError(PyBundle.message("project.error.install.python", error))
        }
        is Result.Success -> {
          // Find the latest python again, after installation
          systemPythonBinary = pythonService.findSystemPythons(forceRefresh = true).findMatchingPython(versionSpecifiers)
        }
      }
    }
  }

  return if (systemPythonBinary == null) {
    PyResult.localizedError(PyBundle.message("project.error.all.pythons.bad"))
  }
  else {
    Result.Success(systemPythonBinary)
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

private suspend fun getSdk(pythonPath: PythonBinary, sdkBasePath: Path?): Sdk =
  withProgressText(ProjectBundle.message("progress.text.configuring.sdk")) {
    val allJdks = PythonSdkUtil.getAllSdks().toTypedArray()
    val currentSdk = allJdks.firstOrNull { sdk -> sdk.homeDirectory?.toNioPath() == pythonPath }
    if (currentSdk != null) return@withProgressText currentSdk

    val localPythonVfs = withContext(Dispatchers.IO) { VfsUtil.findFile(pythonPath, true)!! }
    createSdk(localPythonVfs, sdkBasePath, allJdks)
  }
