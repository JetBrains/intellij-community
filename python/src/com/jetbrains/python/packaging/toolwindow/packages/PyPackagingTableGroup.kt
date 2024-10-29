// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.packages.table.PyPackagesTable
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import org.jetbrains.annotations.Nls
import javax.swing.JPanel

internal class PyPackagingTableGroup(val repository: PyPackageRepository, val table: PyPackagesTable) {
  @NlsSafe
  val name: String = repository.name!!

  private var expanded = false
  private val label = JBLabel(name).apply { icon = AllIcons.General.ArrowDown }
  private val header: JPanel = PyPackagesUiComponents.headerPanel(label, table)
  private var itemsCount: Int? = null


  internal var items: List<DisplayablePackage>
    get() = table.items
    set(value) {
      table.items = value
    }

  fun collapse() {
    expanded = false
    table.isVisible = false
    label.icon = if (table.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
  }

  fun expand() {
    expanded = true
    table.isVisible = true
    label.icon = if (table.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
  }

  fun updateHeaderText(newItemCount: Int?) {
    itemsCount = newItemCount
    label.text = if (itemsCount == null) name else PyBundle.message("python.toolwindow.packages.custom.repo.searched", name, itemsCount)
  }

  fun setSdkToHeader(@Nls sdkName: String?) {
    itemsCount = null
    @Suppress("HardCodedStringLiteral")
    label.text = "<html>$name <b>(${sdkName})</b></html>"
  }


  fun addTo(panel: JPanel) {
    panel.add(header)
    panel.add(table)
  }

  fun removeFrom(panel: JPanel) {
    panel.remove(header)
    panel.remove(table)
  }

  fun repaint() {
    table.invalidate()
    table.repaint()
  }
}