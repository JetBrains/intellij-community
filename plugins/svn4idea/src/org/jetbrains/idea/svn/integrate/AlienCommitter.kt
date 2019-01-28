// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.AbstractCommitter
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.util.NullableFunction

class AlienCommitter(
  private val vcs: AbstractVcs<*>,
  changes: List<Change>,
  commitMessage: String,
  handlers: List<CheckinHandler>,
  additionalData: NullableFunction<Any, Any>
) : AbstractCommitter(vcs.project, changes, commitMessage, handlers, additionalData) {

  override fun commit() = commit(vcs, changes)

  override fun afterCommit() = Unit

  override fun onSuccess() = Unit

  override fun onFailure() = Unit

  override fun onFinish() = Unit
}