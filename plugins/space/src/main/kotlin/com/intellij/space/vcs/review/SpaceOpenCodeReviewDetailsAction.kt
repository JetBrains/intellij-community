package com.intellij.space.vcs.review

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.list.SpaceReviewListDataKeys

class SpaceOpenCodeReviewDetailsAction : DumbAwareAction(SpaceBundle.messagePointer("action.open.review.details.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val data = e.getData(SpaceReviewListDataKeys.SELECTED_REVIEW) ?: return
    e.getData(SpaceReviewDataKeys.SELECTED_REVIEW_VM)?.selectedReview?.value = data
  }
}