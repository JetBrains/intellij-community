/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.JList

/**
 * @author vlan
 */
open class PySdkListCellRenderer @JvmOverloads constructor(@Nls private val nullSdkName: String = noInterpreterMarker,
                                                           private val nullSdkValue: Sdk? = null) : ColoredListCellRenderer<Any>() {

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
