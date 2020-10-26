// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.code.api.ChangeInReview
import circlet.code.api.CodeDiscussionRecord
import circlet.code.api.PropagatedCodeDiscussion
import circlet.m2.ChannelsVm
import circlet.m2.channel.M2DraftsVm
import circlet.platform.client.KCircletClient
import circlet.platform.client.resolve
import circlet.workspaces.Workspace
import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.project.Project
import com.intellij.space.chat.ui.SpaceChatPanel
import com.intellij.space.vcs.review.details.getFilePath
import libraries.coroutines.extra.Lifetime

internal class SpaceReviewCommentPanelFactory(
  private val project: Project,
  private val viewer: FrameDiffTool.DiffViewer,
  private val lifetime: Lifetime,
  private val workspace: Workspace,
  private val client: KCircletClient,
  selectedChange: ChangeInReview
) {
  private val selectedChangeFilePath = getFilePath(selectedChange)

  internal fun createForDiscussion(propagatedCodeDiscussion: PropagatedCodeDiscussion): SpaceChatPanel? {
    val filename = propagatedCodeDiscussion.anchor.filename

    if (filename?.removePrefix("/") != selectedChangeFilePath.path) return null

    val discussionRef = propagatedCodeDiscussion.discussion
    val discussionRecord = discussionRef.resolve()
    return createSpaceChatPanel(discussionRecord)
  }

  private fun createSpaceChatPanel(discussionRecord: CodeDiscussionRecord): SpaceChatPanel {
    val me = workspace.me
    val completionVm = workspace.completion
    val featureFlags = workspace.featureFlags.featureFlags
    return SpaceChatPanel(project,
                          lifetime,
                          viewer,
                          ChannelsVm(client, me, completionVm, M2DraftsVm(client, completionVm, null), featureFlags),
                          discussionRecord.channel)
  }
}