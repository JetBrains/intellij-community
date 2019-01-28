// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.conflict

import org.jetbrains.idea.svn.api.BaseNodeDescription
import org.jetbrains.idea.svn.api.NodeKind
import org.jetbrains.idea.svn.api.Url

class ConflictVersion(val repositoryRoot: Url, val path: String, val pegRevision: Long, nodeKind: NodeKind) : BaseNodeDescription(
  nodeKind) {
  fun toPresentableString() = "($nodeKind) ${repositoryRoot.toDecodedString()}/$path@$pegRevision"

  companion object {
    @JvmStatic
    fun toPresentableString(version: ConflictVersion?) = version?.toPresentableString().orEmpty()
  }
}
