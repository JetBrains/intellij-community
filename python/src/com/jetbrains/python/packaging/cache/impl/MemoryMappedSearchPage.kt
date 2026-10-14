// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.cache.impl

import com.jetbrains.python.Result
import com.jetbrains.python.packaging.cache.PythonPackageSearchPage
import org.jetbrains.annotations.ApiStatus

/**
 * A search page that gets its data from a memory-mapped buffer.
 */
@ApiStatus.Internal
class MemoryMappedSearchPage internal constructor(
  private val searchPageIterable: Iterable<String>,
) : PythonPackageSearchPage {
  /**
   * Gets contents of the page as a list. Returns null if the buffer that the page references was unmapped.
   */
  override fun contents(): Result<List<String>, PythonPackageSearchPage.DataInvalidatedError> =
    try {
      Result.Success(searchPageIterable.toList())
    }
    catch (_: IllegalStateException) {
      Result.Failure(PythonPackageSearchPage.DataInvalidatedError)
    }
}
