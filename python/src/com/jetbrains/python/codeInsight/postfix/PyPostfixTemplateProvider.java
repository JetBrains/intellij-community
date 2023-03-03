// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PyPostfixTemplateProvider implements PostfixTemplateProvider {

  private final @NotNull Set<PostfixTemplate> myTemplates = ContainerUtil.newHashSet(
    new PyNotPostfixTemplate(this),
    new PyParenthesizedExpressionPostfixTemplate(this),
    new PyReturnPostfixTemplate(this),
    new PyIfPostfixTemplate(this),
    new PyWhilePostfixTemplate(this),
    new PyForPostfixTemplate("for", this),
    new PyForPostfixTemplate("iter", this),
    new PyIsNonePostfixTemplate(this),
    new PyIsNotNonePostfixTemplate(this),
    new PyPrintPostfixTemplate(this),
    new PyMainPostfixTemplate(this),
    new PyLenPostfixTemplate(this)
  );

  @NotNull
  @Override
  public String getId() {
    return "builtin.python";
  }

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return myTemplates;
  }

  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return currentChar == '.'|| currentChar == '!';
  }

  @Override
  public void preExpand(@NotNull PsiFile file, @NotNull Editor editor) {

  }

  @Override
  public void afterExpand(@NotNull PsiFile file, @NotNull Editor editor) {

  }

  @NotNull
  @Override
  public PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset) {
    return copyFile;
  }
}
