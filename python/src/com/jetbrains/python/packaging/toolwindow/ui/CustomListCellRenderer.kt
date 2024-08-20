package com.jetbrains.python.packaging.toolwindow.ui

import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class CustomListCellRenderer<T>(private val getText: (T) -> String?) : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(
    list: JList<*>?,
    value: Any?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    @Suppress("UNCHECKED_CAST")
    (value as? T)?.let { typedValue ->
      getText(typedValue)?.let {
        @Suppress("HardCodedStringLiteral") // Sometimes we are rendering user-defined values add they cannot be localized.
        text = it
      }
    }
    return component
  }
}