// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.impl

import circlet.client.api.mc.*
import com.intellij.space.chat.model.api.SpaceMCMessageContent
import com.intellij.space.chat.model.api.SpaceMCMessageElement
import com.intellij.space.chat.model.api.SpaceMCMessageSection

internal fun ChatMessage.Block.toMCMessageContent(): SpaceMCMessageContent =
  SpaceMCMessageContentImpl(outline, sections.mapNotNull { it.convert() }, style ?: MessageStyle.PRIMARY)

private fun MessageSectionElement.convert(): SpaceMCMessageSection? = when (this) {
  is MessageSection -> SpaceMCMessageSection.Section(header, elements.mapNotNull { it.convert() }, footer)
  is MessageDivider -> SpaceMCMessageSection.Divider
  else -> null
}

private fun MessageElement.convert(): SpaceMCMessageElement? = when (this) {
  is MessageText -> SpaceMCMessageElement.Text(content)
  is MessageDivider -> SpaceMCMessageElement.MessageDivider
  else -> null
}

private class SpaceMCMessageContentImpl(
  override val outline: MessageOutline?,
  override val sections: List<SpaceMCMessageSection>,
  override val style: MessageStyle
) : SpaceMCMessageContent