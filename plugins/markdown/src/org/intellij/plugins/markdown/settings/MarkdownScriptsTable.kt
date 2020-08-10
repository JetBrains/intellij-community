// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.extensions.javafx.MarkdownJavaFXPreviewExtension
import org.intellij.plugins.markdown.extensions.jcef.MarkdownJCEFPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.intellij.plugins.markdown.ui.preview.jcef.JCEFHtmlPanelProvider
import java.awt.Component
import javax.swing.JComponent
import javax.swing.table.TableCellRenderer

internal class MarkdownScriptsTable : JBTable() {
  init {
    setState(emptyMap(), null)
  }

  override fun getCellSelectionEnabled(): Boolean = false

  override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
    var prepared = super.prepareRenderer(renderer, row, column)
    if (prepared is JComponent) {
      val tableModel = model as MarkdownScriptsTableModel
      val extension = tableModel.getExtensionAt(row)
      if (column == MarkdownScriptsTableModel.NAME_COLUMN_INDEX) {
        prepared = panel {
          row {
            cell {
              label(tableModel.getValueAt(row, column) as String)
              if (extension is MarkdownExtensionWithExternalFiles && !extension.isAvailable) {
                label(MarkdownBundle.message("markdown.settings.download.extension.not.installed.label")).also {
                  it.enabled(false)
                }
              }
            }
          }
        } as JComponent
      }
      prepared.toolTipText = extension.description
    }
    return prepared
  }

  fun getState(): MutableMap<String, Boolean> = (model as MarkdownScriptsTableModel).state

  fun setState(state: Map<String, Boolean>, providerInfo: MarkdownHtmlPanelProvider.ProviderInfo?) {
    val extensions = with(MarkdownExtension.all.filterIsInstance<MarkdownConfigurableExtension>()) {
      if (providerInfo == null) {
        this
      }
      else when (providerInfo.className) {
        JCEFHtmlPanelProvider::class.java.name -> filter {
          if (it is MarkdownBrowserPreviewExtension) {
            it !is MarkdownJavaFXPreviewExtension
          }
          else true
        }
        else -> this
      }
    }
    val mergedState = extensions.map { it.id to false }.toMap().toMutableMap()
    for ((key, value) in state) {
      if (key in state.keys) {
        mergedState[key] = value
      }
    }
    model = MarkdownScriptsTableModel(mergedState, extensions)
    setColumnWidth(MarkdownScriptsTableModel.ENABLED_COLUMN_INDEX, 60)
    repaint()
  }

  private fun setColumnWidth(index: Int, width: Int) {
    columnModel.getColumn(index)?.let {
      it.preferredWidth = width
      it.maxWidth = width
    }
  }
}
