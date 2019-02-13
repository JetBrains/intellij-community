// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList

class MergeInfoCached(map: Map<Long, MergeCheckResult> = emptyMap(), private val copyRevision: Long = -1L) {
  val map: MutableMap<Long, MergeCheckResult> = map.toMutableMap()

  fun copy(): MergeInfoCached = MergeInfoCached(map, copyRevision)

  fun copiedAfter(list: CommittedChangeList): Boolean = copyRevision != -1L && copyRevision >= list.number
}
