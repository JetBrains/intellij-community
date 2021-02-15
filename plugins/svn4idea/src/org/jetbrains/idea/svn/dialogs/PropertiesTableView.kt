// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil.ELLIPSIS
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.properties.PropertyData
import org.jetbrains.idea.svn.properties.PropertyValue
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

private class PropertiesTableView : TableView<PropertyData>(PropertiesTableModel()) {
  fun setProperties(properties: List<PropertyData>) {
    tableViewModel.items = properties
  }
}

private class PropertiesTableModel : ListTableModel<PropertyData>(PropertyNameColumn, PropertyValueColumn)

private object PropertyNameColumn : ColumnInfo<PropertyData, String>(message("column.name.property.name")) {
  override fun valueOf(item: PropertyData): String = item.name
}

private object PropertyValueColumn : ColumnInfo<PropertyData, PropertyValue>(message("column.name.property.value")) {
  override fun valueOf(item: PropertyData): PropertyValue = item.value

  override fun getRenderer(item: PropertyData): TableCellRenderer = PropertyValueRenderer
}

private object PropertyValueRenderer : DefaultTableCellRenderer() {
  override fun setValue(value: Any?) {
    text = getText(value as PropertyValue)
  }

  private fun getText(property: PropertyValue): @NlsSafe String {
    val value = property.toString()
    val lines = value.lines()

    return if (lines.size > 1) lines.first() + " [$ELLIPSIS]" else value
  }
}
