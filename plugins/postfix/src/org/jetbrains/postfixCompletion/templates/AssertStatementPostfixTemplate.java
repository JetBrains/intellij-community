package org.jetbrains.postfixCompletion.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.util.CommonUtils;

public class AssertStatementPostfixTemplate extends BooleanPostfixTemplate {
  public AssertStatementPostfixTemplate() {
    super("assert", "Creates assertion from boolean expression", "assert expr;");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    CommonUtils.createSimpleStatement(context, editor, "assert");
  }
}