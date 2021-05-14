// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.englishFullName
import circlet.code.api.CodeReviewParticipant
import circlet.code.api.CodeReviewRecord
import circlet.platform.client.resolve
import com.intellij.openapi.util.NlsContexts
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.vcs.review.ReviewUiSpec.avatarSizeIntValue
import com.intellij.space.vcs.review.details.selector.SpaceReviewersSelectorVm
import com.intellij.space.vcs.review.details.selector.showPopup
import com.intellij.space.vcs.review.editIconButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.Property
import runtime.reactive.SequentialLifetimes
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal class AuthorsComponent(
  detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>,
  participantsVm: SpaceReviewParticipantsVm
) : ParticipantsComponent(detailsVm, participantsVm.authors, SpaceBundle.message("review.label.authors"))

internal class ReviewersComponent(
  private val detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>,
  private val participantsVm: SpaceReviewParticipantsVm
) : ParticipantsComponent(detailsVm, participantsVm.reviewers, SpaceBundle.message("review.label.reviewers")) {


  override fun additionalControls(): List<JComponent> {
    val popupLifetime = SequentialLifetimes(lifetime)

    val listener: (e: ActionEvent) -> Unit = {

      val next = popupLifetime.next()
      val possibleReviewersVM = SpaceReviewersSelectorVm(
        next,
        detailsVm.review.value,
        detailsVm.projectKey,
        detailsVm.client,
        detailsVm,
        participantsVm
      )

      showPopup(selectorVm = possibleReviewersVM,
                lifetime = next,
                parent = panel,
                participantsVm = participantsVm)
    }
    val editReviewersButton = editIconButton().apply {
      border = JBUI.Borders.empty(6, 0)
      actionListener = ActionListener { listener(it) }
    }

    return listOf(editReviewersButton)
  }

}

internal open class ParticipantsComponent(
  detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>,
  participantsVm: Property<List<CodeReviewParticipant>?>,
  @NlsContexts.Label labelText: String
) : Lifetimed by detailsVm {

  val panel: NonOpaquePanel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

  private val avatarProvider = SpaceAvatarProvider(detailsVm.lifetime, panel, avatarSizeIntValue)

  val label: JLabel = JLabel(labelText).apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty(6, 0, 6, 5)
  }

  open fun additionalControls(): List<JComponent> = emptyList()

  init {
    participantsVm.forEach(detailsVm.lifetime) { users ->
      panel.removeAll()

      if (users?.size == 0) {
        val additionalControls = additionalControls()
        if (additionalControls.isNotEmpty()) {
          val control = Wrapper(additionalControls[0]).apply {
            border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP / 2)
          }
          panel.add(control)
        }
      }

      users?.forEachIndexed { index, codeReviewParticipant ->
        val memberProfile = codeReviewParticipant.user.resolve()
        val fullName = memberProfile.englishFullName() // NON-NLS
        val reviewerLabel = JLabel(fullName, avatarProvider.getIcon(memberProfile), SwingConstants.LEFT).apply {
          border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP / 2)
          toolTipText = fullName
        }
        avatarProvider.getIcon(memberProfile)
        val participantPanel = Wrapper(reviewerLabel)
        val additionalControls = additionalControls()
        if (index == users.lastIndex && additionalControls.isNotEmpty()) {
          participantPanel.add(additionalControls[0], BorderLayout.LINE_END)
        }
        panel.add(participantPanel)
      }

      panel.validate()
      panel.repaint()
    }
  }
}