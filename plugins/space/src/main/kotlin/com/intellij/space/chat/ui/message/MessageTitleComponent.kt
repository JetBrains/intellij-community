// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.message

import circlet.client.api.CApplicationPrincipalDetails
import circlet.client.api.CPrincipal
import circlet.client.api.CUserPrincipalDetails
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.api.SpaceChatItemAdditionalFeature
import com.intellij.space.chat.ui.link
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAutoUpdatableComponentService
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.utils.formatPrettyDateTime
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.InlineIconButton
import icons.VcsCodeReviewIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.delay
import libraries.coroutines.extra.launch
import runtime.Ui
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JPanel

internal class MessageTitleComponent(
  private val project: Project,
  private val lifetime: Lifetime,
  message: SpaceChatItem
) : JPanel(HorizontalLayout(JBUI.scale(5))) {
  val actionsPanel = JPanel(HorizontalLayout(JBUI.scale(5))).apply {
    isOpaque = false
    isVisible = false
    add(createStartThreadButton(message))
    createEditButton(message)?.let { add(it) }
    createDeleteButton(message)?.let { add(it) }
  }

  init {
    val authorPanel = HtmlEditorPane().apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      setBody(createMessageAuthorChunk(message.author).bold().toString())
    }
    val timePanel = SpaceAutoUpdatableComponentService.createAutoUpdatableComponent {
      HtmlEditorPane().apply {
        putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
        foreground = UIUtil.getContextHelpForeground()
        setBody(HtmlChunk.text(message.created.formatPrettyDateTime()).toString()) // NON-NLS
      }
    }

    isOpaque = false
    add(authorPanel)
    add(timePanel)
    val showResolvedStateFeature = message.additionalFeatures.filterIsInstance<SpaceChatItemAdditionalFeature.ShowResolvedState>().singleOrNull()
    if (showResolvedStateFeature != null) {
      add(createResolvedComponent(lifetime, showResolvedStateFeature.resolved))
    }
    add(actionsPanel)
    launch(lifetime, Ui) {
      delay(2000)
      if (!message.delivered) {
        add(JBLabel(AnimatedIcon.Default.INSTANCE))
        revalidate()
        repaint()
      }
    }
  }

  private fun createMessageAuthorChunk(author: CPrincipal): HtmlChunk =
    when (val details = author.details) {
      is CUserPrincipalDetails -> {
        val user = details.user.resolve()
        user.link()
      }
      is CApplicationPrincipalDetails -> {
        val app = details.app.resolve()
        HtmlChunk.link(SpaceUrls.app(app), app.name) // NON-NLS
      }
      else -> {
        HtmlChunk.text(author.name) // NON-NLS
      }
    }

  private fun createDeleteButton(message: SpaceChatItem): JComponent? {
    if (!message.canDelete) {
      return null
    }
    return InlineIconButton(
      VcsCodeReviewIcons.Delete,
      VcsCodeReviewIcons.DeleteHovered,
      tooltip = SpaceBundle.message("chat.message.action.delete.tooltip")
    ).apply {
      actionListener = ActionListener {
        if (
          MessageDialogBuilder.yesNo(
            SpaceBundle.message("chat.message.delete.dialog.title"),
            SpaceBundle.message("chat.message.delete.dialog.message")
          ).ask(this)
        ) {
          SpaceStatsCounterCollector.DELETE_MESSAGE.log(project)
          launch(lifetime, Ui) {
            message.delete()
          }
        }
      }
    }
  }

  private fun createEditButton(message: SpaceChatItem): JComponent? {
    if (!message.canEdit) {
      return null
    }
    return InlineIconButton(
      AllIcons.General.Inline_edit,
      AllIcons.General.Inline_edit_hovered,
      tooltip = SpaceBundle.message("chat.message.action.edit.tooltip")
    ).apply {
      actionListener = ActionListener {
        message.startEditing()
      }
    }
  }

  private fun createStartThreadButton(message: SpaceChatItem): JComponent {
    val startThreadVm = message.startThreadVm
    val button = InlineIconButton(
      VcsCodeReviewIcons.Comment,
      VcsCodeReviewIcons.CommentHovered,
      tooltip = SpaceBundle.message("chat.message.action.start.thread.tooltip")
    ).apply {
      actionListener = ActionListener {
        startThreadVm.startWritingFirstMessage()
      }
    }
    startThreadVm.canStartThread.forEach(lifetime) {
      button.isVisible = it
    }
    return button
  }
}
