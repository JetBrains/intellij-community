// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.message

import circlet.client.api.AttachmentInfo
import circlet.client.api.UnfurlAttachment
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.chat.model.api.SpaceMCMessageContent
import com.intellij.space.chat.model.api.SpaceMCMessageElement
import com.intellij.space.chat.model.api.SpaceMCMessageSection
import com.intellij.space.chat.ui.SpaceChatMarkdownTextComponent
import com.intellij.space.chat.ui.getGrayTextHtml
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import javax.swing.JPanel

internal class SpaceMCMessageComponent(
  @NonNls private val server: String,
  message: SpaceMCMessageContent,
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

    val sectionsComponent = message.sections.map { it.createComponent() }.wrapToSingleComponent()
    addToCenter(SpaceStyledMessageComponent(sectionsComponent, style = message.style))
  }

  private fun SpaceMCMessageSection.createComponent(): JComponent =
    when (this) {
      is SpaceMCMessageSection.Divider -> createDividerPanel()
      is SpaceMCMessageSection.Section -> createComponent()
    }

  private fun SpaceMCMessageSection.Section.createComponent(): JComponent = JPanel(VerticalLayout(JBUI.scale(10))).apply {
    isOpaque = false

    header?.let { add(createHeaderComponent(it), VerticalLayout.FILL_HORIZONTAL) }

    val elementsComponent = elements.map { it.createComponent() }.wrapToSingleComponent()
    add(elementsComponent, VerticalLayout.FILL_HORIZONTAL)

    footer?.let { add(createFooterComponent(it), VerticalLayout.FILL_HORIZONTAL) }
  }

  private fun SpaceMCMessageElement.createComponent(): JComponent =
    when (this) {
      is SpaceMCMessageElement.Text -> createComponent()
      is SpaceMCMessageElement.MessageDivider -> createDividerPanel()
    }

  private fun SpaceMCMessageElement.Text.createComponent(): JComponent {
    val textComponent = SpaceChatMarkdownTextComponent(server, content, initialUnfurls = unfurls)

    return BorderLayoutPanel().apply {
      isOpaque = false
      createIconComponent()?.let { iconComponent ->
        addToLeft(
          BorderLayoutPanel().apply {
            isOpaque = false
            border = JBUI.Borders.emptyRight(3)
            addToTop(iconComponent)
          }
        )
      }
      addToCenter(textComponent)
    }
  }

  private fun SpaceMCMessageElement.Text.createIconComponent(): JComponent? = icon?.let { JBLabel(icon) }

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