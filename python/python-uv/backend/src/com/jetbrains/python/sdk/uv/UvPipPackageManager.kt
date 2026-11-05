// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.uv.backend.PyUvBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.cachedDependencyTree
import com.jetbrains.python.packaging.packageRequirements.newNodeSet
import com.jetbrains.python.packaging.packageRequirements.packagesUnavailable
import com.jetbrains.python.packaging.requirementsTxt.addRequirement
import com.jetbrains.python.packaging.requirementsTxt.readDeclaredPackages
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.Path

/**
 * The uv package manager of an SDK in [UvMode.Pip]: a plain environment with no `pyproject.toml` and no lock file.
 *
 * Every command goes through `uv pip`. The requirements file, when the SDK has one, is the declared list. Install and
 * sync are two separate operations. `uv pip sync` removes what the file does not name.
 */
@ApiStatus.Internal
class UvPipPackageManager internal constructor(
  project: Project,
  interpreter: PythonInterpreter,
  uvExecutionContextDeferred: Deferred<UvExecutionContext<*>>,
) : UvPackageManagerBase(project, interpreter, uvExecutionContextDeferred) {
  /** What the environment holds, as `uv pip tree` prints it. The requirements file alone keys the cache. */
  override val treeProvider = cachedDependencyTree(dependencyFiles = { resolveDependencyFilesTree() }) {
    withUv { uv -> uv.listAllPackagesTree() }
  }

  override val dependenciesFilesRelativePaths: List<Path>
    get() = listOf(PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT)

  /** Pip mode only. A stored `pyproject.toml` selects [UvPackageManager]. */
  override fun matchesSdk(): Boolean = when (sdk.uvMode) {
    UvMode.Project -> false
    is UvMode.Pip -> true
  }

  override suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    module: Module?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> = withUv { uv -> uv.installPackage(installRequest, options) }

  override suspend fun uninstallPackageCommand(
    vararg pythonPackages: String,
    workspaceMember: PyWorkspaceMember?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> {
    if (pythonPackages.isEmpty()) return PyResult.success(Unit)
    return withUv { uv -> uv.uninstallPackages(pythonPackages) }
  }

  override suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>>? =
    getRootDependenciesFile()?.readDeclaredPackages()

  /**
   * The tree in the shape `uv tree` gives a project. The requirements file stands for the project: the packages it
   * names are the top level, each with the subtree `uv pip tree` printed for it. A root of `uv pip tree` that the file
   * does not name is undeclared. Without a requirements file every root is declared.
   */
  override suspend fun getPackageTree(): PackageStructureNode {
    val roots = treeProvider.getDependencyTrees().getOr { return packagesUnavailable(it.error) }
    val declared = listDeclaredPackagesCached()?.successOrNull
                   ?: return PackageCollectionPackageStructureNode(roots, emptyList())
    val installedByName = collectNodesByName(roots)
    // A requirement can name extras, as in `httpx[http2]`. The tree names the bare package.
    val declaredNames = declared.mapTo(LinkedHashSet()) { PyPackageName.from(it.name.substringBefore('[')).name }
    val declaredNodes = declaredNames.mapNotNull { installedByName[it] }
    val undeclaredRoots = roots.filter { it.name.name !in declaredNames }
    return PackageCollectionPackageStructureNode(declaredNodes, undeclaredRoots)
  }

  override suspend fun syncLockedCommand(): PyResult<Unit> {
    val requirementsFile = getRootDependenciesFile()
                           ?: return PyResult.localizedError(message("python.uv.pip.requirements.file.missing"))
    return withUv { uv -> uv.installRequirements(requirementsFile.virtualFile.toNioPath()) }
  }

  override fun updateLockedAction(): suspend () -> PyResult<Unit> = suspend { syncLocked().mapSuccess { } }

  override fun updateLockedActionName(): @Nls String = message("python.uv.pip.install.requirements")

  /** Runs `uv init --bare --no-project` in the working directory when it has no `pyproject.toml`. */
  suspend fun initProjectIfNeeded(): PyResult<Unit> {
    val workingDir = uvExecutionContextDeferred.await().workingDir
    if (!UvMode.Project.needsInit(workingDir)) return PyResult.success(Unit)
    return withUv { uv -> uv.initProject(version = null) }
  }

  /** Runs `uv pip install -r` for [requirementsFile], then reloads. Adds what the file names and removes nothing. */
  suspend fun installRequirements(requirementsFile: VirtualFile): PyResult<Unit> =
    runAndReload { uv -> uv.installRequirements(requirementsFile.toNioPath()) }

  /** Runs `uv pip sync` for [requirementsFile], then reloads. Removes every package that the file does not name. */
  suspend fun syncRequirements(requirementsFile: VirtualFile): PyResult<Unit> =
    runAndReload { uv -> uv.syncRequirements(requirementsFile.toNioPath()) }

  private suspend fun runAndReload(action: suspend (UvLowLevel<*>) -> PyResult<Unit>): PyResult<Unit> {
    withUv(action).getOr { return it }
    return reloadPackages().mapSuccess { }
  }

  override suspend fun addDependencyImpl(requirement: PyRequirement): Boolean =
    getRootDependenciesFile()?.addRequirement(project, requirement) ?: false
}

/** Every node of the graph under [roots], by package name. The first node found with a name keeps it. */
private fun collectNodesByName(roots: List<PackageTreeNode>): Map<String, PackageTreeNode> {
  val result = LinkedHashMap<String, PackageTreeNode>()
  val visited = newNodeSet()
  val toVisit = ArrayDeque(roots)
  while (toVisit.isNotEmpty()) {
    val node = toVisit.removeLast()
    if (visited.add(node)) {
      result.putIfAbsent(node.name.name, node)
      toVisit.addAll(node.children)
    }
  }
  return result
}
