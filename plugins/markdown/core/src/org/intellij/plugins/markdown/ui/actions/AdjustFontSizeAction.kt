// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.settings.MarkdownSettingsConfigurable.Companion.fontSizeOptions
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor.Companion.PREVIEW_POPUP_POINT
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

class AdjustFontSizeAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    checkNotNull(editor) { "Preview editor should be obtainable from the action event" }
    val preview = editor.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() ?: return
    if (preview !is MarkdownJCEFHtmlPanel) {
      return
    }
    val hintComponent = HintComponent(preview)
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(hintComponent, hintComponent).setRequestFocus(true).createPopup()
    val point = event.dataContext.getData(PREVIEW_POPUP_POINT)
    if (point != null) {
      popup.show(point)
    } else {
      popup.showInFocusCenter()
    }
  }

  private class HintComponent(preview: MarkdownJCEFHtmlPanel) : JPanel(FlowLayout(FlowLayout.LEFT, 10, 5)) {
    private val previewSettings
      get() = service<MarkdownPreviewSettings>()

    init {
      val fontSizeLabel = JBLabel(previewSettings.state.fontSize.toString(), SwingConstants.CENTER).apply {
        preferredSize = JBUI.size(22, 22)
      }
      val decreaseButton = JButton(AllIcons.General.Remove).apply {
        border = BorderFactory.createEmptyBorder()
        isContentAreaFilled = false
        preferredSize = JBUI.size(22, 22)

        setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.Remove))
        isEnabled = previewSettings.state.fontSize != fontSizeOptions.first()
      }
      val increaseButton = JButton(AllIcons.General.Add).apply {
        border = BorderFactory.createEmptyBorder()
        isContentAreaFilled = false
        preferredSize = JBUI.size(22, 22)

        setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.Add))
        isEnabled = previewSettings.state.fontSize != fontSizeOptions.last()
      }

      fun updateFontSize(transform: (Int) -> Int?) {
        val currentSize = preview.getCurrentFontSize()
        val newSize = transform(currentSize) ?: currentSize
        previewSettings.state.fontSize = newSize
        preview.changeFontSize(newSize)

        fontSizeLabel.text = newSize.toString()
        decreaseButton.isEnabled = newSize != fontSizeOptions.first()
        increaseButton.isEnabled = newSize != fontSizeOptions.last()
      }

      add(JBLabel(MarkdownBundle.message("action.Markdown.Preview.FontSize.label.text")))
      add(decreaseButton.apply { addActionListener { updateFontSize { fontSizeOptions.findLast { step -> step < it } } } })
      add(fontSizeLabel.apply { text = preview.getCurrentFontSize().toString() })
      add(increaseButton.apply { addActionListener { updateFontSize { fontSizeOptions.find { step -> step > it } } } })
    }

    private fun MarkdownJCEFHtmlPanel.getCurrentFontSize() = getTemporaryFontSize() ?: previewSettings.state.fontSize
  }

  override fun update(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    event.presentation.isEnabledAndVisible = editor != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

