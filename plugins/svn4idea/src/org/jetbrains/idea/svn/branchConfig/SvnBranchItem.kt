// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import org.jetbrains.idea.svn.api.Url

class SvnBranchItem(val url: Url, val creationDateMillis: Long, val revision: Long) : Comparable<SvnBranchItem> {
  override fun compareTo(other: SvnBranchItem): Int = other.creationDateMillis.compareTo(creationDateMillis)
}