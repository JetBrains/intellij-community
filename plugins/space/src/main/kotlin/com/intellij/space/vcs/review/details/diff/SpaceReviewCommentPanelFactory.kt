// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.code.api.CodeDiscussionRecord
import circlet.code.api.PropagatedCodeDiscussion
import circlet.platform.client.property
import circlet.platform.client.resolve
import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.api.SpaceChatItemAdditionalFeature
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItem
import com.intellij.space.chat.ui.discussion.SpaceChatDiscussionActionsFactory
import com.intellij.space.chat.ui.getLink
import com.intellij.space.chat.ui.thread.SpaceChatStandaloneThreadComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.review.details.SpaceReviewChange
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.comment.wrapComponentUsingRoundedPanel
import libraries.coroutines.extra.Lifetime
import runtime.reactive.Property
import runtime.reactive.property.map
import javax.swing.JComponent

internal class SpaceReviewCommentPanelFactory(
  private val project: Project,
  private val parent: Disposable,
  private val lifetime: Lifetime,
  private val workspace: Workspace,
  private val spaceReviewChange: SpaceReviewChange,
  private val pendingStateProvider: () -> Boolean
) {
  internal fun createForDiscussion(propagatedCodeDiscussion: PropagatedCodeDiscussion): JComponent? {
    val filename = propagatedCodeDiscussion.anchor.filename

    if (filename != spaceReviewChange.spaceFilePath) return null

    val discussionRef = propagatedCodeDiscussion.discussion
    val discussionRecord = discussionRef.resolve()

    if (discussionRecord.archived) return null

    val component = createSpaceChatContentPanel(discussionRef.property()).apply {
      isOpaque = false
      border = JBUI.Borders.empty(10)
    }
    return wrapComponentUsingRoundedPanel(component).apply {
      border = JBUI.Borders.empty(2, 0)
    }
  }

  private fun createSpaceChatContentPanel(discussionRecord: Property<CodeDiscussionRecord>): JComponent =
    SpaceChatStandaloneThreadComponent(
      project,
      lifetime,
      parent,
      workspace.chatVm.channels,
      discussionRecord.value.channel,
      pendingStateProvider,
      SpaceChatDiscussionActionsFactory(lifetime, workspace.client, discussionRecord),
      statsPlace = SpaceStatsCounterCollector.SendMessagePlace.DIFF,
      messageConverter = { index, message ->
        message.convertToChatItem(
          message.getLink(),
          additionalFeatures = if (index == 0) {
            setOf(SpaceChatItemAdditionalFeature.ShowResolvedState(discussionRecord.map(lifetime) { it.resolved }))
          }
          else {
            setOf()
          }
        )
      }
    ).apply {
      setLoadingText(SpaceBundle.message("review.diff.loading.discussion.text"))
    }
}