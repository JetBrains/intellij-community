// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.getSearchApi
import com.jetbrains.python.packaging.repository.PyPackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-page size requested from each repository. Matches the install-dialog list page size so a
 * single network round-trip fills one screen of results.
 */
internal const val INSTALL_DIALOG_PAGE: Int = 50

/**
 * Fans search [query] out over (a) every repository the [repoManager] knows about and
 * (b) every entry in [extraRepositories] not already covered by [repoManager]. Repository
 * coverage is decided by [PyPackageRepository.repositoryUrl], so adding the same repo twice
 * (once as built-in, once as user-configured) results in a single bucket.
 *
 * Returns the cached [PythonPackageSearchResult] per repo so callers can paginate page-by-page —
 * the function itself only triggers the initial search round-trip on each repository, no
 * page contents are materialised here.
 *
 * Pure suspend function with no Swing or service-locator dependencies; runs the network/CPU
 * heavy work on [Dispatchers.Default].
 */
  internal suspend fun aggregateInstallDialogSearch(
  repoManager: PythonRepositoryManager,
  extraRepositories: List<PyPackageRepository>,
  query: String,
): Map<PyPackageRepository, PythonPackageSearchResult> = withContext(Dispatchers.Default) {
  val searchApi = repoManager.getSearchApi()

  val result: MutableMap<PyPackageRepository, PythonPackageSearchResult> =
    searchApi.searchPackages(query).toMutableMap()

  val coveredUrls = result.keys.asSequence()
    .map { it.repositoryUrl }
    .filter(String::isNotEmpty)
    .toSet()
  for (repo in extraRepositories) {
    if (!repo.enabled) continue
    val url = repo.repositoryUrl
    if (url.isNotEmpty() && url in coveredUrls) continue
    result[repo] = repo.search(query)
  }
  result
}
