// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.layout.*
import com.intellij.util.ui.FontInfo
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.jetbrains.annotations.NotNull
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class MarkdownFontSettingsAction() : ComboBoxAction() {

  private val applicationSettings get() = MarkdownApplicationSettings.getInstance()
  private val markdownCssSettings get() = MarkdownApplicationSettings.getInstance().markdownCssSettings
  private val propertyGraph = PropertyGraph()
  private val fontSizeProperty = propertyGraph.graphProperty { markdownCssSettings.fontSize }
  private val fontFamilyProperty = propertyGraph.graphProperty { markdownCssSettings.fontFamily }

  init{
    fontSizeProperty.afterChange { newFontSize -> updateFontSettings(newFontSize, markdownCssSettings.fontFamily) }
    fontFamilyProperty.afterChange { newFontFamily -> updateFontSettings(markdownCssSettings.fontSize, newFontFamily) }
  }

  override fun createPopupActionGroup(button: JComponent?) = DefaultActionGroup()

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return panel(LCFlags.noGrid) {
      row(MarkdownBundle.message("markdown.preview.settings.font.family"), true) {
        cell {
          val fontNames = FontInfo.getAll(false).map { it.toString() }.toSortedSet().toTypedArray()
          val model = DefaultComboBoxModel(fontNames)
          ComboboxSpeedSearch(
            comboBox(model, fontFamilyProperty).component
          )
        }
      }
      row(MarkdownBundle.message("markdown.preview.settings.font.size"), true) {
        cell {
          val fontSizes = UIUtil.getStandardFontSizes().map { Integer.valueOf(it) }.toSortedSet().toTypedArray()
          val model = DefaultComboBoxModel(fontSizes)
          comboBox(model, fontSizeProperty).applyToComponent { isEditable = true }
        }
      }
    }
  }

  private fun updateFontSettings(currentFontSize: @NotNull Int,
                                 newFontFamily: @NotNull String) {
    applicationSettings.markdownCssSettings = createMarkdownCssSettingsWithFont(currentFontSize, newFontFamily)
    ApplicationManager.getApplication().messageBus.syncPublisher(MarkdownApplicationSettings.FontChangedListener.TOPIC).fontChanged()
  }

  private fun createMarkdownCssSettingsWithFont(newFontSize: @NotNull Int, newFontFamily: @NotNull String) = MarkdownCssSettings(
    markdownCssSettings.isCustomStylesheetEnabled,
    markdownCssSettings.customStylesheetPath,
    markdownCssSettings.isTextEnabled,
    markdownCssSettings.customStylesheetText,
    newFontSize,
    newFontFamily)

  override fun update(e: AnActionEvent) {
    val isCustomCssEnabled = markdownCssSettings.isTextEnabled && markdownCssSettings.customStylesheetText.isNotEmpty()
    if (MarkdownActionUtil.findSplitEditor(e) != null) {
      e.presentation.isEnabledAndVisible = MarkdownActionUtil.findSplitEditor(e)!!.currentEditorLayout.showSecond && !isCustomCssEnabled
    }
  }
}
