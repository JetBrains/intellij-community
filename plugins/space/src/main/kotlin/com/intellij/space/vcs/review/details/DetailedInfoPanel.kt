package com.intellij.space.vcs.review.details

import circlet.client.api.englishFullName
import circlet.code.api.CodeReviewRecord
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.space.utils.formatAbsolute
import com.intellij.space.utils.toLocalDateTime
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class DetailedInfoPanel(detailsVm: CrDetailsVm<out CodeReviewRecord>) {
  val view: JComponent

  init {
    val titleComponent = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.2).toFloat())
    }

    val infoLabel = JBLabel().apply {
      font = JBUI.Fonts.smallFont()
      foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
    }

    detailsVm.createdBy.forEach(detailsVm.lifetime) {
      infoLabel.text = "created by ${it!!.englishFullName()} ${detailsVm.createdAt.value.toLocalDateTime().formatAbsolute()}"
    }

    detailsVm.title.forEach(detailsVm.lifetime) {
      titleComponent.setBody(it)
    }

    val gap = 0
    val projectDetails = NonOpaquePanel(
      FlowLayout(FlowLayout.LEADING,
                 JBUI.scale(gap),
                 JBUI.scale(gap))).apply {

      val projectKeyComponent = SimpleColoredComponent().apply {
        ipad = JBUI.insetsLeft(0)
        append(detailsVm.projectKey.key)
        append(" / ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }

      val reviewLinkLabel = LinkLabel.create(detailsVm.reviewKey ?: "") {
        BrowserUtil.open(detailsVm.reviewUrl)
      }

      add(projectKeyComponent)
      add(reviewLinkLabel)
    }

    val detailsPanel: JPanel = JPanel(VerticalLayout(JBUI.scale(6))).apply {
      border = JBUI.Borders.empty(8)
    }

    val usersPanel = NonOpaquePanel().apply {
      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))

      detailsVm.participantsVm.forEach(detailsVm.lifetime) { participantsVm: SpaceReviewParticipantsVm? ->
        removeAll()
        if (participantsVm != null) {
          val reviewersComponent = ReviewersComponent(
            detailsVm,
            participantsVm
          )
          val authorsComponent = AuthorsComponent(
            detailsVm,
            participantsVm
          )

          addListPanel(this, authorsComponent.label, authorsComponent.panel)
          addListPanel(this, reviewersComponent.label, reviewersComponent.panel)
        }
        validate()
        repaint()
      }
    }

    detailsPanel.add(projectDetails)

    if (detailsVm is MergeRequestDetailsVm) {
      detailsPanel.add(createDirectionPanel(detailsVm))
    }

    detailsPanel.add(titleComponent)
    detailsPanel.add(infoLabel)
    detailsPanel.add(usersPanel, VerticalLayout.FILL_HORIZONTAL)


    val scrollablePanel = ScrollablePanel(VerticalFlowLayout(0, 0)).apply {
      isOpaque = false
      add(detailsPanel)
    }

    view = ScrollPaneFactory.createScrollPane(scrollablePanel, true).apply {
      viewport.isOpaque = false
      isOpaque = false
    }

    UIUtil.setBackgroundRecursively(view, UIUtil.getListBackground())

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
      val target = detailsVm.targetBranchInfo.value?.displayName
      val source = detailsVm.sourceBranchInfo.value?.displayName

      add(JLabel("$repo: "))
      add(JLabel(target), CC())
      add(JLabel(" ${UIUtil.leftArrow()} ").apply {
        foreground = CurrentBranchComponent.TEXT_COLOR
        border = JBUI.Borders.empty(0, 5)
      })
      add(JLabel(source), CC())
    }
  }
}

private fun addListPanel(panel: JPanel, label: JLabel, jComponent: JComponent) {
  panel.add(label, CC().alignY("top"))
  panel.add(jComponent, CC().minWidth("0").growX().pushX().wrap())
}


