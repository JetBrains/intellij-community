// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.settings

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NonEmptyInputValidator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.Function
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.table.TableModelEditor
import com.jetbrains.python.PyBundle
import javax.swing.JComponent
import javax.swing.JPanel

class PyDebuggerSteppingConfigurableUi : ConfigurableUi<PyDebuggerSettings> {
  private val myPySteppingFilterEditor: TableModelEditor<PySteppingFilter>
  private val myPanel: JPanel
  private lateinit var myLibrariesFilterCheckBox: JBCheckBox
  private lateinit var myStepFilterEnabledCheckBox: JBCheckBox
  private lateinit var myAlwaysDoSmartStepIntoCheckBox: JBCheckBox

  init {
    myPySteppingFilterEditor = TableModelEditor(
      arrayOf<ColumnInfo<*, *>>(EnabledColumn(), FilterColumn()),
      DialogEditor(),
      PyBundle.message("debugger.stepping.no.script.filters")
    )
    myPanel = panel {
      row {
        myAlwaysDoSmartStepIntoCheckBox = checkBox(PyBundle.message("form.debugger.stepping.always.do.smart.step.into")).component
      }
      row {
        myLibrariesFilterCheckBox =
          checkBox(PyBundle.message("form.debugger.stepping.checkbox.text.do.not.step.into.library.scripts")).component
      }
      row {
        myStepFilterEnabledCheckBox = checkBox(PyBundle.message("form.debugger.stepping.do.not.step.into.scripts")).component
      }
      row {
        cell(myPySteppingFilterEditor.createComponent())
          .align(Align.FILL)
          .enabledIf(myStepFilterEnabledCheckBox.selected)
      }.resizableRow()
    }
  }

  override fun reset(settings: PyDebuggerSettings) {
    myLibrariesFilterCheckBox.isSelected = settings.isLibrariesFilterEnabled
    myStepFilterEnabledCheckBox.isSelected = settings.isSteppingFiltersEnabled
    myAlwaysDoSmartStepIntoCheckBox.isSelected = settings.isAlwaysDoSmartStepInto
    myPySteppingFilterEditor.reset(settings.steppingFilters)
    myPySteppingFilterEditor.enabled(myStepFilterEnabledCheckBox.isSelected)
  }

  override fun isModified(settings: PyDebuggerSettings): Boolean =
    myLibrariesFilterCheckBox.isSelected != settings.isLibrariesFilterEnabled ||
    myStepFilterEnabledCheckBox.isSelected != settings.isSteppingFiltersEnabled ||
    myAlwaysDoSmartStepIntoCheckBox.isSelected != settings.isAlwaysDoSmartStepInto ||
    myPySteppingFilterEditor.isModified

  override fun apply(settings: PyDebuggerSettings) {
    settings.isLibrariesFilterEnabled = myLibrariesFilterCheckBox.isSelected
    settings.isSteppingFiltersEnabled = myStepFilterEnabledCheckBox.isSelected
    settings.setAlwaysDoSmartStepIntoEnabled(myAlwaysDoSmartStepIntoCheckBox.isSelected)
    if (myPySteppingFilterEditor.isModified) {
      settings.steppingFilters = myPySteppingFilterEditor.apply()
    }
  }

  override fun getComponent(): JComponent = myPanel

  private class EnabledColumn : TableModelEditor.EditableColumnInfo<PySteppingFilter, Boolean>() {
    override fun valueOf(filter: PySteppingFilter): Boolean = filter.isEnabled
    override fun getColumnClass(): Class<*> = Boolean::class.javaObjectType
    override fun setValue(filter: PySteppingFilter, value: Boolean) {
      filter.isEnabled = value
    }
  }

  private class FilterColumn : TableModelEditor.EditableColumnInfo<PySteppingFilter, String>() {
    override fun valueOf(filter: PySteppingFilter): String = filter.filter
    override fun setValue(filter: PySteppingFilter, value: String) {
      filter.filter = value
    }
  }

  private inner class DialogEditor : TableModelEditor.DialogItemEditor<PySteppingFilter> {
    override fun clone(item: PySteppingFilter, forInPlaceEditing: Boolean): PySteppingFilter =
      PySteppingFilter(item.isEnabled, item.filter)

    override fun getItemClass(): Class<PySteppingFilter> = PySteppingFilter::class.java

    override fun edit(
      item: PySteppingFilter,
      mutator: Function<in PySteppingFilter, out PySteppingFilter>,
      isAdd: Boolean,
    ) {
      val pattern = Messages.showInputDialog(
        myPanel,
        PyBundle.message("debugger.stepping.filter.specify.pattern"),
        PyBundle.message("debugger.stepping.filter"),
        null,
        item.filter,
        NonEmptyInputValidator()
      )
      if (pattern != null) {
        mutator.`fun`(item).filter = pattern
        myPySteppingFilterEditor.model.fireTableDataChanged()
      }
    }

    override fun applyEdited(oldItem: PySteppingFilter, newItem: PySteppingFilter) {
      oldItem.filter = newItem.filter
    }

    override fun isUseDialogToAdd(): Boolean = true
  }
}