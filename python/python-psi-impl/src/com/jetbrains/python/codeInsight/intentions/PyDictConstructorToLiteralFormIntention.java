// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * Intention to convert dict constructor to dict literal expression with only explicit keyword arguments
 * For instance,
 * dict() -> {}
 * dict(a=3, b=5) -> {'a': 3, 'b': 5}
 * dict(foo) -> no transformation
 * dict(**foo) -> no transformation
 */
public final class PyDictConstructorToLiteralFormIntention extends PsiUpdateModCommandAction<PsiElement> {
  PyDictConstructorToLiteralFormIntention() {
    super(PsiElement.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext actionContext, @NotNull PsiElement element) {
    if (!(actionContext.file() instanceof PyFile)) {
      return null;
    }

    PyCallExpression expression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);

    if (expression != null && expression.isCalleeText("dict")) {
      final TypeEvalContext context = TypeEvalContext.codeAnalysis(actionContext.project(), actionContext.file());
      PyType type = context.getType(expression);
      if (type != null && type.isBuiltin()) {
        PyExpression[] argumentList = expression.getArguments();
        for (PyExpression argument : argumentList) {
          if (!(argument instanceof PyKeywordArgument)) return null;
        }
        return super.getPresentation(actionContext, element);
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.dict.constructor.to.dict.literal");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyCallExpression expression =
      PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
    if (expression != null) {
      replaceDictConstructor(expression, elementGenerator);
    }
  }

  private static void replaceDictConstructor(PyCallExpression expression, PyElementGenerator elementGenerator) {
    PyExpression[] argumentList = expression.getArguments();
    StringBuilder stringBuilder = new StringBuilder();

    int size = argumentList.length;

    for (int i = 0; i != size; ++i) {
      PyExpression argument = argumentList[i];
      if (argument instanceof PyKeywordArgument) {
        stringBuilder.append("'");
        stringBuilder.append(((PyKeywordArgument)argument).getKeyword());
        stringBuilder.append("' : ");
        stringBuilder.append(((PyKeywordArgument)argument).getValueExpression().getText());
        if (i != size-1)
          stringBuilder.append(",");
      }

    }
    PyDictLiteralExpression dict = (PyDictLiteralExpression)elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class,
                                                                                            "{" + stringBuilder + "}").getExpression();
    expression.replace(dict);
  }
}
