// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.message

import com.intellij.icons.AllIcons
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.UIUtil
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SpaceChatMessagePendingHeader(private val item: SpaceChatItem) : JPanel(HorizontalLayout(5)) {
  init {
    isOpaque = false
    isVisible = item.pending == true

    val infoLabel = JBLabel(SpaceBundle.message("chat.message.pending.text"), AllIcons.General.Note, SwingConstants.LEFT).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    val postNowLabel = ActionLink(SpaceBundle.message("chat.message.pending.post.action")) {
      item.chat.publishMessage(item.id)
    }
    add(infoLabel)
    add(postNowLabel)
  }
}