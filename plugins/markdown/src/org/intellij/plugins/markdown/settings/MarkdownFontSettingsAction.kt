// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class MarkdownFontSettingsAction : ComboBoxAction() {
  companion object {
    lateinit var combobox: ComboBox<Int>
      private set

    fun isComboboxInitialized() = this::combobox.isInitialized

    fun reinit() = MarkdownLAFListener.reinit()
  }

  override fun createPopupActionGroup(button: JComponent?) = DefaultActionGroup()

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val mdProperty = PropertyGraph().graphProperty { MarkdownApplicationSettings.getInstance().markdownCssSettings.fontSize }
    mdProperty.afterChange(
      { selected ->
        val cssSettings = MarkdownApplicationSettings.getInstance().markdownCssSettings
        MarkdownFontUtil.updateCustomCss(
          selected,
          cssSettings.customStylesheetText)
      },
      Disposer.newDisposable()
    )

    return panel(LCFlags.noGrid) {
      row(MarkdownBundle.message("markdown.preview.settings.font.size"), true) {
        cell {
          val fontSizes = UIUtil.getStandardFontSizes().map { Integer.valueOf(it) }.toSortedSet().toTypedArray()
          val model = DefaultComboBoxModel(fontSizes)
          combobox = comboBox(model, mdProperty).applyToComponent {
            isEditable = true
            selectedItem = MarkdownApplicationSettings.getInstance().markdownCssSettings.fontSize
          }.component
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    if (MarkdownActionUtil.findSplitEditor(e) != null) {
      e.presentation.isEnabledAndVisible = MarkdownActionUtil.findSplitEditor(e)!!.currentEditorLayout.showSecond
    }
  }
}
