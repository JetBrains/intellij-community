// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.impl

import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import com.intellij.space.chat.model.api.SpaceChatStartThreadVm
import com.intellij.space.chat.ui.awaitFullLoad
import runtime.reactive.mutableProperty
import runtime.reactive.property
import runtime.reactive.property.map

internal class SpaceChatStartThreadVmImpl(private val messageVm: M2MessageVm, private val thread: M2ChannelVm?) : SpaceChatStartThreadVm {
  private val lifetime = messageVm.lifetime

  override val isWritingFirstMessage = mutableProperty(false)

  override val canStartThread = lifetime.map(
    messageVm.canOpenThread,
    isWritingFirstMessage,
    thread?.mvms?.prop ?: property(null)
  ) { canOpenThread, writingFirstMessage, messagesVm ->
    canOpenThread && !writingFirstMessage && messagesVm?.messages.isNullOrEmpty()
  }

  override fun startWritingFirstMessage() {
    isWritingFirstMessage.value = true

  }

  override fun stopWritingFirstMessage() {
    isWritingFirstMessage.value = false
  }

  override suspend fun startThread(message: String) {
    if (thread != null) {
      thread.sendMessage(message)
      stopWritingFirstMessage()
      return
    }
    val threadPreview = messageVm.makeThreadPreview()
    val channelsVm = messageVm.channelsVm
    val viewerLifetime = messageVm.lifetime

    val threadChannel = channelsVm.thread(viewerLifetime, messageVm.channelVm, threadPreview, null)
    threadChannel.awaitFullLoad(viewerLifetime)
    threadChannel.sendMessage(message)
    // don't call stopWritingFirstMessage since messageVm will be recreated in its channel
  }
}