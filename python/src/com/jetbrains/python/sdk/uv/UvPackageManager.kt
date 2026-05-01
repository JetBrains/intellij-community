// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.community.impl.uv.common.UV_TOOL_ID
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.internal.workspaceBridge.getToolWorkspaceLayout
import com.intellij.util.cancelOnDispose
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager.Companion.PackageManagerErrorMessage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.resolvePyProjectToml
import com.jetbrains.python.packaging.packageRequirements.CachedDependencyTreeProvider
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.TreeParser
import com.jetbrains.python.packaging.packageRequirements.WorkspaceMemberPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.collectAllNames
import com.jetbrains.python.packaging.packageRequirements.extractDeclaredDependencies
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
@VisibleForTesting
class UvPackageManager internal constructor(project: Project, sdk: Sdk, uvExecutionContextDeferred: Deferred<UvExecutionContext<*>>) : PythonPackageManager(project, sdk, installedPackagesIncludeTransitive = true) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)
  override val treeProvider = CachedDependencyTreeProvider {
    withUv { uv -> uv.listProjectStructureTree() }.getOrNull()
  }
  private lateinit var uvLowLevel: PyResult<UvLowLevel<*>>
  private val uvExecutionContextDeferred = uvExecutionContextDeferred.also { it.cancelOnDispose(this) }

  private suspend fun <T> withUv(action: suspend (UvLowLevel<*>) -> PyResult<T>): PyResult<T> {
    if (!this::uvLowLevel.isInitialized) {
      uvLowLevel = uvExecutionContextDeferred.await().createUvCli()
    }

    return when (val uvResult = uvLowLevel) {
      is Result.Success -> action(uvResult.result)
      is Result.Failure -> uvResult
    }
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>, module: Module?): PyResult<Unit> {
    return withUv { uv ->
      if (sdk.uvUsePackageManagement) {
        uv.installPackage(installRequest, emptyList())
      }
      else if (module != null) {
        val packageName = resolvePackageName(module)
        uv.addDependency(installRequest, emptyList(), PyWorkspaceMember(packageName))
      }
      else {
        uv.addDependency(installRequest, emptyList())
      }
    }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val specsWithoutVersion = specifications.map { it.copy(requirement = pyRequirement(it.name, null)) }
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specsWithoutVersion)
    val result = installPackageCommand(request, emptyList())

    return result
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    return withUv { uv ->
      if (pythonPackages.isEmpty()) return@withUv PyResult.success(Unit)

      if (workspaceMember != null) {
        val packageNames = pythonPackages.map { PyPackageName.from(it) }
        uninstallDeclaredPackages(uv, packageNames, workspaceMember).getOr { return@withUv it }
        uv.lock().getOr { return@withUv it }
        uv.sync().getOr { return@withUv it }
        return@withUv PyResult.success(Unit)
      }

      val (standalonePackages, declaredPackages) = categorizePackages(pythonPackages).getOr {
        return@withUv it
      }

      uninstallStandalonePackages(uv, standalonePackages).getOr { return@withUv it }
      uninstallDeclaredPackages(uv, declaredPackages, null).getOr { return@withUv it }

      PyResult.success(Unit)
    }
  }

  override suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>> {
    return declaredPackagesFromTrees(treeProvider.getDependencyTrees())
  }

  private fun declaredPackagesFromTrees(trees: List<PackageTreeNode>): PyResult<List<PythonPackage>> {
    if (trees.isEmpty()) return PyResult.success(emptyList())
    return PyResult.success(extractDeclaredDependencies(trees))
  }

  override suspend fun getPackageTree(): PackageStructureNode {
    val allTrees = treeProvider.getDependencyTrees()
    val declaredPackageNames = declaredPackagesFromTrees(allTrees).getOrNull()
      ?.mapTo(mutableSetOf()) { it.name } ?: emptySet()

    val workspaceTree = buildWorkspaceStructure(allTrees, declaredPackageNames)
    if (workspaceTree != null) return workspaceTree

    val declaredPackages = extractDeclaredPackagesFromParsedTrees(allTrees, declaredPackageNames)
    val undeclaredPackages = extractUndeclaredPackages(declaredPackageNames)
    return PackageCollectionPackageStructureNode(declaredPackages, undeclaredPackages)
  }

  private fun extractDeclaredPackagesFromParsedTrees(
    allTrees: List<PackageTreeNode>,
    declaredPackageNames: Set<String>,
  ): List<PackageTreeNode> {
    val projectRoot = allTrees.firstOrNull()
      ?: return declaredPackageNames.map { createLeafNode(it) }
    val childrenByName = projectRoot.children.associateBy { it.name.name }
    return declaredPackageNames.map { name -> childrenByName[name] ?: createLeafNode(name) }
  }

  private fun createLeafNode(packageName: String): PackageTreeNode =
    PackageTreeNode(PyPackageName.from(packageName))

  private suspend fun buildWorkspaceStructure(
    allTrees: List<PackageTreeNode>,
    declaredPackageNames: Set<String>,
  ): WorkspaceMemberPackageStructureNode? {
    val (rootName, subMemberNames) = getWorkspaceLayout() ?: return null

    val allMemberNames = (setOf(rootName) + subMemberNames).mapTo(mutableSetOf()) { PyPackageName.from(it).name }

    val treesByName = allTrees.associateBy { it.name.name }
    val rootTree = (treesByName[PyPackageName.from(rootName).name] ?: createLeafNode(rootName)).filterOutMembers(allMemberNames)
    val subMembers = subMemberNames.map { name ->
      val tree = (treesByName[PyPackageName.from(name).name] ?: createLeafNode(name)).filterOutMembers(allMemberNames)
      WorkspaceMemberPackageStructureNode(name, emptyList(), tree)
    }

    val shownPackageNames = collectAllPackageNames(rootTree, subMembers)
    val undeclared = extractUndeclaredPackages(declaredPackageNames)
      .filter { it.name.name !in shownPackageNames }

    return WorkspaceMemberPackageStructureNode(rootName, subMembers, rootTree, undeclared)
  }

  private fun PackageTreeNode.filterOutMembers(memberNames: Set<String>): PackageTreeNode {
    val filteredChildren = children
      .filter { it.name.name !in memberNames }
      .map { it.filterOutMembers(memberNames) }
    return PackageTreeNode(name, filteredChildren.toMutableList(), group, version)
  }

  private suspend fun getWorkspaceLayout(): Pair<String, List<String>>? {
    val uvWorkingDirectory = uvExecutionContextDeferred.await().workingDir
    val layout = readAction {
      val module = ModuleManager.getInstance(project).modules.firstOrNull { module ->
        ModuleRootManager.getInstance(module).contentRoots.any { it.toNioPath() == uvWorkingDirectory }
      } ?: return@readAction null
      module.getToolWorkspaceLayout(UV_TOOL_ID)
    } ?: return null

    val rootName = resolvePackageName(layout.rootModule)
    val memberNames = layout.memberModules.map { resolvePackageName(it) }
    return rootName to memberNames
  }

  private fun collectAllPackageNames(rootTree: PackageTreeNode?, subMembers: List<WorkspaceMemberPackageStructureNode>): Set<String> {
    val trees = listOfNotNull(rootTree) + subMembers.mapNotNull { it.packageTree }
    return trees.flatMapTo(mutableSetOf()) { it.collectAllNames() }
  }

  private suspend fun extractUndeclaredPackages(declaredPackageNames: Set<String>): List<PackageTreeNode> {
    val output = withUv { uv -> uv.listAllPackagesTree() }.getOrNull() ?: return emptyList()
    return TreeParser.parseTrees(output.lines())
      .filter { it.name.name !in declaredPackageNames }
  }

  /**
   * Categorizes packages into standalone packages and pyproject.toml declared packages.
   */
  private suspend fun categorizePackages(packages: Array<out String>): PyResult<Pair<List<PyPackageName>, List<PyPackageName>>> {
    val dependencyNames = listDeclaredPackages().getOr { return it }.map { it.name }

    val categorizedPackages = packages
      .map { PyPackageName.from(it) }
      .partition { it.name !in dependencyNames || sdk.uvUsePackageManagement }

    return PyResult.success(categorizedPackages)
  }

  /**
   * Uninstalls standalone packages using UV package manager.
   */
  private suspend fun uninstallStandalonePackages(uv: UvLowLevel<*>, packages: List<PyPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      uv.uninstallPackages(packages.map { it.name }.toTypedArray())
    }
    else {
      PyResult.success(Unit)
    }
  }

  /**
   * Removes declared dependencies using UV package manager.
   */
  private suspend fun uninstallDeclaredPackages(uv: UvLowLevel<*>, packages: List<PyPackageName>, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      uv.removeDependencies(packages.map { it.name }.toTypedArray(), workspaceMember)
    }
    else {
      PyResult.success(Unit)
    }
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    return withUv { uv -> uv.listPackages() }
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    return withUv { uv -> uv.listOutdatedPackages() }
  }

  override suspend fun syncCommand(): PyResult<Unit> {
    return withUv { uv -> uv.sync().mapSuccess { } }
  }

  override fun syncErrorMessage(): PackageManagerErrorMessage =
    PackageManagerErrorMessage(
      message("python.uv.lockfile.out.of.sync"),
      message("python.uv.update.lock")
    )

  suspend fun lock(): PyResult<Unit> {
    return withUv { uv ->
      uv.lock().getOr {
        return@withUv it
      }
      reloadPackages().mapSuccess { }
    }
  }

  private suspend fun resolvePackageName(module: Module): String {
    val pyProjectFile = PyProjectToml.findFile(module) ?: return module.name
    return PyProjectToml.parseCached(module.project, pyProjectFile)?.project?.name ?: module.name
  }

  // TODO PY-87712 Double check for remotes
  override fun getDependencyFile(): VirtualFile? {
    val uvWorkingDirectory = runBlockingMaybeCancellable { uvExecutionContextDeferred.await().workingDir }
    return resolvePyProjectToml(uvWorkingDirectory)
  }

  override suspend fun getDependencyFiles(): List<VirtualFile> {
    val rootFile = getDependencyFile() ?: return emptyList()
    val uvWorkingDirectory = uvExecutionContextDeferred.await().workingDir
    val memberModules = readAction {
      val rootModule = ModuleManager.getInstance(project).modules.firstOrNull { module ->
        ModuleRootManager.getInstance(module).contentRoots.any { it.toNioPath() == uvWorkingDirectory }
      } ?: return@readAction emptyList()
      rootModule.getToolWorkspaceLayout(UV_TOOL_ID)?.memberModules.orEmpty()
    }
    val memberFiles = memberModules.mapNotNull { PyProjectToml.findFile(it) }
    return listOf(rootFile) + memberFiles
  }

  override suspend fun addDependencyImpl(requirement: PyRequirement): Boolean = withContext(Dispatchers.IO) {
    val specification = repositoryManager.findPackageSpecification(requirement) ?: return@withContext false
    
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(specification))

    withUv { uv ->
        uv.addDependency(request, emptyList())
    }.getOr { return@withContext false }

    return@withContext true
  }
}

class UvPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (!sdk.isUv) {
      return null
    }

    val uvExecutionContext = sdk.getUvExecutionContextAsync(PyPackageCoroutine.getScope(project), project) ?: return null
    return UvPackageManager(project, sdk, uvExecutionContext)
  }
}

