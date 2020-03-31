// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.vcs.commit.AbstractCommitter

class AlienCommitter(
  private val vcs: AbstractVcs,
  changes: List<Change>,
  commitMessage: String,
  commitContext: CommitContext
) : AbstractCommitter(vcs.project, changes, commitMessage, commitContext) {

  override fun commit() = commit(vcs, changes)

  override fun afterCommit() = Unit

  override fun onSuccess() = Unit

  override fun onFailure() = Unit

  override fun onFinish() = Unit
}