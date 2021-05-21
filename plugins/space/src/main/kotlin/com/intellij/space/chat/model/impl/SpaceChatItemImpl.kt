// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.impl

import circlet.code.api.CodeDiscussionAddedFeedEvent
import circlet.m2.ChannelsVm
import circlet.m2.M2ChannelMode
import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.client.resolve
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.api.SpaceChatItemAdditionalFeature
import com.intellij.space.chat.model.api.toType
import com.intellij.space.chat.ui.awaitFullLoad
import libraries.coroutines.extra.Lifetime

internal class SpaceChatItemImpl private constructor(
  private val messageVm: M2MessageVm,
  override val link: String?,
  override val thread: M2ChannelVm? = null,
  override val additionalFeatures: Set<SpaceChatItemAdditionalFeature> = setOf()
) : SpaceChatItem {
  private val record = messageVm.message

  override val id = record.id

  override val chat = messageVm.channelVm

  override val author = record.author

  override val created = record.created

  override val type = record.details.toType()

  override val delivered = messageVm.delivered

  override val canDelete = messageVm.canDelete

  override val text: @NlsSafe String = record.text

  override val editingVm = messageVm.editingVm

  override val isEditing = messageVm.isEditing

  override val canEdit = messageVm.canEdit

  override val isEdited = record.edited != null

  override val startThreadVm = SpaceChatStartThreadVmImpl(messageVm, thread)

  override val pending = record.pending

  override val attachments = record.attachments ?: listOf()

  override fun startEditing() {
    messageVm.startEditing()
  }

  override fun stopEditing() {
    messageVm.stopEditing()
  }

  override suspend fun delete() {
    chat.deleteCurrentOrOriginalMessage(id)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (other !is SpaceChatItemImpl) {
      return false
    }

    if (record != other.record) {
      return false
    }

    return true
  }

  override fun hashCode(): Int = record.hashCode()

  companion object {
    internal suspend fun M2MessageVm.convertToChatItemWithThread(
      lifetime: Lifetime,
      channelsVm: ChannelsVm,
      link: String?,
      additionalFeatures: Set<SpaceChatItemAdditionalFeature> = setOf()
    ): SpaceChatItem {
      val thread =
        when (val itemDetails = message.details) {
          is CodeDiscussionAddedFeedEvent -> {
            val discussion = itemDetails.codeDiscussion.resolve()
            channelsVm.channel(lifetime, discussion.channel, M2ChannelMode.CodeDiscussion())
          }
          else -> {
            message.thread?.let { channelsVm.channel(lifetime, it) }
          }
        } ?: return SpaceChatItemImpl(this, link)
      thread.awaitFullLoad(lifetime)
      return SpaceChatItemImpl(this, link, thread, additionalFeatures = additionalFeatures)
    }

    internal fun M2MessageVm.convertToChatItem(
      link: String?,
      additionalFeatures: Set<SpaceChatItemAdditionalFeature> = setOf()
    ): SpaceChatItem = SpaceChatItemImpl(this, link, additionalFeatures = additionalFeatures)
  }
}