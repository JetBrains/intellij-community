// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.space.chat.model.api.SpaceChatHeaderDetails
import com.intellij.space.editor.SpaceChatComplexPathVirtualFileSystem
import com.intellij.space.messages.SpaceBundle
import com.intellij.testFramework.LightVirtualFile
import icons.SpaceIcons
import libraries.coroutines.extra.Lifetime
import javax.swing.Icon

/**
 * @param id is used only for equals and hashcode to avoid multiple editor tabs the same [SpaceChatFile]
 */
internal class SpaceChatFile(
  private val sessionId: String,
  private val projectHash: String,
  @NlsSafe val id: String,
  @NlsSafe path: String,
  @NlsContexts.TabTitle val displayName: String,
  @NlsContexts.Tooltip val tabTooltip: String,
  val channelsVm: ChannelsVm,
  val chatRecord: Ref<M2ChannelRecord>,
  val headerDetailsBuilder: (Lifetime) -> SpaceChatHeaderDetails
) : LightVirtualFile(path, SpaceChatFileType.instance, ""), VirtualFilePathWrapper {

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun getFileSystem() = SpaceChatComplexPathVirtualFileSystem.getInstance()

  override fun getPath(): String = try {
    fileSystem.getPath(sessionId, projectHash, id)
  }
  catch (e: Exception) {
    name
  }

  override fun enforcePresentableName(): Boolean = true

  override fun getPresentablePath(): String = name

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SpaceChatFile

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

internal class SpaceChatFileTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? = (file as? SpaceChatFile)?.displayName

  override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): String? = (virtualFile as? SpaceChatFile)?.tabTooltip
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

  override fun getDescription(): String = SpaceBundle.message("filetype.chat.description")

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = SpaceIcons.Main

  override fun isBinary(): Boolean = true

  override fun isReadOnly(): Boolean = true

  companion object {
    val instance = SpaceChatFileType()
  }
}
