// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.platform.api.TID
import com.intellij.openapi.fileTypes.FileType
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.details.SpaceReviewChangesVm
import com.intellij.testFramework.LightVirtualFile
import icons.SpaceIcons
import javax.swing.Icon

internal class SpaceDiffFile(
  val changesVm: runtime.reactive.Property<SpaceReviewChangesVm>,
  val diffVm: SpaceDiffVm
) : LightVirtualFile(SpaceBundle.message("review.diff.tab.title", diffVm.reviewKey),
                     SpaceDiffFileType,
                     "") {

  private val reviewId: TID = diffVm.reviewId

  init {
    isWritable = false
  }

  // equals and hasCode are required to avoid opening each selected change in new tab

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SpaceDiffFile

    if (diffVm != other.diffVm) return false
    if (reviewId != other.reviewId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = diffVm.hashCode()
    result = 31 * result + reviewId.hashCode()
    return result
  }


}

object SpaceDiffFileType : FileType {
  override fun getName(): String = "SpaceDiffFileType"

  override fun getDescription(): String = SpaceBundle.message("review.diff.filetype.description")

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = SpaceIcons.Main

  override fun isBinary(): Boolean = true

  override fun isReadOnly(): Boolean = true
}