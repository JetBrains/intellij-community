// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.jetbrains.python.PyBundle

class PySmartKeysOptions : BeanConfigurable<PyCodeInsightSettings>(PyCodeInsightSettings.getInstance()), SearchableConfigurable {

  init {
    //CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;   //TODO: remove combobox from settings
    val commonSettings = CodeInsightSettings.getInstance()
    checkBox(PyBundle.message("form.edit.smart.indent.pasted.lines"), commonSettings::INDENT_TO_CARET_ON_PASTE)
    checkBox(PyBundle.message("smartKeys.wrap.in.parentheses.instead.of.backslash"), instance::PARENTHESISE_ON_ENTER)
    checkBox(PyBundle.message("smartKeys.insert.self.in.method"), instance::INSERT_SELF_FOR_METHODS)
    checkBox(PyBundle.message("smartKeys.insert.type.placeholder.in.docstring.stub"), instance::INSERT_TYPE_DOCSTUB)
  }

  override fun getDisplayName(): String {
    return PyBundle.message("configurable.PySmartKeysOptions.display.name")
  }

  override fun getId(): String {
    return "editor.preferences.pyOptions"
  }

  override fun getHelpTopic() = "reference.settings.editor.smart.keys.python"
}
