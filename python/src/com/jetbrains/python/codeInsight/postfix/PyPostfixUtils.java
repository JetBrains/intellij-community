// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PyPostfixUtils {

  private PyPostfixUtils() {
  }

  public static final PostfixTemplatePsiInfo PY_PSI_INFO = new PostfixTemplatePsiInfo() {
    @NotNull
    @Override
    public PsiElement createExpression(@NotNull PsiElement context, @NotNull String prefix, @NotNull String suffix) {
      String text = prefix + context.getText() + suffix;
      return PyElementGenerator.getInstance(context.getProject()).createExpressionFromText(LanguageLevel.forElement(context), text);
    }

    @NotNull
    @Override
    public PsiElement getNegatedExpression(@NotNull PsiElement element) {
      String newText = element instanceof PyBinaryExpression ? "(" + element.getText() + ")" : element.getText();
      return PyElementGenerator.getInstance(element.getProject()).
        createExpressionFromText(LanguageLevel.forElement(element), "not " + newText);
    }
  };


  public static PostfixTemplateExpressionSelector selectorAllExpressionsWithCurrentOffset(final Condition<PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int newOffset) {
        PsiElement elementAtCaret = PsiUtilCore.getElementAtOffset(context.getContainingFile(), newOffset - 1);
        final List<PsiElement> expressions = new ArrayList<>();
        while (elementAtCaret != null) {
          if (elementAtCaret instanceof PyStatement || elementAtCaret instanceof PyFile) {
            break;
          }
          if (elementAtCaret instanceof PyExpression) {
            expressions.add(elementAtCaret);
          }
          elementAtCaret = elementAtCaret.getParent();
        }
        return expressions;
      }
    };
  }

  public static PostfixTemplateExpressionSelector selectorAllExpressionsWithCurrentOffset() {
    return selectorAllExpressionsWithCurrentOffset(Conditions.alwaysTrue());
  }

  public static PostfixTemplateExpressionSelector selectorTopmost() {
    return selectorTopmost(Conditions.alwaysTrue());
  }

  public static PostfixTemplateExpressionSelector selectorTopmost(Condition<PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        PyExpressionStatement exprStatement = PsiTreeUtil.getNonStrictParentOfType(context, PyExpressionStatement.class);
        PyExpression expression = exprStatement != null ? PsiTreeUtil.getChildOfType(exprStatement, PyExpression.class) : null;
        return ContainerUtil.createMaybeSingletonList(expression);
      }
    };
  }

  public static PostfixTemplateExpressionSelector currentStatementSelector() {
    return new PostfixTemplateExpressionSelectorBase(null) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        PsiElement elementAtCaret = PsiUtilCore.getElementAtOffset(context.getContainingFile(), offset - 1);
        if (elementAtCaret instanceof PsiComment) {
          return Collections.emptyList();
        }
        while (elementAtCaret != null) {
          if (elementAtCaret instanceof PyStatement) {
            return Collections.singletonList(elementAtCaret);
          }
          elementAtCaret = elementAtCaret.getParent();
        }
        return Collections.emptyList();
      }
    };
  }
}
