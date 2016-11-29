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

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PyPostfixUtils {

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
    return selectorAllExpressionsWithCurrentOffset(Conditions.<PsiElement>alwaysTrue());
  }

  public static PostfixTemplateExpressionSelector selectorTopmost() {
    return selectorTopmost(Conditions.<PsiElement>alwaysTrue());
  }

  public static PostfixTemplateExpressionSelector selectorTopmost(Condition<PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        PyExpressionStatement exprStatement = PsiTreeUtil.getNonStrictParentOfType(context, PyExpressionStatement.class);
        PyExpression expression = exprStatement != null ? PsiTreeUtil.getChildOfType(exprStatement, PyExpression.class) : null;
        return ContainerUtil.<PsiElement>createMaybeSingletonList(expression);
      }
    };
  }

  public static PostfixTemplateExpressionSelector currentStatementSelector() {
    return new PostfixTemplateExpressionSelectorBase(null) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        PsiElement elementAtCaret = PsiUtilCore.getElementAtOffset(context.getContainingFile(), offset - 1);
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
