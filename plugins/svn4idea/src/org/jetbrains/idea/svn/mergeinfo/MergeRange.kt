// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo

data class MergeRange(override val start: Long, override val endInclusive: Long, val isInheritable: Boolean = true) : ClosedRange<Long> {
  val revisions get() = start..endInclusive
}