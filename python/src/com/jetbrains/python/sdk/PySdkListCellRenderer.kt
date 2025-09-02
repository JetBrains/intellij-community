// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.JList

class PySdkListCellRenderer @JvmOverloads constructor(
  @Nls private val nullSdkName: String = noInterpreterMarker,
  private val nullSdkValue: Sdk? = null,
) : ColoredListCellRenderer<Any>() {

  override fun getListCellRendererComponent(list: JList<out Any>?, value: Any?, index: Int, selected: Boolean,
                                            hasFocus: Boolean): Component =
    when (value) {
      SEPARATOR -> TitledSeparator(null).apply {
        border = JBUI.Borders.empty()
      }
      else -> super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }

  override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
    customizeWithSdkValue(value, nullSdkName, nullSdkValue)
  }

  companion object {
    const val SEPARATOR: String = "separator"
  }
}
