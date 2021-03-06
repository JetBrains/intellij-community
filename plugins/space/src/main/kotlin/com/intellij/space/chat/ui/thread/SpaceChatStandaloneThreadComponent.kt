// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import circlet.client.api.M2ChannelRecord
import circlet.m2.ChannelsVm
import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.Ref
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItem
import com.intellij.space.chat.ui.SpaceChatContentPanelBase
import com.intellij.space.chat.ui.getLink
import com.intellij.space.stats.SpaceStatsCounterCollector
import libraries.coroutines.extra.Lifetime

internal class SpaceChatStandaloneThreadComponent(
  private val project: Project,
  private val lifetime: Lifetime,
  parent: Disposable,
  channelsVm: ChannelsVm,
  chatRecord: Ref<M2ChannelRecord>,
  private val pendingStateProvider: () -> Boolean,
  private val threadActionsFactory: SpaceChatThreadActionsFactory,
  private val statsPlace: SpaceStatsCounterCollector.SendMessagePlace = SpaceStatsCounterCollector.SendMessagePlace.THREAD,
  private val messageConverter: (index: Int, message: M2MessageVm) -> SpaceChatItem = { _, message ->
    message.convertToChatItem(message.getLink())
  }
) : SpaceChatContentPanelBase(lifetime, parent, channelsVm, chatRecord) {
  override fun onChatLoad(chatVm: M2ChannelVm) {
    stopLoadingContent(
      createThreadComponent(project, lifetime, chatVm, pendingStateProvider, threadActionsFactory, statsPlace = statsPlace,
                            messageConverter = messageConverter)
    )
  }
}