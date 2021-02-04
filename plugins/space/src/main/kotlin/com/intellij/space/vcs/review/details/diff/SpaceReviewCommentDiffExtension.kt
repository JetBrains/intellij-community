// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.code.api.*
import circlet.platform.client.property
import circlet.platform.client.resolve
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.Side
import com.intellij.openapi.util.Disposer
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.settings.SpaceSettingsPanel
import libraries.coroutines.extra.Lifetime
import libraries.klogging.logger
import runtime.reactive.LoadingValue

class SpaceReviewCommentDiffExtension : DiffExtension() {
  companion object {
    val log = logger<SpaceSettingsPanel>()
  }

  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer,
                               context: DiffContext,
                               request: DiffRequest) {

    val ws = SpaceWorkspaceComponent.getInstance().workspace.value ?: return
    val diffRequestData = request.getUserData(SpaceDiffKeys.DIFF_REQUEST_DATA) ?: return
    val changes = diffRequestData.changes.value as? LoadingValue.Loaded ?: return
    val reviewers: List<CodeReviewParticipant> = diffRequestData.participantsVm?.reviewers?.value ?: emptyList()
    val selectedSpaceChange = diffRequestData.selectedChange
    val discussions = changes.value?.get(selectedSpaceChange.repository)?.discussions ?: return
    val project = context.project!!
    val lifetime = diffRequestData.diffExtensionLifetimes.next()
    val client = diffRequestData.spaceDiffVm.client

    viewer as DiffViewerBase

    fun pendingStateProvider(): Boolean {
      val me = SpaceWorkspaceComponent.getInstance().workspace.value?.me ?: return false
      return reviewers
        .filter { it.theirTurn == true }
        .any { it.user.resolve() == me.value }
    }

    val chatPanelFactory = SpaceReviewCommentPanelFactory(project, viewer, lifetime, ws, selectedSpaceChange, ::pendingStateProvider)

    val spaceReviewCommentSubmitter = SpaceReviewCommentSubmitterImpl(
      lifetime,
      client,
      diffRequestData.spaceDiffVm.projectKey,
      ReviewIdentifier.Id(diffRequestData.spaceDiffVm.reviewId),
      selectedSpaceChange,
      ::pendingStateProvider
    )
    val handler = createHandler(viewer, spaceReviewCommentSubmitter)

    viewer.addListener(object : DiffViewerListener() {
      var viewerIsReady = false // todo: remove this hack

      override fun onAfterRediff() {
        if (!viewerIsReady) {
          handler.updateCommentableRanges()
          discussions.values.forEach { propagatedCodeDiscussion ->
            addCommentWithExceptionHandling(chatPanelFactory, propagatedCodeDiscussion, lifetime, handler)
          }

          discussions.change.forEach(lifetime) { (_, _, newValue) ->
            newValue?.let { addCommentWithExceptionHandling(chatPanelFactory, it, lifetime, handler) }
          }
        }
        viewerIsReady = true
      }
    })
  }

  private fun addCommentWithExceptionHandling(chatPanelFactory: SpaceReviewCommentPanelFactory,
                                              propagatedCodeDiscussion: PropagatedCodeDiscussion,
                                              lifetime: Lifetime,
                                              handler: SpaceDiffCommentsHandler) {
    try {
      addCommentToDiff(chatPanelFactory, propagatedCodeDiscussion, lifetime, handler)
    }
    catch (e: Exception) {
      log.error(e, "Unable to add comment panel")
    }
  }

  private fun addCommentToDiff(commentPanelFactory: SpaceReviewCommentPanelFactory,
                               propagatedCodeDiscussion: PropagatedCodeDiscussion,
                               lifetime: Lifetime,
                               handler: SpaceDiffCommentsHandler) {
    val anchor = propagatedCodeDiscussion.anchor
    if (anchor.interpolatedLineState == InterpolatedLineState.Deleted) return

    val chatPanel = commentPanelFactory.createForDiscussion(propagatedCodeDiscussion)

    chatPanel?.let { panel ->
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