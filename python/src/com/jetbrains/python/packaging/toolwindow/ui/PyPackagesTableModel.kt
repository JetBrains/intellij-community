// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.util.ui.ListTableModel
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage

internal class PyPackagesTableModel<T : DisplayablePackage> : ListTableModel<T>() {
  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
  override fun getColumnCount(): Int = 2
  override fun getColumnName(column: Int): String = column.toString()
  override fun getColumnClass(columnIndex: Int): Class<*> = DisplayablePackage::class.java
  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = items[rowIndex]
}