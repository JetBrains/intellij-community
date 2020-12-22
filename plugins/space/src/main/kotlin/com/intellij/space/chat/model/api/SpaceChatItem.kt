// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.api

import circlet.client.api.CPrincipal
import circlet.client.api.M2ItemContentDetails
import circlet.m2.M2MessageEditingVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.KDateTime
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.codereview.timeline.TimelineItem
import runtime.reactive.MutableProperty

interface SpaceChatItem : TimelineItem {
  val chat: M2ChannelVm

  val author: CPrincipal

  val text: @NlsSafe String

  val created: KDateTime

  val details: M2ItemContentDetails?

  val thread: M2ChannelVm?

  val delivered: Boolean

  val link: String?

  val canDelete: Boolean

  val isEdited: Boolean

  val editingVm: MutableProperty<M2MessageEditingVm?>

  val isEditing: MutableProperty<Boolean>

  val canEdit: Boolean

  fun startEditing()

  fun stopEditing()

  suspend fun delete()
}