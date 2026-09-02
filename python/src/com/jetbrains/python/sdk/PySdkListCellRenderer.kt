// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.JList

/**
 * Draws a list of interpreters.
 *
 * The list holds [PyInterpreterItem]s, never SDKs, because a row's label and its `[invalid]` marker are decided by
 * running the interpreter. Build the items off the EDT with
 * [com.intellij.python.sdk.backend.pyInterpreterItems] and set them as the model.
 *
 * @param nullLabel what the `null` row says when [nullItem] is `null`.
 * @param nullItem the interpreter the `null` row stands for, usually the project default.
 */
@ApiStatus.Internal
class PySdkListCellRenderer @JvmOverloads constructor(
  @Nls private val nullLabel: String = noInterpreterMarker,
  private val nullItem: PyInterpreterItem? = null,
) : ColoredListCellRenderer<Any?>() {

  override fun getListCellRendererComponent(list: JList<out Any?>?, value: Any?, index: Int, selected: Boolean,
                                            hasFocus: Boolean): Component {
    if (list == null) return this
    return when (value) {
      SEPARATOR -> TitledSeparator(null).apply {
        border = JBUI.Borders.empty()
      }
      else -> super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }
  }

  override fun customizeCellRenderer(list: JList<out Any?>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
    customizeWithSdkValue(value, nullLabel, nullItem)
  }

  companion object {
    const val SEPARATOR: String = "separator"
  }
}
