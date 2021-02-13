// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionRecord
import circlet.m2.channel.M2ChannelVm
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.SpaceChatItemComponentFactory
import com.intellij.space.chat.ui.createNewMessageField
import com.intellij.space.chat.ui.thread.SpaceChatThreadActionsFactory
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import runtime.reactive.Property
import javax.swing.JComponent
import javax.swing.JPanel

internal class SpaceChatDiscussionActionsFactory(
  private val discussion: Property<CodeDiscussionRecord>,
  private val avatarType: SpaceChatAvatarType,
  private val pendingStateProvider: () -> Boolean = { false }
) : SpaceChatThreadActionsFactory {
  override fun createActionsComponent(chatVm: M2ChannelVm): JComponent {
    val newMessageStateModel = SingleValueModelImpl(false)
    val actionsPanel = {
      val replyAction = ActionLink(SpaceBundle.message("chat.reply.action")) {
        newMessageStateModel.value = true
      }
      val resolveComponent = createResolveComponent(discussion, chatVm)
      JPanel(HorizontalLayout(JBUI.scale(5))).apply {
        isOpaque = false
        border = JBUI.Borders.emptyLeft(avatarType.size.get() + SpaceChatItemComponentFactory.Item.AVATAR_GAP)
        add(replyAction)
        add(resolveComponent)
      }
    }

    return ToggleableContainer.create(
      newMessageStateModel,
      mainComponentSupplier = actionsPanel,
      toggleableComponentSupplier = {
        createNewMessageField(
          chatVm,
          onCancel = { newMessageStateModel.value = false },
          pendingStateProvider = pendingStateProvider,
          avatarType = SpaceChatAvatarType.THREAD
        )
      }
    )
  }
}