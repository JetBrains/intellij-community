// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.conflict

import org.jetbrains.idea.svn.api.BaseNodeDescription
import org.jetbrains.idea.svn.api.NodeKind

import java.io.File

class TreeConflictDescription(val path: File,
                              nodeKind: NodeKind,
                              val conflictAction: ConflictAction,
                              val conflictReason: ConflictReason,
                              val operation: ConflictOperation,
                              val sourceLeftVersion: ConflictVersion?,
                              val sourceRightVersion: ConflictVersion?) : BaseNodeDescription(nodeKind) {
  fun toPresentableString() = "local $conflictReason, incoming $conflictAction upon $operation"
}
