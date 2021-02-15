// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.message

import circlet.client.api.AttachmentInfo
import circlet.client.api.UnfurlAttachment
import circlet.client.api.mc.*
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.chat.ui.SpaceChatMarkdownTextComponent
import com.intellij.space.chat.ui.getGrayTextHtml
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import javax.swing.JPanel

internal class SpaceMCMessageComponent(
  @NonNls private val server: String,
  message: ChatMessage.Block,
  attachments: List<AttachmentInfo>
) : BorderLayoutPanel() {
  companion object {
    private const val SMALL_FONT_SCALING = 0.9
    private const val HEADER_FONT_SCALING = 1.2
  }

  private val unfurls = attachments.mapNotNull { it.details }.filterIsInstance<UnfurlAttachment>()

  init {
    isOpaque = false

    message.outline?.let { outline ->
      addToTop(SpaceChatMarkdownTextComponent(server, outline.text, initialUnfurls = unfurls).withSmallFont())
    }

    val sectionsComponent = message.sections.mapNotNull { it.createComponent() }.wrapToSingleComponent()
    addToCenter(SpaceStyledMessageComponent(sectionsComponent, style = message.style ?: MessageStyle.PRIMARY))
  }

  private fun MessageSectionElement.createComponent(): JComponent? =
    when (this) {
      is MessageDivider -> createDividerPanel()
      is MessageSection -> createComponent()
      else -> null
    }

  private fun MessageSection.createComponent(): JComponent = JPanel(VerticalLayout(JBUI.scale(10))).apply {
    isOpaque = false

    header?.let { add(createHeaderComponent(it), VerticalLayout.FILL_HORIZONTAL) }

    val elementsComponent = elements.mapNotNull { it.createComponent() }.wrapToSingleComponent()
    add(elementsComponent, VerticalLayout.FILL_HORIZONTAL)

    footer?.let { add(createFooterComponent(it), VerticalLayout.FILL_HORIZONTAL) }
  }

  private fun MessageElement.createComponent(): JComponent? =
    when (this) {
      is MessageDivider -> createDividerPanel()
      is MessageText -> SpaceChatMarkdownTextComponent(server, content, initialUnfurls = unfurls)
      is MessageFields -> null
      is MessageControlGroup -> null
      else -> null
    }

  private fun createHeaderComponent(@NlsSafe text: String) = SpaceChatMarkdownTextComponent(server, text, initialUnfurls = unfurls).apply {
    scaleFont(HEADER_FONT_SCALING)
  }

  private fun createFooterComponent(@NlsSafe text: String) =
    SpaceChatMarkdownTextComponent(server, getGrayTextHtml(text), initialUnfurls = unfurls).withSmallFont()

  private fun JComponent.withSmallFont(): JComponent = scaleFont(SMALL_FONT_SCALING)

  private fun JComponent.scaleFont(scaling: Double): JComponent = apply {
    font = font.deriveFont((font.size * scaling).toFloat())
  }

  private fun createDividerPanel() = JPanel().apply {
    isOpaque = false
    border = IdeBorderFactory.createBorder(OnePixelDivider.BACKGROUND, SideBorder.TOP)
  }

  private fun List<JComponent>.wrapToSingleComponent(): JComponent {
    val wrapper = JPanel(VerticalLayout(JBUI.scale(10)))
    forEach { component ->
      wrapper.add(component, VerticalLayout.FILL_HORIZONTAL)
    }
    return wrapper.apply {
      isOpaque = false
    }
  }
}