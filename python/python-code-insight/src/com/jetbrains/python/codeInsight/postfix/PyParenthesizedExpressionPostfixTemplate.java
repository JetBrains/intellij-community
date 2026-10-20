// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.ParenthesizedPostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.project.DumbAware;

public class PyParenthesizedExpressionPostfixTemplate extends ParenthesizedPostfixTemplate implements DumbAware {
  public PyParenthesizedExpressionPostfixTemplate(PostfixTemplateProvider provider) {
    super(PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider);
  }
}
