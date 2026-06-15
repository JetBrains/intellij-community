// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.cache

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import org.jetbrains.annotations.ApiStatus

/**
 * Cache for Python packages.
 * The interface exposes an API for searching and lookup. The querying API exposes a paginated output, discouraging unnecessary copying. 
 */
@ApiStatus.Internal
interface PythonPackageCache {
  /**
   * The total amount of packages tracked by the cache.
   */
  @get:RequiresBackgroundThread
  val size: Int

  /**
   * Checks if the specified package name is contained in this cache.
   * 
   * @param name the exact name of the package to check.
   * @return true if the package is contained, false otherwise.
   */
  @RequiresBackgroundThread
  operator fun contains(name: String): Boolean

  /**
   * Searches the cache for packages that start with a specific prefix.
   * 
   * @param prefix the prefix to search the cache against.
   * @param pageSize the size of the page returned.
   * @return the search result.
   */
  @RequiresBackgroundThread
  fun search(prefix: String, pageSize: Int = 100): PythonPackageSearchResult
}

/**
 * Python package search result.
 */
@ApiStatus.Internal
data class PythonPackageSearchResult(
  /**
   * The total number of packages that match the query.
   */
  val total: Int,

  /**
   * A list of pages, where each page holds packages that match the query.
   */
  val pages: List<PythonPackageSearchPage>,

  internal val maxPageSize: Int,
)

/**
 * Gets the amount of items after a specific page index (i.e., amount of items not yet consumed by pagination).
 * 
 * @param index the page index.
 * @return the total number of items.
 */
@ApiStatus.Internal
fun PythonPackageSearchResult.remainingItemsAfterPageIndex(index: Int): Int =
  total - ((index + 1) * maxPageSize)

/**
 * Checks if the page index has more pages proceeding it.
 * 
 * @param index the page index.
 * @return true if the index is not last (has more pages proceeding it), false otherwise.
 */
@ApiStatus.Internal
fun PythonPackageSearchResult.hasMorePagesAfterPageIndex(index: Int): Boolean =
  index < pages.size - 1

/**
 * Gets the first page of the result.
 * 
 * @return an instance of [Result.Success] containing the first page, or if none exists, an empty list. If the page exists but was
 * invalidated, returns an instance of [Result.Failure] that wraps [PythonPackageSearchPage.DataInvalidatedError].
 */
@ApiStatus.Internal
fun PythonPackageSearchResult.firstPageOrEmpty(): Result<List<String>, PythonPackageSearchPage.DataInvalidatedError> =
  pages.firstOrNull()?.contents() ?: Result.Success(emptyList())

/**
 * Represents a search result page.
 */
@ApiStatus.Internal
interface PythonPackageSearchPage {
  /**
   * @return an instance of [Result.Success] containing a list of package names stored in this page, or an instance of [Result.Failure] that
   * wraps [DataInvalidatedError] when the underlying page data is invalidated (e.g., unloaded from memory and no longer available). When
   * data is invalidated, the caller should then rerun search.
   */
  fun contents(): Result<List<String>, DataInvalidatedError>

  /**
   * This error is returned when [contents] is called on a page that has its underlying data invalidated.
   */
  object DataInvalidatedError
}
