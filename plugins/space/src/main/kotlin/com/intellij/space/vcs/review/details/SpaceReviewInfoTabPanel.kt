// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.englishFullName
import circlet.code.api.CodeReviewRecord
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.utils.formatPrettyDateTime
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.space.vcs.review.details.process.SpaceReviewAction
import com.intellij.space.vcs.review.details.process.SpaceReviewActionsBuilder
import com.intellij.space.vcs.review.openReviewInEditor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.FontUtil
import com.intellij.util.containers.tail
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.FlowLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class SpaceReviewInfoTabPanel(detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>) : BorderLayoutPanel() {
  init {
    val titleComponent = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.2).toFloat())
    }

    val createdByComponent = JBLabel().apply {
      font = JBUI.Fonts.smallFont()
      foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
    }

    detailsVm.createdBy.forEach(detailsVm.lifetime) {
      createdByComponent.text = SpaceBundle.message(
        "review.label.created.by.user.at.time",
        it.englishFullName(),
        detailsVm.createdAt.value.formatPrettyDateTime()
      )
    }

    detailsVm.title.forEach(detailsVm.lifetime) {
      titleComponent.setBody(it)
    }

    val gap = 0
    val projectDetails = NonOpaquePanel(
      FlowLayout(FlowLayout.LEADING,
                 JBUI.scale(gap),
                 JBUI.scale(gap))).apply {

      @NlsSafe val projectName = detailsVm.spaceProjectInfo.project.name
      val projectLink = link(projectName, SpaceUrls.project(detailsVm.projectKey))
      val reviewLinkLabel = link(detailsVm.reviewKey ?: "", detailsVm.reviewUrl)

      add(projectLink)
      add(JLabel("${FontUtil.spaceAndThinSpace()}/${FontUtil.spaceAndThinSpace()}"))
      add(reviewLinkLabel)
    }

    val actionsPanel = NonOpaquePanel()

    val usersPanel = NonOpaquePanel().apply {
      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))
    }

    detailsVm.participantsVm.forEach(detailsVm.lifetime) { participantsVm: SpaceReviewParticipantsVm? ->
      usersPanel.removeAll()
      if (participantsVm != null) {
        val reviewersComponent = ReviewersComponent(
          detailsVm,
          participantsVm
        )
        val authorsComponent = AuthorsComponent(
          detailsVm,
          participantsVm
        )

        addListPanel(usersPanel, authorsComponent.label, authorsComponent.panel)
        addListPanel(usersPanel, reviewersComponent.label, reviewersComponent.panel)

        participantsVm.controlVm.forEach(detailsVm.lifetime) {
          actionsPanel.setContent(createActionButton(detailsVm, it))
          actionsPanel.validate()
          actionsPanel.repaint()
          validate()
          repaint()
        }
      }

      validate()
      repaint()
    }

    val openTimelineLinkLabel = LinkLabel.create(SpaceBundle.message("review.details.view.timeline.link.action")) {
      openReviewInEditor(detailsVm.ideaProject,
                         detailsVm.workspace,
                         detailsVm.spaceProjectInfo,
                         detailsVm.reviewRef
      )
    }


    val contentPanel: JPanel = ScrollablePanel(VerticalLayout(JBUI.scale(6))).apply {
      border = JBUI.Borders.empty(8)
      add(projectDetails)

      if (detailsVm is MergeRequestDetailsVm) {
        add(createDirectionPanel(detailsVm))
      }

      add(titleComponent)
      add(createdByComponent)
      add(usersPanel)
      add(actionsPanel)
      add(openTimelineLinkLabel)
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(contentPanel, true)

    addToCenter(scrollPane)
    UIUtil.setOpaqueRecursively(scrollPane, false)
    UIUtil.setBackgroundRecursively(this, UIUtil.getListBackground())
  }

  private fun createActionButton(detailsVm: SpaceReviewDetailsVm<*>, controlVM: ParticipantStateControlVM): JComponent? {
    val actionsBuilder = SpaceReviewActionsBuilder(detailsVm.reviewStateUpdater)
    return when (controlVM) {
      is ParticipantStateControlVM.ReviewerDropdown -> optionButton {
        actionsBuilder.createAcceptActions(detailsVm.lifetime, controlVM)
      }
      is ParticipantStateControlVM.ReviewerResumeReviewButton -> optionButton {
        actionsBuilder.createResumeActions(detailsVm.lifetime, controlVM)
      }

      is ParticipantStateControlVM.AuthorEndTurnButton -> null
      ParticipantStateControlVM.AuthorResumeReviewButton -> null
      ParticipantStateControlVM.WithoutControls -> null
    }
  }
}

private fun createDirectionPanel(detailsVm: MergeRequestDetailsVm): NonOpaquePanel {
  val layout = MigLayout(
    LC()
      .fillX()
      .gridGap("0", "0")
      .insets("0", "0", "0", "0")
  )
  return NonOpaquePanel(layout).apply {
    val repo = detailsVm.repository.value
    val target = detailsVm.targetBranchInfo.value?.displayName // NON-NLS
    val source = detailsVm.sourceBranchInfo.value?.displayName // NON-NLS

    add(JLabel("$repo: "))
    add(JLabel(target), CC())
    add(JLabel(" ${UIUtil.leftArrow()} ").apply {
      foreground = CurrentBranchComponent.TEXT_COLOR
      border = JBUI.Borders.empty(0, 5)
    })
    add(JLabel(source), CC())
  }
}

private fun addListPanel(panel: JPanel, label: JLabel, jComponent: JComponent) {
  panel.add(label, CC().alignY("top"))
  panel.add(jComponent, CC().minWidth("0").growX().pushX().wrap())
}


fun link(@NlsContexts.LinkLabel text: String, url: String): LinkLabel<*> {
  return LinkLabel.create(text) { BrowserUtil.browse(url) }
}

private fun optionButton(builder: () -> List<SpaceReviewAction>): JBOptionButton {
  val actions = builder()
  val options: Array<Action> = actions.tail().toTypedArray()
  return JBOptionButton(actions.firstOrNull(), options.ifEmpty { null })
}