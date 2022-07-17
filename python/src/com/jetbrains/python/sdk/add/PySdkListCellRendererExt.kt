// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.jetbrains.python.sdk.PySdkListCellRenderer
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Adapt [PySdkListCellRenderer] for the list with [PySdkComboBoxItem] items
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