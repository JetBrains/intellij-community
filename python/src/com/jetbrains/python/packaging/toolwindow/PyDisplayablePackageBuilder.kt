// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.requirements.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.packageRequirements.FlatPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.PackagesUnavailableNode
import com.jetbrains.python.packaging.packageRequirements.WorkspaceMemberPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.collectAllNames
import com.jetbrains.python.packaging.packageRequirements.newNodeSet
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.ModuleDependencyDisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.findModuleForSdk
import org.jetbrains.annotations.ApiStatus

/**
 * A uv / poetry workspace member name — typed so lookups against the settings-side member set
 * cannot be crossed with a raw string. Shared with `PyPackagesTreePane`.
 */
@ApiStatus.Internal
@JvmInline
value class WorkspaceMemberName(val value: String)

/** A JPS-module dependency name rendered as a pseudo-package row. Typed for the same reason as [WorkspaceMemberName]. */
@ApiStatus.Internal
@JvmInline
value class ModuleDepName(val value: String)

/**
 * Shared build pipeline for the Python Packages tool window and the redesigned Interpreter Settings
 * "Dependencies" tab (PY-89840). Sources the same [PackageStructureNode] both surfaces already read
 * via [PythonPackageManager.getPackageTree], applies the same cycle / project-package guards, and
 * returns a flat [List] of [DisplayablePackage] that neither has shared nodes nor can cycle — so a
 * downstream renderer walks it plainly, without a graph-safe visit set of its own.
 *
 * The Settings view narrows the workspace to one member via [memberFilter]. The tool-window pane
 * leaves it `null` for the workspace-wide roll-up.
 */
@ApiStatus.Internal
data class PyDisplayablePackagesResult(
  val packages: List<DisplayablePackage>,
  val moduleAliasNames: Set<WorkspaceMemberName>,
  val jpsModuleDependencyNames: List<ModuleDepName>,
  val unavailable: PackagesUnavailableNode?,
)

/**
 * @param memberFilter when set on a uv / poetry workspace, keeps only the matching workspace member;
 *   `null` returns every member (tool-window behaviour).
 * @param moduleOrProject carries both the [Project] and the optional per-module context: pass
 *   [ModuleOrProject.ProjectOnly] from the tool window (no module scope), a
 *   [ModuleOrProject.ModuleAndProject] from the settings pane so the JPS `ModuleOrderEntry` roll-up
 *   uses the module the user is editing rather than the SDK's owner module.
 */
@ApiStatus.Internal
suspend fun buildDisplayablePackages(
  moduleOrProject: ModuleOrProject,
  manager: PythonPackageManager,
  memberFilter: String? = null,
): PyDisplayablePackagesResult {
  val packageTree = manager.getPackageTree()
  return when (packageTree) {
    is PackagesUnavailableNode -> PyDisplayablePackagesResult(emptyList(), emptySet(), emptyList(), packageTree)
    is WorkspaceMemberPackageStructureNode,
    is PackageCollectionPackageStructureNode,
    is FlatPackageStructureNode, -> buildAvailable(moduleOrProject, manager, packageTree, memberFilter)
  }
}

private suspend fun buildAvailable(
  moduleOrProject: ModuleOrProject,
  manager: PythonPackageManager,
  packageTree: PackageStructureNode,
  memberFilter: String?,
): PyDisplayablePackagesResult {
  val project = moduleOrProject.project
  val packageIndex = PyBuilderPackageIndex(manager)
  val declaredPackageNames = collectDeclaredNames(packageTree, packageIndex)
  // Union in every JPS module's PyProject alias so any row whose name matches an in-project
  // sub-project is treated as a project package. That flips its icon to the module glyph and stops
  // transitive expansion at the member row via `buildRequirements`' project-package guard.
  val aliasNames = computeModuleAliasNames(project)
  val projectPackageNames = collectProjectPackageNames(packageTree) + aliasNames.map { it.value }
  val ctx = PyPackageBuildContext(project, manager)
  val packages = buildPackages(ctx, packageTree, packageIndex, declaredPackageNames, projectPackageNames, newNodeSet(), memberFilter)
  val jpsModule = when (moduleOrProject) {
    is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
    is ModuleOrProject.ProjectOnly -> readAction { project.findModuleForSdk(manager.sdk) }
  }
  val jpsDeps = collectJpsModuleDependencyNames(project, jpsModule)
  return PyDisplayablePackagesResult(packages, aliasNames, jpsDeps, unavailable = null)
}

