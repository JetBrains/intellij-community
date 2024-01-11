// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * Intention to convert dict literal expression to dict constructor if the keys are all string constants on a literal dict.
 * For instance,
 * {} -> dict
 * {'a': 3, 'b': 5} -> dict(a=3, b=5)
 * {a: 3, b: 5} -> no transformation
 */
public final class PyDictLiteralFormToConstructorIntention extends PsiUpdateModCommandAction<PsiElement> {
  PyDictLiteralFormToConstructorIntention() {
    super(PsiElement.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    PyDictLiteralExpression dictExpression =
      PsiTreeUtil.getParentOfType(element, PyDictLiteralExpression.class);

    if (dictExpression != null) {
      PyKeyValueExpression[] elements = dictExpression.getElements();
      for (PyKeyValueExpression expression : elements) {
        PyExpression key = expression.getKey();
        if (!(key instanceof PyStringLiteralExpression)) return null;
        String str = ((PyStringLiteralExpression)key).getStringValue();
        if (PyNames.isReserved(str)) return null;

        if (str.isEmpty() || Character.isDigit(str.charAt(0))) return null;
        if (!StringUtil.isJavaIdentifier(str)) return null;
      }
      return super.getPresentation(context, element);
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyDictLiteralExpression dictExpression =
      PsiTreeUtil.getParentOfType(element, PyDictLiteralExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
    if (dictExpression != null) {
      replaceDictLiteral(dictExpression, elementGenerator);
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.dict.literal.to.dict.constructor");
  }

  private static void replaceDictLiteral(PyDictLiteralExpression dictExpression, PyElementGenerator elementGenerator) {
    PyExpression[] argumentList = dictExpression.getElements();
    StringBuilder stringBuilder = new StringBuilder("dict(");
    int size = argumentList.length;
    for (int i = 0; i != size; ++i) {
      PyExpression argument = argumentList[i];
      if (argument instanceof PyKeyValueExpression) {
        PyExpression key = ((PyKeyValueExpression)argument).getKey();
        PyExpression value = ((PyKeyValueExpression)argument).getValue();
        if (key instanceof PyStringLiteralExpression && value != null) {
          stringBuilder.append(((PyStringLiteralExpression)key).getStringValue());
          stringBuilder.append("=");
          stringBuilder.append(value.getText());
          if (i != size-1)
            stringBuilder.append(", ");
        }
      }
    }
    stringBuilder.append(")");
    PyCallExpression callExpression = (PyCallExpression)elementGenerator.createFromText(LanguageLevel.forElement(dictExpression),
                                                     PyExpressionStatement.class, stringBuilder.toString()).getExpression();
    dictExpression.replace(callExpression);
  }
}
