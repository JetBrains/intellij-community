// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Tools of [PersistentStateComponent] that stores folders as [VirtualFile]
 */
@ApiStatus.Internal
class FoldersComponentTools(private val folders: MutableList<VirtualFile>) {

  fun setFoldersAsStrings(folders: List<String>) {
    this.folders.apply {
      clear()
      addAll(folders.mapNotNull { StandardFileSystems.local().findFileByPath(it) })
    }
  }

  fun setFoldersAsVirtualFiles(folders: List<VirtualFile>) {
    this.folders.apply {
      clear()
      addAll(folders)
    }
  }
}