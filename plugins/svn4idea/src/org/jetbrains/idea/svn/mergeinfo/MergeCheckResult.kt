// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo

enum class MergeCheckResult {
  COMMON,
  MERGED,
  NOT_MERGED,
  NOT_EXISTS;

  companion object {
    @JvmStatic
    fun getInstance(merged: Boolean): MergeCheckResult = if (merged) MERGED else NOT_MERGED
  }
}
