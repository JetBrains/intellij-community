// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.PyPackageService
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCacheService
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.net.URI
import java.net.URISyntaxException
import javax.swing.JComponent

internal class PyPackageRepositoriesConfigurable(private val project: Project) : SearchableConfigurable {
  private lateinit var repositoriesList: PyRepositoriesList

  override fun getId(): String = "python.package.repositories"

  @Nls
  override fun getDisplayName(): String = message("python.packaging.repository.configurable.name")

  override fun createComponent(): JComponent {
    repositoriesList = PyRepositoriesList(project)
    return repositoriesList.createComponent()
  }

  override fun isModified(): Boolean = repositoriesList.isModified

  override fun apply() {
    repositoriesList.apply()

    val service = PyPackagingToolWindowService.getInstance(project)
    service.serviceScope.launch(Dispatchers.IO) {
      syncRepositories()
      service<PythonSimpleRepositoryCacheService>().reloadAll()
      service.refreshInstalledPackages()
      withContext(Dispatchers.EDT) {
        repositoriesList.resetAllItems()
      }
      PythonPackagesToolwindowStatisticsCollector.repositoriesChangedEvent.log(project)
    }
  }

  override fun reset() {
    repositoriesList.reset()
  }

  override fun disposeUIResources() {
    repositoriesList.disposeUIResources()
  }
}

private fun syncRepositories() {
  val repositoryService = service<PyPackageRepositories>()
  syncRepositories(repositoryService.repositories, repositoryService.invalidRepositories, PyPackageService.getInstance())
}

/**
 * Synchronizes [PyPackageService.additionalRepositories] with the given [repositories]:
 * removes stale entries, adds new valid ones, and moves repos with invalid URLs to [invalidRepositories].
 */
@ApiStatus.Internal
fun syncRepositories(
  repositories: MutableList<PyPackageRepository>,
  invalidRepositories: MutableSet<PyPackageRepository>,
  packageService: PyPackageService,
) {
  val allRepoUrls: Set<NormalizedRepositoryUrl> = repositories.asSequence()
    .map { it.repositoryUrl }
    .filter(String::isNotEmpty)
    .map(::normalizeRepoUrl)
    .toSet()

  packageService.additionalRepositories.asSequence()
    .filter { normalizeRepoUrl(it) !in allRepoUrls }
    .forEach { packageService.removeRepository(it) }

  val (valid, invalid) = repositories.partition { isValidRepositoryUrl(it.repositoryUrl) }
  invalidRepositories.clear()
  invalidRepositories.addAll(invalid)
  invalid.forEach { repo ->
    if (repo.repositoryUrl.isNotEmpty()) packageService.removeRepository(repo.repositoryUrl)
    repo.enabled = false
  }

  val alreadyAdded: Set<NormalizedRepositoryUrl> = packageService.additionalRepositories.mapTo(mutableSetOf(), ::normalizeRepoUrl)
  valid.asSequence()
    .map { it.repositoryUrl }
    .filter(String::isNotEmpty)
    .filter { normalizeRepoUrl(it) !in alreadyAdded }
    .forEach { packageService.addRepository(it) }
}

/**
 * Already-normalised repository URL — wraps the canonical comparison form produced by
 * [RepositoryUrl.normalize]. The dedicated type prevents accidental double / missed normalisation
 * at call sites (a plain `String` made it easy to compare a raw URL against a normalised one and
 * miss a `/`-trailing-slash mismatch).
 */
@JvmInline
@ApiStatus.Internal
value class NormalizedRepositoryUrl(val value: String)

@JvmInline
@ApiStatus.Internal
value class RepositoryUrl(val raw: String) {
  fun isValid(): Boolean {
    if (raw.isBlank()) return false
    return try {
      val uri = URI(raw.trim())
      uri.scheme?.lowercase() in listOf("http", "https") &&
      !uri.host.isNullOrEmpty()
    }
    catch (_: URISyntaxException) {
      false
    }
  }

  /**
   * Returns the canonical comparison form: trim, lowercase scheme+host, strip trailing slash.
   * `HTTP://Example.COM/simple/` → `http://example.com/simple`. Malformed URIs fall back to the
   * trimmed, slash-stripped raw text so two equivalently typed values still compare equal.
   */
  fun normalize(): NormalizedRepositoryUrl {
    val canonical = try {
      val uri = URI(raw.trim())
      val scheme = uri.scheme?.lowercase().orEmpty()
      val host = uri.host?.lowercase().orEmpty()
      val port = if (uri.port != -1) ":${uri.port}" else ""
      val path = uri.path?.trimEnd('/').orEmpty()
      "$scheme://$host$port$path"
    }
    catch (_: URISyntaxException) {
      raw.trim().trimEnd('/')
    }
    return NormalizedRepositoryUrl(canonical)
  }
}

/** @see RepositoryUrl.normalize */
@ApiStatus.Internal
fun normalizeRepoUrl(url: String): NormalizedRepositoryUrl = RepositoryUrl(url).normalize()

/** Callers that still have a nullable URL should null-check first; the empty/blank check is no
 *  longer this function's responsibility. */
@ApiStatus.Internal
fun isValidRepositoryUrl(url: String): Boolean = RepositoryUrl(url).isValid()
