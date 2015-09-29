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

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.jetbrains.python.PyBundle;

/**
 * @author yole
 */
public class PySpecificSmartKeysOptions extends BeanConfigurable<PyCodeInsightSettings> implements UnnamedConfigurable {
  public PySpecificSmartKeysOptions() {
    super(PyCodeInsightSettings.getInstance());
    checkBox("INSERT_BACKSLASH_ON_WRAP", PyBundle.message("smartKeys.insert.backslash.in.statement.on.enter"));
    checkBox("INSERT_SELF_FOR_METHODS", PyBundle.message("smartKeys.insert.self.in.method"));
    checkBox("INSERT_TYPE_DOCSTUB", PyBundle.message("smartKeys.insert.type.placeholder.in.docstring.stub"));
  }
}