@ApiStatus.Internal
class PyPackageBuildContext(
  val project: Project,
  val manager: PythonPackageManager,
) {
  /** Convenience accessor — [PythonPackageManager] already owns its SDK. */
  val sdk: Sdk get() = manager.sdk
}

internal class PyBuilderPackageIndex(
  val installedByName: Map<String, PythonPackage>,
  val outdated: Map<String, PythonOutdatedPackage>,
) {
  companion object {
    suspend operator fun invoke(manager: PythonPackageManager): PyBuilderPackageIndex =
      PyBuilderPackageIndex(
        installedByName = manager.listInstalledPackages().associateBy { it.name },
        outdated = manager.listOutdatedPackages(),
      )
  }
}

/** The workspace members and project packages, which the tree marks apart from a PyPI package. */
internal fun collectProjectPackageNames(node: PackageStructureNode): Set<String> = when (node) {
  is WorkspaceMemberPackageStructureNode ->
    (listOf(node.name) + node.subMembers.map { it.name }).mapTo(mutableSetOf()) { PyPackageName.from(it).name }
  is PackageCollectionPackageStructureNode -> node.projectPackageNames
  FlatPackageStructureNode, is PackagesUnavailableNode -> emptySet()
}

internal fun collectDeclaredNames(node: PackageStructureNode, packageIndex: PyBuilderPackageIndex): Set<String> = when (node) {
  is PackageCollectionPackageStructureNode ->
    node.declaredPackages.flatMapTo(mutableSetOf()) { it.collectAllNames() }
  is WorkspaceMemberPackageStructureNode ->
    (listOfNotNull(node.packageTree) + node.subMembers.mapNotNull { it.packageTree })
      .flatMapTo(mutableSetOf()) { it.collectAllNames() }
  is FlatPackageStructureNode -> packageIndex.installedByName.keys
  is PackagesUnavailableNode -> emptySet()
}

internal suspend fun buildPackages(
  context: PyPackageBuildContext,
  node: PackageStructureNode,
  packageIndex: PyBuilderPackageIndex,
  declaredPackageNames: Set<String>,
  projectPackageNames: Set<String>,
  path: MutableSet<PackageTreeNode>,
  memberFilter: String? = null,
): List<DisplayablePackage> = when (node) {
  is WorkspaceMemberPackageStructureNode -> {
    val workspaceMembers = buildWorkspaceMembers(context, node, packageIndex, declaredPackageNames, projectPackageNames, memberFilter)
    val undeclared = if (memberFilter == null) {
      buildInstalledPackages(context, node.undeclaredPackages, packageIndex, declaredPackageNames, projectPackageNames, path)
    }
    else emptyList()
    val result = mutableListOf<DisplayablePackage>()
    if (undeclared.isNotEmpty()) result.add(UndeclaredPackagesGroup(undeclared))
    result.addAll(workspaceMembers)
    result
  }
  is PackageCollectionPackageStructureNode -> {
    val declared = buildInstalledPackages(context, node.declaredPackages, packageIndex, declaredPackageNames, projectPackageNames, path)
    val undeclared = buildInstalledPackages(context, node.undeclaredPackages, packageIndex, declaredPackageNames, projectPackageNames, path)
    val result = mutableListOf<DisplayablePackage>()
    if (undeclared.isNotEmpty()) result.add(UndeclaredPackagesGroup(undeclared))
    result.addAll(declared)
    result
  }
  is FlatPackageStructureNode -> buildPackagesFromManager(packageIndex, declaredPackageNames, projectPackageNames)
  is PackagesUnavailableNode -> emptyList()
}

