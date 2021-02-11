// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import circlet.m2.channel.M2ChannelVm
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.SpaceChatItemComponentFactory
import com.intellij.space.chat.ui.createNewMessageField
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import javax.swing.JComponent

internal class SpaceChatReplyActionFactory(
  private val avatarType: SpaceChatAvatarType = SpaceChatAvatarType.THREAD
) : SpaceChatThreadActionsFactory {
  override fun createActionsComponent(chatVm: M2ChannelVm): JComponent {
    val newMessageStateModel = SingleValueModelImpl(false)
    val replyActionSupplier: () -> JComponent = {
      val label = ActionLink(SpaceBundle.message("chat.reply.action")) {
        newMessageStateModel.value = true
      }
      JBUI.Panels.simplePanel(label).apply {
        isOpaque = false
        border = JBUI.Borders.emptyLeft(avatarType.size.get() + SpaceChatItemComponentFactory.Item.AVATAR_GAP)
      }
    }
    return ToggleableContainer.create(
      newMessageStateModel,
      mainComponentSupplier = replyActionSupplier,
      toggleableComponentSupplier = {
        createNewMessageField(chatVm, onCancel = { newMessageStateModel.value = false }, avatarType = avatarType)
      }
    )
  }
}