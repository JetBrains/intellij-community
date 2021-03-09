// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.platform.api.TID
import com.intellij.diff.editor.DiffContentVirtualFile
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.space.editor.SpaceDiffComplexPathVirtualFileSystem
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.details.SpaceReviewChangesVm
import com.intellij.testFramework.LightVirtualFile
import icons.SpaceIcons
import runtime.reactive.MutableProperty
import runtime.reactive.Property
import runtime.reactive.mutableProperty
import javax.swing.Icon

internal data class SpaceDiffFileId(val projectKey: String, val reviewKey: String, val reviewId: TID)

internal class SpaceDiffFile(
  private val sessionId: String,
  private val projectHash: String,
  val fileId: SpaceDiffFileId,
  reviewKey: String
) : LightVirtualFile(SpaceBundle.message("review.diff.tab.title", reviewKey),
                     SpaceDiffFileType,
                     ""), DiffContentVirtualFile, VirtualFilePathWrapper {

  init {
    isWritable = false
  }
  val spaceDiffFileData: MutableProperty<SpaceDiffFileData?> = mutableProperty(null)

  fun updateDiffFileData(newSpaceFileData: SpaceDiffFileData) {
    spaceDiffFileData.value = newSpaceFileData
  }

  override fun getFileSystem() = SpaceDiffComplexPathVirtualFileSystem.getInstance()

  override fun getPath(): String = try {
    fileSystem.getPath(sessionId, projectHash, fileId)
  }
  catch (e: Exception) {
    name
  }

  override fun enforcePresentableName(): Boolean = true

  override fun getPresentablePath(): String = name

  // equals and hasCode are required to avoid opening each selected change in new tab

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SpaceDiffFile

    if (fileId != other.fileId) return false

    return true
  }

  override fun hashCode(): Int = fileId.hashCode()
}

object SpaceDiffFileType : FileType {
  override fun getName(): String = "SpaceDiffFileType"

  override fun getDescription(): String = SpaceBundle.message("filetype.review.diff.description")

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = SpaceIcons.Main

  override fun isBinary(): Boolean = true

  override fun isReadOnly(): Boolean = true
}

internal data class SpaceDiffFileData(
  val changesVm: Property<SpaceReviewChangesVm>,
  val spaceDiffVm: SpaceDiffVm
)
