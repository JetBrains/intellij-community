// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt.error

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

internal object TerminalPromptErrorUtil {
  fun createErrorComponent(description: TerminalPromptErrorDescription, colorScheme: EditorColorsScheme): JComponent {
    val panel = JPanel(ListLayout.horizontal())
    panel.border = JBUI.Borders.emptyTop(4)
    panel.isOpaque = false

    val errorLabel = JBLabel(description.errorText)
    errorLabel.setCopyable(true)
    errorLabel.icon = description.icon
    errorLabel.iconTextGap = JBUI.scale(6)
    errorLabel.foreground = colorScheme.getAttributes(ConsoleViewContentType.ERROR_OUTPUT_KEY)?.foregroundColor
    panel.add(errorLabel)

    val linkText = description.linkText
    if (linkText != null) {
      val linkLabel = ActionLink(linkText) { description.onLinkClick() }
      linkLabel.foreground = colorScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)?.foregroundColor
      linkLabel.border = JBUI.Borders.emptyLeft(5)
      panel.add(linkLabel)
    }

    return panel
  }
}