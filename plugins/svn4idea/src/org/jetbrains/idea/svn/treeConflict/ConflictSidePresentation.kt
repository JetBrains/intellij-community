// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import org.jetbrains.idea.svn.ConflictedSvnChange
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.conflict.ConflictVersion
import javax.swing.JPanel

internal interface ConflictSidePresentation : Disposable {
  fun createPanel(): JPanel?

  @Throws(VcsException::class)
  fun load()

  companion object {
    @JvmStatic
    fun getDescription(version: ConflictVersion?, change: ConflictedSvnChange): String {
      if (version != null) return toSystemIndependentName(ConflictVersion.toPresentableString(version))

      val isFile = !getFilePath(change).isDirectory
      val isAdded = change.beforeDescription == null
      return when {
        isFile && isAdded -> message("label.conflict.file.added")
        isFile && !isAdded -> message("label.conflict.file.unversioned")
        !isFile && isAdded -> message("label.conflict.directory.added")
        else -> message("label.conflict.directory.unversioned")
      }
    }
  }
}

internal object EmptyConflictSide : ConflictSidePresentation {
  override fun createPanel(): JPanel? = null
  override fun load() = Unit
  override fun dispose() = Unit
}