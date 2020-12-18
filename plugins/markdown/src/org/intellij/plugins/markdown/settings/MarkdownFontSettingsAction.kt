// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.jetbrains.annotations.NotNull
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class MarkdownFontSettingsAction : ComboBoxAction() {

  private val applicationSettings get() = MarkdownApplicationSettings.getInstance()
  private val markdownCssSettings get() = MarkdownApplicationSettings.getInstance().markdownCssSettings
  private val fontSizeProperty = PropertyGraph().graphProperty { markdownCssSettings.fontSize }

  override fun createPopupActionGroup(button: JComponent?) = DefaultActionGroup()

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    fontSizeProperty.afterChange(
      { newFontSize ->
        applicationSettings.markdownCssSettings = createMarkdownCssSettingsWithFont(newFontSize)
        ApplicationManager.getApplication().messageBus.syncPublisher(MarkdownApplicationSettings.FontChangedListener.TOPIC).fontChanged()
      },
      Disposer.newDisposable()
    )

    return panel(LCFlags.noGrid) {
      row(MarkdownBundle.message("markdown.preview.settings.font.size"), true) {
        cell {
          val fontSizes = UIUtil.getStandardFontSizes().map { Integer.valueOf(it) }.toSortedSet().toTypedArray()
          val model = DefaultComboBoxModel(fontSizes)
          comboBox(model, fontSizeProperty).applyToComponent { isEditable = true }
        }
      }
    }
  }

  private fun createMarkdownCssSettingsWithFont(newFontSize: @NotNull Int) = MarkdownCssSettings(
    markdownCssSettings.isCustomStylesheetEnabled,
    markdownCssSettings.customStylesheetPath,
    markdownCssSettings.isTextEnabled,
    markdownCssSettings.customStylesheetText,
    newFontSize)

  override fun update(e: AnActionEvent) {
    if (MarkdownActionUtil.findSplitEditor(e) != null) {
      e.presentation.isEnabledAndVisible = MarkdownActionUtil.findSplitEditor(e)!!.currentEditorLayout.showSecond
    }
  }
}
