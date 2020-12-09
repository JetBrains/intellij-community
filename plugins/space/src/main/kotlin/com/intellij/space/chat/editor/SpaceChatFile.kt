// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.editor

import circlet.client.api.M2ChannelRecord
import circlet.m2.ChannelsVm
import circlet.platform.api.Ref
import com.intellij.ide.FileIconProvider
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.chat.model.api.SpaceChatHeaderDetails
import com.intellij.space.messages.SpaceBundle
import com.intellij.testFramework.LightVirtualFile
import icons.SpaceIcons
import javax.swing.Icon

internal class SpaceChatFile(
  @NlsSafe path: String,
  @NlsContexts.TabTitle val displayName: String,
  val channelsVm: ChannelsVm,
  val chatRecord: Ref<M2ChannelRecord>,
  val headerDetails: SpaceChatHeaderDetails
) : LightVirtualFile(path, SpaceChatFileType.instance, "") {
  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    isWritable = false
  }
}

internal class SpaceChatFileTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (file !is SpaceChatFile) {
      return null
    }
    return file.displayName
  }
}

internal class SpaceChatIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (file !is SpaceChatFile) {
      return null
    }
    return SpaceIcons.Main
  }
}

private class SpaceChatFileType private constructor(): FileType {

  override fun getName(): String = "SpaceChat"

  override fun getDescription(): String = SpaceBundle.message("chat.filetype.description")

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = SpaceIcons.Main

  override fun isBinary(): Boolean = true

  override fun isReadOnly(): Boolean = true

  companion object {
    val instance = SpaceChatFileType()
  }
}
