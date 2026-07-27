// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.helpersLocator.PythonHelpersLocator.Companion.findPathInHelpers
import com.intellij.python.venv.MINIMUM_SUPPORTED_VENV_PYTHON_VERSION
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.toPythonPackage
import com.jetbrains.python.packaging.management.DependenciesExporter
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonManagerCliSpec
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.hasInstalledPackage
import com.jetbrains.python.packaging.requirementsTxt.RequirementsTxtManipulationHelper
import com.jetbrains.python.packaging.syncWithImports
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.statistics.version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * This class will be internal soon, please do not use it outside of this module, even in monorepo
 */
@ApiStatus.Internal
@PyInternalExecApi
open class PipPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)
  override val cliSpecs: List<PythonManagerCliSpec> = listOf(
    PythonManagerCliSpec("pip", { sdk.homePath?.let { Path.of(it) } }, runAsModule = true)
  )
  private val engine = PipPackageManagerEngine(project, sdk)

  override val dependenciesExporter: DependenciesExporter?
    get() = object : DependenciesExporter {
      override fun export(file: PsiFile) {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return
        syncWithImports(module)
      }
    }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> = engine.loadOutdatedPackagesCommand()

  override suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    module: Module?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> = engine.installPackageCommand(installRequest, options)

  override suspend fun syncLockedCommand(): PyResult<Unit> {
    val requirementsFile = getRootDependenciesFile()
    return if (requirementsFile != null) {
      engine.syncRequirementsTxt(requirementsFile.virtualFile)
    }
    else {
      engine.syncProject()
    }
  }

  suspend fun syncRequirementsTxt(requirementsFile: VirtualFile): PyResult<Unit> {
    engine.syncRequirementsTxt(requirementsFile).getOr {
      return it
    }
    return reloadPackages().mapSuccess { }
  }

  override suspend fun updatePackageCommand(
    vararg specifications: PythonRepositoryPackageSpecification,
  ): PyResult<Unit> = engine.updatePackageCommand(*specifications)

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?, dependencyGroup: PyDependencyGroup?): PyResult<Unit> = engine.uninstallPackageCommand(*pythonPackages, workspaceMember = workspaceMember, dependencyGroup = dependencyGroup)

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> = engine.loadPackagesCommand()

  override suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>>? {
    val requirementsFile = getRootDependenciesFile() ?: return null
    val requirements = readAction {
      PyRequirementParser.fromFile(requirementsFile.virtualFile)
    }
    return PyResult.success(requirements.mapNotNull { it.toPythonPackage() })
  }

  override val dependenciesFilesRelativePaths: List<Path>
    get() = listOf(
      PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT,
    )

  override suspend fun addDependencyImpl(requirement: PyRequirement): Boolean {
    val requirementsFile = getRootDependenciesFile() ?: return false
    return withContext(Dispatchers.EDT) {
      RequirementsTxtManipulationHelper.addToRequirementsTxt(project, requirementsFile.virtualFile, requirement.presentableText)
    }
  }
}

@ApiStatus.Internal
class PipManagementInstaller(private val sdk: Sdk, private val manager: PythonPackageManager) {
  private val languageLevel: LanguageLevel = sdk.version

  /**
   * This method is for local SDK only. It does nothing for remote SDK.
   */
  internal suspend fun installManagementIfNeeded(): PyResult<Unit> {
    if (PythonSdkUtil.isRemote(sdk)) return PyResult.success(Unit)
    // The bundled pip and setuptools wheels require MINIMUM_SUPPORTED_VENV_PYTHON_VERSION+. For older
    // interpreters we never attempt to install them (the install would fail anyway) and rely on the
    // management tools already present.
    if (languageLevel < MINIMUM_SUPPORTED_VENV_PYTHON_VERSION) return PyResult.success(Unit)
    if (hasManagement()) return PyResult.success(Unit)
    return installManagement()
  }

  suspend fun hasManagement(): Boolean =
    languageLevel < LanguageLevel.PYTHON27 || (hasPip() && hasSetuptools())

  private suspend fun installManagement(): PyResult<Unit> {
    if (!hasPip()) installWheel(WheelFiles.PIP_WHEEL_NAME).getOr { return it }
    if (!hasSetuptools()) installWheel(WheelFiles.SETUPTOOLS_WHEEL_NAME).getOr { return it }
    return PyResult.success(Unit)
  }

  private suspend fun installWheel(wheelName: String): PyResult<Unit> = withContext(Dispatchers.IO) {
    val pipPath = findPathInHelpers(WheelFiles.PIP_WHEEL_NAME).resolve(Path.of(PyPackageUtil.PIP))
    val wheelPath = findPathInHelpers(wheelName).toString()
    val pythonPath = requireNotNull(sdk.homePath).let { Path.of(it) }
    val args = Args(pipPath.toString(), "install", "--no-index", wheelPath)
    ExecService().execGetStdout(pythonPath, args).mapSuccess { }
  }

  private suspend fun hasPip(): Boolean = manager.hasInstalledPackage(PyPackageUtil.PIP)

  private suspend fun hasSetuptools(): Boolean =
    languageLevel >= LanguageLevel.PYTHON312 ||
    manager.hasInstalledPackage(PyPackageUtil.SETUPTOOLS) ||
    manager.hasInstalledPackage(PyPackageUtil.DISTRIBUTE)

  /**
   * pip and setuptools wheels bundled under `community/python/helpers`, used to bootstrap package
   * management into interpreters that lack it (see [installManagement]).
   *
   * Both are pinned to the **last release that still supports [MINIMUM_SUPPORTED_VENV_PYTHON_VERSION]**
   * (see the guard in [installManagementIfNeeded] and venv creation in `venv.kt`). Do not bump past these
   * without first raising that floor:
   *  - setuptools 75.3.4: 76.0.0 requires Python >= 3.9. (Must also stay >= 65.5.1, which fixed
   *    CVE-2022-40897.)
   *  - pip 25.0.1: 25.1 requires Python >= 3.9. (Note: pip < 25.3 is subject to CVE-2025-8869, whose
   *    fix is only in pip 25.3 / Python >= 3.9.)
   *
   * Update source: these are the exact wheels embedded in the bundled `virtualenv-py3.pyz`
   * (`virtualenv/seed/wheels/embed/`), i.e. the versions virtualenv itself seeds for Python 3.8.
   */
  private object WheelFiles {
    const val SETUPTOOLS_WHEEL_NAME = "setuptools-75.3.4-py3-none-any.whl"
    const val PIP_WHEEL_NAME = "pip-25.0.1-py3-none-any.whl"
  }

}
