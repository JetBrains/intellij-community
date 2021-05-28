// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.M2ChannelRecord
import circlet.client.api.TD_MemberProfile
import circlet.client.api.fullName
import circlet.m2.ChannelsVm
import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.Ref
import circlet.platform.api.isTemporary
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItem
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.utils.SpaceUrls
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.timeline.TimelineComponent
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.delay
import org.jetbrains.annotations.Nls
import runtime.reactive.awaitTrue
import javax.swing.JPanel

internal class SpaceChatContentPanel(
  private val project: Project,
  private val lifetime: Lifetime,
  parent: Disposable,
  channelsVm: ChannelsVm,
  chatRecord: Ref<M2ChannelRecord>
) : SpaceChatContentPanelBase(lifetime, parent, channelsVm, chatRecord) {
  private val server = channelsVm.client.server

  override fun onChatLoad(chatVm: M2ChannelVm) {
    val itemsListModel = SpaceChatItemListModel()
    val contentPanel = createContentPanel(itemsListModel, chatVm)
    chatVm.mvms.forEach(lifetime) { messagesViewModel ->
      val chatItems = messagesViewModel.messages.map { messageViewModel ->
        messageViewModel.convertToChatItem(messageViewModel.getLink())
      }
      itemsListModel.messageListUpdated(chatItems)
      stopLoadingContent(contentPanel)
    }
  }

  private fun createContentPanel(model: SpaceChatItemListModel, chatVM: M2ChannelVm): JPanel {
    val avatarSize = SpaceChatAvatarType.MAIN_CHAT.size
    val avatarProvider = SpaceAvatarProvider(lifetime, this, avatarSize)
    val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server, avatarProvider)
    val timeline = TimelineComponent(model, itemComponentFactory, offset = 0).apply {
      border = JBUI.Borders.emptyBottom(10)
    }

    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(timeline, VerticalLayout.FILL_HORIZONTAL)
      add(createNewMessageField(chatVM, SpaceChatAvatarType.MAIN_CHAT, SpaceStatsCounterCollector.SendMessagePlace.MAIN_CHAT),
          VerticalLayout.FILL_HORIZONTAL)
    }
  }
}

internal suspend fun M2ChannelVm.awaitFullLoad(lifetime: Lifetime) {
  ready.awaitTrue(lifetime)
  val messages = mvms.prop
  while (messages.value.hasPrev) {
    // TODO: remove this temporary fix when it will be fixed in platform
    delay(1)
    loadPrev()
  }
  while (messages.value.hasNext) {
    delay(1)
    loadNext()
  }
}

internal fun M2MessageVm.getLink(): String? =
  if (canCopyLink && !message.isTemporary()) {
    SpaceUrls.message(channelVm.key.value, message)
  }
  else {
    null
  }

@Nls
internal fun TD_MemberProfile.link(): HtmlChunk =
  HtmlChunk.link(SpaceUrls.member(username), name.fullName()) // NON-NLS

@NlsSafe
internal fun getGrayTextHtml(@Nls text: String): String =
  HtmlChunk.span("color: ${ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())}").addRaw(text).toString()