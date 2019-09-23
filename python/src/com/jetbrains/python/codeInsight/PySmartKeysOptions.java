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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PySmartKeysOptions extends BeanConfigurable<CodeInsightSettings> implements SearchableConfigurable {
  public PySmartKeysOptions() {
    super(CodeInsightSettings.getInstance());
    //CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;   //TODO: remove combobox from settings
    CodeInsightSettings commonSettings = CodeInsightSettings.getInstance();
    checkBox("Smart indent pasted lines", ()->commonSettings.INDENT_TO_CARET_ON_PASTE, v->commonSettings.INDENT_TO_CARET_ON_PASTE=v);

    PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    checkBox(PyBundle.message("smartKeys.insert.backslash.in.statement.on.enter"), ()->settings.INSERT_BACKSLASH_ON_WRAP, v-> settings.INSERT_BACKSLASH_ON_WRAP=v);
    checkBox(PyBundle.message("smartKeys.insert.self.in.method"), ()->settings.INSERT_SELF_FOR_METHODS, v-> settings.INSERT_SELF_FOR_METHODS=v);
    checkBox(PyBundle.message("smartKeys.insert.type.placeholder.in.docstring.stub"), ()->settings.INSERT_TYPE_DOCSTUB, v-> settings.INSERT_TYPE_DOCSTUB=v);
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return "Python";
  }

  @NotNull
  @Override
  public String getId() {
    return "editor.preferences.pyOptions";
  }
}
