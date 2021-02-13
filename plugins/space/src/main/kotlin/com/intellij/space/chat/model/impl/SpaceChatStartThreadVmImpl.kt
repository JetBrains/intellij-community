// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.impl

import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.m2.threads.M2ThreadPreviewVm
import com.intellij.space.chat.model.api.SpaceChatStartThreadVm
import com.intellij.space.chat.ui.awaitFullLoad
import kotlinx.coroutines.CompletableDeferred
import runtime.reactive.MutableProperty
import runtime.reactive.mutableProperty
import runtime.reactive.property
import runtime.reactive.property.map

internal class SpaceChatStartThreadVmImpl(
  private val messageVm: M2MessageVm,
  private val threadPreview: M2ThreadPreviewVm?,
  private val loadingThread: MutableProperty<CompletableDeferred<M2ChannelVm>?>
) : SpaceChatStartThreadVm {
  private val lifetime = messageVm.lifetime

  override val isWritingFirstMessage = mutableProperty(false)

  override val canStartThread = lifetime.map(
    messageVm.canOpenThread,
    isWritingFirstMessage,
    threadPreview?.messageCount ?: property(null)
  ) { canOpenThread, writingFirstMessage, messagesCount ->
    canOpenThread && !writingFirstMessage && (messagesCount == null || messagesCount == 0)
  }

  override fun startWritingFirstMessage() {
    isWritingFirstMessage.value = true

  }

  override fun stopWritingFirstMessage() {
    isWritingFirstMessage.value = false
  }

  override suspend fun startThread(message: String) {
    val threadChannelDeferred = CompletableDeferred<M2ChannelVm>()
    loadingThread.value = threadChannelDeferred

    val newThreadPreview = threadPreview ?: messageVm.makeThreadPreview()

    val channelsVm = messageVm.channelsVm
    val viewerLifetime = messageVm.lifetime

    val threadChannel = channelsVm.thread(viewerLifetime, messageVm.channelVm, newThreadPreview, null)
    threadChannel.awaitFullLoad(viewerLifetime)
    threadChannel.sendMessage(message)
    threadChannelDeferred.complete(threadChannel)
    stopWritingFirstMessage()
  }
}