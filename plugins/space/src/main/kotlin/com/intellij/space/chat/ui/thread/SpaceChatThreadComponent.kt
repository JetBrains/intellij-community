// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItem
import com.intellij.space.chat.ui.*
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.TimelineComponent
import com.intellij.util.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import libraries.coroutines.extra.Lifetime
import javax.swing.JComponent
import javax.swing.JPanel

internal fun createThreadComponent(
  project: Project,
  lifetime: Lifetime,
  thread: M2ChannelVm,
  pendingStateProvider: () -> Boolean,
  threadActionsFactory: SpaceChatThreadActionsFactory? = null,
  withFirst: Boolean = true,
  folded: Boolean = true,
  statsPlace: SpaceStatsCounterCollector.SendMessagePlace = SpaceStatsCounterCollector.SendMessagePlace.THREAD,
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
  val threadTimeline = if (folded) {
    TimelineThreadCommentsPanel(
      itemsListModel,
      commentComponentFactory = itemComponentFactory::createComponent,
      offset = 0
    ).apply {
      border = JBUI.Borders.emptyBottom(5)
    }
  }
  else {
    TimelineComponent(itemsListModel, itemComponentFactory, offset = 0).apply {
      border = JBUI.Borders.emptyBottom(5)
    }
  }

  val actionsComponent = createActionsComponent(thread, threadActionsFactory, statsPlace, pendingStateProvider)
  return threadComponent.apply {
    add(threadTimeline, VerticalLayout.FILL_HORIZONTAL)
    add(actionsComponent, VerticalLayout.FILL_HORIZONTAL)
  }
}

private fun createActionsComponent(
  thread: M2ChannelVm,
  threadActionsFactory: SpaceChatThreadActionsFactory?,
  statsPlace: SpaceStatsCounterCollector.SendMessagePlace,
  pendingStateProvider: () -> Boolean
): JComponent {
  val newMessageStateModel = SingleValueModelImpl(false)
  val label = ActionLink(SpaceBundle.message("chat.reply.action")) {
    newMessageStateModel.value = true
  }

  val additionalActions = threadActionsFactory?.createActionsComponent()
  val actionsComponent = JPanel(HorizontalLayout(JBUI.scale(5))).apply {
    isOpaque = false
    border = JBUI.Borders.emptyLeft(SpaceChatAvatarType.THREAD.size.get() + SpaceChatItemComponentFactory.Item.AVATAR_GAP)
    add(label)
    additionalActions?.let { add(it) }
  }
  return ToggleableContainer.create(
    newMessageStateModel,
    mainComponentSupplier = { actionsComponent },
    toggleableComponentSupplier = {
      createNewMessageField(
        thread,
        statsPlace = statsPlace,
        onCancel = { newMessageStateModel.value = false },
        avatarType = SpaceChatAvatarType.THREAD,
        pendingStateProvider = pendingStateProvider
      )
    }
  )
}