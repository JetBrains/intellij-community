// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * @param label the label of the option, together with its context help if the option has help
 */
internal class OptionHolder private constructor(private val option: PyTestCustomOption,
                                                private val label: JComponent,
                                                private val component: JComponent,
                                                private val accessor: Accessor) {
  constructor(option: PyTestCustomOption,
              label: JComponent,
              component: JTextComponent) : this(option, label, component, TextAccessor(component))

  constructor(option: PyTestCustomOption,
              label: JComponent,
              component: JCheckBox) : this(option, label, component, BoolAccessor(component))


  fun setType(type: PyRunTargetVariant) {
    val visible = option.mySupportedTypes.contains(type)
    label.isVisible = visible
    component.isVisible = visible
  }

  var value: Any?
    get() = accessor.value
    set(value) {
      accessor.value = value
    }


  private interface Accessor {
    var value: Any?
  }

  private class TextAccessor(private val component: JTextComponent) : Accessor {
    override var value: Any?
      get() = component.text
      set(value) {
        component.text = value as? String ?: ""
      }
  }

  private class BoolAccessor(private val component: JCheckBox) : Accessor {
    override var value: Any?
      get() = component.isSelected
      set(value) {
        component.isSelected = value as? Boolean ?: false
      }
  }
}
