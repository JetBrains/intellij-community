package com.intellij.space.chat.model

import circlet.client.api.CPrincipal
import circlet.client.api.ChannelItemRecord
import circlet.client.api.M2ItemContentDetails
import circlet.code.api.CodeDiscussionAddedFeedEvent
import circlet.m2.ChannelsVm
import circlet.m2.M2ChannelMode
import circlet.m2.channel.M2ChannelVm
import circlet.platform.client.resolve
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.chat.ui.awaitFullLoad
import com.intellij.util.ui.codereview.timeline.TimelineItem
import libraries.coroutines.extra.Lifetime

internal class SpaceChatItem private constructor(
  private val record: ChannelItemRecord,
  val link: String?,
  val thread: M2ChannelVm? = null
) : TimelineItem {
  val author: CPrincipal = record.author
  val created: circlet.platform.api.KDateTime = record.created
  val details: M2ItemContentDetails? = record.details

  @NlsSafe
  val text: String = record.text

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (other !is SpaceChatItem) {
      return false
    }

    if (record != other.record) {
      return false
    }

    return true
  }

  override fun hashCode(): Int = record.hashCode()

  companion object {
    internal suspend fun ChannelItemRecord.convertToChatItemWithThread(
      lifetime: Lifetime,
      channelsVm: ChannelsVm,
      link: String?
    ): SpaceChatItem {
      val thread =
        when (val itemDetails = details) {
          is CodeDiscussionAddedFeedEvent -> {
            val discussion = itemDetails.codeDiscussion.resolve()
            channelsVm.channel(lifetime, discussion.channel, M2ChannelMode.CodeDiscussion())
          }
          else -> {
            thread?.let { channelsVm.channel(lifetime, it) }
          }
        } ?: return SpaceChatItem(this, link)
      thread.awaitFullLoad(lifetime)
      return SpaceChatItem(this, link, thread)
    }

    internal fun ChannelItemRecord.convertToChatItem(link: String?): SpaceChatItem = SpaceChatItem(this, link)
  }
}