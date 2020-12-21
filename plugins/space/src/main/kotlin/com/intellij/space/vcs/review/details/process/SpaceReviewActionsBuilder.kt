// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.process

import circlet.code.api.ReviewerState
import circlet.platform.client.resolve
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.details.ParticipantStateControlVM
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.Nls
import runtime.Ui
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class SpaceReviewActionsBuilder constructor(private val stateUpdater: SpaceReviewStateUpdater) {
  fun createResumeActions(lifetime: Lifetime,
                          controlVM: ParticipantStateControlVM.ReviewerResumeReviewButton): List<SpaceReviewAction> {
    val resumeAction = SpaceReviewAction(SpaceBundle.message("review.actions.resume.review"), lifetime, stateUpdater::resumeReview)

    val acceptAction = if (controlVM.reviewerState != ReviewerState.Accepted) {
      SpaceReviewAction(SpaceBundle.message("review.actions.accept.changes"), lifetime, stateUpdater::acceptReview)
    }
    else null

    return listOfNotNull(resumeAction, acceptAction)
  }

  fun createAcceptActions(
    lifetime: Lifetime,
    reviewerDropdown: ParticipantStateControlVM.ReviewerDropdown,
  ): List<SpaceReviewAction> {
    val waitForResponseName = SpaceBundle.message("review.actions.wait.for.response")
    val acceptName = SpaceBundle.message("review.actions.accept.changes")

    val acceptAction = SpaceReviewAction(acceptName, lifetime, stateUpdater::acceptReview)

    val waitForReplyAction = if (reviewerDropdown.turnBased) {
      SpaceReviewAction(waitForResponseName, lifetime, stateUpdater::waitAuthorReply)
    }
    else {
      if (reviewerDropdown.pendingCounterRef.resolve().count > 0) {
        SpaceReviewAction(waitForResponseName, lifetime, stateUpdater::submitPendingMessages)
      }
      null
    }

    return listOfNotNull(acceptAction, waitForReplyAction)
  }

  fun createAuthorResumeActions() {

  }
}


internal class SpaceReviewAction(@Nls actionName: String,
                                 private val lifetime: Lifetime,
                                 private val run: suspend () -> Unit) : AbstractAction(actionName) {
  override fun actionPerformed(e: ActionEvent?) {
    launch(lifetime, Ui) {
      try {
        isEnabled = false
        run()
      }
      finally {
        isEnabled = true
      }
    }
  }
}