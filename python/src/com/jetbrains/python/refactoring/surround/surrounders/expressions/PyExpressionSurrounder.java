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
package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 6:06:34 PM
 */
public abstract class PyExpressionSurrounder implements Surrounder {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.refactoring.surround.surrounders.expressions.PyExpressionSurrounder");

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PyExpression);
    return isApplicable((PyExpression)elements[0]);
  }

  public abstract boolean isApplicable(@NotNull final PyExpression expr);

  public abstract TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PyExpression element)
    throws IncorrectOperationException;

  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
    throws IncorrectOperationException {
    return surroundExpression(project, editor, (PyExpression)elements[0]);
  }
}
