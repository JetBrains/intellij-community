// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.options.ConfigurableBuilder;
import com.intellij.openapi.options.SearchableConfigurable;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;


public class PySmartKeysOptions extends ConfigurableBuilder implements SearchableConfigurable {
  public PySmartKeysOptions() {
    //CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;   //TODO: remove combobox from settings
    CodeInsightSettings commonSettings = CodeInsightSettings.getInstance();
    checkBox(PyBundle.message("form.edit.smart.indent.pasted.lines"), () -> commonSettings.INDENT_TO_CARET_ON_PASTE,
             v -> commonSettings.INDENT_TO_CARET_ON_PASTE = v);
    PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    checkBox(PyBundle.message("smartKeys.wrap.in.parentheses.instead.of.backslash"), () -> settings.PARENTHESISE_ON_ENTER,
             v -> settings.PARENTHESISE_ON_ENTER = v);
    checkBox(PyBundle.message("smartKeys.insert.self.in.method"), () -> settings.INSERT_SELF_FOR_METHODS,
             v -> settings.INSERT_SELF_FOR_METHODS = v);
    checkBox(PyBundle.message("smartKeys.insert.type.placeholder.in.docstring.stub"), () -> settings.INSERT_TYPE_DOCSTUB,
             v -> settings.INSERT_TYPE_DOCSTUB = v);
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return PyBundle.message("configurable.PySmartKeysOptions.display.name");
  }

  @NotNull
  @Override
  public String getId() {
    return "editor.preferences.pyOptions";
  }
}
