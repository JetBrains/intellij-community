// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.cache.impl

import com.jetbrains.python.Result
import com.jetbrains.python.packaging.cache.PythonPackageSearchPage
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import org.jetbrains.annotations.ApiStatus

/**
 * A search page that holds its data in memory.
 */
@ApiStatus.Internal
class InMemorySearchPage(
  private val items: List<String>,
) : PythonPackageSearchPage {
  override fun contents(): Result<List<String>, PythonPackageSearchPage.DataInvalidatedError> =
    Result.Success(items)

  companion object {
    /**
     * Constructs a [PythonPackageSearchResult] with [InMemorySearchPage] pages.
     * 
     * @param matches A list of items to paginate.
     * @param pageSize Maximum page size.
     * @return a constructed result, where each page is represented by a chunk of [matches] of size [pageSize].
     */
    fun resultFromMatches(matches: List<String>, pageSize: Int): PythonPackageSearchResult =
      PythonPackageSearchResult(
        matches.size,
        matches.chunked(pageSize).map { InMemorySearchPage(it) },
        pageSize,
      )
  }
}
