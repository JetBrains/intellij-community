// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.header

import circlet.code.api.CodeReviewState
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.space.chat.model.impl.SpaceChatReviewHeaderDetails
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.getGrayTextHtml
import com.intellij.space.ui.resizeIcon
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.VcsCodeReviewIcons
import libraries.coroutines.extra.Lifetime
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class SpaceChatReviewHeaderComponent(
  lifetime: Lifetime,
  details: SpaceChatReviewHeaderDetails
) : JPanel() {
  companion object {
    private const val STATE_ICON_FACTOR: Double = 2.0 / 3

    private const val STATE_ICON_GAP_FACTOR: Double = 0.5
  }

  private val stateIconSize: Int
    get() = (SpaceChatAvatarType.MAIN_CHAT.size.get() * STATE_ICON_FACTOR).toInt()

  init {
    isOpaque = false
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill()).apply {
      columnConstraints = "[][]"
    }

    val reviewStateIconPanel = BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.emptyRight((stateIconSize * STATE_ICON_GAP_FACTOR).toInt())
    }
    val headerContent = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.5).toFloat())
    }

    add(reviewStateIconPanel, CC().pushY())
    add(headerContent, CC().pushX().alignY("center"))

    details.title.forEach(lifetime) { newTitle ->
      headerContent.setBody(getHeaderHtml(newTitle, details.reviewKey))
    }
    details.state.forEach(lifetime) { newState ->
      if (reviewStateIconPanel.components.isNotEmpty()) {
        reviewStateIconPanel.remove(0)
      }
      reviewStateIconPanel.add(getReviewStateIcon(newState), 0)
      reviewStateIconPanel.revalidate()
      reviewStateIconPanel.repaint()
    }
  }

  private fun getReviewStateIcon(state: CodeReviewState): JComponent {
    val icon = when (state) {
      CodeReviewState.Opened -> VcsCodeReviewIcons.PullRequestOpen
      CodeReviewState.Closed, CodeReviewState.Deleted -> VcsCodeReviewIcons.PullRequestClosed
    }
    return JLabel(resizeIcon(icon, stateIconSize))
  }

  @Nls
  private fun getHeaderHtml(@Nls title: String, @NlsSafe reviewKey: String?): String {
    val builder = HtmlBuilder().append(title)
    if (reviewKey != null) {
      builder
        .nbsp()
        .appendRaw(getGrayTextHtml(reviewKey))
    }
    return builder.toString()
  }
}