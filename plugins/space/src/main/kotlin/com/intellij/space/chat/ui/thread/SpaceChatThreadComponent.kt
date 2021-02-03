// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItem
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.SpaceChatItemComponentFactory
import com.intellij.space.chat.ui.SpaceChatItemListModel
import com.intellij.space.chat.ui.getLink
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import libraries.coroutines.extra.Lifetime
import javax.swing.JComponent
import javax.swing.JPanel

internal fun createThreadComponent(
  project: Project,
  lifetime: Lifetime,
  thread: M2ChannelVm,
  threadActionsFactory: SpaceChatThreadActionsFactory,
  withFirst: Boolean = true,
  messageConverter: (index: Int, message: M2MessageVm) -> SpaceChatItem = { _, message -> message.convertToChatItem(message.getLink()) }
): JComponent {
  val threadComponent = JPanel(VerticalLayout(0)).apply {
    isOpaque = false
  }
  val server = thread.client.server
  val avatarSize = SpaceChatAvatarType.THREAD.size
  val avatarProvider = SpaceAvatarProvider(lifetime, threadComponent, avatarSize)

  val itemsListModel = SpaceChatItemListModel()

  thread.mvms.forEach(lifetime) { messageList ->
    val messages = messageList.messages
    itemsListModel.messageListUpdated(
      messages
        .drop(if (withFirst) 0 else 1)
        .mapIndexed { index, message -> messageConverter(index, message) }
    )
    threadComponent.isVisible = messages.size > if (withFirst) 0 else 1
  }

  val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server, avatarProvider)
  val threadTimeline = TimelineThreadCommentsPanel(
    itemsListModel,
    commentComponentFactory = itemComponentFactory::createComponent,
    offset = 0
  ).apply {
    border = JBUI.Borders.emptyBottom(5)
  }

  val replyComponent = threadActionsFactory.createActionsComponent(thread)

  return threadComponent.apply {
    add(threadTimeline, VerticalLayout.FILL_HORIZONTAL)
    add(replyComponent, VerticalLayout.FILL_HORIZONTAL)
  }
}