// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.table

import com.intellij.ui.hover.TableHoverListener
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import java.awt.Cursor
import javax.swing.JTable

internal class PyPackagesHoverListener(private val pyPackageTable: PyPackagesTable) : TableHoverListener() {

  override fun onHover(table: JTable, row: Int, column: Int) {
    pyPackageTable.hoveredColumn = column
    if (column == 1) {
      table.repaint(table.getCellRect(row, column, true))
      val currentPackage = pyPackageTable.items[row]
      if (currentPackage is InstallablePackage
          || (currentPackage is InstalledPackage && currentPackage.canBeUpdated)) {
        table.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        return
      }
    }
    table.cursor = Cursor.getDefaultCursor()
  }
}