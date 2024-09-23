// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.jetbrains.python.sdk.PySdkListCellRenderer
import com.jetbrains.python.sdk.add.ExistingPySdkComboBoxItem
import com.jetbrains.python.sdk.add.NewPySdkComboBoxItem
import com.jetbrains.python.sdk.add.PySdkComboBoxItem
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Adapt [PySdkListCellRenderer] for the list with [com.jetbrains.python.sdk.add.PySdkComboBoxItem] items
 * rather than with [com.intellij.openapi.projectRoots.Sdk] items.
 */
class PySdkListCellRendererExt : ListCellRenderer<PySdkComboBoxItem> {
  private val component = PySdkListCellRenderer()

  override fun getListCellRendererComponent(list: JList<out PySdkComboBoxItem>?,
                                            value: PySdkComboBoxItem?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val convertedValue = value?.run {
      when (this) {
        is NewPySdkComboBoxItem -> title
        is ExistingPySdkComboBoxItem -> sdk
      }
    }
    return component.getListCellRendererComponent(list, convertedValue, index, isSelected, cellHasFocus)
  }
}