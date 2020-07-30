// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.VcsException
import javax.swing.JPanel

internal interface ConflictSidePresentation : Disposable {
  fun createPanel(): JPanel?

  @Throws(VcsException::class)
  fun load()
}

internal object EmptyConflictSide : ConflictSidePresentation {
  override fun createPanel(): JPanel? = null
  override fun load() = Unit
  override fun dispose() = Unit
}