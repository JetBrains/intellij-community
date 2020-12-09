// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.code.api.CodeDiscussionRecord
import circlet.code.api.PropagatedCodeDiscussion
import circlet.platform.client.property
import circlet.platform.client.resolve
import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.discussion.SpaceChatDiscussionActionsFactory
import com.intellij.space.chat.ui.thread.SpaceChatStandaloneThreadComponent
import com.intellij.space.vcs.review.details.SpaceReviewChange
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.comment.wrapComponentUsingRoundedPanel
import libraries.coroutines.extra.Lifetime
import runtime.reactive.Property
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
      SpaceChatDiscussionActionsFactory(
        discussionRecord,
        avatarType = SpaceChatAvatarType.THREAD,
        pendingStateProvider = pendingStateProvider
      )
    )
}