internal fun buildPackagesFromManager(
  packageIndex: PyBuilderPackageIndex,
  declaredPackageNames: Set<String>,
  projectPackageNames: Set<String>,
): List<InstalledPackage> =
  packageIndex.installedByName.values.map { pkg ->
    val nextVersion = packageIndex.outdated[pkg.name]?.latestVersion?.let { PyPackageVersionNormalizer.normalize(it) }
    InstalledPackage(pkg, defaultRepositoryFor(pkg), nextVersion, emptyList(),
                     isDeclared = pkg.name in declaredPackageNames,
                     isProjectPackage = pkg.name in projectPackageNames)
  }

internal fun getBuilderSortPriority(pkg: DisplayablePackage): Int = when (pkg) {
  is UndeclaredPackagesGroup -> 0
  is WorkspaceMember -> 1
  is ModuleDependencyDisplayablePackage -> 1
  is DependencyGroupNode -> 1
  is InstalledPackage -> if (pkg.isDeclared) 1 else 2
  is RequirementPackage -> if (pkg.isDeclared) 1 else 2
  is InstallablePackage, is LoadingNode -> 3
}

/** Each member starts its own path, since a member row shows its own packages in full. */
internal suspend fun buildWorkspaceMembers(
  context: PyPackageBuildContext,
  root: WorkspaceMemberPackageStructureNode,
  packageIndex: PyBuilderPackageIndex,
  declaredPackageNames: Set<String>,
  projectPackageNames: Set<String>,
  memberFilter: String? = null,
): List<WorkspaceMember> {
  val members = mutableListOf<WorkspaceMemberPackageStructureNode>()
  root.packageTree?.let { members.add(root) }
  for (subMember in root.subMembers) {
    if (subMember.packageTree != null) members.add(subMember)
  }
  val selected = if (memberFilter == null) members else members.filter { it.name == memberFilter }
  return selected.mapNotNull { member ->
    member.packageTree?.let { packageTree ->
      buildWorkspaceMember(context, member.name, packageTree, packageIndex, declaredPackageNames, projectPackageNames,
                           newNodeSet().apply { add(packageTree) })
    }
  }
}

internal suspend fun buildWorkspaceMember(
  context: PyPackageBuildContext,
  memberName: String,
  tree: PackageTreeNode,
  packageIndex: PyBuilderPackageIndex,
  declaredPackageNames: Set<String>,
  projectPackageNames: Set<String>,
  path: MutableSet<PackageTreeNode>,
): WorkspaceMember {
  val member = PyWorkspaceMember(memberName)
  val packages = tree.children.distinctBy { it.name.name }.mapNotNull { node ->
    val pkg = packageIndex.installedByName[node.name.name] ?: return@mapNotNull null
    val repository = resolveRepository(context, pkg)
    val nextVersion = packageIndex.outdated[pkg.name]?.latestVersion?.let { PyPackageVersionNormalizer.normalize(it) }
    val requirements = if (pkg.name !in projectPackageNames && path.add(node)) {
      buildRequirements(node.children, packageIndex, repository, true, member, declaredPackageNames, projectPackageNames, path)
        .also { path.remove(node) }
    }
    else emptyList()
    InstalledPackage(pkg, repository, nextVersion, requirements, isDeclared = true, workspaceMember = member,
                     dependencyGroup = node.group?.let { PyDependencyGroup(it) },
                     isProjectPackage = pkg.name in projectPackageNames, extras = node.extras)
  }
  return WorkspaceMember(memberName, packages.sortedForDisplay(),
                         instance = packageIndex.installedByName[PyPackageName.from(memberName).name])
}

internal suspend fun buildInstalledPackages(
  context: PyPackageBuildContext,
  nodes: List<PackageTreeNode>,
  packageIndex: PyBuilderPackageIndex,
  declaredPackageNames: Set<String>,
  projectPackageNames: Set<String>,
  path: MutableSet<PackageTreeNode>,
  workspaceMember: PyWorkspaceMember? = null,
): List<InstalledPackage> =
  nodes.mapNotNull { node ->
    val pkg = packageIndex.installedByName[node.name.name] ?: return@mapNotNull null
    val repository = resolveRepository(context, pkg)
    val nextVersion = packageIndex.outdated[pkg.name]?.latestVersion?.let { PyPackageVersionNormalizer.normalize(it) }
    val isDeclared = pkg.name in declaredPackageNames
    val requirements = if (path.add(node)) {
      buildRequirements(node.children, packageIndex, repository, isDeclared, workspaceMember, declaredPackageNames, projectPackageNames, path)
        .also { path.remove(node) }
    }
    else emptyList()
    InstalledPackage(pkg, repository, nextVersion, requirements, isDeclared, workspaceMember,
                     dependencyGroup = node.group?.let { PyDependencyGroup(it) },
                     isProjectPackage = pkg.name in projectPackageNames, extras = node.extras)
  }.sortedForDisplay()

