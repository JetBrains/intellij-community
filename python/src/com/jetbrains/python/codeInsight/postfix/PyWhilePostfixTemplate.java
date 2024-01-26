// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.project.DumbAware;
import com.jetbrains.python.refactoring.surround.surrounders.expressions.PyWhileExpressionSurrounder;
import org.jetbrains.annotations.NotNull;

public class PyWhilePostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

  public PyWhilePostfixTemplate(PostfixTemplateProvider provider) {
    super("while", "while expr", PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorTopmost(), provider);
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new PyWhileExpressionSurrounder();
  }
}
