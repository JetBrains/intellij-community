// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.code.api.CodeDiscussionAnchor
import circlet.code.api.PropagatedCodeDiscussion
import circlet.platform.client.property
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.Side
import com.intellij.openapi.util.Disposer
import com.intellij.space.components.SpaceWorkspaceComponent
import libraries.coroutines.extra.Lifetime

class SpaceReviewCommentsDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer,
                               context: DiffContext,
                               request: DiffRequest) {

    val ws = SpaceWorkspaceComponent.getInstance().workspace.value ?: return
    val diffRequestData = request.getUserData(SpaceDiffKeys.DIFF_REQUEST_DATA) ?: return
    val selectedChange = diffRequestData.changesVm.selectedChange.value ?: return
    val discussions = diffRequestData.changesVm.selectedChangeDiscussions.value ?: return
    val project = context.project!!
    val lifetime = diffRequestData.lifetime
    val client = diffRequestData.changesVm.client

    viewer as DiffViewerBase

    val chatPanelFactory = SpaceReviewCommentPanelFactory(project, viewer, lifetime, ws, client, selectedChange)

    viewer.addListener(object : DiffViewerListener() {
      var viewerIsReady = false // todo: remove this hack

      override fun onAfterRediff() {
        if (!viewerIsReady) {
          val handler = createHandler(viewer)

          discussions.values.forEach { propagatedCodeDiscussion ->
            addCommentToDiff(chatPanelFactory, propagatedCodeDiscussion, lifetime, handler)
          }

          discussions.change.forEach(lifetime) { (_, oldValue, newValue) ->
            newValue?.let { addCommentToDiff(chatPanelFactory, it, lifetime, handler) }
          }
        }
        viewerIsReady = true
      }
    })
  }

  private fun addCommentToDiff(commentPanelFactory: SpaceReviewCommentPanelFactory,
                               propagatedCodeDiscussion: PropagatedCodeDiscussion,
                               lifetime: Lifetime,
                               handler: SpaceDiffCommentsHandler) {
    val chatPanel = commentPanelFactory.createForDiscussion(propagatedCodeDiscussion)

    chatPanel?.let { panel ->
      val anchor = propagatedCodeDiscussion.anchor
      val (diffSide, line) = anchor.getCommentSideAndLine()
      val disposable = when (diffSide) {
        Side.LEFT -> handler.insertLeft(line, panel)
        Side.RIGHT -> handler.insertRight(line, panel)
      }

      propagatedCodeDiscussion.discussion.property().forEach(lifetime) { newProperty ->
        if (newProperty.archived) {
          disposable?.let { Disposer.dispose(it) }
        }
      }
    }
  }
}

private fun CodeDiscussionAnchor.getCommentSideAndLine(): Pair<Side, Int> = when {
  line != null -> Side.RIGHT to line as Int
  oldLine != null -> Side.LEFT to oldLine as Int
  else -> error("CodeDiscussionAnchor don't have line")
}