// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.impl

import circlet.m2.M2MessageVm
import com.intellij.space.chat.model.api.SpaceChatStartThreadVm
import com.intellij.space.chat.ui.awaitFullLoad
import runtime.reactive.mutableProperty
import runtime.reactive.property.map

internal class SpaceChatStartThreadVmImpl(private val messageVm: M2MessageVm) : SpaceChatStartThreadVm {
  private val lifetime = messageVm.lifetime

  override val isWritingFirstMessage = mutableProperty(false)

  override val canStartThread = lifetime.map(messageVm.canOpenThread, isWritingFirstMessage) { canOpenThread, writingFirstMessage ->
    messageVm.thread == null && canOpenThread && !writingFirstMessage
  }

  override fun startWritingFirstMessage() {
    isWritingFirstMessage.value = true

  }

  override fun stopWritingFirstMessage() {
    isWritingFirstMessage.value = false
  }

  override suspend fun startThread(message: String) {
    val threadPreview = messageVm.makeThreadPreview()
    val channelsVm = messageVm.channelsVm
    val viewerLifetime = messageVm.lifetime

    val threadChannel = channelsVm.thread(viewerLifetime, messageVm.channelVm, threadPreview, null)
    threadChannel.awaitFullLoad(viewerLifetime)
    threadChannel.sendMessage(message)
  }
}