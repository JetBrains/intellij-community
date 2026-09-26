// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.util.text.UniqueNameGenerator
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.sdk.PythonSdkUtil

/**
 * Receives the rebuild instructions emitted by [PyRepositoriesPresenter.rebuildTree]. The view
 * implementation translates them to tree-node operations on the underlying
 * [com.intellij.openapi.ui.MasterDetailsComponent] tree.
 */
internal interface RepositoryTreeSink {
  /** Remove every repository node (custom and default) from the tree. */
  fun removeAllRepositoryNodes()

  /** Append a node representing the given custom repository at the end of the tree. */
  fun addCustomRepositoryNode(repo: PyPackageRepository)

  /** Append the pre-existing default repository nodes back, after all custom nodes. */
  fun reinstateDefaultRepositoryNodes()
}

/**
 * Headless half of [PyRepositoriesList]. Owns all repository business logic, so the Swing
 * component is restricted to tree node lifecycle and the rules below stay unit-testable.
 */
internal class PyRepositoriesPresenter(
  private val repositoriesService: PyPackageRepositories,
) {
  /** Snapshot of the persisted custom repositories. */
  fun loadCustomRepositories(): List<PyPackageRepository> = repositoriesService.repositories.toList()

  /**
   * Built-in repositories for the project's first module SDK, or the static default set
   * when no Python SDK can be resolved (e.g. a freshly opened project).
   */
  fun loadBuiltInRepositories(project: Project): List<PyPackageRepository> {
    val sdk = project.modules.firstOrNull()?.let { PythonSdkUtil.findPythonSdk(it) }
    return sdk?.let { PythonPackageManager.forSdk(project, it).repositoryManager.builtInRepositories }
           ?: PythonRepositoryManager.DEFAULT_BUILT_IN_REPOSITORIES
  }

  /** Replaces the persisted custom repositories with [repos]. */
  fun commitCustomRepositories(repos: List<PyPackageRepository>) {
    repositoriesService.repositories.clear()
    repositoriesService.repositories.addAll(repos)
  }

  /** Repositories currently persisted but not present in [remaining]. */
  fun findRemoved(remaining: Collection<PyPackageRepository>): List<PyPackageRepository> {
    val remainingSet = remaining.toSet()
    return repositoriesService.repositories.filter { it !in remainingSet }
  }

  /** Clears stored credentials for repositories the user removed from the editor. */
  fun clearCredentialsForRemoved(remaining: Collection<PyPackageRepository>) {
    findRemoved(remaining).forEach { it.clearCredentials() }
  }

  /** Builds a brand-new repository with a unique display name and an empty URL. */
  fun createDraftRepository(existingNames: List<String>): PyPackageRepository =
    PyPackageRepository(nextUniqueName(existingNames), null, null)

  /** Suggests a name for a newly added repository that does not collide with [existingNames]. */
  fun nextUniqueName(existingNames: List<String>): String =
    UniqueNameGenerator.generateUniqueName(
      PyBundle.message("python.packaging.repository.form.default.name"),
      existingNames.toMutableList(),
    )

  /** True iff [repo] is currently persisted in the repositories service. */
  fun isPersisted(repo: PyPackageRepository): Boolean =
    repositoriesService.repositories.any { it == repo }

  /**
   * Rebuilds the editor tree from the persisted state: clears every existing node, re-adds the
   * persisted custom repositories in order, then re-appends the default nodes so they render
   * after the custom ones. The actual tree mutations are delegated to [sink], which keeps this
   * orchestration testable without a Swing component.
   */
  fun rebuildTree(sink: RepositoryTreeSink) {
    sink.removeAllRepositoryNodes()
    loadCustomRepositories().forEach(sink::addCustomRepositoryNode)
    sink.reinstateDefaultRepositoryNodes()
  }
}
