// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.*
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlManager
import com.jetbrains.python.packaging.dependencies.PythonDependenciesManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementsTxtManager
import com.jetbrains.python.packaging.setupPy.SetupPyManager
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  override var installedPackages: List<PythonPackage> = DEFAULT_PACKAGES.toMutableList()
  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null
  private var packageVersions: Map<String, List<String>> = emptyMap()

  override val repositoryManager: TestPythonRepositoryManager
    get() = TestPythonRepositoryManager(project)
      .withPackageNames(packageNames)
      .withPackageDetails(packageDetails)
      .withRepoPackagesVersions(packageVersions)

  override fun getDependencyManager(): PythonDependenciesManager? {
    val data = sdk.getUserData(REQUIREMENTS_PROVIDER_KEY) ?: return null
    return when (data) {
      RequirementsProviderType.REQUIREMENTS_TXT -> PythonRequirementsTxtManager.getInstance(project, sdk)
      RequirementsProviderType.SETUP_PY -> SetupPyManager.getInstance(project, sdk)
      RequirementsProviderType.ENVIRONMENT_YML -> CondaEnvironmentYmlManager.getInstance(project, sdk)
    }
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    return PyResult.success(emptyList())
  }

  override suspend fun syncCommand(): PyResult<Unit> {
    return PyResult.success(Unit)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
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

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
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