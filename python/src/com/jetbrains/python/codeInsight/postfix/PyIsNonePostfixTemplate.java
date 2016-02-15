/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase;
import com.intellij.lang.surroundWith.Surrounder;
import com.jetbrains.python.refactoring.surround.surrounders.expressions.PyIsNoneSurrounder;
import org.jetbrains.annotations.NotNull;

public class PyIsNonePostfixTemplate extends SurroundPostfixTemplateBase {

  public PyIsNonePostfixTemplate() {
    super("ifn", "if expr is None", PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorTopmost());
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new PyIsNoneSurrounder();
  }
}
