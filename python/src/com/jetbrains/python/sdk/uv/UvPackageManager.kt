// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.PyProjectTomlFile
import com.intellij.python.pyproject.model.internal.workspaceBridge.getToolWorkspaceLayout
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.common.UV_TOOL_ID
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.uv.UV_LOCK
import com.jetbrains.python.packaging.packageRequirements.packagesUnavailable
import com.jetbrains.python.packaging.packageRequirements.cachedDependencyTree
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.orLogException
import com.jetbrains.python.packaging.management.PythonManagerCliSpec
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager.Companion.PackageManagerErrorMessage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.PythonWorkspaceSupport
import com.jetbrains.python.packaging.packageRequirements.CachedDependencyTreeProvider
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.WorkspaceMemberPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.collectAllNames
import com.jetbrains.python.packaging.packageRequirements.extractDeclaredDependencies
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.findModuleForSdk
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal class UvPackageManager internal constructor(
  project: Project,
  sdk: Sdk,
  uvExecutionContextDeferred: Deferred<UvExecutionContext<*>>,
) : PythonPackageManager(project, sdk) {
  override val workspaceSupport: PythonWorkspaceSupport = UvWorkspaceSupport(project, sdk)
  override val installedPackagesIncludeTransitive: Boolean = true
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)
  override val cliSpecs: List<PythonManagerCliSpec> = listOf(
    PythonManagerCliSpec("uv", { UvPyTool.getInstance().resolveExecutable(EelFileSystem(localEel))?.path })
  )
  override val treeProvider = cachedDependencyTree(
    lockFileName = UV_LOCK.value,
    dependencyFiles = { resolveDependencyFilesTree() },
  ) { withUv { uv -> uv.listProjectStructureTree() } }

  /**
   * What the environment holds, which is not the same as what the project declares. A package here
   * and missing from [treeProvider] was installed into the environment on its own.
   *
   * Cached like the project tree: the tool window refreshes several times over a session, and every
   * refresh would otherwise run `uv pip tree` again. Not through [cachedDependencyTree], because this tree is what
   * the environment holds and the dependency files do not say when that changes.
   */
  private val installedTreeProvider = CachedDependencyTreeProvider(fetchOutput = {
    withUv { uv -> uv.listAllPackagesTree() }
  })
  override val dependenciesFilesRelativePaths: List<Path>
    get() = listOf(
      Path.of(PY_PROJECT_TOML),
      PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT,
    )

  private lateinit var uvLowLevel: PyResult<UvLowLevel<*>>
  private val uvExecutionContextDeferred = uvExecutionContextDeferred.cancelWithManager()

  private suspend fun <T> withUv(action: suspend (UvLowLevel<*>) -> PyResult<T>): PyResult<T> {
    if (!this::uvLowLevel.isInitialized) {
      uvLowLevel = uvExecutionContextDeferred.await().createUvCli()
    }

    return when (val uvResult = uvLowLevel) {
      is Result.Success -> action(uvResult.result)
      is Result.Failure -> uvResult
    }
  }

  override suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    module: Module?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> {
    return withUv { uv ->
      if (sdk.uvUsePackageManagement) {
        uv.installPackage(installRequest, options)
      }
      else if (module != null) {
        val packageName = resolvePackageName(module)
        uv.addDependency(installRequest, options, PyWorkspaceMember(packageName), dependencyGroup)
      }
      else {
        uv.addDependency(installRequest, options, dependencyGroup = dependencyGroup)
      }
    }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications.toList())
    val result = installPackageCommand(request, emptyList())

    return result
  }

  override suspend fun uninstallPackageCommand(
    vararg pythonPackages: String,
    workspaceMember: PyWorkspaceMember?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> {
    return withUv { uv ->
      if (pythonPackages.isEmpty()) return@withUv PyResult.success(Unit)

      if (workspaceMember != null) {
        val packageNames = pythonPackages.map { PyPackageName.from(it) }
        uninstallDeclaredPackages(uv, packageNames, workspaceMember, dependencyGroup).getOr { return@withUv it }
        uv.lock().getOr { return@withUv it }
        uv.sync().getOr { return@withUv it }
        return@withUv PyResult.success(Unit)
      }

      val (standalonePackages, declaredPackages) = categorizePackages(pythonPackages).getOr {
        return@withUv it
      }

      uninstallStandalonePackages(uv, standalonePackages).getOr { return@withUv it }
      uninstallDeclaredPackages(uv, declaredPackages, null, dependencyGroup).getOr { return@withUv it }

      PyResult.success(Unit)
    }
  }

  override suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>> =
    treeProvider.getDependencyTrees().mapSuccess { extractDeclaredDependencies(it) }

  /**
   * Shows the `uv tree` output as uv prints it: one row per root, which is one row per project in
   * the workspace, and under each root exactly the dependencies uv listed for it.
   *
   * The declared set is not flattened into the top level. Doing that put a package that is only a
   * dependency of one member, such as `a2wsgi`, next to the members themselves (PY-90174).
   */
  override suspend fun getPackageTree(): PackageStructureNode {
    val roots = treeProvider.getDependencyTrees().getOr { return packagesUnavailable(it.error) }
    if (roots.isEmpty()) return PackageCollectionPackageStructureNode(emptyList(), emptyList())

    // Every package the output names. What is installed and missing from it is undeclared.
    val shownPackageNames = roots.flatMapTo(mutableSetOf()) { it.collectAllNames() }
    val undeclared = extractUndeclaredPackages(shownPackageNames)
    val projectNames = roots.mapTo(mutableSetOf()) { it.name.name }

    // A single project has nothing to group by, so its dependencies stay at the top level. Giving it
    // a row of its own would put every package one level down.
    val singleProject = roots.singleOrNull()
    if (singleProject != null) {
      return PackageCollectionPackageStructureNode(singleProject.children, undeclared, projectNames)
    }

    return WorkspaceMemberPackageStructureNode(
      name = roots.first().name.name,
      subMembers = roots.drop(1).map { WorkspaceMemberPackageStructureNode(it.name.name, emptyList(), it) },
      packageTree = roots.first(),
      undeclaredPackages = undeclared,
    )
  }

  private suspend fun extractUndeclaredPackages(shownPackageNames: Set<String>): List<PackageTreeNode> =
    installedTreeProvider.getDependencyTrees().orLogException(thisLogger()).orEmpty()
      .filter { it.name.name !in shownPackageNames }

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
  private suspend fun uninstallDeclaredPackages(
    uv: UvLowLevel<*>,
    packages: List<PyPackageName>,
    workspaceMember: PyWorkspaceMember?,
    dependencyGroup: PyDependencyGroup? = null,
  ): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      uv.removeDependencies(packages.map { it.name }.toTypedArray(), workspaceMember, dependencyGroup)
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

  override suspend fun syncLockedCommand(): PyResult<Unit> {
    return withUv { uv -> uv.sync().mapSuccess { } }
  }

  override fun syncErrorMessage(): PackageManagerErrorMessage =
    PackageManagerErrorMessage(
      message("python.uv.lockfile.out.of.sync"),
      message("python.uv.update.lock")
    )

  override suspend fun reloadPackages(): PyResult<List<PythonPackage>> {
    installedTreeProvider.invalidateCache()
    return super.reloadPackages()
  }

  suspend fun lock(): PyResult<Unit> {
    return withUv { uv ->
      uv.lock().getOr {
        return@withUv it
      }
      reloadPackages().mapSuccess { }
    }
  }

  override fun updateLockedAction(): suspend () -> PyResult<Unit> = suspend { syncLocked().mapSuccess { } }

  private suspend fun resolvePackageName(module: Module): String {
    val pyProjectFile = PyProjectToml.findPyProjectTomlFile(module) ?: return module.name
    return PyProjectToml.parseCached(module.project, pyProjectFile.virtualFile)?.project?.name ?: module.name
  }

  override suspend fun resolveDependencyFilesTree(): List<PyDependenciesFile> {
    val rootFile = getRootDependenciesFile() ?: return emptyList()
    val rootPyProjectToml = (rootFile as? PyProjectTomlFile) ?: return listOf(rootFile)

    val uvWorkingDirectory = uvExecutionContextDeferred.await().workingDir
    val memberModules = readAction {
      val rootModule = ModuleManager.getInstance(project).modules.firstOrNull { module ->
        ModuleRootManager.getInstance(module).contentRoots.any { it.toNioPath() == uvWorkingDirectory }
      } ?: return@readAction emptyList()
      rootModule.getToolWorkspaceLayout(UV_TOOL_ID)?.memberModules.orEmpty()
    }
    val memberFiles = memberModules.mapNotNull { member -> PyProjectToml.findPyProjectTomlFile(member) }
    return listOf(rootPyProjectToml) + memberFiles
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

private class UvWorkspaceSupport(private val project: Project, private val sdk: Sdk) : PythonWorkspaceSupport {
  override suspend fun getWorkspaceMembers(projectName: ProjectName): List<PyWorkspaceMember> {
    val modules = getProjectModules()
    if (modules.isEmpty()) return listOf(PyWorkspaceMember(projectName.name))
    return modules.map { module ->
      val tomlVf = readAction {
        ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.findFileByRelativePath(PY_PROJECT_TOML)
      }
      val name = if (tomlVf != null) PyProjectToml.parseCached(project, tomlVf)?.project?.name ?: module.name
      else module.name
      PyWorkspaceMember(name)
    }
  }

  override suspend fun getDependencyGroups(projectName: ProjectName): Map<PyWorkspaceMember, List<PyDependencyGroup>> {
    val modules = getProjectModules()
    val result = mutableMapOf<PyWorkspaceMember, List<PyDependencyGroup>>()
    for (module in modules) {
      val (name, groups) = parseModuleGroups(module) ?: continue
      result[PyWorkspaceMember(name)] = groups.map { PyDependencyGroup(it) }
    }
    return result
  }

  override suspend fun resolveModule(member: PyWorkspaceMember): Module? {
    return getProjectModules().firstOrNull { module ->
      val tomlVf = readAction {
        ModuleRootManager.getInstance(module).contentRoots
          .firstNotNullOfOrNull { it.findFileByRelativePath(PY_PROJECT_TOML) }
      }
      val name = if (tomlVf != null) PyProjectToml.parseCached(project, tomlVf)?.project?.name ?: module.name
      else module.name
      name == member.name
    }
  }

  private suspend fun getProjectModules(): List<Module> {
    return readAction {
      val modules = ModuleManager.getInstance(project).modules
      val layout = modules.firstNotNullOfOrNull { it.getToolWorkspaceLayout(UV_TOOL_ID) }
      if (layout != null) return@readAction listOf(layout.rootModule) + layout.memberModules
      listOfNotNull(project.findModuleForSdk(sdk))
    }
  }

  private suspend fun parseModuleGroups(module: Module): Pair<String, List<String>>? {
    val tomlVf = readAction {
      ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.findFileByRelativePath(PY_PROJECT_TOML)
    } ?: return null
    val parsed = PyProjectToml.parseCached(project, tomlVf) ?: return null
    return parsed.project.name to parsed.getDependencyGroupNames()
  }
}

internal class UvPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (!sdk.isUv) {
      return null
    }

    val uvExecutionContext = sdk.getUvExecutionContextAsync(PyPackageCoroutine.getScope(project), project) ?: return null
    return UvPackageManager(project, sdk, uvExecutionContext)
  }
}

