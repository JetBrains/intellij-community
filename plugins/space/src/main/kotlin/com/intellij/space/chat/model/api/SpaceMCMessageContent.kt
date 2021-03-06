// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.api

import circlet.client.api.mc.MessageOutline
import circlet.client.api.mc.MessageStyle
import javax.swing.Icon

internal interface SpaceMCMessageContent {
  val outline: MessageOutline?

  val sections: List<SpaceMCMessageSection>

  val style: MessageStyle
}

internal sealed class SpaceMCMessageSection {
  class Section(val header: String?, val elements: List<SpaceMCMessageElement>, val footer: String?) : SpaceMCMessageSection()
  object Divider : SpaceMCMessageSection()
}

internal sealed class SpaceMCMessageElement {
  class Text(val icon: Icon?, val content: String) : SpaceMCMessageElement()
  object MessageDivider : SpaceMCMessageElement()
}