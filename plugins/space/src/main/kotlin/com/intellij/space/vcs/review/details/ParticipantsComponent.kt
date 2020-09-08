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
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel

internal class AuthorsComponent(
  detailsVm: CrDetailsVm<out CodeReviewRecord>,
  participantsVm: SpaceReviewParticipantsVm
) : ParticipantsComponent(detailsVm, participantsVm.authors, SpaceBundle.message("review.label.authors"))

internal class ReviewersComponent(
  private val detailsVm: CrDetailsVm<out CodeReviewRecord>,
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
  detailsVm: CrDetailsVm<out CodeReviewRecord>,
  participantsVm: Property<List<CodeReviewParticipant>?>,
  @NlsContexts.Label labelText: String
) : Lifetimed by detailsVm {


  val panel: NonOpaquePanel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, JBUI.scale(5), JBUI.scale(5))).apply {
    border = JBUI.Borders.empty(6, 0, 6, 0)
  }

  private val avatarProvider = SpaceAvatarProvider(detailsVm.lifetime, panel, avatarSizeIntValue)


  val label: JLabel = JLabel(labelText).apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty(6, 0, 6, 5)
  }

  open fun additionalControls(): List<JComponent> = emptyList()


  init {
    participantsVm.forEach(detailsVm.lifetime) { users ->
      panel.removeAll()

      users?.forEach { codeReviewParticipant ->
        val memberProfile = codeReviewParticipant.user.resolve()
        val fullName = memberProfile.englishFullName()
        val reviewerLabel = JLabel(avatarProvider.getIcon(memberProfile)).apply {
          toolTipText = fullName
          text = fullName
        }
        avatarProvider.getIcon(memberProfile)
        panel.add(Wrapper(reviewerLabel))
      }

      additionalControls().forEach {
        panel.add(Wrapper(it))
      }

      panel.validate()
      panel.repaint()
    }
  }
}