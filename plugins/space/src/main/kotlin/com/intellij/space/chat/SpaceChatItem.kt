package com.intellij.space.chat

import circlet.client.api.CPrincipal
import circlet.client.api.ChannelItemRecord
import circlet.client.api.M2ChannelRecord
import circlet.client.api.M2ItemContentDetails
import circlet.platform.api.Ref
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.codereview.timeline.TimelineItem

class SpaceChatItem(record: ChannelItemRecord) : TimelineItem {
  val author: CPrincipal = record.author
  val created: circlet.platform.api.KDateTime = record.created
  val details: M2ItemContentDetails? = record.details

  @NlsSafe
  val text: String = record.text

  val thread: Ref<M2ChannelRecord>? = record.thread
}