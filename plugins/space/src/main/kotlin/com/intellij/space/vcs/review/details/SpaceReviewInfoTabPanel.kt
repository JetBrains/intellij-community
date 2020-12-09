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
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nullable
import java.awt.FlowLayout
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


    val contentPanel: JPanel = ScrollablePanel(VerticalLayout(JBUI.scale(6))).apply {
      border = JBUI.Borders.empty(8)
      add(projectDetails)

      if (detailsVm is MergeRequestDetailsVm) {
        add(createDirectionPanel(detailsVm))
      }

      add(titleComponent)
      add(createdByComponent)
      add(usersPanel, VerticalLayout.FILL_HORIZONTAL)
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(contentPanel, true)

    addToCenter(scrollPane)
    UIUtil.setOpaqueRecursively(scrollPane, false)
    UIUtil.setBackgroundRecursively(this, UIUtil.getListBackground())
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
}

private fun addListPanel(panel: JPanel, label: JLabel, jComponent: JComponent) {
  panel.add(label, CC().alignY("top"))
  panel.add(jComponent, CC().minWidth("0").growX().pushX().wrap())
}


fun link(@Nullable @NlsContexts.LinkLabel text: String, url: String): LinkLabel<*> {
  return LinkLabel.create(text) { BrowserUtil.browse(url) }
}

