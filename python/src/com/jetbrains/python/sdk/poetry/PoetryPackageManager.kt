// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PyDependencyGroupName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.resolvePyProjectToml
import com.jetbrains.python.packaging.packageRequirements.CachedDependencyTreeProvider
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.TreeParser
import com.jetbrains.python.packaging.packageRequirements.collectAllNames
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.sdk.associatedModulePath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@ApiStatus.Internal
class PoetryPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk, installedPackagesIncludeTransitive = true) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)
  override val treeProvider = CachedDependencyTreeProvider {
    runPoetryWithSdk(sdk, "show", "--tree").getOrNull()
  }

  override suspend fun syncCommand(): PyResult<Unit> {
    return runPoetryWithSdk(sdk, "install").mapSuccess { }
  }

  suspend fun updateProject(): PyResult<Unit> {
    runPoetryWithSdk(sdk, "update").getOr {
      return it
    }
    return reloadPackages().mapSuccess { }
  }

  suspend fun lockProject(): PyResult<Unit> {
    runPoetryWithSdk(sdk, "lock").getOr {
      return it
    }
    return reloadPackages().mapSuccess { }
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>, module: Module?): PyResult<Unit> =
    when (installRequest) {
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications ->
        addPackages(installRequest.specifications, options)
      is PythonPackageInstallRequest.ByLocation -> PyResult.localizedError(PyBundle.message("python.sdk.poetry.supports.installing.only.packages.from.repositories"))
    }

  override suspend fun installPackageDetachedCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> =
    when (installRequest) {
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications ->
        installPackages(installRequest.specifications, options)
      is PythonPackageInstallRequest.ByLocation -> PyResult.localizedError(PyBundle.message("python.sdk.poetry.supports.installing.only.packages.from.repositories"))
    }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    return addPackages(specifications.map { it.copy(requirement = pyRequirement(it.name, null)) }, emptyList())
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    if (pythonPackages.isEmpty()) return PyResult.success(Unit)

    val (standalonePackages, declaredPackages) = categorizePackages(pythonPackages).getOr {
      return it
    }

    uninstallDeclaredPackages(declaredPackages).getOr { return it }
    uninstallStandalonePackages(standalonePackages).getOr { return it }

    return PyResult.success(Unit)
  }

  /**
   * Returns declared dependencies parsed from `poetry show --tree`.
   * This intentionally excludes standalone pip-installed packages — those are
   * handled separately in [getPackageTree] as undeclared/standalone packages.
   */
  override suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>> {
    return declaredPackagesFromTrees(treeProvider.getDependencyTrees())
  }

  private suspend fun declaredPackagesFromTrees(trees: List<PackageTreeNode>): PyResult<List<PythonPackage>> {
    if (trees.isEmpty()) return PyResult.success(emptyList())

    val allPackages = trees.map { PythonPackage(it.name.name, it.version ?: "", false) }

    val mainOutput = runPoetryWithSdk(sdk, "show", "--only", "main", "--tree")
      .getOrNull() ?: return PyResult.success(allPackages)
    val mainNames = TreeParser.parseTrees(mainOutput.lines()).mapTo(mutableSetOf()) { it.name.name }

    val annotated = allPackages.map { pkg ->
      if (pkg.name in mainNames) pkg
      else PythonPackage(pkg.name, pkg.version, pkg.isEditableMode, NON_MAIN_DEPENDENCY_GROUP)
    }

    return PyResult.success(annotated)
  }

  /**
   * Categorizes packages into standalone packages and pyproject.toml declared packages.
   */
  private suspend fun categorizePackages(packages: Array<out String>): PyResult<Pair<List<PyPackageName>, List<PyPackageName>>> {
    val dependencyNames = listDeclaredPackages().getOr {
      return it
    }.map { it.name }

    val categorizedPackages = packages
      .map { PyPackageName.from(it) }
      .partition { it.name !in dependencyNames }

    return PyResult.success(categorizedPackages)
  }

  /**
   * Uninstalls packages using pip through Poetry.
   */
  private suspend fun uninstallStandalonePackages(packages: List<PyPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      poetryUninstallPackage(
        sdk = sdk,
        packages = packages.map { it.name }.toTypedArray()
      ).mapSuccess { }
    }
    else {
      PyResult.success(Unit)
    }
  }

  /**
   * Removes packages declared in pyproject.toml using Poetry.
   */
  private suspend fun uninstallDeclaredPackages(packages: List<PyPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      poetryRemovePackage(
        sdk = sdk,
        packages = packages.map { it.name }.toTypedArray()
      ).mapSuccess { }
    }
    else {
      PyResult.success(Unit)
    }
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val (installed, _) = poetryListPackages(sdk).getOr { return it }

    val packages = installed.map {
      PythonPackage(it.name, it.version, false)
    }

    return PyResult.success(packages)
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> = poetryShowOutdated(sdk).mapSuccess {
    it.values.toList()
  }

  private suspend fun addPackages(
    packageSpecifications: List<PythonRepositoryPackageSpecification>,
    options: List<String>,
  ): PyResult<Unit> {
    val specifications = packageSpecifications.map {
      it.getPackageWithVersionInPoetryFormat()
    }

    return poetryInstallPackage(sdk, specifications, options).mapSuccess { }
  }

  private suspend fun installPackages(
    packageSpecifications: List<PythonRepositoryPackageSpecification>,
    options: List<String>,
  ): PyResult<Unit> {
    val specifications = packageSpecifications.map {
      it.getPackageWithVersionInPoetryFormat()
    }

    return poetryInstallPackageDetached(sdk, specifications, options).mapSuccess { }
  }

  private fun PythonRepositoryPackageSpecification.getPackageWithVersionInPoetryFormat(): String {
    return versionSpec?.let { "$name@${it.presentableText}" } ?: name
  }

  override suspend fun getPackageTree(): PackageStructureNode {
    val allTrees = treeProvider.getDependencyTrees()
    if (allTrees.isEmpty()) return PackageCollectionPackageStructureNode(emptyList(), emptyList())
    val declaredPackageNames = declaredPackagesFromTrees(allTrees).getOrNull()
      ?.mapTo(mutableSetOf()) { it.name } ?: emptySet()
    val declared = allTrees.filter { it.name.name in declaredPackageNames }
    val undeclared = allTrees.filter { it.name.name !in declaredPackageNames }

    val treePackageNames = allTrees.flatMapTo(mutableSetOf()) { it.collectAllNames() }

    val standalonePackages = listInstalledPackages()
      .filter { it.name !in treePackageNames }
      .map { PackageTreeNode(PyPackageName.from(it.name)) }

    return PackageCollectionPackageStructureNode(declared, undeclared + standalonePackages)
  }

  override fun getDependencyFile(): VirtualFile? {
    val projectPathStr = sdk.associatedModulePath ?: return null
    val projectPath = Path.of(projectPathStr)
    return resolvePyProjectToml(projectPath)
  }

  override suspend fun addDependencyImpl(requirement: PyRequirement): Boolean {
    poetryInstallPackage(sdk, listOf(requirement.presentableText), emptyList()).getOr { return false }
    return true
  }

  companion object {
    private val NON_MAIN_DEPENDENCY_GROUP = PyDependencyGroupName("dev")
  }
}

/**
 * Parses the output of `poetry show` into a list of packages.
 */

@TestOnly
fun parsePoetryShowOutdatedTest(input: String): Map<String, PythonOutdatedPackage> {
  return parsePoetryShowOutdated(input)
}