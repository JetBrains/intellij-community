// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.GitCommitChange
import circlet.client.api.GitFile
import circlet.code.api.DiffContext
import circlet.code.api.DiffSide
import com.intellij.diff.util.Side

internal fun GitCommitChange.toDiffContext(): DiffContext = DiffContext(
  this.old?.toNonEmptyDiffSide(),
  this.new?.toNonEmptyDiffSide() ?: DiffSide.Empty(this.revision)
)

private fun GitFile.toNonEmptyDiffSide(): DiffSide.NonEmpty = DiffSide.NonEmpty(commit, path)

internal fun DiffContext.getFilePath(side: Side): String? = when (side) {
  Side.LEFT -> this.left?.path
  Side.RIGHT -> this.right.path
}

internal fun DiffContext.getRevision(side: Side): String = when (side) {
  Side.LEFT -> this.left?.revision
  Side.RIGHT -> this.right.revision
} as String
