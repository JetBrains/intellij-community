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
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenameUnsupportedExpressionProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return PsiTreeUtil.instanceOf(element, 
                                  PyStringLiteralExpression.class,
                                  PyNumericLiteralExpression.class,
                                  PyListLiteralExpression.class,
                                  PyDictLiteralExpression.class,
                                  PySetLiteralExpression.class,
                                  PyStarArgument.class,
                                  PyCallExpression.class,
                                  PyBinaryExpression.class,
                                  PySubscriptionExpression.class);
  }

  @Override
  public void renameElement(PsiElement element, String newName, UsageInfo[] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE = enabled;
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename(PsiElement element, @Nullable Editor editor) {
    return element;
  }
}
