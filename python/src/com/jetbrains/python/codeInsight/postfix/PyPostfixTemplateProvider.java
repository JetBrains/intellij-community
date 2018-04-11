// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PyPostfixTemplateProvider implements PostfixTemplateProvider {
  @NotNull
  @Override
  public String getId() {
    return "builtin.python";
  }

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return ContainerUtil.newHashSet(new PyNotPostfixTemplate(),
                                    new PyParenthesizedExpressionPostfixTemplate(),
                                    new PyReturnPostfixTemplate(),
                                    new PyIfPostfixTemplate(),
                                    new PyWhilePostfixTemplate(),
                                    new PyIsNonePostfixTemplate(),
                                    new PyIsNotNonePostfixTemplate(),
                                    new PyPrintPostfixTemplate(),
                                    new PyMainPostfixTemplate());
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
