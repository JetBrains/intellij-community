// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.message

import circlet.client.api.CExternalServicePrincipalDetails
import circlet.client.api.CPrincipal
import circlet.client.api.CUserPrincipalDetails
import circlet.client.api.Navigator
import circlet.platform.api.format
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.ui.link
import com.intellij.space.messages.SpaceBundle
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
import runtime.date.DateFormat
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JPanel

internal class MessageTitleComponent(
  private val lifetime: Lifetime,
  message: SpaceChatItem,
  private val server: String
) : JPanel(HorizontalLayout(JBUI.scale(5))) {
  val actionsPanel = JPanel(HorizontalLayout(JBUI.scale(5))).apply {
    isOpaque = false
    isVisible = false
    createEditButton(message)?.let { add(it) }
    createDeleteButton(message)?.let { add(it) }
  }

  init {
    val authorPanel = HtmlEditorPane().apply {
      setBody(createMessageAuthorChunk(message.author).bold().toString())
    }
    val timePanel = HtmlEditorPane().apply {
      foreground = UIUtil.getContextHelpForeground()
      setBody(HtmlChunk.text(message.created.format(DateFormat.HOURS_AND_MINUTES)).toString()) // NON-NLS
    }
    val editedPanel = JBLabel(SpaceBundle.message("chat.message.edited.text"), UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
      isOpaque = true
    }

    isOpaque = false
    add(authorPanel)
    add(timePanel)
    if (message.isEdited) {
      add(editedPanel)
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
        user.link(server)
      }
      is CExternalServicePrincipalDetails -> {
        val service = details.service.resolve()
        val location = Navigator.manage.oauthServices.service(service)
        HtmlChunk.link(location.href, service.name) // NON-NLS
      }
      else -> {
        HtmlChunk.text(author.name) // NON-NLS
      }
    }

  private fun createDeleteButton(message: SpaceChatItem): JComponent? {
    if (!message.canDelete) {
      return null
    }
    return InlineIconButton(VcsCodeReviewIcons.Delete, VcsCodeReviewIcons.DeleteHovered).apply {
      actionListener = ActionListener {
        if (
          MessageDialogBuilder.yesNo(
            SpaceBundle.message("chat.message.delete.dialog.title"),
            SpaceBundle.message("chat.message.delete.dialog.message")
          ).ask(this)
        ) {
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
    return InlineIconButton(AllIcons.General.Inline_edit, AllIcons.General.Inline_edit_hovered).apply {
      actionListener = ActionListener {
        message.startEditing()
      }
    }
  }
}
