// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tools of [PersistentStateComponent] that stores folders as [VirtualFile]
 */
class FoldersComponentTools(private val folders: MutableList<VirtualFile>) {

  fun getFoldersAsStrings(): List<String> {
    return folders.map { it.path }
  }

  fun setFoldersAsStrings(folders: List<String>) {
    this.folders.apply {
      clear()
      addAll(folders.mapNotNull { LocalFileSystem.getInstance().findFileByPath(it) })
    }
  }

  fun setFoldersAsVirtualFiles(folders: List<VirtualFile>) {
    this.folders.apply {
      clear()
      addAll(folders)
    }
  }
}