package com.intellij.space.chat.model.api

import circlet.client.api.CPrincipal
import circlet.client.api.M2ItemContentDetails
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.KDateTime
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.codereview.timeline.TimelineItem

interface SpaceChatItem : TimelineItem {
  val author: CPrincipal

  val text: @NlsSafe String

  val created: KDateTime

  val details: M2ItemContentDetails?

  val thread: M2ChannelVm?

  val delivered: Boolean

  val link: String?
}