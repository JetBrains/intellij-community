// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyReturnPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
  public PyReturnPostfixTemplate(PostfixTemplateProvider provider) {
    super("return", "return expr", PyPostfixUtils.selectorTopmost(), provider);
  }


  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    return "return $expr$$END$";
  }
}
