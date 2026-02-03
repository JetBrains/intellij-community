// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.vcs.commit.VcsCommitter
import com.intellij.vcs.commit.vetoDocumentSaving

class AlienCommitter(
  private val vcs: AbstractVcs,
  changes: List<Change>,
  commitMessage: String,
  commitContext: CommitContext
) : VcsCommitter(vcs.project, changes, commitMessage, commitContext, false) {

  override fun commit() {
    vetoDocumentSaving(project, changes) {
      vcsCommit(vcs, changes)
    }
  }
}