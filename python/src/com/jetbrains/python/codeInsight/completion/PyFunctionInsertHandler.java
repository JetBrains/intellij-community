/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;

/**
 * @author yole
 */
public class PyFunctionInsertHandler extends ParenthesesInsertHandler<LookupElement> {
  public static PyFunctionInsertHandler INSTANCE = new PyFunctionInsertHandler();

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    super.handleInsert(context, item);
    if (hasParams(context, item)) {
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), (PyFunction) item.getObject());
    }
  }

  @Override
  protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
    return hasParams(context, item);
  }

  private static boolean hasParams(InsertionContext context, LookupElement item) {
    return hasParams(context, (PyFunction) item.getObject());
  }

  public static boolean hasParams(InsertionContext context, PyFunction function) {
    final PsiElement element = context.getFile().findElementAt(context.getStartOffset());
    PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
    int implicitArgsCount = refExpr != null
                            ? PyCallExpressionHelper.getImplicitArgumentCount(refExpr, function)
                            : 0;
    return function.getParameterList().getParameters().length > implicitArgsCount;
  }
}
