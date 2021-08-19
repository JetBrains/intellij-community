// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.layout.*
import com.intellij.util.ui.FontInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.jetbrains.annotations.NotNull
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class MarkdownFontSettingsAction : ComboBoxAction() {
  private var lastProject: Project? = null

  override fun createPopupActionGroup(button: JComponent?) = DefaultActionGroup()

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val settings = MarkdownSettings.getInstance(currentOrDefaultProject(lastProject))
    val propertyGraph = PropertyGraph()
    val fontSizeProperty = propertyGraph.graphProperty { settings.fontSize }
    val fontFamilyProperty = propertyGraph.graphProperty { settings.fontFamily }
    fontSizeProperty.afterChange { newFontSize -> updateFontSettings(settings, newFontSize, settings.fontFamily) }
    fontFamilyProperty.afterChange { newFontFamily -> updateFontSettings(settings, settings.fontSize, newFontFamily) }
    return panel(LCFlags.noGrid) {
      row(MarkdownBundle.message("markdown.preview.settings.font.family"), true) {
        cell {
          val fontNames = FontInfo.getAll(false).map { it.toString() }.toSortedSet().toTypedArray()
          val model = DefaultComboBoxModel(fontNames)
          comboBox(model, fontFamilyProperty).applyToComponent {
            putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
            ComboboxSpeedSearch(this)
            font = JBUI.Fonts.smallFont()
          }
        }
      }
      row(MarkdownBundle.message("markdown.preview.settings.font.size"), true) {
        cell {
          val fontSizes = UIUtil.getStandardFontSizes().map { Integer.valueOf(it) }.toSortedSet()
          fontSizes.add(fontSizeProperty.get())
          val model = DefaultComboBoxModel(fontSizes.toTypedArray())
          comboBox(model, property = fontSizeProperty).applyToComponent {
            isEditable = true
            putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
            font = JBUI.Fonts.smallFont()
          }
        }
      }
    }
  }

  private fun updateFontSettings(settings: MarkdownSettings, currentFontSize: @NotNull Int, newFontFamily: String?) {
    settings.update {
      it.fontSize = currentFontSize
      it.fontFamily = newFontFamily
    }
  }

  override fun update(event: AnActionEvent) {
    val project = event.project
    lastProject = project
    val settings = MarkdownSettings.getInstance(currentOrDefaultProject(project))
    val isCustomCssEnabled = settings.useCustomStylesheetText && settings.customStylesheetText != null
    val editor = MarkdownActionUtil.findSplitEditor(event)
    event.presentation.isEnabledAndVisible = editor?.layout in allowedLayouts && !isCustomCssEnabled
  }

  companion object {
    private val allowedLayouts = arrayOf(
      TextEditorWithPreview.Layout.SHOW_PREVIEW,
      TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
    )
  }
}
