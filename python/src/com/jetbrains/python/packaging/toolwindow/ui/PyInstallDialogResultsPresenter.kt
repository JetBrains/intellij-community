// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService

/**
 * Pure logic for the install dialog results list.
 *
 * Drains every cached page of every repository into a single list sorted by the legacy
 * `createNameComparator` priority (exact → prefix → substring → name). Pages are read from
 * the in-memory cache built by [aggregateInstallDialogSearch], so this is bounded by the
 * number of matches the cache returns for [query] — typed queries on PyPI yield hundreds,
 * not the full 800k index. Cross-repo merge + global sort matches the pre-pagination
 * behaviour the user expects.
 *
 * Installed packages stay visible — user may install the same name into another module or
 * dependency group. PTW tree filters them out (different audience), so visible sets differ
 * by design; comparator is the same → intersection orders identically.
 *
 * `managedRepositories` are those the manager owns natively; anything else (user-configured
 * "extra" repositories) is filtered to entries whose name actually contains [query] before
 * merging — extra repos return their full index, not query-filtered pages.
 */
internal fun buildInstallDialogResults(
  results: Map<PyPackageRepository, PythonPackageSearchResult>,
  managedRepositories: Set<PyPackageRepository>,
  query: String,
): List<PackageLeafNode> {
  val nodes = mutableListOf<PackageLeafNode>()
  for ((repo, result) in results) {
    val isExtraRepo = repo !in managedRepositories
    for (page in result.pages) {
      val names = page.contents().successOrNull ?: continue
      for (name in names) {
        if (!isExtraRepo || StringUtil.containsIgnoreCase(name, query)) {
          nodes += PackageLeafNode(name, repo.name)
        }
      }
    }
  }
  val comparator = PyPackagingToolWindowService.createNameComparator(query)
  nodes.sortWith(compareBy(comparator) { it.packageName })
  return nodes
}
