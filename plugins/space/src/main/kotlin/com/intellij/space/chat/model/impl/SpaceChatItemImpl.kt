// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.impl

import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.client.property
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.api.SpaceChatItemAdditionalFeature
import com.intellij.space.chat.model.api.toType
import com.intellij.space.chat.ui.awaitFullLoad
import kotlinx.coroutines.CompletableDeferred
import libraries.coroutines.extra.Lifetime
import runtime.reactive.mutableProperty

internal class SpaceChatItemImpl private constructor(
  private val messageVm: M2MessageVm,
  override val link: String?,
  override val additionalFeatures: Set<SpaceChatItemAdditionalFeature> = setOf()
) : SpaceChatItem {
  private val record = messageVm.message

  override val id = record.id

  override val thread = messageVm.message.thread

  override val loadingThread = mutableProperty<CompletableDeferred<M2ChannelVm>?>(null)

  override val threadPreview = messageVm.thread

  override val chat = messageVm.channelVm

  override val author = record.author

  override val created = record.created

  override val type = record.details.toType()

  override val delivered = messageVm.delivered

  override val canDelete = messageVm.canDelete

  override val text = record.text

  override val editingVm = messageVm.editingVm

  override val isEditing = messageVm.isEditing

  override val canEdit = messageVm.canEdit

  override val isEdited = record.edited != null

  override val startThreadVm = SpaceChatStartThreadVmImpl(messageVm, threadPreview, loadingThread)

  override val pending = record.pending

  override val attachments = record.attachments ?: listOf()

  override val projectedItem = record.projectedItem?.property()

  override fun startEditing() {
    messageVm.startEditing()
  }

  override fun stopEditing() {
    messageVm.stopEditing()
  }

  override suspend fun delete() {
    chat.deleteCurrentOrOriginalMessage(id)
  }

  override suspend fun loadThread(lifetime: Lifetime): M2ChannelVm? {
    threadPreview ?: return null
    val loadingThreadDeferred = CompletableDeferred<M2ChannelVm>()
    loadingThread.value = loadingThreadDeferred
    return chat.channelsVm.thread(lifetime, chat, threadPreview, null).also {
      it.awaitFullLoad(lifetime)
      loadingThreadDeferred.complete(it)
    }
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
    internal fun M2MessageVm.convertToChatItem(
      link: String?,
      additionalFeatures: Set<SpaceChatItemAdditionalFeature> = setOf()
    ): SpaceChatItem = SpaceChatItemImpl(this, link, additionalFeatures = additionalFeatures)
  }
}