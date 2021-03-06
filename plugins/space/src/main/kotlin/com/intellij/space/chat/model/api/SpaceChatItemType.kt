// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.api

import circlet.client.api.M2ItemContentDetails
import circlet.client.api.M2TextItemContent
import circlet.client.api.TD_MemberProfile
import circlet.client.api.mc.ChatMessage
import circlet.client.api.mc.toMessage
import circlet.code.api.*
import circlet.platform.api.Ref
import com.intellij.space.chat.model.api.SpaceChatItemType.*
import com.intellij.space.chat.model.impl.toMCMessageContent

internal fun M2ItemContentDetails?.toType(): SpaceChatItemType = when (this) {
  is CodeDiscussionAddedFeedEvent -> CodeDiscussion(codeDiscussion)
  is M2TextItemContent -> SimpleText
  is ReviewCompletionStateChangedEvent -> ReviewCompletionStateChanged
  is ReviewerChangedEvent -> ReviewerChanged(changeType, uid)
  is MergeRequestMergedEvent -> MergeRequestMerged(sourceBranch, targetBranch)
  is MergeRequestBranchDeletedEvent -> MergeRequestBranchDeleted(branch)
  is ReviewTitleChangedEvent -> ReviewTitleChanged(oldTitle, newTitle)
  is circlet.client.api.mc.MCMessage -> when (val chatMessage = toMessage()) {
    is ChatMessage.Text -> SimpleText
    is ChatMessage.Block -> MCMessage(chatMessage.toMCMessageContent())
  }
  null -> Deleted
  else -> Unknown
}

internal sealed class SpaceChatItemType {
  class CodeDiscussion(val discussion: Ref<CodeDiscussionRecord>) : SpaceChatItemType()

  object SimpleText : SpaceChatItemType()

  object ReviewCompletionStateChanged : SpaceChatItemType()

  class ReviewerChanged(val changeType: ReviewerChangedType, val uid: Ref<TD_MemberProfile>) : SpaceChatItemType()

  class MergeRequestMerged(val sourceBranch: String, val targetBranch: String) : SpaceChatItemType()

  class MergeRequestBranchDeleted(val branch: String) : SpaceChatItemType()

  class ReviewTitleChanged(val oldTitle: String, val newTitle: String) : SpaceChatItemType()

  class MCMessage(val content: SpaceMCMessageContent) : SpaceChatItemType()

  object Deleted : SpaceChatItemType()

  object Unknown : SpaceChatItemType()
}
