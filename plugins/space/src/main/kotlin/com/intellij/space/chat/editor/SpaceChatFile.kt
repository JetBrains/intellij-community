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
import com.intellij.space.messages.SpaceBundle
import com.intellij.testFramework.LightVirtualFile
import icons.SpaceIcons
import javax.swing.Icon

internal class SpaceChatFile(
  @NlsSafe path: String,
  @NlsContexts.TabTitle val displayName: String,
  val channelsVm: ChannelsVm,
  val chatRecord: Ref<M2ChannelRecord>
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

  override fun getIcon(): Icon? = SpaceIcons.Main

  override fun isBinary(): Boolean = true

  override fun isReadOnly(): Boolean = true

  override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

  companion object {
    val instance = SpaceChatFileType()
  }
}
