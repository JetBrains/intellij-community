// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import javax.swing.event.TableModelListener
import javax.swing.table.AbstractTableModel

internal class MarkdownScriptsTableModel(
  val state: MutableMap<String, Boolean> = HashMap(),
  val extensions: List<MarkdownConfigurableExtension> = emptyList()
) : AbstractTableModel() {
  override fun addTableModelListener(l: TableModelListener?) {
  }

  override fun getRowCount(): Int = extensions.size

  override fun getColumnName(columnIndex: Int): String {
    return columnNames[columnIndex]
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
    return columnIndex == ENABLED_COLUMN_INDEX
  }

  override fun getColumnClass(columnIndex: Int): Class<*> {
    return getValueAt(0, columnIndex).javaClass
  }

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
    if (columnIndex != ENABLED_COLUMN_INDEX) {
      return
    }
    val extension = extensions[rowIndex]
    if (extension is MarkdownExtensionWithExternalFiles && !extension.isAvailable && extension.downloadLink != null) {
      MarkdownSettingsUtil.downloadExtension(extension)
      // Explicitly set state to prevent checkbox mark blinking
      state[extension.id] = extension.isAvailable
    } else {
      state[extension.id] = !state[extension.id]!!
    }
  }

  override fun getColumnCount(): Int = 2

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    val extension = extensions[rowIndex]
    return when (columnIndex) {
      ENABLED_COLUMN_INDEX -> when (extension) {
        is MarkdownExtensionWithExternalFiles -> state[extension.id]!! && extension.isAvailable
        else -> state[extension.id]!!
      }
      NAME_COLUMN_INDEX -> extension.displayName
      else -> error("Unsupported column index")
    }
  }

  override fun removeTableModelListener(l: TableModelListener?) {
  }

  fun getExtensionAt(row: Int): MarkdownConfigurableExtension {
    return extensions[row]
  }

  companion object {
    private val columnNames = arrayOf(
      MarkdownBundle.message("markdown.settings.download.extension.is.enabled.column"),
      MarkdownBundle.message("markdown.settings.download.extension.extension.name.column")
    )

    const val ENABLED_COLUMN_INDEX = 0
    const val NAME_COLUMN_INDEX = 1
  }
}
