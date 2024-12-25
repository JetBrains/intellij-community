/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyFunctionInsertHandler extends ParenthesesInsertHandler<LookupElement> {
  public static PyFunctionInsertHandler INSTANCE = new PyFunctionInsertHandler();

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    super.handleInsert(context, item);
    if (hasParams(context, item)) {
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), getFunction(item));
    }
  }

  @Override
  protected boolean placeCaretInsideParentheses(@NotNull InsertionContext context, @NotNull LookupElement item) {
    return hasParams(context, item);
  }

  private static boolean hasParams(@NotNull InsertionContext context, @NotNull LookupElement item) {
    final PyFunction function = getFunction(item);
    return function != null && hasParams(context, function);
  }

  public static boolean hasParams(@NotNull InsertionContext context, @NotNull PyFunction function) {
    final PsiElement element = context.getFile().findElementAt(context.getStartOffset());
    PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
    final int implicitArgsCount;
    if (refExpr != null) {
      final var typeEvalContext = TypeEvalContext.codeInsightFallback(refExpr.getProject());
      final var resolveContext = PyResolveContext.defaultContext(typeEvalContext);
      implicitArgsCount = PyCallExpressionHelper.getImplicitArgumentCount(refExpr, function, resolveContext);
    }
    else {
      implicitArgsCount = 0;
    }
    return function.getParameterList().getParameters().length > implicitArgsCount;
  }

  private static @Nullable PyFunction getFunction(@NotNull LookupElement item) {
    return PyUtil.as(item.getPsiElement(), PyFunction.class);
  }
}
