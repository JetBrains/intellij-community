// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.toPythonPackage
import com.jetbrains.python.packaging.common.toPythonPackages
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.packaging.setupPy.SetupPyHelpers
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.associatedModuleDir
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null
  private var packageVersions: Map<String, List<String>> = emptyMap()

  init {
    installedPackages = DEFAULT_PACKAGES.toMutableList()
  }

  override val repositoryManager: TestPythonRepositoryManager
    get() = TestPythonRepositoryManager(project)
      .withPackageNames(packageNames)
      .withPackageDetails(packageDetails)
      .withRepoPackagesVersions(packageVersions)

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    return PyResult.success(emptyList())
  }

  override suspend fun syncCommand(): PyResult<Unit> {
    return PyResult.success(Unit)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>, module: Module?): PyResult<Unit> {
    if (installRequest !is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications) {
      return PyResult.localizedError("Test Manager supports only simple repository package specification")
    }

    val specification = installRequest.specifications.single()
    return if (repositoryManager.allPackages().contains(specification.name)) {
      val version = specification.versionSpec?.version.orEmpty()
      installedPackages += PythonPackage(specification.name, version, false)
      PyResult.success(Unit)
    }
    else {
      PyResult.localizedError(PACKAGE_INSTALL_FAILURE_MESSAGE)
    }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    return PyResult.success(Unit)
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    pythonPackages.forEach { pyPackage ->
      val packageToRemove = findPackageByName(pyPackage)
                            ?: return PyResult.localizedError(PACKAGE_UNINSTALL_FAILURE_MESSAGE)
      installedPackages -= packageToRemove
    }

    return PyResult.success(Unit)
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    return PyResult.success(installedPackages)
  }

  override fun getDependencyFile(): VirtualFile? {
    val providerType = sdk.getUserData(REQUIREMENTS_PROVIDER_KEY) ?: return null
    val moduleDir = sdk.associatedModuleDir ?: return null

    return when (providerType) {
      RequirementsProviderType.REQUIREMENTS_TXT -> moduleDir.findChild("requirements.txt")
      RequirementsProviderType.SETUP_PY -> moduleDir.findChild("setup.py")
      RequirementsProviderType.ENVIRONMENT_YML -> moduleDir.findChild("environment.yml")
    }
  }

  override suspend fun extractDependencies(): PyResult<List<PythonPackage>>? {
    val providerType = sdk.getUserData(REQUIREMENTS_PROVIDER_KEY) ?: return null
    val moduleDir = sdk.associatedModuleDir ?: return null

    return when (providerType) {
      RequirementsProviderType.REQUIREMENTS_TXT -> {
        val requirementsFile = moduleDir.findChild("requirements.txt") ?: return null
        extractFromRequirementsTxt(requirementsFile)
      }
      RequirementsProviderType.SETUP_PY -> {
        val setupPyFile = moduleDir.findChild("setup.py") ?: return null
        extractFromSetupPy(setupPyFile)
      }
      RequirementsProviderType.ENVIRONMENT_YML -> {
        val environmentYmlFile = moduleDir.findChild("environment.yml") ?: return null
        extractFromEnvironmentYml(environmentYmlFile)
      }
    }
  }

  private suspend fun extractFromRequirementsTxt(file: VirtualFile): PyResult<List<PythonPackage>> {
    val requirements = readAction {
      PyRequirementParser.fromFile(file)
    }
    return PyResult.success(requirements.mapNotNull { requirement -> requirement.toPythonPackage() })
  }

  private suspend fun extractFromSetupPy(file: VirtualFile): PyResult<List<PythonPackage>>? {
    val requirements = readAction {
      val pyFile = PsiManager.getInstance(project).findFile(file) as? PyFile ?: return@readAction null
      SetupPyHelpers.parseSetupPy(pyFile)
    } ?: return null
    return PyResult.success(requirements.map { requirement -> requirement.toPythonPackage() })
  }

  private suspend fun extractFromEnvironmentYml(file: VirtualFile): PyResult<List<PythonPackage>>? {
    val requirements = CondaEnvironmentYmlParser.fromFile(file) ?: return null
    return PyResult.success(requirements.toPythonPackages())
  }

  private fun findPackageByName(name: String): PythonPackage? {
    return installedPackages.find { it.name == name }
  }

  fun withRepoPackagesVersions(packageVersions: Map<String, List<String>>): TestPythonPackageManager {
    this.packageVersions = packageVersions
    return this
  }


  fun withPackageNames(packageNames: List<String>): TestPythonPackageManager {
    this.packageNames = packageNames
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails?): TestPythonPackageManager {
    this.packageDetails = details
    return this
  }

  fun withPackageInstalled(packages: List<PythonPackage>): TestPythonPackageManager {
    this.installedPackages = packages
    return this
  }

  companion object {
    @JvmField
    val REQUIREMENTS_PROVIDER_KEY: Key<RequirementsProviderType> = Key<RequirementsProviderType>("REQUIREMENTS_PROVIDER_KEY")

    private val DEFAULT_PACKAGES = listOf(
      PythonPackage(PIP_PACKAGE, EMPTY_STRING, false),
      PythonPackage(SETUP_TOOLS_PACKAGE, EMPTY_STRING, false)
    )

    private const val PIP_PACKAGE = "pip"
    private const val SETUP_TOOLS_PACKAGE = "setuptools"
    private const val PACKAGE_INSTALL_FAILURE_MESSAGE = "Failed to install package"
    private const val PACKAGE_UNINSTALL_FAILURE_MESSAGE = "No such package found"
    private const val EMPTY_STRING = ""
  }
}