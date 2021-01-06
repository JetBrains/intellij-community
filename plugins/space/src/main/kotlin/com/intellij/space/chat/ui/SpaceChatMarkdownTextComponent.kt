// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.completion.mentions.MentionConverter
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.chat.markdown.convertToHtml
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.HtmlEditorPane
import org.jetbrains.annotations.NonNls

internal class SpaceChatMarkdownTextComponent(
  private val server: @NonNls String,
  initialText: @NlsSafe String? = null,
  initialIsEdited: Boolean = false
) : HtmlEditorPane() {
  init {
    initialText?.let { setMarkdownText(it, initialIsEdited) }
  }

  fun setMarkdownText(text: @NlsSafe String, isEdited: Boolean) {
    setBody(processItemText(server, text, isEdited))
  }
}

@NlsSafe
private fun processItemText(server: @NonNls String, @NlsSafe text: String, isEdited: Boolean) = convertToHtml(
  MentionConverter.markdown(text, server).let {
    if (isEdited) {
      it + " " + getGrayTextHtml(SpaceBundle.message("chat.message.edited.text"))
    }
    else {
      it
    }
  }
)