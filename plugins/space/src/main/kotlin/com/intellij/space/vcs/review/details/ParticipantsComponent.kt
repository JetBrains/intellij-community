// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.englishFullName
import circlet.client.api.identifier
import circlet.code.api.CodeReviewParticipant
import circlet.code.api.CodeReviewParticipantRole
import circlet.code.api.CodeReviewRecord
import circlet.code.api.identifier
import circlet.platform.client.resolve
import com.intellij.openapi.util.NlsContexts
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.vcs.review.ReviewUiSpec.avatarSizeIntValue
import com.intellij.space.vcs.review.details.selector.SpaceReviewParticipantSelectorFactory
import com.intellij.space.vcs.review.details.selector.createSelectorVm
import com.intellij.space.vcs.review.details.selector.createSpaceReviewParticipantController
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
  private val parentComponent: JComponent,
  detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>,
  participantsVm: SpaceReviewParticipantsVm
) : ParticipantsComponent(parentComponent,
                          detailsVm,
                          participantsVm,
                          participantsVm.authors,
                          SpaceBundle.message("review.label.authors"),
                          CodeReviewParticipantRole.Author)

internal class ReviewersComponent(
  private val parentComponent: JComponent,
  detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>,
  participantsVm: SpaceReviewParticipantsVm
) : ParticipantsComponent(parentComponent,
                          detailsVm,
                          participantsVm,
                          participantsVm.reviewers,
                          SpaceBundle.message("review.label.reviewers"),
                          CodeReviewParticipantRole.Reviewer)

internal open class ParticipantsComponent(
  private val parentComponent: JComponent,
  private val detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>,
  private val participantsVm: SpaceReviewParticipantsVm,
  participants: Property<List<CodeReviewParticipant>?>,
  @NlsContexts.Label labelText: String,
  val role: CodeReviewParticipantRole
) : Lifetimed by detailsVm {

  val panel: NonOpaquePanel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

  private val avatarProvider = SpaceAvatarProvider(detailsVm.lifetime, panel, avatarSizeIntValue)

  val label: JLabel = JLabel(labelText).apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty(6, 0, 6, 5)
  }

  init {
    participants.forEach(detailsVm.lifetime) { users ->
      panel.removeAll()

      if (users?.size == 0) {
        val additionalControl = additionalControl(role)
        val control = Wrapper(additionalControl).apply {
          border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP / 2)
        }
        panel.add(control)
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
        val additionalControl = additionalControl(role)
        if (index == users.lastIndex) {
          participantPanel.add(additionalControl, BorderLayout.LINE_END)
        }
        panel.add(participantPanel)
      }

      parentComponent.validate()
      parentComponent.repaint()
    }
  }

  private fun additionalControl(role: CodeReviewParticipantRole): JComponent {
    val popupLifetime = SequentialLifetimes(lifetime)

    val listener: (e: ActionEvent) -> Unit = {
      val nextLifetime = popupLifetime.next()

      val client = detailsVm.client
      val projectIdentifier = detailsVm.projectKey.identifier
      val reviewIdentifier = detailsVm.review.value.identifier

      val selectorVm = createSelectorVm(participantsVm, detailsVm, projectIdentifier, nextLifetime, client, reviewIdentifier, role)
      val controller = createSpaceReviewParticipantController(client, projectIdentifier, reviewIdentifier, role)

      val componentData = SpaceReviewParticipantSelectorFactory.create(
        selectorVm, nextLifetime, controller,
        if (role == CodeReviewParticipantRole.Reviewer) SpaceBundle.message("review.participant.suggested.reviewers") else ""
      )
      showPopup(panel,
                componentData.component,
                componentData.focusableComponent,
                componentData.clickListener)
    }

    return editIconButton().apply {
      border = JBUI.Borders.empty(6, 0)
      actionListener = ActionListener {
        SpaceStatsCounterCollector.EDIT_PARTICIPANT_ICON.log(role)
        listener(it)
      }
    }
  }
}