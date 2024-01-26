// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.NotPostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.project.DumbAware;

public class PyNotPostfixTemplate extends NotPostfixTemplate implements DumbAware {
  public PyNotPostfixTemplate(PostfixTemplateProvider provider) {
    super(null, "not", "not expr", PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider);
  }
}
