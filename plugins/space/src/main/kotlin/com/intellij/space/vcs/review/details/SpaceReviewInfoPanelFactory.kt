// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.englishFullName
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.utils.formatPrettyDateTime
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.space.vcs.review.details.process.SpaceReviewAction
import com.intellij.space.vcs.review.details.process.SpaceReviewActionsBuilder
import com.intellij.space.vcs.review.openReviewInEditor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.*
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.FontUtil
import com.intellij.util.containers.tail
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.reactive.view
import java.awt.FlowLayout
import java.beans.PropertyChangeListener
import javax.swing.*

internal object SpaceReviewInfoPanelFactory {
  internal fun create(detailsVm: SpaceReviewDetailsVm<*>): JComponent {
    val topLevelPanel = BorderLayoutPanel()

    val titleComponent = HtmlEditorPane().apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      font = font.deriveFont((font.size * 1.3).toFloat())
    }

    val createdByComponent = JBLabel().apply {
      font = JBUI.Fonts.smallFont()
      foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
      setCopyable(true)
    }

    detailsVm.createdBy.forEach(detailsVm.lifetime) { author ->
      val createdAt = detailsVm.createdAt.value.formatPrettyDateTime()
      createdByComponent.text = if (author != null) {
        SpaceBundle.message(
          "review.label.created.by.user.at.time",
          author.englishFullName(),
          createdAt
        )
      }
      else {
        SpaceBundle.message("review.label.created.at.time", createdAt)
      }
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
      val projectLink = BrowserLink(
        icon = null,
        text = projectName,
        tooltip = null,
        url = SpaceUrls.project(detailsVm.projectKey)
      ).apply {
        addActionListener {
          SpaceStatsCounterCollector.OPEN_PROJECT_IN_SPACE.log(detailsVm.ideaProject)
        }
      }
      val reviewLink = BrowserLink(
        icon = null,
        text = detailsVm.reviewKey ?: "",
        tooltip = null,
        url = detailsVm.reviewUrl
      ).apply {
        addActionListener {
          SpaceStatsCounterCollector.OPEN_REVIEW_IN_SPACE.log(detailsVm.ideaProject)
        }
      }

      add(projectLink)
      add(JLabel("${FontUtil.spaceAndThinSpace()}/${FontUtil.spaceAndThinSpace()}"))
      add(reviewLink)
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
          topLevelPanel,
          detailsVm,
          participantsVm
        )
        val authorsComponent = AuthorsComponent(
          topLevelPanel,
          detailsVm,
          participantsVm
        )

        addListPanel(usersPanel, authorsComponent.label, authorsComponent.panel)
        addListPanel(usersPanel, reviewersComponent.label, reviewersComponent.panel)

        participantsVm.controlVm.forEach(detailsVm.lifetime) {
          actionsPanel.setContent(createActionButton(detailsVm, it))
          topLevelPanel.validate()
          topLevelPanel.repaint()
        }
      }
    }

    val openTimelineActionLink = ActionLink(SpaceBundle.message("review.details.view.chat.link.action")) {
      SpaceStatsCounterCollector.SHOW_TIMELINE.log(detailsVm.ideaProject)
      openReviewInEditor(detailsVm.ideaProject,
                         detailsVm.workspace,
                         detailsVm.spaceProjectInfo,
                         detailsVm.reviewRef
      )
    }

    val contentPanel: JScrollPane = ScrollablePanel(VerticalLayout(JBUI.scale(8))).apply {
      border = JBUI.Borders.empty(8)
      isOpaque = false
      add(projectDetails)

      if (detailsVm is MergeRequestDetailsVm) {
        add(createBranchesPanel(this, detailsVm))
      }

      add(titleComponent)
      add(createdByComponent)
      add(usersPanel, VerticalLayout.FILL_HORIZONTAL)
      add(openTimelineActionLink)
      add(actionsPanel)
    }.let { scrollablePanel ->
      ScrollPaneFactory.createScrollPane(scrollablePanel, true).apply {
        isOpaque = false
        viewport.isOpaque = false
      }
    }

    return topLevelPanel.apply {
      andOpaque()
      background = UIUtil.getListBackground()
      addToCenter(contentPanel)
    }
  }

  private fun createBranchesPanel(parent: JPanel, detailsVm: MergeRequestDetailsVm): BorderLayoutPanel {
    val actionManager = ActionManager.getInstance()

    val checkoutAction = actionManager.getAction("com.intellij.space.vcs.review.details.SpaceReviewCheckoutBranchAction")
    val checkoutActionLink = AnActionLink(SpaceBundle.message("review.actions.link.label.checkout"), checkoutAction)

    val updateAction = actionManager.getAction("com.intellij.space.vcs.review.details.SpaceReviewUpdateBranchAction")
    val updateActionLink = AnActionLink(SpaceBundle.message("review.actions.link.label.update"), updateAction)

    val branchActionPanel = NonOpaquePanel().apply {
      border = JBUI.Borders.emptyLeft(4)
    }

    val branchesPanel = BorderLayoutPanel()
      .addToCenter(createDirectionPanel(detailsVm))
      .addToRight(branchActionPanel)
      .andTransparent()

    detailsVm.mergeRequestBranchInfo.view(detailsVm.lifetime) { _, mergeRequestBranchInfo ->
      val action = if (mergeRequestBranchInfo.isCurrentBranch) updateActionLink else checkoutActionLink
      branchActionPanel.setContent(action)
      parent.repaint()
    }
    return branchesPanel
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

private fun addListPanel(panel: JPanel, label: JLabel, jComponent: JComponent) {
  panel.add(label, CC().alignY("top"))
  panel.add(jComponent, CC().minWidth("0").growX().pushX().wrap())
}

private fun optionButton(builder: () -> List<SpaceReviewAction>): JBOptionButton {
  val actions = builder()
  val options: Array<Action> = actions.tail().toTypedArray()
  return JBOptionButton(actions.firstOrNull(), options.ifEmpty { null }).apply {
    isOpaque = false
    background = UIUtil.getListBackground()
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

    if (target == null || source == null) return@apply

    val repoLabel = JLabel("$repo${FontUtil.spaceAndThinSpace()}/${FontUtil.spaceAndThinSpace()}")
      .apply { foreground = CurrentBranchComponent.TEXT_COLOR }
    add(repoLabel)
    add(branchLabel(target), CC())
    add(JLabel("${FontUtil.spaceAndThinSpace()}${UIUtil.leftArrow()}${FontUtil.spaceAndThinSpace()}").apply {
      foreground = CurrentBranchComponent.TEXT_COLOR
    })
    add(branchLabel(source), CC())
  }
}

private fun branchLabel(@NlsContexts.Label text: String): JBLabel {
  return JBLabel(text).apply {
    border = JBUI.Borders.empty(0, 3)
    addPropertyChangeListener("UI", PropertyChangeListener {
      foreground = CurrentBranchComponent.TEXT_COLOR
      background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())
    })
    foreground = CurrentBranchComponent.TEXT_COLOR
    background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())
  }.andOpaque()
}