/**
 * Package-manager-agnostic repository lookup. Asks the manager for an explicit specification first,
 * then falls back to [PythonPackage.defaultRepository] — a polymorphic hook the package subtype
 * overrides. Conda's own repository handling lives on
 * [com.jetbrains.python.packaging.conda.CondaPackage], so the shared builder no longer sniffs the
 * concrete package type here.
 */
internal suspend fun resolveRepository(context: PyPackageBuildContext, pkg: PythonPackage): PyPackageRepository =
  context.manager.findPackageSpecification(pkg.name, pkg.version)?.repository ?: pkg.defaultRepository

internal fun defaultRepositoryFor(pkg: PythonPackage): PyPackageRepository = pkg.defaultRepository

internal fun buildRequirements(
  nodes: List<PackageTreeNode>,
  packageIndex: PyBuilderPackageIndex,
  repository: PyPackageRepository,
  isDeclared: Boolean,
  workspaceMember: PyWorkspaceMember?,
  declaredPackageNames: Set<String>,
  projectPackageNames: Set<String>,
  path: MutableSet<PackageTreeNode>,
): List<RequirementPackage> =
  nodes.mapNotNull { node ->
    val pkg = packageIndex.installedByName[node.name.name] ?: return@mapNotNull null
    val effectiveIsDeclared = pkg.name in declaredPackageNames || isDeclared
    val isProjectPackage = pkg.name in projectPackageNames
    val children = if (!isProjectPackage && path.add(node)) {
      buildRequirements(node.children, packageIndex, repository, effectiveIsDeclared,
                        workspaceMember, declaredPackageNames, projectPackageNames, path)
        .also { path.remove(node) }
    }
    else emptyList()
    RequirementPackage(pkg, repository, children, node.group, effectiveIsDeclared, workspaceMember,
                       isProjectPackage = isProjectPackage, extras = node.extras)
  }.sortedForDisplay()

internal fun <T : DisplayablePackage> List<T>.sortedForDisplay(): List<T> =
  sortedWith(compareBy({ if (it.isBuilderProjectPackage()) 0 else 1 }, { it.name.lowercase() }))

internal fun DisplayablePackage.isBuilderProjectPackage(): Boolean = when (this) {
  is InstalledPackage -> isProjectPackage
  is RequirementPackage -> isProjectPackage
  is WorkspaceMember,
  is UndeclaredPackagesGroup,
  is DependencyGroupNode,
  is InstallablePackage,
  is ModuleDependencyDisplayablePackage,
  is LoadingNode -> false
}

/**
 * Enumerates every [com.jetbrains.python.project.PyProject] the platform knows about and returns
 * the underlying JPS module name for each. Poetry / uv monorepo sub-projects surface here, so the
 * renderer can flip their icon to the Python module glyph even when the manager returns them as
 * ordinary `InstalledPackage` rows.
 *
 * Reads through the [PyProject] API instead of parsing `pyproject.toml` from disk — the API
 * already caches the workspace bridge.
 */
internal suspend fun computeModuleAliasNames(project: Project): Set<WorkspaceMemberName> =
  project.getPyProjects().mapTo(HashSet()) { WorkspaceMemberName(it.residesOnModule.name) }

internal fun collectJpsModuleDependencyNames(project: Project, module: Module?): List<ModuleDepName> {
  if (module == null) return emptyList()
  if (ModuleManager.getInstance(project).modules.size <= 1) return emptyList()
  return ModuleRootManager.getInstance(module).orderEntries
    .filterIsInstance<ModuleOrderEntry>()
    .mapNotNull { it.module?.name?.let { name -> ModuleDepName(name) } }
}
