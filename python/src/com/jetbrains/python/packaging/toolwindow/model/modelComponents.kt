// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.model

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.PythonPackage
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus

sealed class DisplayablePackage(val name: @NlsSafe String, open val repository: PyPackageRepository?) {
  open fun getRequirements(): List<DisplayablePackage> = emptyList()
}

/**
 * Represents a package that is currently installed in the Python environment.
 *
 * @param instance The underlying Python package instance
 * @param repository The repository where this package can be updated from
 * @param nextVersion The next available version for update (if any)
 * @param requirements List of packages that this package depends on
 * @param isDeclared True if explicitly declared in project files (requirements.txt, pyproject.toml), false if transitive dependency
 * @param workspaceMember The workspace member this package belongs to (for uv/Poetry workspaces)
 * @param dependencyGroup The dependency group this package belongs to (e.g. "dev"), null for the default group
 */
class InstalledPackage(
  val instance: PythonPackage,
  repository: PyPackageRepository?,
  val nextVersion: PyPackageVersion? = null,
  private val requirements: List<RequirementPackage>,
  val isDeclared: Boolean = true,
  val workspaceMember: PyWorkspaceMember? = null,
  val dependencyGroup: PyDependencyGroup? = null,
) : DisplayablePackage(instance.name, repository) {
  val currentVersion: PyPackageVersion? = PyPackageVersionNormalizer.normalize(instance.version)

  val isEditMode: Boolean = instance.isEditableMode

  val canBeUpdated: Boolean
    get() {
      currentVersion ?: return false
      return nextVersion != null && PyPackageVersionComparator.compare(nextVersion, currentVersion) > 0
    }

  override fun getRequirements(): List<DisplayablePackage> = requirements
}

@ApiStatus.Internal
class WorkspaceMember(
  name: String,
  private val packages: List<DisplayablePackage>,
) : DisplayablePackage(name, null) {
  override fun getRequirements(): List<DisplayablePackage> = packages
}

@ApiStatus.Internal
class UndeclaredPackagesGroup(
  private val packages: List<InstalledPackage>,
) : DisplayablePackage(PyBundle.message("python.toolwindow.packages.not.added.to.pyproject"), null) {
  override fun getRequirements(): List<DisplayablePackage> = packages
}

@ApiStatus.Internal
class DependencyGroupNode(
  groupName: String,
  private val packages: List<DisplayablePackage>,
) : DisplayablePackage(groupName, null) {
  override fun getRequirements(): List<DisplayablePackage> = packages
}

class RequirementPackage(
  val instance: PythonPackage,
  override val repository: PyPackageRepository,
  private val requirements: List<RequirementPackage> = emptyList(),
  val group: String? = null,
  val isDeclared: Boolean = true,
  val workspaceMember: PyWorkspaceMember? = null
) : DisplayablePackage(instance.name, repository) {
  override fun getRequirements(): List<DisplayablePackage> = requirements
}

class InstallablePackage(name: String, override val repository: PyPackageRepository) : DisplayablePackage(name, repository)

class LoadingNode : DisplayablePackage("", null)

open class PyPackagesViewData(
  val repository: PyPackageRepository,
  val result: PythonPackageSearchResult,
  val pageIndex: Int,
  val displayable: List<DisplayablePackage>,
  val exactMatch: Int = -1,
  /**
   * Full sorted list of matches for the current query (across all pages), filtered to exclude
   * already-installed packages. Used by the tree to paginate locally on scroll so the visible
   * order stays consistent with the install-dialog popup (PY-89774 follow-up).
   *
   * Defaults to [displayable] for callers that don't pre-sort all matches (e.g. the empty-query
   * branch that only shows the first page from each repository).
   */
  val sortedAll: List<DisplayablePackage> = displayable,
)

class PyInvalidRepositoryViewData(repository: PyPackageRepository) :
  PyPackagesViewData(repository, PythonPackageSearchResult(0, emptyList(), 0), 0, emptyList())