// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.code.api.CodeReviewRecord
import circlet.platform.api.TID
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.details.CrDetailsVm
import com.intellij.testFramework.LightVirtualFile
import icons.SpaceIcons
import javax.swing.Icon

internal class SpaceDiffFile(
  val detailsDetailsVm: CrDetailsVm<out CodeReviewRecord>
) : LightVirtualFile(SpaceBundle.message("review.diff.tab.title", detailsDetailsVm.reviewKey),
                     SpaceDiffFileType,
                     "") {

  val reviewId: TID = detailsDetailsVm.reviewId

  init {
    isWritable = false
  }

  // equals and hashCode are required to avoid opening each selected change in new tab
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SpaceDiffFile

    if (detailsDetailsVm != other.detailsDetailsVm) return false
    if (reviewId != other.reviewId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = detailsDetailsVm.hashCode()
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

  override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}