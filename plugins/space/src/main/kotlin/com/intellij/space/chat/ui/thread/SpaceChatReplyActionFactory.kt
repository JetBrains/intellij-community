// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import circlet.m2.channel.M2ChannelVm
import com.intellij.space.chat.ui.createReplyComponent
import javax.swing.JComponent

internal class SpaceChatReplyActionFactory : SpaceChatThreadActionsFactory {
  override fun createActionsComponent(chatVm: M2ChannelVm): JComponent = createReplyComponent(chatVm)
}